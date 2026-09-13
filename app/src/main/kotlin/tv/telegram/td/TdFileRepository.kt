package tv.telegram.td

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.ConcurrentHashMap

sealed class FileDownloadState {
    data object Remote : FileDownloadState()
    data class Pending(val expectedSize: Int = 0) : FileDownloadState()
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
            // (frontier=3.5MB vs pos=866MB in the 2026-09-13 log).
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
            // Never let a file fetch crash the app — callers fall back to placeholder.
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
    fun startStreaming(fileId: Int, priority: Int = 1) {
        if (streamingStates.containsKey(fileId)) return
        val existing = _states.value[fileId]
        if (existing is FileDownloadState.Local) {
            streamingStates[fileId] = StreamingState(
                fileId = fileId,
                path = existing.path,
                expectedSize = 0,
                completed = true,
            )
            Log.d(TAG, "startStreaming(fileId=$fileId): already local, marked complete")
            return
        }
        // Windowed download: fetch [0, windowBytes) only — playback advances
        // the window via extendStream as the playhead moves.
        //
        // The file is deleted first. Bytes left by an earlier session are of
        // unknown shape (a past seek may have downloaded a window far from 0),
        // and serving them as a contiguous prefix is what made a fresh
        // playback of an already-touched file read zeros and fail container
        // sniffing — "None of the available extractors could read the stream"
        // (third live log, 2026-09-13). A fresh window costs a re-download; a
        // hole costs correctness.
        resetStreamWindow(fileId, 0, priority)
        Log.d(TAG, "startStreaming(fileId=$fileId, priority=$priority, window=${windowBytes / 1024 / 1024}MB)")
    }

    /**
     * Re-target the active download to start at [offset] (used on seek).
     *
     * Goes through [resetStreamWindow] deliberately: the previous window is
     * deleted rather than remembered as a frozen range, so the local file
     * always holds exactly one contiguous window and "is this byte real?" is
     * answerable from the file itself. Keeping those bytes alive is what let a
     * stale range hand the extractor hole data.
     */
    fun seekStream(fileId: Int, offset: Long, priority: Int = 1) {
        Log.d(TAG, "seekStream(fileId=$fileId, offset=$offset)")
        resetStreamWindow(fileId, offset, priority)
    }

    /**
     * Grow the windowed download frontier to [targetBytes] (absolute file
     * position). Continues the sequential write from the current downloaded
     * size — no holes within the active segment. No-op if the target is
     * already covered or the file is complete.
     */
    fun extendStream(fileId: Int, targetBytes: Long, priority: Int = 1) {
        streamingStates[fileId]?.let { cur ->
            if (cur.completed) return
            if (targetBytes <= cur.targetBytes) return
            val limit = (targetBytes - cur.downloadedSize).toIntOffset().coerceAtLeast(1)
            streamingStates[fileId] = cur.copy(targetBytes = targetBytes)
            client.send(TdApi.DownloadFile(fileId, priority, cur.downloadedSize.toIntOffset(), limit, false))
            Log.d(TAG, "extendStream(fileId=$fileId, to=$targetBytes)")
        }
    }

    /** Current window size used for windowed streaming (bytes). */
    val streamWindowBytes: Long get() = windowBytes

    // TDLib's DownloadFile offset/limit are int32. windowBytes is clamped to
    // ≤256MB, but seek offsets / downloaded sizes are Longs and could exceed
    // Int.MAX_VALUE for >2GB files — clamp instead of wrapping negative.
    private fun Long.toIntOffset(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Cancel an in-flight download for [fileId] (progressive streaming or
     * ensureLocal). Called when the player moves away from a file so abandoned
     * downloads don't keep consuming bandwidth in the background. No-op if the
     * file is already local or nothing is downloading.
     */
    fun cancelDownload(fileId: Int) {
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
     * Delete the local copy of [fileId] and restart windowed download at
     * [pos]. Used by the disk water-mark: collapses on-disk usage back to a
     * single window. TdDataSource detects the epoch bump and re-opens the
     * (recreated) file.
     */
    fun resetStreamWindow(fileId: Int, pos: Long, priority: Int = 1) {
        val cur = streamingStates[fileId]
        client.send(TdApi.CancelDownloadFile(fileId, false))
        client.send(TdApi.DeleteFile(fileId))
        streamingStates[fileId] = StreamingState(
            fileId = fileId,
            activeStart = pos,
            downloadedSize = pos,
            targetBytes = pos + windowBytes,
            epoch = (cur?.epoch ?: 0) + 1,
        )
        wakeStreamWaiters(fileId)
        client.send(TdApi.DownloadFile(fileId, priority, pos.toIntOffset(), windowBytes.toIntOffset(), false))
        _states.value = _states.value + (fileId to FileDownloadState.Pending())
        Log.i(TAG, "resetStreamWindow(fileId=$fileId, pos=$pos, epoch=${(cur?.epoch ?: 0) + 1})")
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

        // `failed` in StreamingState goes true transiently during a seek
        // re-target (old download cancelled, new one not yet active).
        // awaitStreamBytes only treats it as terminal after it has persisted
        // this long, so a seek doesn't surface a spurious "download stalled".
        private const val FAILED_GRACE_MS = 1_000L
    }
}
