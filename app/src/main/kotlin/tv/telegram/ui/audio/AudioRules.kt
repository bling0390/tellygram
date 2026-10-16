package tv.telegram.ui.audio

import tv.telegram.td.MediaItem

/**
 * The audio screen's pure rules — what a track is called and where its transport controls land —
 * kept out of the composable so they can be unit tested (2026-10-16).
 */

/** The app's "no value" mark; the settings rows use the same one. */
internal const val AudioDash = "—"

/**
 * What the screen calls this track (decision D5): the ID3 title, else the file name without its
 * extension, else [AudioDash]. TDLib reports an absent title as an empty string, and the message
 * mapping is where that is normalised to null.
 */
internal fun audioTitle(item: MediaItem): String {
    val title = item.title?.trim().orEmpty()
    if (title.isNotEmpty()) return title
    val name = item.fileName?.trim().orEmpty()
    return name.substringBeforeLast('.', name).ifEmpty { AudioDash }
}

/**
 * The artist line, or null when the track carries none: the screen drops the line rather than
 * filling it with a placeholder (decision D5).
 */
internal fun audioArtist(item: MediaItem): String? =
    item.performer?.trim()?.takeIf { it.isNotEmpty() }

/** Where a +/- [deltaSeconds] jump lands, clamped to the track (decision C2: 10-second steps). */
internal fun seekTarget(positionMs: Long, deltaSeconds: Int, durationMs: Long): Long {
    val target = positionMs + deltaSeconds * 1_000L
    if (target <= 0L) return 0L
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

/** Progress as 0..1; a duration we do not know yet reads as 0 rather than as a full bar. */
internal fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
