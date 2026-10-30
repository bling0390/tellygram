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

/**
 * Progressive (streaming) download state for a file that is being played
 * while still downloading. Driven by updateFile: downloadedSize grows until
 * the file is complete. A single TDLib download task per fileId is active at
 * a time, so playback reads sequentially and waits for the download to catch
 * up when read() reaches the downloaded frontier.
 *
 * Seeking re-points the download to a new offset (seekStream), which leaves
 * the previously downloaded bytes as a frozen [ranges] segment and starts a
 * fresh active segment at [activeStart]. Bytes between segments are holes
 * that were never downloaded; read() must not treat them as available just
 * because the file on disk is longer (TDLib seeks the file and writes past
 * the hole).
 */
data class StreamingState(
    val fileId: Int,
    val path: String? = null,
    val downloadedSize: Long = 0,
    val expectedSize: Long = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    // Start offset of the current window. The file is deleted whenever the
    // window moves (see seekStream), so the local file holds exactly this
    // window, written sequentially from here — bytes below it are not data.
    // TdDataSource measures how far the window has got from the FILE's own
    // length: TDLib's downloadedSize counts bytes on disk rather than a
    // contiguous prefix, and using it as a frontier served holes as data.
    val activeStart: Long = 0,
    // Absolute end position of the current windowed download task
    // (activeStart + limit). Windowed streaming only downloads up to this
    // frontier instead of the whole file, so disk usage stays ≈ playhead +
    // window instead of the full video size. Grows as playback advances
    // (extendStream).
    val targetBytes: Long = 0,
    // Bumped every time the file is deleted and re-downloaded from a new
    // offset (emergency disk-watermark reset). TdDataSource watches this to
    // know when to close the old RandomAccessFile and re-open the new one.
    val epoch: Int = 0,
    // When this window was (re)created. TDLib can still be reporting progress
    // for the previous download (including a path to the file that was just
    // deleted), so readers must not trust anything written before this
    // moment: the file is only data once TDLib has touched it again.
    val resetAtMs: Long = 0,
    // Tail window [tailStart, tailEnd) kept in a SIDE file, never in the streaming file: the
    // streaming file must stay a pure prefix from byte 0, or its length can no longer tell
    val tailPath: String? = null,
    val tailStart: Long = 0,
    val tailEnd: Long = 0,
)

class TdFileRepository(
    private val client: TdClient = TdClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    // Windowed streaming: DownloadFile only fetches up to this many bytes
    // ahead of the playhead (instead of the whole file), so a multi-GB video
    // doesn't fill tiny TV storage. Sized from device RAM (see MainViewModel:
    // ram/18, clamped 64-256MB).
    private val windowBytes: Long = DEFAULT_WINDOW_BYTES,
    // TDLib files directory — used for StatFs low-disk-watermark checks.
    private val filesDirectory: String? = null,
) {

    private val _states = MutableStateFlow<Map<Int, FileDownloadState>>(emptyMap())
    val states: StateFlow<Map<Int, FileDownloadState>> = _states.asStateFlow()

    private val pendingDownloads = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val previewDownloads = ConcurrentHashMap<Int, PreviewRequest>()

    // LRU bookkeeping for the media cache quota: fileId -> last-touched
    // epoch ms. Touched whenever a file is used (streaming start, download
    // completion). enforceQuota() evicts the oldest files once the total
    // on-disk size of locally downloaded media exceeds CACHE_QUOTA_BYTES.
    private val lastAccess = ConcurrentHashMap<Int, Long>()

    // Streaming (progressive playback) state, one entry per fileId that is
    // being played while still downloading.
    private val streamingStates = ConcurrentHashMap<Int, StreamingState>()

    // Per-file monitors so the loader thread can block on Object.wait()
    // instead of busy-polling Thread.sleep(50) in awaitStreamPath / awaitStreamBytes.
    private val streamMonitors = ConcurrentHashMap<Int, Any>()

    private fun monitorFor(fileId: Int): Any =
        streamMonitors.computeIfAbsent(fileId) { Any() }

    /** Wake any thread blocked in awaitStreamPath/awaitStreamBytes for [fileId]. */
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
            // UpdateFile has its own high-capacity channel (see TdClient), so
            // stream progress can't be starved by chat/media updates.
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
            // A file just landed on disk — check the cache quota right away
            // so a long playlist can't grow the cache past the cap.
            enforceCacheQuota()
        } else {
            // Preview download: complete as soon as we have the requested prefix.
            val pr = previewDownloads[fileId]
            if (pr != null && local.path.isNotEmpty() && local.downloadedSize >= pr.limitBytes) {
                previewDownloads.remove(fileId)
                pr.deferred.complete(local.path)
            }
        }

        // Plain downloads (photo preview, thumbnails): expose the byte counts so the UI can
        // keeps its own state below, with the activeStart clamp for re-pointed windows.
        if (streamingStates[fileId] == null && local.isDownloadingActive && !local.isDownloadingCompleted) {
            _states.value = _states.value + (
                fileId to FileDownloadState.Downloading(
                    downloaded = local.downloadedSize.toLong(),
                    expected = file.expectedSize.toLong(),
                )
                )
        }

        // Progressive download progress for streaming playback.
        val cur = streamingStates[fileId]
        if (cur != null) {
            val failed = !local.isDownloadingActive && !local.isDownloadingCompleted
                && local.path.isNotEmpty() && cur.path != null
            // TDLib keeps reporting the previous request's progress for a
            // moment after seekStream re-points the download, and that value
            // can sit BELOW the new window's start. Storing it verbatim left
            // the state incoherent (activeStart ahead of downloadedSize), so
            // every availability check failed and the reader waited out its
            // budget on a download that had nothing reported for it yet
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

            // A whole-file download must never fill the TV's storage: with every video now
            // streaming first, this path is the fallback, and it is the only one that can grow
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

    /**
     * Download only the first [limitBytes] of a file — enough to start a
     * muted hover-preview without waiting for the full video. Never marks
     * the file as fully Local (a later ensureLocal re-downloads the rest).
     */
    suspend fun ensurePreview(
        fileId: Int,
        limitBytes: Int = 2 * 1024 * 1024,
        priority: Int = 32,
        timeoutMs: Long = 20_000L,
    ): String? {
        val current = _states.value[fileId]
        if (current is FileDownloadState.Local) return current.path

        // Already have enough bytes on disk? Use them.
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

    /** Mark [fileId] as recently used for LRU eviction purposes. */
    fun touch(fileId: Int) {
        lastAccess[fileId] = System.currentTimeMillis()
    }

    /**
     * Delete the local copy of [fileId] (keeps the remote reference — a
     * later ensureLocal / startStreaming re-downloads it). Called when a
     * video finishes playing and by the cache quota eviction.
     */
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
        // Best-effort: drop the state so the UI no longer reports it local.
        _states.value = _states.value + (fileId to FileDownloadState.Remote)
        Log.d(TAG, "deleteLocalFile(fileId=$fileId)")
    }

    /**
     * Enforce the media cache quota: if the total size of locally stored
     * media exceeds [CACHE_QUOTA_BYTES], evict the least-recently-used
     * files (skipping any that are actively downloading / being previewed)
     * until total size drops to [CACHE_QUOTA_FLOOR_BYTES].
     */
    fun enforceCacheQuota() {
        val localEntries = _states.value.entries
            .filter { (_, st) -> st is FileDownloadState.Local }
        if (localEntries.isEmpty()) return

        val total = localEntries.sumOf { (_, st) ->
            runCatching { File((st as FileDownloadState.Local).path).length() }.getOrDefault(0L)
        }
        if (total <= CACHE_QUOTA_BYTES) return

        // Active downloads (streaming, pending, preview) must not be evicted.
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

    /**
     * Fetch current file metadata (size / expectedSize / local state) from
     * TDLib. Returns null if the query fails or times out.
     */
    suspend fun fileInfo(fileId: Int): TdApi.File? =
        client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L).valueOrNull<TdApi.File>()

    // ── Progressive / streaming playback ────────────────────────────────────

    /**
     * Start (or reuse) a sequential full-file download for progressive playback.
     * Idempotent: repeated calls for the same fileId keep the existing task.
     * If the file is already fully downloaded locally, mark the streaming
     * state complete immediately — a completed file never fires updateFile,
     * so without this awaitStreamPath would hang and playback would fail.
     */
    // One probe per file per download generation. Cleared whenever the local copy is
    // dropped, so a re-download probes again.
    private val tailProbed = mutableSetOf<Int>()
    // One gate per file being probed: the data source waits on it before opening the file, so
    private val probeLatches = ConcurrentHashMap<Int, CountDownLatch>()

    fun startStreaming(fileId: Int, priority: Int = 1) {
        if (streamingStates.containsKey(fileId)) return
        val existing = _states.value[fileId]
        if (existing is FileDownloadState.Local) {
            // Measure the file before declaring the stream complete. A Local entry can
            // outlive its bytes (our own deleteLocalFile on player exit, an evicted cache
            // file), and expecting "completed, size 0" made TdDataSource report end-of-input
            // at byte 0 — which ExoPlayer surfaces as UnrecognizedInputFormatException
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
        // Fresh contiguous download from 0. The delete matters: bytes left by
        // an earlier attempt (a windowed download from an older build, or a
        // seek) are NOT a prefix, and serving them as one is what fed the
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

    /**
     * Grow the windowed download frontier to [targetBytes] (absolute file
     * position). Continues the sequential write from the current downloaded
     * size — no holes within the active segment. No-op if the target is
     * already covered or the file is complete.
     */
    /** Current window size used for windowed streaming (bytes). */
    val streamWindowBytes: Long get() = windowBytes

    // TDLib's DownloadFile offset/limit are int32. windowBytes is clamped to
    // ≤256MB, but seek offsets / downloaded sizes are Longs and could exceed
    // Int.MAX_VALUE for >2GB files — clamp instead of wrapping negative.
    private fun Long.toIntOffset(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** True when [bytes] fits in 60% of the free space on the files volume. */
    private fun hasRoomFor(bytes: Long): Boolean {
        if (bytes <= 0L) return true
        val dir = filesDirectory ?: return true
        val free = runCatching { StatFs(dir).availableBytes }.getOrDefault(Long.MAX_VALUE)
        return bytes <= (free * 0.6).toLong()
    }

    /**
     * Cancel an in-flight download for [fileId] (progressive streaming or
     * ensureLocal). Called when the player moves away from a file so abandoned
     * downloads don't keep consuming bandwidth in the background. No-op if the
     * file is already local or nothing is downloading.
     */
    fun cancelDownload(fileId: Int) {
        tailProbed.remove(fileId)
        streamingStates.remove(fileId)
        wakeStreamWaiters(fileId)
        // Unblock a waiting ensureLocal() coroutine immediately (its
        // await returns null → the pending entry is dropped, not marked
        // Local). The next open of this file re-downloads from scratch.
        pendingDownloads.remove(fileId)?.cancel()
        client.send(TdApi.CancelDownloadFile(fileId, false))
        Log.d(TAG, "cancelDownload(fileId=$fileId)")
    }

    fun streamState(fileId: Int): StreamingState? = streamingStates[fileId]

    /**
     * Block until at least [minBytes] have been downloaded (or the file is
     * complete / failed). Polls the updateFile-driven state; used by the
     * streaming DataSource on ExoPlayer's loader thread. Returns false on
     * timeout or failure.
     */
    fun awaitStreamBytes(fileId: Int, minBytes: Long, timeoutMs: Long = 30_000): Boolean {
        val monitor = monitorFor(fileId)
        val deadline = System.currentTimeMillis() + timeoutMs
        var failedSince = -1L
        synchronized(monitor) {
            while (true) {
                val st = streamingStates[fileId] ?: return false
                if (st.completed || st.downloadedSize >= minBytes) return true
                if (st.failed) {
                    // `failed` goes true transiently during a seek re-target:
                    // TDLib emits UpdateFile with isDownloadingActive=false
                    // (old download cancelled, new one not yet started) while
                    // path is already set. Treating that instant as terminal
                    // turns every seek into a spurious "download stalled".
                    // Only fail once it has stayed failed for a grace period.
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

    /** Block until TDLib has assigned a local path for the streaming file. */
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

    /** Drop the streaming state (called when leaving the player). */
    fun stopStreaming(fileId: Int) {
        streamingStates.remove(fileId)
        wakeStreamWaiters(fileId)
    }

    // ── Disk water-mark emergency ───────────────────────────────────────────

    /**
     * Check free space on the TDLib files volume. If it drops below
     * [LOW_DISK_WATERMARK_BYTES]: first evict inactive fully-downloaded files
     * (LRU); if still critically low, reset the actively-streamed file to a
     * fresh window at [activePos] — deleting the local copy and re-downloading
     * only [windowBytes] from the playhead, so disk usage collapses from
     * "playhead + window" back to just the window. Playback stutters briefly
     * (re-download) but the disk can never fill up.
     */
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

    /** Delete all fully-downloaded files not currently in use (LRU order). */
    private fun evictInactiveLocalFiles() {
        val inUse = streamingStates.keys + pendingDownloads.keys + previewDownloads.keys
        val evictable = _states.value.entries
            .filter { (fileId, st) -> st is FileDownloadState.Local && fileId !in inUse }
            .sortedBy { (fileId, _) -> lastAccess[fileId] ?: 0L }
        for ((fileId, _) in evictable) {
            deleteLocalFile(fileId)
        }
    }

    /**
     * Fetches the last [TAIL_PROBE_BYTES] of [fileId] once, looks for the MP4 'moov' box and,
     * when it is there, records it as a known-good tail window. Playback can then start from
     * the prefix while the sequential download continues; a non-faststart MP4 becomes playable
     *
     * Any failure simply resumes the sequential download: the tail is an optimisation, never a
     * requirement.
     */
    /** Blocks until the tail probe finishes for [fileId], or the timeout expires. */
    fun awaitTailProbe(fileId: Int, timeoutMs: Long = 12_000L) {
        try {
            probeLatches[fileId]?.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Waits for the size to be known (updateFile), then probes once. */
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

        // Keep the tail in our own file so the streaming file can go back to being a plain
        // contiguous prefix from 0 (the invariant everything else relies on).
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
        // Drop the sparse temp file and start the prefix over from byte 0.
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

    /** Back to the whole-file sequential download after a probe. */
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

    /**
     * Scans [buf] (which covers [bufOffset, bufOffset + buf.size)) for a 'moov' box whose
     * declared size lands inside the file. Returns (offset, size) or null.
     */
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

    /**
     * Delete the local copy of [fileId] and restart windowed download at
     * [pos]. Used by the disk water-mark: collapses on-disk usage back to a
     * single window. TdDataSource detects the epoch bump and re-opens the
     * (recreated) file.
     */
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
        // Whole file, sequential from 0 (limit 0 = no limit). Disk usage is
        // bounded by the cache quota and the water-mark eviction instead of by a
        // window — the price of never serving a hole. [pos] is only logged, so
        // the caller's intent stays visible.
        client.send(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        _states.value = _states.value + (fileId to FileDownloadState.Pending())
        Log.i(TAG, "resetStreamWindow(fileId=$fileId, wasAt=$pos): restarting contiguous from 0")
    }

    /**
     * Forget a streamed path that is no longer on disk.
     *
     * A window reset deletes the file, but TDLib's next UpdateFile can still
     * carry the old temp path, and awaitStreamPath would hand that dead path to
     * Dropping it parks readers until the recreated file is reported.
     */
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

        // Media cache quota: total on-disk size of locally downloaded media
        // (videos, photos, previews). TV boxes have tiny storage (6-8 GB), so
        // cap aggressively: evict oldest files once total exceeds 256 MB and
        // keep evicting down to the 128 MB floor.
        private const val CACHE_QUOTA_BYTES = 256L * 1024 * 1024
        private const val CACHE_QUOTA_FLOOR_BYTES = 128L * 1024 * 1024

        // Windowed streaming fallback (used when MainViewModel doesn't pass a
        // RAM-derived value): fetch this many bytes ahead of the playhead.
        private const val DEFAULT_WINDOW_BYTES = 128L * 1024 * 1024

        // If the TDLib files volume has less than this much free space,
        // start evicting inactive files, then reset the active stream window.
        private const val LOW_DISK_WATERMARK_BYTES = 800L * 1024 * 1024

        // each broke playback outright, so the probe does not run in shipped builds. Re-enable
        // only with a device in hand and a log capture that is not all NUL bytes.
        private const val TAIL_PREFETCH_ENABLED = false

        // Tail probe: how much of the end of the file to fetch to look for the MP4 index.
        private const val TAIL_PROBE_BYTES = 4L * 1024 * 1024
        private const val TAIL_PROBE_MIN_BYTES = 128L * 1024
        private const val TAIL_PROBE_TIMEOUT_MS = 20_000L
        // Sanity cap for a 'moov' box (real ones are KB to a few MB).
        private const val MAX_MOOV_BYTES = 64L * 1024 * 1024

        // `failed` in StreamingState goes true transiently during a seek
        // re-target (old download cancelled, new one not yet active).
        // awaitStreamBytes only treats it as terminal after it has persisted
        // this long, so a seek doesn't surface a spurious "download stalled".
        private const val FAILED_GRACE_MS = 1_000L
    }
}
