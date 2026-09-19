package tv.telegram.td

import org.drinkless.td.libcore.telegram.TdApi

/**
 * The media wall's filter chips. Declaration order is the order the design draws
 * them in.
 *
 * Each chip maps to a different server-side query, which is why this lives in the
 * data layer rather than the UI: switching chips re-queries from page one instead
 * of sifting whatever happens to be loaded. "Text" is the exception — TDLib has no
 * text-only search filter, so it pages through everything and keeps
 * MessageText messages client-side.
 */
enum class MediaFilter(val label: String) {
    All("ALL"),
    Video("Video"),
    Audio("Audio"),
    Image("Image"),
    Text("Text"),
}
internal fun MediaFilter.searchFilter(): TdApi.SearchMessagesFilter = when (this) {
    MediaFilter.All, MediaFilter.Text -> TdApi.SearchMessagesFilterEmpty()
    MediaFilter.Video -> TdApi.SearchMessagesFilterVideo()
    MediaFilter.Image -> TdApi.SearchMessagesFilterPhoto()
    MediaFilter.Audio -> TdApi.SearchMessagesFilterAudio()
}

/**
 * Belt-and-braces type guard. The server already scopes everything except
 * Text, and Text is precisely the case that needs a client-side check.
 */
internal fun MediaItem.matches(filter: MediaFilter): Boolean = when (filter) {
    MediaFilter.All -> true
    MediaFilter.Video -> type == MediaType.Video || type == MediaType.Animation
    MediaFilter.Image -> type == MediaType.Photo
    MediaFilter.Audio -> type == MediaType.Audio
    MediaFilter.Text -> type == MediaType.Text
}
