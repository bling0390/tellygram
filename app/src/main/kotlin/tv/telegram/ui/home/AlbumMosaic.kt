package tv.telegram.ui.home

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/**
 *
 * Members keep their own aspect ratio, every row fills the card's width, and the finished
 * block is scaled to fill its height exactly — the frame's seven-member card comes out as
 * rows of 52 / 36 / 28 with 2px seams. Enumerating row splits is 2^(n-1) and an album holds
 * at most ten members, so the exhaustive search is a few thousand float operations.
 *
 * Aspects are clamped to [0.5, 2] so one panorama cannot squeeze its row into a sliver;
 * members with no dimensions count as square.
 */
internal fun albumMosaicRects(
    aspects: List<Float>,
    width: Float,
    height: Float,
    gap: Float = 2f,
    minRowHeight: Float = 16f,
    maxRowHeight: Float = 160f,
): List<Rect> {
    if (aspects.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val clamped = aspects.map { if (it.isFinite() && it > 0f) it.coerceIn(0.5f, 2f) else 1f }
    if (clamped.size == 1) return listOf(Rect(0f, 0f, width, height))

    var bestRows: IntArray? = null
    var bestTotal = 0f
    var bestError = Float.MAX_VALUE

    // Mask 0 is the "every member in one row" split; leaving it out made a two-member
    for (mask in 0 until (1 shl (clamped.size - 1))) {
        var rows = 1
        for (boundary in 0 until clamped.size - 1) {
            if ((mask shr boundary) and 1 == 1) rows++
        }

        var index = 0
        var total = gap * (rows - 1)
        var ok = true
        for (boundary in 0 until clamped.size - 1) {
            if ((mask shr boundary) and 1 == 0) continue
            val count = boundary + 1 - index
            val rowHeight = (width - gap * (count - 1)) / sumOf(clamped, index, count)
            if (rowHeight < minRowHeight || rowHeight > maxRowHeight) {
                ok = false
                break
            }
            total += rowHeight
            index = boundary + 1
        }
        if (!ok) continue
        val tailCount = clamped.size - index
        val tailHeight = (width - gap * (tailCount - 1)) / sumOf(clamped, index, tailCount)
        if (tailHeight < minRowHeight || tailHeight > maxRowHeight) continue
        total += tailHeight

        val error = abs(total - height)
        if (error < bestError) {
            bestError = error
            bestTotal = total
            val counts = ArrayList<Int>(rows)
            var start = 0
            for (boundary in 0 until clamped.size - 1) {
                if ((mask shr boundary) and 1 == 1) {
                    counts += boundary + 1 - start
                    start = boundary + 1
                }
            }
            counts += clamped.size - start
            bestRows = counts.toIntArray()
        }
    }

    val rows = bestRows ?: return listOf(Rect(0f, 0f, width, height))
    // Scale the row heights only: the seams keep their exact size, so the block ends on the
    // card's bottom edge instead of overshooting by (1 - scale) * gaps.
    val gapTotal = gap * (rows.size - 1)
    val scale = (height - gapTotal) / (bestTotal - gapTotal)

    val out = ArrayList<Rect>(clamped.size)
    var y = 0f
    var index = 0
    for (count in rows) {
        val sum = sumOf(clamped, index, count)
        val rowHeight = ((width - gap * (count - 1)) / sum) * scale
        var x = 0f
        for (i in 0 until count) {
            val cellWidth = (width - gap * (count - 1)) * (clamped[index + i] / sum)
            out += Rect(x, y, x + cellWidth, y + rowHeight)
            x += cellWidth + gap
        }
        y += rowHeight + gap
        index += count
    }
    return out
}

private fun sumOf(values: List<Float>, from: Int, count: Int): Float {
    var sum = 0f
    for (i in from until from + count) sum += values[i]
    return sum
}
