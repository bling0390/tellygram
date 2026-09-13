package tv.telegram.td

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
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

        // If ExoPlayer seeks past the current download frontier (user jump,
        // or the extractor probing the tail), re-point the TDLib download to
        // that offset instead of waiting for the sequential download to crawl
        // up. Seeking within already-downloaded bytes never re-targets.
        val st = fileRepo.streamState(fileId)
        if (st != null && !st.completed && dataSpec.position > st.downloadedSize) {
            fileRepo.seekStream(fileId, dataSpec.position)
        }

        val path = fileRepo.awaitStreamPath(fileId)
            ?: throw IOException("no local path for file $fileId (download failed?)")
        val f = RandomAccessFile(path, "r")
        f.seek(dataSpec.position)
        file = f
        openedEpoch = fileRepo.streamState(fileId)?.epoch ?: 0
        bytesRemaining = dataSpec.length
        val expected = fileRepo.streamState(fileId)?.expectedSize ?: 0L
        return if (expected > 0) expected - dataSpec.position else C.LENGTH_UNSET.toLong()
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
            val jumpedAhead = pos > st.downloadedSize + READ_AHEAD_SLACK_BYTES
            val jumpedBack = pos < st.activeStart
            if (!jumpedAhead && !jumpedBack && !st.completed && pos + length > st.targetBytes) {
                fileRepo.extendStream(fileId, pos + fileRepo.streamWindowBytes)
            }

            // The one legitimate end of stream.
            if (st.completed && st.expectedSize > 0 && pos >= st.expectedSize) {
                return C.RESULT_END_OF_INPUT
            }

            // The requested range must be real downloaded data (inside the active
            // segment or a frozen downloaded range). A hole — bytes skipped when a
            // seek jumped the download forward — reads as zeros and corrupts
            // playback, so re-point the download at the hole and wait for it to
            // be filled instead of reading garbage.
            if (!fileRepo.isStreamRangeAvailable(fileId, pos, length)) {
                if (jumpedAhead || jumpedBack) {
                    fileRepo.seekStream(fileId, pos)
                }
                if (!waitForStreamRange(fileId, pos, length, deadline)) {
                    Log.w(
                        TAG,
                        "read: download stalled for file $fileId at pos $pos " +
                            "(frontier=${st.downloadedSize}, target=${st.targetBytes}, " +
                            "active=${st.activeStart}, complete=${st.completed}, " +
                            "jumped=${jumpedAhead || jumpedBack})",
                    )
                    throw IOException("download stalled for file $fileId at $pos")
                }
                continue
            }

            // Clamp the read to the end of the downloaded segment containing pos.
            // downloadedSize/file.length() may extend past the segment when a seek
            // left holes behind, so never read beyond the segment boundary.
            val cur = fileRepo.streamState(fileId)
                ?: throw IOException("streaming state lost for file $fileId")
            val segEnd = when {
                cur.completed -> cur.expectedSize.coerceAtLeast(0L) - 1
                pos >= cur.activeStart -> cur.downloadedSize - 1
                else -> cur.ranges.firstOrNull { pos in it }?.last ?: (pos - 1)
            }
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
                        "(frontier=${cur.downloadedSize}, complete=${cur.completed})",
                )
                throw IOException("stream stalled for file $fileId at $pos")
            }
            fileRepo.awaitStreamBytes(fileId, pos + 1, timeoutMs = READ_WAIT_SLICE_MS)
        }
    }

    /**
     * Waits in slices (up to [deadline]) for the download to cover
     * `pos .. pos+length` in real bytes. Kept separate from the read loop so a
     * partial arrival is usable: the caller re-reads availability and serves
     * whatever is on disk instead of insisting on the full request.
     */
    private fun waitForStreamRange(fileId: Int, pos: Long, length: Int, deadline: Long): Boolean {
        while (true) {
            if (fileRepo.isStreamRangeAvailable(fileId, pos, length)) return true
            // A partial arrival is usable — ExoPlayer takes short reads — so
            // start serving as soon as the first byte of the range exists
            // rather than insisting on the whole request.
            if (fileRepo.isStreamRangeAvailable(fileId, pos, 1)) return true
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return false
            fileRepo.awaitStreamBytes(
                fileId,
                pos + length,
                timeoutMs = minOf(remaining, READ_WAIT_SLICE_MS),
            )
            // A file that finished downloading while we waited can serve the
            // tail immediately, even if the byte accounting lagged.
            if (fileRepo.streamState(fileId)?.completed == true &&
                fileRepo.isStreamRangeAvailable(fileId, pos, length)
            ) {
                return true
            }
        }
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

        // Slice size for those waits: small enough to start serving a partial
        // range as soon as bytes land, large enough not to spin.
        private const val READ_WAIT_SLICE_MS = 500L

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
