package tv.telegram.td

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.td.libcore.telegram.TdApi
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

sealed class FileDownloadState {
    data object Remote : FileDownloadState()
    data class Pending(val expectedSize: Int = 0) : FileDownloadState()
    data class Downloading(val downloaded: Long, val expected: Long) : FileDownloadState()
    data class Local(val path: String) : FileDownloadState()
    data class Failed(val reason: String) : FileDownloadState()
}

data class StreamingState(
    val fileId: Int,
    val path: String? = null,
    val downloadedSize: Long = 0,
    val expectedSize: Long = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val activeStart: Long = 0,
    val targetBytes: Long = 0,
    val epoch: Int = 0,
    val resetAtMs: Long = 0,
    val tailPath: String? = null,
    val tailStart: Long = 0,
    val tailEnd: Long = 0,
)

class TdFileRepository(
    private val client: TdClient = TdClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val windowBytes: Long = DEFAULT_WINDOW_BYTES,
    private val filesDirectory: String? = null,
) {

    private val _states = MutableStateFlow<Map<Int, FileDownloadState>>(emptyMap())
    val states: StateFlow<Map<Int, FileDownloadState>> = _states.asStateFlow()

    private val pendingDownloads = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val previewDownloads = ConcurrentHashMap<Int, PreviewRequest>()

    private val lastAccess = ConcurrentHashMap<Int, Long>()

    private val streamingStates = ConcurrentHashMap<Int, StreamingState>()

    private val streamMonitors = ConcurrentHashMap<Int, Any>()

    private fun monitorFor(fileId: Int): Any =
        streamMonitors.computeIfAbsent(fileId) { Any() }

    private fun wakeStreamWaiters(fileId: Int) {
        streamMonitors[fileId]?.let { m ->
            synchronized(m) { (m as java.lang.Object).notifyAll() }
        }
    }

    private data class PreviewRequest(
        val deferred: CompletableDeferred<String>,
        val limitBytes: Int,
    )

    init {
        scope.launch {
            client.fileUpdates.collect { update -> handleUpdateFile(update.file) }
        }
    }

    private fun handleUpdateFile(file: TdApi.File) {
        val local = file.local ?: return
        val fileId = file.id
        if (local.isDownloadingCompleted && local.path.isNotEmpty()) {
            touch(fileId)
            val d = pendingDownloads.remove(fileId)
            d?.complete(local.path)
            val pr = previewDownloads.remove(fileId)
            pr?.deferred?.complete(local.path)
            _states.value = _states.value + (fileId to FileDownloadState.Local(local.path))
            enforceCacheQuota()
        } else {
            val pr = previewDownloads[fileId]
            if (pr != null && local.path.isNotEmpty() && local.downloadedSize >= pr.limitBytes) {
                previewDownloads.remove(fileId)
                pr.deferred.complete(local.path)
            }
        }

        if (streamingStates[fileId] == null && local.isDownloadingActive && !local.isDownloadingCompleted) {
            _states.value = _states.value + (
                fileId to FileDownloadState.Downloading(
                    downloaded = local.downloadedSize.toLong(),
                    expected = file.expectedSize.toLong(),
                )
                )
        }

        val cur = streamingStates[fileId]
        if (cur != null) {
            val failed = !local.isDownloadingActive && !local.isDownloadingCompleted
                && local.path.isNotEmpty() && cur.path != null
            val reported = local.downloadedSize.toLong()
            val clamped = if (local.isDownloadingCompleted) reported
            else reported.coerceAtLeast(cur.activeStart)
            if (clamped != reported) {
                Log.d(
                    TAG,
                    "updateFile($fileId): reported downloadedSize $reported below " +
                        "activeStart ${cur.activeStart} — clamped (window re-point)",
                )
            }
            streamingStates[fileId] = cur.copy(
                path = local.path.takeIf { it.isNotEmpty() } ?: cur.path,
                downloadedSize = clamped,
                expectedSize = file.expectedSize.toLong().takeIf { it > 0 } ?: cur.expectedSize,
                completed = local.isDownloadingCompleted,
                failed = failed,
            )
            wakeStreamWaiters(fileId)
        }
    }

    suspend fun ensureLocal(fileId: Int, priority: Int = 32, timeoutMs: Long = 60_000L): String? {
        val current = _states.value[fileId]
        if (current is FileDownloadState.Local) return current.path

        _states.value = _states.value + (fileId to FileDownloadState.Pending())

        return try {
            val fileObj = client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L).valueOrNull<TdApi.File>()
            if (fileObj == null) {
                Log.w(TAG, "ensureLocal($fileId): getFile failed")
                _states.value = _states.value + (fileId to FileDownloadState.Failed("getFile failed"))
                return null
            }
            val local = fileObj.local
            if (local != null && local.isDownloadingCompleted) {
                _states.value = _states.value + (fileId to FileDownloadState.Local(local.path))
                return local.path
            }

            val expectedBytes = fileObj.expectedSize.toLong().takeIf { it > 0 }
                ?: fileObj.size.toLong()
            if (!hasRoomFor(expectedBytes)) {
                Log.w(TAG, "ensureLocal($fileId): $expectedBytes bytes do not fit in local storage")
                _states.value = _states.value +
                    (fileId to FileDownloadState.Failed("not enough free space"))
                return null
            }

            val deferred = CompletableDeferred<String>()
            pendingDownloads[fileId] = deferred
            client.send(TdApi.DownloadFile(fileId, priority, 0, 0, false))

            val path = withTimeoutOrNull(timeoutMs) {
                try { deferred.await() } catch (_: Throwable) { null }
            }
            if (path == null) {
                pendingDownloads.remove(fileId)
                _states.value = _states.value + (fileId to FileDownloadState.Failed("download timeout"))
            }
            path
        } catch (e: Throwable) {
            Log.w(TAG, "ensureLocal($fileId) threw", e)
            pendingDownloads.remove(fileId)
            _states.value = _states.value + (fileId to FileDownloadState.Failed("exception: ${e.javaClass.simpleName}"))
            null
        }
    }

    suspend fun ensurePreview(
        fileId: Int,
        limitBytes: Int = 2 * 1024 * 1024,
        priority: Int = 32,
        timeoutMs: Long = 20_000L,
    ): String? {
        val current = _states.value[fileId]
        if (current is FileDownloadState.Local) return current.path

        val fileObj = client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L).valueOrNull<TdApi.File>()
        if (fileObj != null) {
            val local = fileObj.local
            if (local.path.isNotEmpty() &&
                (local.isDownloadingCompleted || local.downloadedSize >= limitBytes)
            ) {
                return local.path
            }
        }

        val deferred = CompletableDeferred<String>()
        previewDownloads[fileId] = PreviewRequest(deferred, limitBytes)
        client.send(TdApi.DownloadFile(fileId, priority, 0, limitBytes, false))

        val path = withTimeoutOrNull(timeoutMs) {
            try { deferred.await() } catch (_: Throwable) { null }
        }
        if (path == null) {
            previewDownloads.remove(fileId)
        }
        return path
    }

    fun stateFor(fileId: Int): FileDownloadState? = _states.value[fileId]

    fun touch(fileId: Int) {
        lastAccess[fileId] = System.currentTimeMillis()
    }

    fun deleteLocalFile(fileId: Int) {
        tailProbed.remove(fileId)
        runCatching {
            streamingStates[fileId]?.tailPath?.let { File(it).delete() }
        }
        streamingStates.remove(fileId)
        wakeStreamWaiters(fileId)
        pendingDownloads.remove(fileId)?.cancel()
        previewDownloads.remove(fileId)
        lastAccess.remove(fileId)
        client.send(TdApi.DeleteFile(fileId))
        _states.value = _states.value + (fileId to FileDownloadState.Remote)
        Log.d(TAG, "deleteLocalFile(fileId=$fileId)")
    }

    fun enforceCacheQuota() {
        val localEntries = _states.value.entries
            .filter { (_, st) -> st is FileDownloadState.Local }
        if (localEntries.isEmpty()) return

        val total = localEntries.sumOf { (_, st) ->
            runCatching { File((st as FileDownloadState.Local).path).length() }.getOrDefault(0L)
        }
        if (total <= CACHE_QUOTA_BYTES) return

        val inUse = streamingStates.keys + pendingDownloads.keys + previewDownloads.keys
        val evictable = localEntries
            .filter { (fileId, _) -> fileId !in inUse }
            .sortedBy { (fileId, _) -> lastAccess[fileId] ?: 0L }

        var freed = 0L
        for ((fileId, _) in evictable) {
            if (total - freed <= CACHE_QUOTA_FLOOR_BYTES) break
            val sz = runCatching {
                File((_states.value[fileId] as FileDownloadState.Local).path).length()
            }.getOrDefault(0L)
            deleteLocalFile(fileId)
            freed += sz
            Log.i(TAG, "enforceCacheQuota: evicted fileId=$fileId (${sz / 1024 / 1024} MB)")
        }
    }

    suspend fun fileInfo(fileId: Int): TdApi.File? =
        client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L).valueOrNull<TdApi.File>()

    // ── Progressive / streaming playback ────────────────────────────────────

    private val tailProbed = mutableSetOf<Int>()
    private val probeLatches = ConcurrentHashMap<Int, CountDownLatch>()

    fun startStreaming(fileId: Int, priority: Int = 1) {
        if (streamingStates.containsKey(fileId)) return
        val existing = _states.value[fileId]
        if (existing is FileDownloadState.Local) {
            val onDisk = runCatching { File(existing.path).length() }.getOrDefault(0L)
            if (onDisk > 0L) {
                streamingStates[fileId] = StreamingState(
                    fileId = fileId,
                    path = existing.path,
                    expectedSize = onDisk,
                    completed = true,
                )
                Log.d(TAG, "startStreaming(fileId=$fileId): already local ($onDisk bytes), marked complete")
                return
            }
            Log.w(TAG, "startStreaming(fileId=$fileId): state says local but 0 bytes on disk — restarting")
        }
        resetStreamWindow(fileId, 0, priority)
        if (TAIL_PREFETCH_ENABLED && probeLatches[fileId] == null && tailProbed.add(fileId)) {
            val latch = CountDownLatch(1)
            probeLatches[fileId] = latch
            scope.launch {
                try {
                    probeTailWhenSized(fileId)
                } finally {
                    latch.countDown()
                    probeLatches.remove(fileId)
                }
            }
        }
        Log.d(TAG, "startStreaming(fileId=$fileId, priority=$priority): contiguous from 0")
    }

    val streamWindowBytes: Long get() = windowBytes

    private fun Long.toIntOffset(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun hasRoomFor(bytes: Long): Boolean {
        if (bytes <= 0L) return true
        val dir = filesDirectory ?: return true
        val free = runCatching { StatFs(dir).availableBytes }.getOrDefault(Long.MAX_VALUE)
        return bytes <= (free * 0.6).toLong()
    }

    fun cancelDownload(fileId: Int) {
        tailProbed.remove(fileId)
        streamingStates.remove(fileId)
        wakeStreamWaiters(fileId)
        pendingDownloads.remove(fileId)?.cancel()
        client.send(TdApi.CancelDownloadFile(fileId, false))
        Log.d(TAG, "cancelDownload(fileId=$fileId)")
    }

    fun streamState(fileId: Int): StreamingState? = streamingStates[fileId]

    fun awaitStreamBytes(fileId: Int, minBytes: Long, timeoutMs: Long = 30_000): Boolean {
        val monitor = monitorFor(fileId)
        val deadline = System.currentTimeMillis() + timeoutMs
        var failedSince = -1L
        synchronized(monitor) {
            while (true) {
                val st = streamingStates[fileId] ?: return false
                if (st.completed || st.downloadedSize >= minBytes) return true
                if (st.failed) {
                    val now = System.currentTimeMillis()
                    if (failedSince < 0) failedSince = now
                    else if (now - failedSince >= FAILED_GRACE_MS) return false
                } else {
                    failedSince = -1L
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                try {
                    (monitor as java.lang.Object).wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
    }

    fun awaitStreamPath(fileId: Int, timeoutMs: Long = 15_000): String? {
        val monitor = monitorFor(fileId)
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(monitor) {
            while (true) {
                val st = streamingStates[fileId] ?: return null
                if (st.path != null) return st.path
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                try {
                    (monitor as java.lang.Object).wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
    }

    fun stopStreaming(fileId: Int) {
        streamingStates.remove(fileId)
        wakeStreamWaiters(fileId)
    }

    // ── Disk water-mark emergency ───────────────────────────────────────────

    fun ensureStorageHeadroom(activeFileId: Int?, activePos: Long) {
        val dir = filesDirectory ?: return
        if (StatFs(dir).availableBytes.toLong() >= LOW_DISK_WATERMARK_BYTES) return
        Log.w(TAG, "ensureStorageHeadroom: low disk (< ${LOW_DISK_WATERMARK_BYTES / 1024 / 1024}MB free), evicting inactive files")
        evictInactiveLocalFiles()
        if (StatFs(dir).availableBytes.toLong() >= LOW_DISK_WATERMARK_BYTES) return
        if (activeFileId != null) {
            Log.w(TAG, "ensureStorageHeadroom: still low, resetting stream window for file $activeFileId at pos $activePos")
            resetStreamWindow(activeFileId, activePos)
        }
    }

    private fun evictInactiveLocalFiles() {
        val inUse = streamingStates.keys + pendingDownloads.keys + previewDownloads.keys
        val evictable = _states.value.entries
            .filter { (fileId, st) -> st is FileDownloadState.Local && fileId !in inUse }
            .sortedBy { (fileId, _) -> lastAccess[fileId] ?: 0L }
        for ((fileId, _) in evictable) {
            deleteLocalFile(fileId)
        }
    }

    fun awaitTailProbe(fileId: Int, timeoutMs: Long = 12_000L) {
        try {
            probeLatches[fileId]?.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private suspend fun probeTailWhenSized(fileId: Int) {
        val deadline = System.currentTimeMillis() + TAIL_PROBE_TIMEOUT_MS / 2
        var size = streamingStates[fileId]?.expectedSize ?: 0L
        while (size < TAIL_PROBE_MIN_BYTES && System.currentTimeMillis() < deadline) {
            delay(50L)
            size = streamingStates[fileId]?.expectedSize ?: 0L
        }
        if (size < TAIL_PROBE_MIN_BYTES) return
        probeTail(fileId, size)
    }

    private suspend fun probeTail(fileId: Int, fileSize: Long) {
        val path = awaitStreamPath(fileId, timeoutMs = TAIL_PROBE_TIMEOUT_MS) ?: return
        val probeLen = minOf(fileSize, TAIL_PROBE_BYTES).toInt()
        val probeStart = fileSize - probeLen
        Log.d(TAG, "probeTail($fileId): reading [$probeStart, $fileSize) for moov")
        client.send(TdApi.DownloadFile(fileId, 1, probeStart.toIntOffset(), probeLen, false))
        if (!waitForFileLength(path, fileSize, TAIL_PROBE_TIMEOUT_MS)) {
            Log.w(TAG, "probeTail($fileId): tail did not arrive in time")
            resumeSequential(fileId)
            return
        }
        val buffer = ByteArray(probeLen)
        val copied = runCatching {
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(probeStart)
                raf.readFully(buffer)
            }
        }.isSuccess
        if (!copied) {
            resumeSequential(fileId)
            return
        }
        val moov = findMoov(probeStart, buffer, fileSize)
        if (moov == null) {
            Log.d(TAG, "probeTail($fileId): no moov in the last ${probeLen}B")
            resumeSequential(fileId)
            return
        }

        val dir = filesDirectory?.let { File(it, "tails") }
        if (dir == null || !runCatching { dir.mkdirs() }.isSuccess) {
            resumeSequential(fileId)
            return
        }
        val tailFile = File(dir, "$fileId.tail")
        val written = runCatching { tailFile.writeBytes(buffer) }.isSuccess
        if (!written) {
            resumeSequential(fileId)
            return
        }

        val cur = streamingStates[fileId] ?: return
        streamingStates[fileId] = cur.copy(
            tailPath = tailFile.absolutePath,
            tailStart = moov.first,
            tailEnd = fileSize,
            epoch = cur.epoch + 1,
            resetAtMs = System.currentTimeMillis(),
        )
        client.send(TdApi.CancelDownloadFile(fileId, false))
        client.send(TdApi.DeleteFile(fileId))
        client.send(TdApi.DownloadFile(fileId, 1, 0, 0, false))
        wakeStreamWaiters(fileId)
        Log.i(
            TAG,
            "probeTail($fileId): moov at ${moov.first} (${moov.second}B) — tail kept aside, " +
                "prefix restarts at 0",
        )
    }

    private fun resumeSequential(fileId: Int) {
        client.send(TdApi.DownloadFile(fileId, 1, 0, 0, false))
    }

    private suspend fun waitForFileLength(path: String, target: Long, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val len = runCatching { File(path).length() }.getOrDefault(0L)
            if (len >= target) return true
            delay(50L)
        }
        return false
    }

    private fun findMoov(bufOffset: Long, buf: ByteArray, fileSize: Long): Pair<Long, Long>? {
        var i = 0
        val limit = buf.size - 8
        while (i <= limit) {
            if (buf[i + 4] == 'm'.code.toByte() && buf[i + 5] == 'o'.code.toByte() &&
                buf[i + 6] == 'o'.code.toByte() && buf[i + 7] == 'v'.code.toByte()
            ) {
                var size = readU32(buf, i)
                if (size == 1L && i + 16 <= buf.size) size = readU64(buf, i + 8)
                val start = bufOffset + i
                if (size >= 8L && size <= MAX_MOOV_BYTES && start + size <= fileSize) {
                    return start to size
                }
            }
            i++
        }
        return null
    }

    private fun readU32(buf: ByteArray, at: Int): Long =
        ((buf[at].toLong() and 0xFF) shl 24) or
            ((buf[at + 1].toLong() and 0xFF) shl 16) or
            ((buf[at + 2].toLong() and 0xFF) shl 8) or
            (buf[at + 3].toLong() and 0xFF)

    private fun readU64(buf: ByteArray, at: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (buf[at + k].toLong() and 0xFF)
        return v
    }

    fun resetStreamWindow(fileId: Int, pos: Long = 0, priority: Int = 1) {
        tailProbed.remove(fileId)
        val cur = streamingStates[fileId]
        client.send(TdApi.CancelDownloadFile(fileId, false))
        client.send(TdApi.DeleteFile(fileId))
        streamingStates[fileId] = StreamingState(
            fileId = fileId,
            targetBytes = Long.MAX_VALUE,
            epoch = (cur?.epoch ?: 0) + 1,
            resetAtMs = System.currentTimeMillis(),
        )
        wakeStreamWaiters(fileId)
        client.send(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        _states.value = _states.value + (fileId to FileDownloadState.Pending())
        Log.i(TAG, "resetStreamWindow(fileId=$fileId, wasAt=$pos): restarting contiguous from 0")
    }

    fun invalidateStreamPath(fileId: Int, path: String) {
        streamingStates[fileId]?.let { cur ->
            if (cur.path == path) {
                streamingStates[fileId] = cur.copy(path = null)
                wakeStreamWaiters(fileId)
            }
        }
    }

    companion object {
        private const val TAG = "TdFileRepo"

        private const val CACHE_QUOTA_BYTES = 256L * 1024 * 1024
        private const val CACHE_QUOTA_FLOOR_BYTES = 128L * 1024 * 1024

        private const val DEFAULT_WINDOW_BYTES = 128L * 1024 * 1024

        private const val LOW_DISK_WATERMARK_BYTES = 800L * 1024 * 1024

        private const val TAIL_PREFETCH_ENABLED = false

        private const val TAIL_PROBE_BYTES = 4L * 1024 * 1024
        private const val TAIL_PROBE_MIN_BYTES = 128L * 1024
        private const val TAIL_PROBE_TIMEOUT_MS = 20_000L
        private const val MAX_MOOV_BYTES = 64L * 1024 * 1024

        private const val FAILED_GRACE_MS = 1_000L
    }
}
