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
 * URI scheme: `td://<fileId>`. The local file is always ONE CONTIGUOUS PREFIX
 * FROM BYTE 0: the download only ever starts at 0 and appends. Nothing else is
 * trusted, because TDLib's byte counters describe "bytes on disk" rather than
 * a contiguous prefix, and an offset (windowed) download leaves a hole below it
 * that a reader cannot tell apart from data. Five live sessions on 2026-09-13
 * ended in "UnrecognizedInputFormatException" / "Invalid NAL length" for
 * exactly that reason — the extractor was fed bytes nobody had written.
 *
 * The consequence is deliberate: seeking into a region that has not downloaded
 * yet means WAITING for the download to reach it, and gives up with a retryable
 * IOException if that takes too long (ExoPlayer retries those itself). Slow
 * beats corrupt.
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

        // A seek never re-points the download: the file stays a contiguous
        // prefix from 0, and reading an undownloaded region waits for it (see
        // the class comment). Every open therefore just attaches to the prefix.
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

            // How much of the file is really on disk. The download only ever
            // appends to a prefix from 0, so the file's own length is the whole
            // truth — no TDLib counter, no window bookkeeping.
            val end = prefixEnd(st, handle)

            // The one legitimate end of stream.
            if (st.completed && st.expectedSize > 0 && pos >= st.expectedSize) {
                return C.RESULT_END_OF_INPUT
            }

            // Past the prefix: the download has not reached here yet. Wait for
            // it — jumping the download forward is what left holes the extractor
            // choked on.
            if (pos >= end) {
                if (st.completed) return C.RESULT_END_OF_INPUT
                if (System.currentTimeMillis() >= deadline) {
                    Log.w(
                        TAG,
                        "read: waiting on the download for file $fileId at pos $pos " +
                            "(downloaded=$end, complete=${st.completed})",
                    )
                    throw IOException("download has not reached $pos for file $fileId")
                }
                sleepBriefly()
                continue
            }

            val toRead = minOf(length.toLong(), end - pos).toInt()
            if (toRead > 0) {
                val n = handle.read(buffer, offset, toRead)
                if (n > 0) {
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
                    return n
                }
            }

            // Short/nothing read even though the prefix claims bytes: wait for
            // the file to catch up. Only a completed file may end here.
            if (st.completed) return C.RESULT_END_OF_INPUT
            if (System.currentTimeMillis() >= deadline) {
                Log.w(
                    TAG,
                    "read: no bytes for file $fileId at pos $pos " +
                        "(downloaded=${prefixEnd(st, handle)}, complete=${st.completed})",
                )
                throw IOException("stream stalled for file $fileId at $pos")
            }
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
     * How many bytes of the file are really on disk.
     *
     * The download always starts at 0 and appends, so this is a contiguous
     * prefix: every byte below it is data. This is the only frontier the reader
     * trusts — TDLib's downloadedSize counts bytes on disk rather than a
     * contiguous prefix, and offset downloads left holes that read as zeros and
     * killed playback (2026-09-13).
     */
    private fun prefixEnd(state: StreamingState, handle: RandomAccessFile): Long {
        if (state.completed) return state.expectedSize.coerceAtLeast(0L)
        // Nothing written since the file was restarted: whatever is on disk
        // belongs to a previous attempt, so nothing may be served yet.
        val path = state.path
        if (state.resetAtMs > 0 && path != null) {
            val touched = try { File(path).lastModified() } catch (_: SecurityException) { 0L }
            if (touched < state.resetAtMs) return 0L
        }
        return try { handle.length() } catch (_: IOException) { 0L }
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

        // How long open() may wait for the recreated file after a restart.
        private const val PATH_OPEN_WAIT_MS = 15_000L

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
