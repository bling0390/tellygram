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

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME_TD) {
            return fallback.open(dataSpec)
        }
        openedUri = uri
        fileId = uri.host?.toIntOrNull() ?: throw IOException("bad td uri: $uri")
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
        bytesRemaining = dataSpec.length
        val expected = fileRepo.streamState(fileId)?.expectedSize ?: 0L
        return if (expected > 0) expected - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val f = file ?: return C.RESULT_END_OF_INPUT
        val pos = f.filePointer
        val st = fileRepo.streamState(fileId)
            ?: throw IOException("streaming state lost for file $fileId")

        // Bytes actually on disk — the authoritative frontier. downloadedSize
        // can be offset-relative after a seek, file.length() never lies.
        val avail = maxOf(st.downloadedSize, runCatching { f.length() }.getOrDefault(0L))

        if (st.completed) {
            if (st.expectedSize > 0 && pos >= st.expectedSize) return C.RESULT_END_OF_INPUT
            val n = f.read(buffer, offset, length)
            return if (n < 0) C.RESULT_END_OF_INPUT else n
        }

        // Wait for the download to reach pos + length.
        if (pos + length > avail) {
            if (!fileRepo.awaitStreamBytes(fileId, pos + length)) {
                // Re-check completion; if genuinely stalled, surface the error.
                val cur = fileRepo.streamState(fileId)
                if (cur?.completed == true) {
                    val n = f.read(buffer, offset, length)
                    return if (n < 0) C.RESULT_END_OF_INPUT else n
                }
                Log.w(TAG, "read: download stalled for file $fileId at pos $pos")
                throw IOException("download stalled for file $fileId at $pos")
            }
        }
        val now = maxOf(
            fileRepo.streamState(fileId)?.downloadedSize ?: 0L,
            runCatching { f.length() }.getOrDefault(0L),
        )
        val toRead = minOf(length.toLong(), now - pos).toInt()
        if (toRead <= 0) return C.RESULT_END_OF_INPUT
        val n = f.read(buffer, offset, toRead)
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        return n
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
