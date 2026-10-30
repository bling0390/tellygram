package tv.telegram.ui.home

import androidx.compose.ui.graphics.Color
import tv.telegram.td.ChatItem
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import androidx.compose.ui.unit.Dp
import tv.telegram.ui.components.RowColors

internal const val GridColumns = 3

internal enum class HomeBackTarget { SelectedChat, FirstGridCell, TopBar }

internal fun isFirstColumn(index: Int, columns: Int = GridColumns): Boolean = index % columns == 0

internal fun isLastColumn(index: Int, columns: Int = GridColumns): Boolean =
    index % columns == columns - 1

internal fun isFirstRow(index: Int, columns: Int = GridColumns): Boolean = index < columns

internal fun gridEntryIndex(remembered: Int, total: Int): Int =
    if (remembered in 0 until total) remembered else 0

internal fun backTarget(region: HomeRegion?, gridIndex: Int): HomeBackTarget? = when (region) {
    HomeRegion.Grid -> if (gridIndex == 0) HomeBackTarget.SelectedChat else HomeBackTarget.FirstGridCell
    HomeRegion.Chips -> HomeBackTarget.SelectedChat
    HomeRegion.ChatList -> HomeBackTarget.TopBar
    null -> null
}

internal sealed interface MediaCellKind {
    data object Photo : MediaCellKind
    data object Video : MediaCellKind
    data object Audio : MediaCellKind
    data object Text : MediaCellKind
    data class Album(val count: Int) : MediaCellKind
}

internal fun MediaItem.cellKind(): MediaCellKind = when {
    albumSize > 1 -> MediaCellKind.Album(albumSize)
    type == MediaType.Audio -> MediaCellKind.Audio
    type == MediaType.Text -> MediaCellKind.Text
    type == MediaType.Video || type == MediaType.Animation -> MediaCellKind.Video
    else -> MediaCellKind.Photo
}

internal fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

internal data class ChatRowColors(val fill: Color, val text: Color, val unreadDot: Color)

internal const val ChatOpenDebounceMs = 300L

internal fun chatRowColors(highlighted: Boolean, pinned: Boolean): ChatRowColors = if (highlighted) {
    ChatRowColors(HomeSpec.White, HomeSpec.InverseOnSurface, HomeSpec.TertiaryFixed)
} else {
    ChatRowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface, HomeSpec.Tertiary)
}

internal fun filterChipColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.Background, HomeSpec.White)
}

internal enum class MediaOpenTarget { Player, AudioPlayer, PhotoPreview }

internal fun mediaOpenTarget(kind: MediaCellKind): MediaOpenTarget = when (kind) {
    is MediaCellKind.Photo -> MediaOpenTarget.PhotoPreview
    is MediaCellKind.Audio -> MediaOpenTarget.AudioPlayer
    else -> MediaOpenTarget.Player
}

internal fun indexOfMessage(items: List<MediaItem>, messageId: Long): Int =
    items.indexOfFirst { it.messageId == messageId }

internal fun gridIndexForMessage(items: List<MediaItem>, messageId: Long): Int {
    val direct = indexOfMessage(items, messageId)
    if (direct >= 0) return direct
    return items.indexOfFirst { item -> item.albumMembers.any { it.messageId == messageId } }
}

internal fun albumFocusIndex(members: List<MediaItem>, messageId: Long?): Int {
    if (messageId == null) return 0
    val index = members.indexOfFirst { it.messageId == messageId }
    return if (index >= 0) index else 0
}

internal fun entryChatId(selectedChatId: Long?, visibleChats: List<ChatItem>): Long? =
    selectedChatId ?: visibleChats.firstOrNull()?.id

internal fun verifiedIconColor(highlighted: Boolean): Color =
    if (highlighted) HomeSpec.InverseOnSurface else HomeSpec.Primary

internal fun albumHeaderCaption(members: List<MediaItem>): String? =
    members.firstNotNullOfOrNull { it.caption?.takeIf { text -> text.isNotBlank() } }

internal fun slotGap(cellGap: Dp, ring: Dp): Dp = cellGap - ring * 2

internal fun returnFocusIndex(chats: List<ChatItem>, id: Long?): Int? {
    if (id == null) return null
    return chats.indexOfFirst { it.id == id }.takeIf { it >= 0 }
}
