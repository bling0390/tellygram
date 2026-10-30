package tv.telegram.td

enum class MediaType { Photo, Video, Animation, Audio, Text, Unknown }

data class MediaItem(
    val messageId: Long,
    val type: MediaType,
    val fileId: Int,
    val thumbnailFileId: Int? = null,
    val localPath: String? = null,
    val thumbnailLocalPath: String? = null,
    val minithumbnail: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val caption: String? = null,
    val date: Int = 0,
    val chatId: Long = 0,
    val senderId: Long = 0,
    val senderIsChat: Boolean = false,
    val supportsStreaming: Boolean = false,
    val duration: Int = 0,
    val title: String? = null,
    val performer: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Int = 0,
    val albumId: Long = 0,
    val albumSize: Int = 1,
    val albumMembers: List<MediaItem> = emptyList(),
)

data class SenderInfo(
    val name: String?,
    val avatarFileId: Int?,
)
