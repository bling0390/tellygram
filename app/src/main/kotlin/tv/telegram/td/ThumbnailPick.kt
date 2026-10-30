package tv.telegram.td

import kotlin.math.max

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
