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

class TdDataSource(
    private val fileRepo: TdFileRepository,
    private val fallback: DataSource,
) : DataSource {

    private var file: RandomAccessFile? = null
    private var fileId: Int = -1
    private var openedUri: Uri? = null
    private var bytesRemaining: Long = 0
    private var openedEpoch: Int = 0
    private var tailFile: RandomAccessFile? = null
    private var tailPathOpened: String? = null
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
        fileRepo.awaitTailProbe(fileId)

        val f = openStreamFile(fileId, dataSpec.position)
            ?: throw IOException("no local path for file $fileId (download failed?)")
        file = f
        openedEpoch = fileRepo.streamState(fileId)?.epoch ?: 0
        bytesRemaining = dataSpec.length
        val expected = fileRepo.streamState(fileId)?.expectedSize ?: 0L
        return if (expected > 0) expected - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

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
        if (file == null) return C.RESULT_END_OF_INPUT
        var handle = file!!
        val deadline = System.currentTimeMillis() + READ_WAIT_BUDGET_MS

        while (true) {
            val pos = handle.filePointer

            val nowMs = System.currentTimeMillis()
            if (nowMs - lastHeadroomCheckMs >= HEADROOM_CHECK_INTERVAL_MS) {
                lastHeadroomCheckMs = nowMs
                fileRepo.ensureStorageHeadroom(fileId, pos)
            }

            var st = fileRepo.streamState(fileId)
                ?: throw IOException("streaming state lost for file $fileId")

            if (st.epoch != openedEpoch) {
                val newPath = fileRepo.awaitStreamPath(fileId)
                    ?: throw IOException("stream reset (epoch ${st.epoch}) but no local path for file $fileId")
                handle.close()
                handle = RandomAccessFile(newPath, "r").apply { seek(pos) }
                file = handle
                openedEpoch = st.epoch
                st = fileRepo.streamState(fileId) ?: st
            }

            val tailEnd = st.tailEnd
            val tailPath = st.tailPath
            if (tailPath != null && tailEnd > st.tailStart &&
                pos >= st.tailStart && pos < tailEnd
            ) {
                if (tailPathOpened != tailPath) {
                    runCatching { tailFile?.close() }
                    tailFile = runCatching { RandomAccessFile(tailPath, "r") }.getOrNull()
                    tailPathOpened = tailPath
                }
                val tf = tailFile
                if (tf == null) {
                    throw IOException("tail file missing for file $fileId")
                }
                val canRead = minOf(length.toLong(), tailEnd - pos)
                if (canRead > 0) {
                    tf.seek(pos - st.tailStart)
                    val n = tf.read(buffer, offset, canRead.toInt())
                    if (n > 0) {
                        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
                        return n
                    }
                }
                if (System.currentTimeMillis() >= deadline) {
                    Log.w(TAG, "read: tail not readable for file $fileId at pos $pos")
                    throw IOException("tail not readable for file $fileId at $pos")
                }
                sleepBriefly()
                continue
            }

            val end = prefixEnd(st, handle)

            if (st.completed && st.expectedSize > 0 && pos >= st.expectedSize) {
                return C.RESULT_END_OF_INPUT
            }

            if (pos >= end) {
                if (st.completed) {
                    if (end <= 0L) throw IOException("empty stream for file $fileId (stale completion)")
                    return C.RESULT_END_OF_INPUT
                }
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

    private fun sleepBriefly() {
        try {
            Thread.sleep(READ_POLL_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun prefixEnd(state: StreamingState, handle: RandomAccessFile): Long {
        if (state.completed) {
            return state.expectedSize.takeIf { it > 0L }
                ?: runCatching { handle.length() }.getOrDefault(0L)
        }
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
        runCatching { tailFile?.close() }
        tailFile = null
        tailPathOpened = null
        openedUri = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
    }

    companion object {
        private const val TAG = "TdDataSource"
        const val SCHEME_TD = "td"

        private const val HEADROOM_CHECK_INTERVAL_MS = 3_000L

        private const val READ_WAIT_BUDGET_MS = 30_000L

        private const val READ_POLL_MS = 100L

        private const val PATH_OPEN_WAIT_MS = 15_000L

        fun uriFor(fileId: Int): Uri = Uri.parse("$SCHEME_TD://$fileId")
    }
}

class TdDataSourceFactory(
    private val fileRepo: TdFileRepository,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        TdDataSource(fileRepo, fallback.createDataSource())
}
