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
 */
data class StreamingState(
    val fileId: Int,
    val path: String? = null,
    val downloadedSize: Long = 0,
    val expectedSize: Long = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
)

class TdFileRepository(
    private val client: TdClient = TdClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    private val _states = MutableStateFlow<Map<Int, FileDownloadState>>(emptyMap())
    val states: StateFlow<Map<Int, FileDownloadState>> = _states.asStateFlow()

    private val pendingDownloads = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val previewDownloads = ConcurrentHashMap<Int, PreviewRequest>()

    // Streaming (progressive playback) state, one entry per fileId that is
    // being played while still downloading.
    private val streamingStates = ConcurrentHashMap<Int, StreamingState>()

    private data class PreviewRequest(
        val deferred: CompletableDeferred<String>,
        val limitBytes: Int,
    )

    init {
        scope.launch {
            client.updates.collect { obj -> dispatch(obj) }
        }
    }

    private fun dispatch(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateFile -> handleUpdateFile(obj.file)
            else -> {  }
        }
    }

    private fun handleUpdateFile(file: TdApi.File) {
        val local = file.local ?: return
        val fileId = file.id
        if (local.isDownloadingCompleted && local.path.isNotEmpty()) {
            val d = pendingDownloads.remove(fileId)
            d?.complete(local.path)
            val pr = previewDownloads.remove(fileId)
            pr?.deferred?.complete(local.path)
            _states.value = _states.value + (fileId to FileDownloadState.Local(local.path))
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
            streamingStates[fileId] = cur.copy(
                path = local.path.takeIf { it.isNotEmpty() } ?: cur.path,
                downloadedSize = local.downloadedSize.toLong(),
                expectedSize = file.expectedSize.toLong().takeIf { it > 0 } ?: cur.expectedSize,
                completed = local.isDownloadingCompleted,
                failed = failed,
            )
        }
    }

    suspend fun ensureLocal(fileId: Int, priority: Int = 32, timeoutMs: Long = 60_000L): String? {
        val current = _states.value[fileId]
        if (current is FileDownloadState.Local) return current.path

        _states.value = _states.value + (fileId to FileDownloadState.Pending())

        return try {
            val fileObj = client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L)
            if (fileObj !is TdApi.File) {
                Log.w(TAG, "ensureLocal($fileId): getFile returned ${fileObj?.javaClass?.simpleName ?: "null"}")
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
        val fileObj = client.execute(TdApi.GetFile(fileId), timeoutMs = 5_000L)
        if (fileObj is TdApi.File) {
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
        streamingStates[fileId] = StreamingState(fileId = fileId)
        client.send(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        Log.d(TAG, "startStreaming(fileId=$fileId, priority=$priority)")
    }

    /** Re-target the active download to start at [offset] (used on seek). */
    fun seekStream(fileId: Int, offset: Long, priority: Int = 1) {
        streamingStates[fileId]?.let {
            streamingStates[fileId] = it.copy(downloadedSize = offset, completed = false)
        }
        client.send(TdApi.DownloadFile(fileId, priority, offset.toInt(), 0, false))
        Log.d(TAG, "seekStream(fileId=$fileId, offset=$offset)")
    }

    fun streamState(fileId: Int): StreamingState? = streamingStates[fileId]

    /**
     * Block until at least [minBytes] have been downloaded (or the file is
     * complete / failed). Polls the updateFile-driven state; used by the
     * streaming DataSource on ExoPlayer's loader thread. Returns false on
     * timeout or failure.
     */
    fun awaitStreamBytes(fileId: Int, minBytes: Long, timeoutMs: Long = 30_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val st = streamingStates[fileId] ?: return false
            if (st.failed) return false
            if (st.completed || st.downloadedSize >= minBytes) return true
            Thread.sleep(50)
        }
        val st = streamingStates[fileId]
        return st != null && (st.completed || st.downloadedSize >= minBytes)
    }

    /** Block until TDLib has assigned a local path for the streaming file. */
    fun awaitStreamPath(fileId: Int, timeoutMs: Long = 15_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val st = streamingStates[fileId] ?: return null
            if (st.path != null) return st.path
            Thread.sleep(50)
        }
        return streamingStates[fileId]?.path
    }

    /** Drop the streaming state (called when leaving the player). */
    fun stopStreaming(fileId: Int) {
        streamingStates.remove(fileId)
    }

    companion object {
        private const val TAG = "TdFileRepo"
    }
}
