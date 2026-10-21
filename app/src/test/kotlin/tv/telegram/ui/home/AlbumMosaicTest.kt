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
        val aspects = listOf(1.33f, 0.75f, 1f, 1.78f, 0.66f, 1f, 1.5f)

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
        val aspects = listOf(1.33f, 0.75f, 1f, 1.78f, 0.66f, 1f, 1.5f)

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
        val aspects = listOf(1.0f, 0.8f, 1.6f, 1.0f)

        assertEquals(
            albumMosaicRects(aspects, width, height),
            albumMosaicRects(aspects, width, height),
        )
    }
}
