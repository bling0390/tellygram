@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package tv.telegram.ui.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.requestFocus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ChatContextMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private var picked: ChatMenuAction? = null
    private var dismissed = false

    private fun chat(
        pinned: Boolean = false,
        muted: Boolean = false,
        type: ChatType = ChatType.Private,
    ) = ChatItem(
        id = 1,
        title = "Alpha",
        type = type,
        unreadCount = 0,
        isMuted = muted,
        isPinned = pinned,
        lastMessageText = null,
    )

    private var owner = false

    private fun show(isChat: ChatItem = chat(), archived: Boolean = false, owner: Boolean = false) {
        this.owner = owner
        picked = null
        dismissed = false
        rule.setContent {
            ChatContextMenu(
                chat = isChat,
                archived = archived,
                isOwner = owner,
                top = 0.dp,
                onSelect = { picked = it },
                onDismiss = { dismissed = true },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `it shows the four fixed rows and focuses the first`() {
        show()
        for (tag in listOf("pin", "mute", "archive", "delete")) {
            rule.onNodeWithTag("chat-menu-$tag").assertExists()
        }
        rule.onNodeWithTag("chat-menu-pin").assertIsFocused()
    }

    @Test
    fun `confirming a row reports its action`() {
        show()
        // move focus to the row first: key input goes to whatever is focused (the first row)
        rule.onNodeWithTag("chat-menu-mute").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("chat-menu-mute").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(ChatMenuAction.Mute, picked)
    }

    @Test
    fun `back closes the popover`() {
        show()
        rule.onNodeWithTag("chat-menu-pin").performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun `focus cannot escape the menu`() {
        // Only Up/Down (+ OK, Back) do anything here: Left/Right and the two ends are
        // cancelled, so the search can never land on a row or cell behind the popover.
        show()
        val first = rule.onNodeWithTag("chat-menu-pin")
        first.assertIsFocused()
        for (key in listOf(Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight)) {
            first.performKeyInput { pressKey(key) }
            rule.waitForIdle()
            first.assertIsFocused()
        }
        val last = rule.onNodeWithTag("chat-menu-delete")
        last.requestFocus()
        rule.waitForIdle()
        for (key in listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
            last.performKeyInput { pressKey(key) }
            rule.waitForIdle()
            last.assertIsFocused()
        }
    }

    @Test
    fun `the destructive row says Delete for a private chat`() {
        // Private chats are deleted, not left.
        show(chat(type = ChatType.Private))
        rule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun `the destructive row says Delete and leave for a group I own`() {
        show(chat(type = ChatType.Group), owner = true)
        rule.onNodeWithText("Delete and leave").assertExists()
    }

    @Test
    fun `the destructive row says Leave for a group I am in`() {
        show(chat(type = ChatType.Group), owner = false)
        rule.onNodeWithText("Leave").assertExists()
    }

    @Test
    fun `the destructive row says Leave for a channel even when I own it`() {
        // Product: channels never distinguish the owner; only the action differs.
        show(chat(type = ChatType.Channel), owner = true)
        rule.onNodeWithText("Leave").assertExists()
    }

    @Test
    fun `a release that is not armed does not fire a row`() {
        // P0 (2026-09-28): the long press that opened the menu keeps sending into it, and its
        // release must not act. The harness cannot send a bare key-up, so the equivalent path
        // is: press (arms), navigate away (drops the arming), release -> nothing fires.
        show()
        rule.onNodeWithTag("chat-menu-pin").performKeyInput { keyDown(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag("chat-menu-pin").performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("chat-menu-mute").performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        assertTrue(picked == null)
    }
}
