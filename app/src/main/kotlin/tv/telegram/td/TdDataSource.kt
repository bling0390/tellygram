package tv.telegram.td

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * ExoPlayer DataSource that serves a Telegram file progressively.
 *
 * URI scheme: `td://<fileId>`. Every read is backed by TDLib's sequential
 * `DownloadFile`: playback starts as soon as the moov header has arrived and
 * keeps playing while the rest of the file streams down (updateFile-driven
 * progress). `read()` blocks until the download frontier passes the requested
 * offset — same approach as Telegram X's streaming player.
 *
 * Seeking past the current frontier re-points the TDLib download to that
 * offset (DownloadFile offset param) so resume/jump-to-position don't wait
 * for the sequential download to crawl up.
 *
 * Non-`td://` URIs are delegated to [fallback] (file:// for previews etc).
 */
class TdDataSource(
    private val fileRepo: TdFileRepository,
    private val fallback: DataSource,
) : DataSource {

    private var file: RandomAccessFile? = null
    private var fileId: Int = -1
    private var openedUri: Uri? = null
    private var bytesRemaining: Long = 0
    // Epoch of the StreamingState the current RandomAccessFile was opened
    // against. When the repo resets the stream window (emergency disk
    // watermark: DeleteFile + re-download from a new offset), the epoch
    // bumps — the old handle points at a deleted file, so read() must close
    // it and re-open the recreated file.
    private var openedEpoch: Int = 0
    // Throttle for the disk water-mark check: statfs is a syscall, and read()
    // is called with ~32KB buffers, so only re-check every few seconds.
    private var lastHeadroomCheckMs: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME_TD) {
            return fallback.open(dataSpec)
        }
        openedUri = uri
        fileId = uri.host?.toIntOrNull() ?: throw IOException("bad td uri: $uri")
        Log.d(TAG, "open: file=$fileId pos=${dataSpec.position} len=${dataSpec.length}")
        fileRepo.startStreaming(fileId, priority = 1)

        // Only a genuine jump re-points the download — and with it builds a new
        // window on disk. The extractor re-opens the source to probe (live log
        // 2026-09-13: opens at 0, 683846, 999233 inside 400ms), and treating
        // that as a seek deleted the window and restarted the download on every
        // probe. A position inside the current window is left alone; the read
        // path waits for the download if it has not arrived yet.
        val st = fileRepo.streamState(fileId)
        if (st != null && !st.completed &&
            (dataSpec.position >= st.targetBytes || dataSpec.position < st.activeStart)
        ) {
            fileRepo.seekStream(fileId, dataSpec.position)
        }

        val f = openStreamFile(fileId, dataSpec.position)
            ?: throw IOException("no local path for file $fileId (download failed?)")
        file = f
        openedEpoch = fileRepo.streamState(fileId)?.epoch ?: 0
        bytesRemaining = dataSpec.length
        val expected = fileRepo.streamState(fileId)?.expectedSize ?: 0L
        return if (expected > 0) expected - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    /**
     * Opens the streamed file, tolerating the window-reset race.
     *
     * A reset deletes the file, but TDLib's next UpdateFile can still carry the
     * old temp path, so the first RandomAccessFile hits ENOENT (live log
     * 2026-09-13: FileNotFoundException on .../tdlib-files/temp/38). Drop that
     * path and wait for the recreated file instead of failing playback.
     */
    private fun openStreamFile(fileId: Int, position: Long): RandomAccessFile? {
        val deadline = System.currentTimeMillis() + PATH_OPEN_WAIT_MS
        while (true) {
            val path = fileRepo.awaitStreamPath(fileId, timeoutMs = PATH_OPEN_WAIT_MS)
                ?: return null
            try {
                return RandomAccessFile(path, "r").apply { seek(position) }
            } catch (e: FileNotFoundException) {
                Log.d(TAG, "open: $path is gone (window reset), waiting for the recreated file")
                fileRepo.invalidateStreamPath(fileId, path)
                if (System.currentTimeMillis() >= deadline) return null
                sleepBriefly()
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // End-of-input is only legal at the true end of a COMPLETE file. The MP4
        // extractor turns an unexpected EOF in the middle of a sample into
        // ERROR_CODE_PARSING_CONTAINER_MALFORMED and kills playback, even when
        // the bytes had simply not arrived yet (live log, 2026-09-13: our
        // "download stalled" IOException, then ExoPlayer retried and died with
        // an EOFException out of Mp4Extractor.readSample). Every "not there
        // yet" path below therefore waits for the download frontier, and
        // throws a *retryable* IOException once the wait budget is spent —
        // ExoPlayer retries those on its own.
        if (file == null) return C.RESULT_END_OF_INPUT
        var handle = file!!
        val deadline = System.currentTimeMillis() + READ_WAIT_BUDGET_MS

        while (true) {
            val pos = handle.filePointer

            // Throttled disk water-mark check (we know the exact byte position
            // here — PlayerScreen only has milliseconds). If the TDLib files
            // volume is critically low the repo evicts inactive files and may
            // reset the stream window, bumping the epoch; the re-check below
            // picks up the new StreamingState and re-opens the recreated file.
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastHeadroomCheckMs >= HEADROOM_CHECK_INTERVAL_MS) {
                lastHeadroomCheckMs = nowMs
                fileRepo.ensureStorageHeadroom(fileId, pos)
            }

            var st = fileRepo.streamState(fileId)
                ?: throw IOException("streaming state lost for file $fileId")

            // Emergency disk-watermark reset: the repo deleted the local file and
            // restarted a fresh windowed download at the playhead (epoch bump).
            // The old handle points at the deleted inode — close it and re-open
            // the recreated file, then continue reading at the same position.
            // `handle` (not just the field) has to move with it: reading through
            // the closed handle was a second way to turn a transient reset into
            // a fake end-of-stream.
            if (st.epoch != openedEpoch) {
                val newPath = fileRepo.awaitStreamPath(fileId)
                    ?: throw IOException("stream reset (epoch ${st.epoch}) but no local path for file $fileId")
                handle.close()
                handle = RandomAccessFile(newPath, "r").apply { seek(pos) }
                file = handle
                openedEpoch = st.epoch
                st = fileRepo.streamState(fileId) ?: st
            }

            // How far the request sits from the download in flight decides who
            // moves. A read that merely reaches past the frontier is served by
            // the sequential download: extend its window and keep waiting. A
            // read that jumped AHEAD of the download, or back behind the active
            // segment with no frozen range covering it, has to re-point the
            // download instead.
            //
            // Order matters: extendStream only continues from the current
            // frontier, so extending first seeds a window that starts hundreds
            // of MB behind the playhead and playback waits for a download that
            // can never catch up (2026-09-13 log: read at 866MB, frontier 3.5MB,
            // target 982MB — target was computed from the playhead, the
            // download still inched up from the old frontier).
            // Frontier: how far the current window has really been written.
            // Measured from the file itself — TDLib's counter counts bytes on
            // disk rather than a contiguous prefix.
            val frontier = windowFrontier(st, handle)
            val jumpedAhead = pos > frontier + READ_AHEAD_SLACK_BYTES
            val jumpedBack = pos < st.activeStart
            if (!jumpedAhead && !jumpedBack && !st.completed && pos + length > st.targetBytes) {
                fileRepo.extendStream(fileId, pos + fileRepo.streamWindowBytes)
            }

            // The one legitimate end of stream.
            if (st.completed && st.expectedSize > 0 && pos >= st.expectedSize) {
                return C.RESULT_END_OF_INPUT
            }

            // Only bytes the file actually contains may be served. The file is
            // ground truth: the repo deletes it whenever the window moves, so
            // what is on disk is exactly the current window, written from
            // activeStart upwards. Nothing below the window is data — serving a
            // hole is what made the extractor reject the stream ("None of the
            // available extractors could read the stream", third live log
            // 2026-09-13).
            val available = st.completed ||
                (pos >= st.activeStart && pos + length <= windowFrontier(st, handle))
            if (!available) {
                if (jumpedAhead || jumpedBack) {
                    fileRepo.seekStream(fileId, pos)
                }
                if (!waitForStreamRange(fileId, handle, pos, deadline)) {
                    Log.w(
                        TAG,
                        "read: download stalled for file $fileId at pos $pos " +
                            "(frontier=${windowFrontier(st, handle)}, " +
                            "window=${st.activeStart}..${st.targetBytes}, " +
                            "complete=${st.completed}, jumped=${jumpedAhead || jumpedBack})",
                    )
                    throw IOException("download stalled for file $fileId at $pos")
                }
                continue
            }

            val cur = st
            val segEnd = windowFrontier(cur, handle) - 1
            val toRead = minOf(length.toLong(), segEnd - pos + 1).toInt()
            if (toRead > 0) {
                val n = handle.read(buffer, offset, toRead)
                if (n > 0) {
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
                    return n
                }
            }

            // Nothing readable yet: the frontier has not reached `pos`, or TDLib
            // has not flushed that part of the file to disk. Only a completed file
            // may end here — otherwise wait, then fail retryably.
            if (cur.completed) return C.RESULT_END_OF_INPUT
            if (System.currentTimeMillis() >= deadline) {
                Log.w(
                    TAG,
                    "read: no bytes for file $fileId at pos $pos " +
                        "(frontier=${windowFrontier(cur, handle)}, complete=${cur.completed})",
                )
                throw IOException("stream stalled for file $fileId at $pos")
            }
            sleepBriefly()
        }
    }

    /**
     * Waits (up to [deadline]) for the current window to reach [pos].
     *
     * Polls the file's length on purpose: TDLib's counters lag, and a counter
     * that never moves while the file grows would stall playback. ExoPlayer
     * takes short reads, so one byte past [pos] is enough to hand something
     * back.
     */
    private fun waitForStreamRange(
        fileId: Int,
        handle: RandomAccessFile,
        pos: Long,
        deadline: Long,
    ): Boolean {
        while (true) {
            val st = fileRepo.streamState(fileId) ?: return false
            if (st.completed) return true
            if (pos >= st.activeStart && pos + 1 <= windowFrontier(st, handle)) return true
            if (System.currentTimeMillis() >= deadline) return false
            sleepBriefly()
        }
    }

    /** Sleeps one poll interval, restoring the interrupt flag if asked to stop. */
    private fun sleepBriefly() {
        try {
            Thread.sleep(READ_POLL_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * How far the current window has really been written.
     *
     * TDLib's downloadedSize is not usable here — it counts bytes on disk, not
     * a contiguous prefix, and sat below the window start right after
     * re-points in the live logs. The file's own length is ground truth because
     * the repo keeps exactly one window on disk.
     */
    private fun windowFrontier(state: StreamingState, handle: RandomAccessFile): Long {
        if (state.completed) return state.expectedSize.coerceAtLeast(0L)
        // Nothing written since the window was rebuilt: what is on disk is the
        // PREVIOUS window (a hole where the new one goes), so nothing may be
        // served yet. Waiting for the first write is what stops a fresh
        // playback from reading the old file's holes and failing container
        // sniffing (live log 2026-09-13: UnrecognizedInputFormatException 1.3s
        // after open).
        val path = state.path
        if (state.resetAtMs > 0 && path != null) {
            val touched = try { File(path).lastModified() } catch (_: SecurityException) { 0L }
            if (touched < state.resetAtMs) return state.activeStart
        }
        val written = try { handle.length() } catch (_: IOException) { 0L }
        return minOf(state.targetBytes, written.coerceAtLeast(state.activeStart))
    }

    override fun getUri(): Uri = openedUri ?: Uri.EMPTY

    override fun close() {
        file?.close()
        file = null
        openedUri = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // Progress is tracked via updateFile / StreamingState, not transfer
        // listeners, so this is a no-op.
    }

    companion object {
        private const val TAG = "TdDataSource"
        const val SCHEME_TD = "td"

        // Re-check free space every 3s while streaming (statfs cost amortized).
        private const val HEADROOM_CHECK_INTERVAL_MS = 3_000L

        // How long a single read() may wait for the download frontier before it
        // gives up with a retryable IOException (was the awaitStreamBytes
        // default of 30s, kept so stall behaviour is unchanged).
        private const val READ_WAIT_BUDGET_MS = 30_000L

        // Poll interval while waiting for the window to reach a position — the
        // file is checked directly, so this is only the sleep between checks.
        private const val READ_POLL_MS = 100L

        // How long open() may wait for the recreated file after a window reset.
        private const val PATH_OPEN_WAIT_MS = 15_000L

        // A read further ahead of the download frontier than this is treated as
        // a jump and re-points the download, instead of waiting for the
        // sequential download to crawl there (which it cannot do in time for a
        // multi-hundred-MB gap).
        private const val READ_AHEAD_SLACK_BYTES = 8L * 1024 * 1024

        fun uriFor(fileId: Int): Uri = Uri.parse("$SCHEME_TD://$fileId")
    }
}

/**
 * DataSource.Factory for [TdDataSource]. Creates one TdDataSource per call
 * (ExoPlayer may open several); non-td URIs fall through to [fallback].
 */
class TdDataSourceFactory(
    private val fileRepo: TdFileRepository,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        TdDataSource(fileRepo, fallback.createDataSource())
}
