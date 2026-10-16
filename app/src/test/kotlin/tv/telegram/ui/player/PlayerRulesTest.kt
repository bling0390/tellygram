package tv.telegram.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

/**
 * The prev/next walk list each player uses. The audio player will walk its own type through the
 * same rule, so both are covered here (2026-10-16).
 */
class PlayerRulesTest {

    private fun item(id: Long, type: MediaType) = MediaItem(
        messageId = id,
        type = type,
        fileId = id.toInt(),
    )

    // A wall holding what the two players walk, plus things neither of them walks.
    private val wall = listOf(
        item(1, MediaType.Video),
        item(2, MediaType.Audio),
        item(3, MediaType.Photo),
        item(4, MediaType.Video),
        item(5, MediaType.Animation),
        item(6, MediaType.Audio),
        item(7, MediaType.Text),
    )

    @Test
    fun `the video walk is the videos, in order`() {
        assertEquals(listOf(0, 3), walkIndices(wall, MediaType.Video))
    }

    @Test
    fun `the audio walk is the audio, in order`() {
        assertEquals(listOf(1, 5), walkIndices(wall, MediaType.Audio))
    }

    @Test
    fun `a type the wall has none of walks nothing`() {
        assertTrue(walkIndices(wall, MediaType.Unknown).isEmpty())
    }

    @Test
    fun `the ends of the walk stop instead of wrapping`() {
        val videos = walkIndices(wall, MediaType.Video)

        assertFalse("the first video has no previous", hasPreviousIn(videos, 0))
        assertTrue(hasNextIn(videos, 0))
        assertTrue(hasPreviousIn(videos, 3))
        assertFalse("the last video has no next", hasNextIn(videos, 3))

        assertNull(neighbourIn(videos, 0, -1))
        assertEquals(3, neighbourIn(videos, 0, 1))
        assertNull(neighbourIn(videos, 3, 1))
        assertEquals(0, neighbourIn(videos, 3, -1))
    }

    @Test
    fun `a single entry has neither neighbour`() {
        val walk = walkIndices(listOf(item(1, MediaType.Audio)), MediaType.Audio)

        assertFalse(hasPreviousIn(walk, 0))
        assertFalse(hasNextIn(walk, 0))
        assertNull(neighbourIn(walk, 0, 1))
    }

    @Test
    fun `an item outside the walk keeps the video player's old behaviour`() {
        // Playing an animation in the video player: the walk is still the videos. The screen
        // reported NO next there — the item's position came out as -1 and -1 is not in
        // `0 until lastIndex` — even though neighbourIn(+1) would land on the first video.
        // That inconsistency is preserved as-is by this slice rather than silently changed.
        val videos = walkIndices(wall, MediaType.Video)

        assertNull(neighbourIn(videos, 4, -1))
        assertEquals(0, neighbourIn(videos, 4, 1))
        assertFalse(hasPreviousIn(videos, 4))
        assertFalse(hasNextIn(videos, 4))
    }

    @Test
    fun `the walk extends when another page arrives`() {
        // Decision B2 (2026-10-16): next stops at the end of what is loaded, and loadMore
        // extends it — no prefetching, nothing held back.
        val loaded = listOf(item(1, MediaType.Audio), item(2, MediaType.Audio))
        val withNextPage = loaded + item(3, MediaType.Audio)

        assertEquals(listOf(0, 1), walkIndices(loaded, MediaType.Audio))
        assertEquals(listOf(0, 1, 2), walkIndices(withNextPage, MediaType.Audio))
        assertFalse(hasNextIn(walkIndices(loaded, MediaType.Audio), 1))
        assertTrue(hasNextIn(walkIndices(withNextPage, MediaType.Audio), 1))
    }
}
