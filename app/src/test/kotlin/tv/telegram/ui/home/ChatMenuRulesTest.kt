package tv.telegram.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.R
import tv.telegram.td.ChatType

class ChatMenuRulesTest {

    @Test
    fun `the four actions are fixed and only their labels follow the state`() {
        // Plain chat -> 置顶 / 静音 / 归档 / 删除
        assertEquals(R.string.chat_menu_pin, chatMenuLabelRes(ChatMenuAction.Pin, pinned = false, muted = false, archived = false))
        assertEquals(R.string.chat_menu_mute, chatMenuLabelRes(ChatMenuAction.Mute, pinned = false, muted = false, archived = false))
        assertEquals(R.string.chat_menu_archive, chatMenuLabelRes(ChatMenuAction.Archive, pinned = false, muted = false, archived = false))
        // Pinned + muted + archived -> the inverses; delete never changes.
        assertEquals(R.string.chat_menu_unpin, chatMenuLabelRes(ChatMenuAction.Pin, pinned = true, muted = true, archived = true))
        assertEquals(R.string.chat_menu_unmute, chatMenuLabelRes(ChatMenuAction.Mute, pinned = true, muted = true, archived = true))
        assertEquals(R.string.chat_menu_unarchive, chatMenuLabelRes(ChatMenuAction.Archive, pinned = true, muted = true, archived = true))
        assertEquals(R.string.chat_menu_delete, chatMenuLabelRes(ChatMenuAction.Delete, pinned = true, muted = true, archived = true))
        assertEquals(4, ChatMenuAction.entries.size)
    }

    @Test
    fun `the menu sits on the row, but never past the bottom of the list`() {
        // Level with the row when there is room…
        assertEquals(100.dp, chatMenuTop(rowTop = 100.dp, listHeight = 412.dp, menuHeight = 188.dp))
        // …and pinned to the bottom when there is not (list 412 - menu 188 = 224).
        assertEquals(224.dp, chatMenuTop(rowTop = 300.dp, listHeight = 412.dp, menuHeight = 188.dp))
        assertTrue(chatMenuTop(0.dp, 100.dp, 188.dp).value >= 0f)
    }

    @Test
    fun `the destructive row and its dialog follow the chat type`() {
        // Private: Delete. Group I own: Delete and leave. Group I am in / channel: Leave.
        assertEquals(ChatDeleteCase.Private, chatDeleteCase(ChatType.Private, isOwner = true))
        assertEquals(ChatDeleteCase.GroupOwner, chatDeleteCase(ChatType.Group, isOwner = true))
        assertEquals(ChatDeleteCase.GroupMember, chatDeleteCase(ChatType.Group, isOwner = false))
        assertEquals(ChatDeleteCase.Channel, chatDeleteCase(ChatType.Channel, isOwner = true))

        assertEquals(tv.telegram.R.string.chat_menu_delete, chatMenuDestructiveLabelRes(ChatDeleteCase.Private))
        assertEquals(tv.telegram.R.string.chat_menu_delete_and_leave, chatMenuDestructiveLabelRes(ChatDeleteCase.GroupOwner))
        assertEquals(tv.telegram.R.string.chat_menu_leave, chatMenuDestructiveLabelRes(ChatDeleteCase.GroupMember))
        assertEquals(tv.telegram.R.string.chat_menu_leave, chatMenuDestructiveLabelRes(ChatDeleteCase.Channel))
    }

    @Test
    fun `only the private dialog carries the toggle, and it decides the revoke`() {
        assertTrue(chatDeleteHasRadio(ChatType.Private, isOwner = false))
        assertFalse(chatDeleteHasRadio(ChatType.Group, isOwner = true))
        assertFalse(chatDeleteHasRadio(ChatType.Group, isOwner = false))
        assertFalse(chatDeleteHasRadio(ChatType.Channel, isOwner = true))
        assertFalse(chatDeleteHasRadio(ChatType.SavedMessages, isOwner = false))   // no other side

        // Private: the toggle says whether the other side loses the history too.
        assertFalse(chatDeleteRevokes(ChatType.Private, isOwner = false, radioChecked = false))
        assertTrue(chatDeleteRevokes(ChatType.Private, isOwner = false, radioChecked = true))
        // A group owner's frame promises the history goes for everyone.
        assertTrue(chatDeleteRevokes(ChatType.Group, isOwner = true, radioChecked = false))
        // Leaving never wipes anything for others.
        assertFalse(chatDeleteRevokes(ChatType.Group, isOwner = false, radioChecked = true))
        assertFalse(chatDeleteRevokes(ChatType.Channel, isOwner = true, radioChecked = true))
    }

    @Test
    fun `a channel owner's Leave deletes the channel`() {
        // Label still says Leave, but the action wipes the channel (product, 2026-09-27).
        assertTrue(chatDeleteRevokes(ChatType.Channel, isOwner = true, radioChecked = false))
        assertFalse(chatDeleteRevokes(ChatType.Channel, isOwner = false, radioChecked = false))
    }
}
