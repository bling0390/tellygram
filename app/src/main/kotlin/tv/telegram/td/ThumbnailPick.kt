package tv.telegram.td

import kotlin.math.max

/**
 * Which size to use as a thumbnail, given the picture's size ladder as (width, height) pairs.
 *
 * Telegram ships several sizes per photo (typically 90 / 320 / 800 …). The smallest is too soft
 * for a 160dp TV card once upscaled, and the largest is wasteful for a thumbnail, so the rule is:
 * the smallest size whose long edge still reaches [minLongEdge], falling back to the largest
 *
 * Pure so the choice can be unit tested without TDLib objects.
 */
internal fun pickThumbnailIndex(
    sizes: List<Pair<Int, Int>>,
    minLongEdge: Int = 320,
): Int? {
    val usable = sizes.withIndex().filter { (_, wh) -> wh.first > 0 && wh.second > 0 }
    if (usable.isEmpty()) return null
    val largeEnough = usable.filter { (_, wh) -> max(wh.first, wh.second) >= minLongEdge }
    val chosen = largeEnough.minByOrNull { (_, wh) -> wh.first.toLong() * wh.second }
        ?: usable.maxByOrNull { (_, wh) -> wh.first.toLong() * wh.second }
    return chosen?.index
}
