package tv.telegram.ui.home

import kotlinx.coroutines.flow.StateFlow
import tv.telegram.td.ChatItem
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem
import tv.telegram.td.SenderInfo

internal interface HomeState {
    val chatList: StateFlow<List<ChatItem>>
    val archiveChats: StateFlow<List<ChatItem>>
    val mediaItems: StateFlow<List<MediaItem>>
    val currentChatId: StateFlow<Long?>
    val mediaLoadingMore: StateFlow<Boolean>
    val mediaExhausted: StateFlow<Boolean>
    val fileStates: StateFlow<Map<Int, FileDownloadState>>

    val playerReturnFocusMessageId: StateFlow<Long?>

    fun ensureMediaFile(fileId: Int, priority: Int = 16)
    fun openChat(chatId: Long)
    fun loadMoreMedia()
    fun setMediaFilter(filter: MediaFilter)
    fun setPlayerReturnFocus(messageId: Long)
    fun consumePlayerReturnFocus()

    fun toggleChatPin(chatId: Long, inArchive: Boolean, pinned: Boolean)
    fun toggleChatMute(chatId: Long, muted: Boolean)
    fun toggleChatArchive(chatId: Long, archived: Boolean)
    fun deleteChat(chat: ChatItem, revoke: Boolean)
    suspend fun isGroupOwner(chatId: Long): Boolean
    suspend fun senderInfoFor(item: MediaItem): SenderInfo?

    fun openViewerByMessage(items: List<MediaItem>, messageId: Long)
}
