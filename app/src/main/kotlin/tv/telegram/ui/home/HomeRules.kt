package tv.telegram.ui.home

import androidx.compose.ui.graphics.Color
import tv.telegram.td.ChatItem
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import androidx.compose.ui.unit.Dp
import tv.telegram.ui.components.RowColors

/**
 * The home screen's pure rules, kept out of the composables so they can be unit
 * tested. Everything here is a direct translation of the design annotations; the
 * UI calls into these functions rather than re-deriving the conditions inline.
 */

/** Columns in the media grid (design: three 160dp cells). */
internal const val GridColumns = 3

/** Where the Back key sends focus, per the design's rules. */
internal enum class HomeBackTarget { SelectedChat, FirstGridCell, TopBar }

internal fun isFirstColumn(index: Int, columns: Int = GridColumns): Boolean = index % columns == 0

internal fun isLastColumn(index: Int, columns: Int = GridColumns): Boolean =
    index % columns == columns - 1

internal fun isFirstRow(index: Int, columns: Int = GridColumns): Boolean = index < columns

/**
 * The cell focus enters the grid on: the one the user last left it from, clamped to what the chat
 * switched chat always enters at [0,0]; a chat with fewer cells than the remembered index also
 * falls back to the start instead of landing past the end.
 */
internal fun gridEntryIndex(remembered: Int, total: Int): Int =
    if (remembered in 0 until total) remembered else 0

/** Back rules: grid [0,*] -> selected chat, any deeper cell -> [0,0], chips ->
 *  selected chat, chat list -> the bar's selected menu. */
internal fun backTarget(region: HomeRegion?, gridIndex: Int): HomeBackTarget? = when (region) {
    HomeRegion.Grid -> if (gridIndex == 0) HomeBackTarget.SelectedChat else HomeBackTarget.FirstGridCell
    HomeRegion.Chips -> HomeBackTarget.SelectedChat
    HomeRegion.ChatList -> HomeBackTarget.TopBar
    null -> null
}

/** How a media card is drawn. Album carries its member count for the "+N". */
internal sealed interface MediaCellKind {
    data object Photo : MediaCellKind
    data object Video : MediaCellKind
    data object Audio : MediaCellKind
    data object Text : MediaCellKind
    data class Album(val count: Int) : MediaCellKind
}

/**
 * Repository item -> card kind. Album wins over the member's own type: the repo
 * collapses a whole album into one item and stamps its size on it.
 */
internal fun MediaItem.cellKind(): MediaCellKind = when {
    albumSize > 1 -> MediaCellKind.Album(albumSize)
    type == MediaType.Audio -> MediaCellKind.Audio
    type == MediaType.Text -> MediaCellKind.Text
    type == MediaType.Video || type == MediaType.Animation -> MediaCellKind.Video
    else -> MediaCellKind.Photo
}

/** Seconds -> "m:ss", or "h:mm:ss" past an hour (matches the design's 01:59:59). */
internal fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Colours of one chat row and its badges, decided in one place. */
internal data class ChatRowColors(val fill: Color, val text: Color, val unreadDot: Color)

/**
 * selects a chat for the eye at once, but the query waits: holding Down through a long
 * list would otherwise fire one TDLib query per row it passes. OK skips the wait.
 */
internal const val ChatOpenDebounceMs = 300L

/**
 * Row state -> colours.
 *
 * The design annotates the chat-name row so the filled background belongs to the row that
 * A pinned chat keeps the plain styling and shows its state through the pin glyph. `pinned`
 * is accepted and deliberately ignored so the rule is stated once and can be asserted: a
 * pinned row must look exactly like a plain one.
 */
internal fun chatRowColors(highlighted: Boolean, pinned: Boolean): ChatRowColors = if (highlighted) {
    // label (it used to be the on-surface grey), and keep the brighter tertiary-fixed dot.
    ChatRowColors(HomeSpec.White, HomeSpec.InverseOnSurface, HomeSpec.TertiaryFixed)
} else {
    ChatRowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface, HomeSpec.Tertiary)
}

/**
 * label belongs to FOCUS alone, and the check — not the fill — is what marks the filter in
 * effect. A chip that is merely applied therefore keeps the outlined look with a white label.
 */
internal fun filterChipColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    // did on the page — the colour is the page's own — but nothing behind it can show through.
    RowColors(HomeSpec.Background, HomeSpec.White)
}

/** What confirming a media card opens. */
internal enum class MediaOpenTarget { Player, AudioPlayer, PhotoPreview }

/**
 * Photo cards open the full-screen preview; videos open the player; the remaining
 * kinds keep the behaviour they had (the player), as requested.
 */
internal fun mediaOpenTarget(kind: MediaCellKind): MediaOpenTarget = when (kind) {
    is MediaCellKind.Photo -> MediaOpenTarget.PhotoPreview
    // for it and its walk list is videos only, so it is the wrong home for audio.
    is MediaCellKind.Audio -> MediaOpenTarget.AudioPlayer
    else -> MediaOpenTarget.Player
}

/** Index of the media item carrying this message id; -1 when it is not loaded. */
internal fun indexOfMessage(items: List<MediaItem>, messageId: Long): Int =
    items.indexOfFirst { it.messageId == messageId }

/**
 * The grid card that stands for [messageId]: the card itself, or the album card whose members
 * include it — album members are grouped under their representative, so a preview opened from
 */
internal fun gridIndexForMessage(items: List<MediaItem>, messageId: Long): Int {
    val direct = indexOfMessage(items, messageId)
    if (direct >= 0) return direct
    return items.indexOfFirst { item -> item.albumMembers.any { it.messageId == messageId } }
}

/**
 * Which album member the popup should focus: the one that was opened before a viewer took over,
 */
internal fun albumFocusIndex(members: List<MediaItem>, messageId: Long?): Int {
    if (messageId == null) return 0
    val index = members.indexOfFirst { it.messageId == messageId }
    return if (index >= 0) index else 0
}

/**
 * The chat row that acts as the list's entry: the selected chat, or the first row of the list
 * against the main one, which left it with no entry row at all).
 */
internal fun entryChatId(selectedChatId: Long?, visibleChats: List<ChatItem>): Long? =
    selectedChatId ?: visibleChats.firstOrNull()?.id

/**
 * own inverse-on-surface, so it reads as part of the label; at rest it keeps the brand
 * primary that the earlier frames drew.
 */
internal fun verifiedIconColor(highlighted: Boolean): Color =
    if (highlighted) HomeSpec.InverseOnSurface else HomeSpec.Primary

/**
 * The album popup's header caption. Telegram hangs an album's caption on ONE of its messages
 * (usually the first, but not always), and only the members know which — the representative
 * alone can be blank. Pure, so it is unit tested.
 */
internal fun albumHeaderCaption(members: List<MediaItem>): String? =
    members.firstNotNullOfOrNull { it.caption?.takeIf { text -> text.isNotBlank() } }

/**
 * The space BETWEEN slots, given the gap the design wants between the visible cards: the ring
 */
internal fun slotGap(cellGap: Dp, ring: Dp): Dp = cellGap - ring * 2

/**
 * Where focus has to go back after a menu action reordered the list: the chat with [id], found by
 * when there is nothing to return to: no id, or the chat left this list (archived/deleted).
 */
internal fun returnFocusIndex(chats: List<ChatItem>, id: Long?): Int? {
    if (id == null) return null
    return chats.indexOfFirst { it.id == id }.takeIf { it >= 0 }
}
