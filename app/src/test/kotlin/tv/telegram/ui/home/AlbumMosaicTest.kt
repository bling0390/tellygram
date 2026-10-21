package tv.telegram.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The album collage (design node 3636:2311): members keep their aspect ratio, rows fill the
 * card, and the block fills its height exactly. The frame's seven-member card is the
 * reference — 1 + 3 + 3 with rows of 52 / 36 / 28 and 2px seams.
 */
class AlbumMosaicTest {

    private val width = 160f
    private val height = 120f
    private val epsilon = 0.01f

    /** A mix of landscape, portrait, square and wide members. */
    private val aspects = listOf(1.33f, 0.75f, 1f, 1.78f, 0.66f, 1.5f)

    private fun aspectsFor(count: Int) = List(count) { index -> aspects[index % aspects.size] }

    @Test
    fun `a single member fills the card`() {
        val rects = albumMosaicRects(listOf(1.5f), width, height)

        assertEquals(1, rects.size)
        assertEquals(0f, rects[0].left, epsilon)
        assertEquals(0f, rects[0].top, epsilon)
        assertEquals(width, rects[0].right, epsilon)
        assertEquals(height, rects[0].bottom, epsilon)
    }

    @Test
    fun `no members means no tiles`() {
        assertTrue(albumMosaicRects(emptyList(), width, height).isEmpty())
    }

    @Test
    fun `seven members fill the card without leaving a gap`() {
        val rects = albumMosaicRects(aspects, width, height)

        assertEquals(aspects.size, rects.size)
        rects.forEach { rect ->
            assertTrue("tile starts inside the card", rect.left >= -epsilon && rect.top >= -epsilon)
            assertTrue("tile ends inside the card", rect.right <= width + epsilon && rect.bottom <= height + epsilon)
            assertTrue("tile has area", rect.width > 0f && rect.height > 0f)
        }
        // The block starts at the top, ends at the bottom, and every row reaches the right edge.
        assertEquals(0f, rects.first().top, epsilon)
        assertEquals(height, rects.maxOf { it.bottom }, epsilon)
        rects.groupBy { it.top }.values.forEach { row ->
            assertEquals("row fills the width", width, row.maxOf { it.right }, epsilon)
        }
    }

    @Test
    fun `portrait and landscape mix uses more than one row height`() {
        val rowHeights = albumMosaicRects(aspects, width, height)
            .groupBy { it.top }
            .values
            .map { it.first().height }

        assertTrue("expected a mosaic, got uniform rows", rowHeights.distinct().size > 1)
    }

    @Test
    fun `extreme aspects are clamped instead of collapsing`() {
        val rects = albumMosaicRects(listOf(0.02f, 30f, 1f), width, height)

        assertEquals(3, rects.size)
        rects.forEach { rect ->
            assertTrue("width survives clamping", rect.width >= 1f)
            assertTrue("height survives clamping", rect.height >= 1f)
        }
    }

    @Test
    fun `the same input lays out the same way`() {
        assertEquals(
            albumMosaicRects(aspects, width, height),
            albumMosaicRects(aspects, width, height),
        )
    }

    /**
     * Albums hold one to ten members; the frame only draws seven, so every count the client
     * can show is checked for the same invariants: one tile per member, all inside the card,
     * the block starting at the top and ending on the bottom edge, and every row touching the
     * right edge.
     */
    @Test
    fun `every album size the client can show fills the card`() {
        for (count in 1..10) {
            val rects = albumMosaicRects(aspectsFor(count), width, height)

            assertEquals("size $count: one tile per member", count, rects.size)
            rects.forEach { rect ->
                assertTrue(
                    "size $count: tile starts inside",
                    rect.left >= -epsilon && rect.top >= -epsilon,
                )
                assertTrue(
                    "size $count: tile ends inside",
                    rect.right <= width + epsilon && rect.bottom <= height + epsilon,
                )
                assertTrue("size $count: tile has area", rect.width >= 1f && rect.height >= 1f)
            }
            assertEquals("size $count: starts at the top", 0f, rects.first().top, epsilon)
            assertEquals("size $count: ends at the bottom", height, rects.maxOf { it.bottom }, epsilon)
            rects.groupBy { it.top }.values.forEach { row ->
                assertEquals("size $count: row reaches the right edge", width, row.maxOf { it.right }, epsilon)
            }
        }
    }

    @Test
    fun `two members sit side by side in one row`() {
        val rects = albumMosaicRects(listOf(1f, 1f), width, height)

        assertEquals(2, rects.size)
        assertEquals("one row", 1, rects.map { it.top }.distinct().size)
        // Equal aspects: two equal halves of the card.
        assertEquals(rects[0].width, rects[1].width, 0.5f)
        assertEquals(width, rects.sumOf { it.width.toDouble() }.toFloat() + 2f, 0.5f)
    }

    @Test
    fun `ten members never overlap`() {
        val rects = albumMosaicRects(aspectsFor(10), width, height)

        assertEquals(10, rects.size)
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue("tiles $i and $j overlap", overlapX <= epsilon || overlapY <= epsilon)
            }
        }
    }
}
