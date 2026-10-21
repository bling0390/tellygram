package tv.telegram.td

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Thumbnail size choice (2026-10-21): the smallest size that still reaches the medium box, so a
 * 160dp TV card is not an upscaled 90px picture; the largest wins when the source is smaller.
 */
class ThumbnailPickTest {

    @Test
    fun `picks the medium size over the tiny and the huge one`() {
        // Telegram's photo ladder: s / m / x.
        val sizes = listOf(90 to 90, 320 to 320, 800 to 800)

        assertEquals(1, pickThumbnailIndex(sizes))
    }

    @Test
    fun `falls back to the largest when nothing reaches the medium box`() {
        assertEquals(1, pickThumbnailIndex(listOf(90 to 90, 160 to 120)))
    }

    @Test
    fun `a landscape video thumbnail counts by its long edge`() {
        assertEquals(1, pickThumbnailIndex(listOf(90 to 50, 320 to 180)))
    }

    @Test
    fun `ignores sizes without dimensions`() {
        assertEquals(1, pickThumbnailIndex(listOf(0 to 0, 320 to 320)))
    }

    @Test
    fun `nothing usable means no thumbnail`() {
        assertNull(pickThumbnailIndex(emptyList()))
        assertNull(pickThumbnailIndex(listOf(0 to 0, 0 to 100)))
    }

    @Test
    fun `the threshold is configurable`() {
        val sizes = listOf(90 to 90, 320 to 320)

        // Nothing reaches 600, so the largest available wins.
        assertEquals(1, pickThumbnailIndex(sizes, minLongEdge = 600))
        // 90 already qualifies, so the smallest qualifying size wins.
        assertEquals(0, pickThumbnailIndex(sizes, minLongEdge = 90))
    }
}
