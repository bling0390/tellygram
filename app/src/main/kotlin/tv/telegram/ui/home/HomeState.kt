package tv.telegram.ui.home

import kotlinx.coroutines.flow.StateFlow
import tv.telegram.td.ChatItem
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem

/**
 * Everything the home screen needs from the app, behind one interface so the UI can
 * be driven by fakes in tests — MainViewModel is a concrete class that starts TDLib,
 * which cannot run on the JVM.
 */
internal interface HomeState {
    val chatList: StateFlow<List<ChatItem>>
    val mediaItems: StateFlow<List<MediaItem>>
    val mediaLoadingMore: StateFlow<Boolean>
    val mediaExhausted: StateFlow<Boolean>
    /** Live download state per file id, for avatars and thumbnails. */
    val fileStates: StateFlow<Map<Int, FileDownloadState>>

    /** Card to focus after a full-screen viewer closes; null when there is none. */
    val playerReturnFocusMessageId: StateFlow<Long?>

    fun ensureMediaFile(fileId: Int, priority: Int = 16)
    fun openChat(chatId: Long)
    fun loadMoreMedia()
    fun setMediaFilter(filter: MediaFilter)
    /** Remembered by the player / photo preview when they close. */
    fun setPlayerReturnFocus(messageId: Long)
    fun consumePlayerReturnFocus()
}
