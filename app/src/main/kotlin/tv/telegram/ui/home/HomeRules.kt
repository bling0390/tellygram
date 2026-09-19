package tv.telegram.ui.home

import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

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
 * Leaving row 0 upwards remembers that cell so the chips' Down can return to it.
 * Cells below row 0 return null and leave the remembered value alone.
 */
internal fun rememberedIndexFor(index: Int, columns: Int = GridColumns): Int? =
    if (isFirstRow(index, columns)) index else null

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
