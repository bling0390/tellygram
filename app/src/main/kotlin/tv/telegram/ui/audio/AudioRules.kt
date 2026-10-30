package tv.telegram.ui.audio

import tv.telegram.td.MediaItem

internal const val AudioDash = "—"

internal fun audioTitle(item: MediaItem): String {
    val title = item.title?.trim().orEmpty()
    if (title.isNotEmpty()) return title
    val name = item.fileName?.trim().orEmpty()
    return name.substringBeforeLast('.', name).ifEmpty { AudioDash }
}

internal fun audioArtist(item: MediaItem): String? =
    item.performer?.trim()?.takeIf { it.isNotEmpty() }

internal fun seekTarget(positionMs: Long, deltaSeconds: Int, durationMs: Long): Long {
    val target = positionMs + deltaSeconds * 1_000L
    if (target <= 0L) return 0L
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

internal fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
