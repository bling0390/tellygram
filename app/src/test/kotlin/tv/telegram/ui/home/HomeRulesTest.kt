package tv.telegram.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

/**
 * The home screen's D-pad and card rules, straight from the design annotations.
 * These are the rules the composables call, so the tests move with the UI.
 */
class HomeRulesTest {

    private fun item(
        type: MediaType = MediaType.Photo,
        albumId: Long = 0L,
        albumSize: Int = 1,
        messageId: Long = 1L,
    ) = MediaItem(
        messageId = messageId,
        type = type,
        fileId = 1,
        albumId = albumId,
        albumSize = albumSize,
    )

    // ── card kind ──────────────────────────────────────────────────────────────

    @Test
    fun `photo renders as a photo card`() {
        assertEquals(MediaCellKind.Photo, item(MediaType.Photo).cellKind())
    }

    @Test
    fun `video and animation render as video cards`() {
        assertEquals(MediaCellKind.Video, item(MediaType.Video).cellKind())
        assertEquals(MediaCellKind.Video, item(MediaType.Animation).cellKind())
    }

    @Test
    fun `audio and text have their own cards`() {
        assertEquals(MediaCellKind.Audio, item(MediaType.Audio).cellKind())
        assertEquals(MediaCellKind.Text, item(MediaType.Text).cellKind())
    }

    @Test
    fun `unknown types fall back to the image card`() {
        assertEquals(MediaCellKind.Photo, item(MediaType.Unknown).cellKind())
    }

    @Test
    fun `an album wins over its members' type`() {
        assertEquals(MediaCellKind.Album(3), item(MediaType.Photo, albumSize = 3).cellKind())
        assertEquals(MediaCellKind.Album(10), item(MediaType.Audio, albumSize = 10).cellKind())
    }

    @Test
    fun `a single-member album is not an album card`() {
        // The repository stamps albumSize 1 for a lone member, so it must fall
        // through to the member's own kind.
        assertEquals(MediaCellKind.Photo, item(MediaType.Photo, albumId = 7, albumSize = 1).cellKind())
    }

    // ── duration formatting ────────────────────────────────────────────────────

    @Test
    fun `duration formatting covers minutes, hours and negatives`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("0:00", formatDuration(-5))
    }

    // ── grid geometry (three columns) ──────────────────────────────────────────

    @Test
    fun `first column is every third cell from zero`() {
        assertTrue(isFirstColumn(0)); assertFalse(isFirstColumn(1)); assertFalse(isFirstColumn(2))
        assertTrue(isFirstColumn(3)); assertFalse(isFirstColumn(4)); assertFalse(isFirstColumn(5))
    }

    @Test
    fun `last column is every third cell from two`() {
        assertFalse(isLastColumn(0)); assertFalse(isLastColumn(1)); assertTrue(isLastColumn(2))
        assertTrue(isLastColumn(5)); assertTrue(isLastColumn(8))
    }

    @Test
    fun `first row is the first three cells`() {
        assertTrue(isFirstRow(0)); assertTrue(isFirstRow(1)); assertTrue(isFirstRow(2))
        assertFalse(isFirstRow(3)); assertFalse(isFirstRow(9))
    }

    @Test
    fun `only row zero cells are remembered for the chips to return to`() {
        assertEquals(0, rememberedIndexFor(0))
        assertEquals(2, rememberedIndexFor(2))
        assertNull(rememberedIndexFor(3))
        assertNull(rememberedIndexFor(11))
    }

    // ── Back rules ─────────────────────────────────────────────────────────────

    @Test
    fun `back leaves the grid at 0,0 for the selected chat`() {
        assertEquals(HomeBackTarget.SelectedChat, backTarget(HomeRegion.Grid, 0))
    }

    @Test
    fun `back from any deeper grid cell goes to 0,0`() {
        assertEquals(HomeBackTarget.FirstGridCell, backTarget(HomeRegion.Grid, 1))
        assertEquals(HomeBackTarget.FirstGridCell, backTarget(HomeRegion.Grid, 7))
    }

    @Test
    fun `back from the chips and the chat list`() {
        assertEquals(HomeBackTarget.SelectedChat, backTarget(HomeRegion.Chips, 4))
        assertEquals(HomeBackTarget.TopBar, backTarget(HomeRegion.ChatList, 0))
    }

    @Test
    fun `back with no focused region does nothing`() {
        assertNull(backTarget(null, 0))
    }
}
