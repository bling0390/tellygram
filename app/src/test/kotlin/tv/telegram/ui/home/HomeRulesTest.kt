package tv.telegram.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import androidx.compose.ui.unit.dp

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
    // ── row colours: only focus/selection may fill a row ───────────────────────

    @Test
    fun `only a focused or selected row gets a fill`() {
        val plain = chatRowColors(highlighted = false, pinned = false)
        val active = chatRowColors(highlighted = true, pinned = false)

        assertEquals(HomeSpec.SurfaceContainer, plain.fill)
        assertEquals(HomeSpec.White, active.fill)
        assertEquals(HomeSpec.OnSurface, plain.text)
        assertEquals(HomeSpec.InverseOnSurface, active.text)
        assertEquals(HomeSpec.Tertiary, plain.unreadDot)
        assertEquals(HomeSpec.TertiaryFixed, active.unreadDot)
    }

    @Test
    fun `a pinned row is coloured exactly like a plain one`() {
        // Regression guard: a pinned row must NOT look selected.
        assertEquals(
            chatRowColors(highlighted = false, pinned = false),
            chatRowColors(highlighted = false, pinned = true),
        )
    }

    // ── confirm-key target ─────────────────────────────────────────────────────

    @Test
    fun `photos open the preview and everything else opens the player`() {
        assertEquals(MediaOpenTarget.PhotoPreview, mediaOpenTarget(MediaCellKind.Photo))
        assertEquals(MediaOpenTarget.Player, mediaOpenTarget(MediaCellKind.Video))
        assertEquals(MediaOpenTarget.AudioPlayer, mediaOpenTarget(MediaCellKind.Audio))
        assertEquals(MediaOpenTarget.Player, mediaOpenTarget(MediaCellKind.Text))
        assertEquals(MediaOpenTarget.Player, mediaOpenTarget(MediaCellKind.Album(4)))
    }

    @Test
    fun `indexOfMessage finds the card to restore focus to`() {
        val items = listOf(item(messageId = 7), item(messageId = 9))
        assertEquals(1, indexOfMessage(items, 9))
        assertEquals(-1, indexOfMessage(items, 404))
    }


    @Test
    fun `the verified badge follows the row it sits on`() {
        // On the focused white row it takes the inverse label colour; at rest it stays primary.
        assertEquals(HomeSpec.InverseOnSurface, verifiedIconColor(highlighted = true))
        assertEquals(HomeSpec.Primary, verifiedIconColor(highlighted = false))
    }

    @Test
    fun `the album header takes the first member that actually has a caption`() {
        fun m(id: Long, caption: String?) = MediaItem(
            messageId = id, type = MediaType.Photo, fileId = 0, caption = caption,
        )
        // The representative is blank but a later member carries it (Telegram's usual case).
        assertEquals("hi", albumHeaderCaption(listOf(m(1, null), m(2, "hi"), m(3, "later"))))
        // Blank strings are not captions either.
        assertEquals("hi", albumHeaderCaption(listOf(m(1, "   "), m(2, "hi"))))
        // Nothing anywhere -> no header block at all.
        assertNull(albumHeaderCaption(listOf(m(1, null), m(2, "  "))))
        assertNull(albumHeaderCaption(emptyList()))
    }

    @Test
    fun `the chip's pill is focus, and the applied filter keeps the outlined look`() {
        // Design 3608:2203 (2026-10-16): the white pill with the dark label is the FOCUS
        // treatment. The filter in effect is marked by the check, not by the fill, so a chip
        // that is merely applied keeps the outlined look.
        val focused = filterChipColors(focused = true)
        assertEquals(HomeSpec.White, focused.fill)
        assertEquals(HomeSpec.InverseOnSurface, focused.text)

        val rest = filterChipColors(focused = false)
        // Opaque page colour, not transparent (2026-10-17): same look on the page, but nothing
        // behind the chip can show through it.
        assertEquals(HomeSpec.Background, rest.fill)
        assertEquals(HomeSpec.White, rest.text)
    }

    @Test
    fun `slot gap keeps the visible cards 20dp apart`() {
        // The ring band lives inside each slot, so two neighbours contribute one band each.
        assertEquals(14.dp, slotGap(cellGap = 20.dp, ring = 3.dp))
        // And the slot itself is the card plus a band on each side: 160+3+3 x 120+3+3.
        assertEquals(166.dp, 160.dp + 3.dp * 2)
        assertEquals(126.dp, 120.dp + 3.dp * 2)
    }

    @Test
    fun `return focus finds the chat by id, wherever the reorder put it`() {
        // A pin sends the chat to the top, so the index it used to sit at means nothing
        // (2026-10-17); the return path has to follow the id.
        val chats = listOf(chat(9), chat(7), chat(3))

        assertEquals(2, returnFocusIndex(chats, 3))
        assertEquals(0, returnFocusIndex(chats, 9))
        // Gone from this list (archived/deleted) or nothing pending at all.
        assertNull(returnFocusIndex(chats, 42))
        assertNull(returnFocusIndex(chats, null))
    }

    @Test
    fun `return focus finds the card, and an album member resolves to its album card`() {
        // Album members are grouped under their representative, so a preview opened from the
        // album popup has no card of its own to come back to (2026-10-17).
        val album = item(messageId = 10, albumId = 10, albumSize = 2).copy(
            albumMembers = listOf(item(messageId = 10), item(messageId = 11)),
        )
        val wall = listOf(item(messageId = 1), album, item(messageId = 20))

        // A plain card is itself.
        assertEquals(0, gridIndexForMessage(wall, 1))
        // A member resolves to the album's card.
        assertEquals(1, gridIndexForMessage(wall, 11))
        assertEquals(1, gridIndexForMessage(wall, 10))
        // Nothing in the loaded wall holds it.
        assertEquals(-1, gridIndexForMessage(wall, 99))
    }

    private fun chat(id: Long) = ChatItem(
        id = id,
        title = "chat $id",
        type = ChatType.Private,
        unreadCount = 0,
        lastMessageText = null,
    )
}
