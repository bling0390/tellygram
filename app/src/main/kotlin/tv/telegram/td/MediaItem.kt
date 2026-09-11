package tv.telegram.td

enum class MediaType { Photo, Video, Animation, Unknown }

data class MediaItem(
    val messageId: Long,
    val type: MediaType,
    val fileId: Int,
    val thumbnailFileId: Int? = null,
    val localPath: String? = null,
    val thumbnailLocalPath: String? = null,
    // Raw JPEG bytes of TDLib's embedded minithumbnail. It ships inside the
    // message, so the player can paint a poster with zero latency and no
    // download.
    val minithumbnail: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val caption: String? = null,
    val date: Int = 0,
    val chatId: Long = 0,
    val supportsStreaming: Boolean = false,
    // Video duration in seconds (0 for non-video / unknown). Displayed as a
    // badge on video cards only.
    val duration: Int = 0,
)
