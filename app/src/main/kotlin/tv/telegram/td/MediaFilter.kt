package tv.telegram.td

import org.drinkless.td.libcore.telegram.TdApi

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

internal fun MediaItem.matches(filter: MediaFilter): Boolean = when (filter) {
    MediaFilter.All -> true
    MediaFilter.Video -> type == MediaType.Video || type == MediaType.Animation
    MediaFilter.Image -> type == MediaType.Photo
    MediaFilter.Audio -> type == MediaType.Audio
    MediaFilter.Text -> type == MediaType.Text
}
