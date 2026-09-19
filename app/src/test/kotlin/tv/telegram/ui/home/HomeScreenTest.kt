@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package tv.telegram.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.ui.focus.BackController
import tv.telegram.ui.focus.LocalBackController

/** Stand-in for MainViewModel: the real one starts TDLib, which cannot run on the JVM. */
private class FakeHomeState : HomeState {
    override val chatList: StateFlow<List<ChatItem>> = MutableStateFlow(
        listOf(chat(1, "Alpha"), chat(2, "Beta")),
    )
    override val mediaItems: StateFlow<List<MediaItem>> = MutableStateFlow(
        listOf(media(1), media(2), media(3), media(4), media(5)),
    )
    override val mediaLoadingMore: StateFlow<Boolean> = MutableStateFlow(false)
    override val mediaExhausted: StateFlow<Boolean> = MutableStateFlow(false)
    override val fileStates: StateFlow<Map<Int, FileDownloadState>> = MutableStateFlow(emptyMap())

    override fun ensureMediaFile(fileId: Int, priority: Int) = Unit
    override fun openChat(chatId: Long) = Unit
    override fun loadMoreMedia() = Unit
    override fun setMediaFilter(filter: MediaFilter) = Unit

    private companion object {
        fun chat(id: Long, title: String) = ChatItem(
            id = id,
            title = title,
            type = ChatType.Private,
            unreadCount = 0,
            lastMessageText = null,
        )

        fun media(id: Long) = MediaItem(messageId = id, type = MediaType.Photo, fileId = 0)
    }
}

/**
 * The home screen's D-pad graph end to end: focus moves through real key presses and
 * the Back rules are dispatched through the project's BackController.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val back = BackController()
    private val state = FakeHomeState()

    private companion object {
        const val TOP_BAR_TAG = "test-top-bar"
    }

    /** Renders HomeScreen under a stand-in top bar that owns the Back target. */
    private fun show(withTopBar: Boolean = true) {
        val contentEntry = FocusRequester()
        val topBar = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalBackController provides back) {
                Column {
                    if (withTopBar) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .testTag(TOP_BAR_TAG)
                                .focusRequester(topBar)
                                .focusable(),
                        )
                    }
                    HomeScreen(
                        state = state,
                        contentEntryFocus = contentEntry,
                        topBarFocus = if (withTopBar) topBar else null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    // ── 会话列表 → 媒体格 ─────────────────────────────────────────────────────

    @Test
    fun `right from the chat list enters the media grid`() {
        show()
        rule.onNodeWithTag("home-chat-row-1").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home-chat-row-1").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-0").assertIsFocused()
    }

    // ── 媒体格的四向边界 ──────────────────────────────────────────────────────

    @Test
    fun `left from the first column returns to the selected chat`() {
        show()
        rule.onNodeWithTag("home-media-cell-0").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-0").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        rule.onNodeWithTag("home-chat-row-1").assertIsFocused()
    }

    @Test
    fun `right at the last column stops there`() {
        show()
        rule.onNodeWithTag("home-media-cell-2").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-2").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-2").assertIsFocused()
    }

    @Test
    // Robolectric does not reliably apply *programmatic* focus requests here: key-driven
    // moves assert fine, but a requestFocus() issued from Back dispatch or from a
    // focusProperties edge does not land before the assertion. The RULES behind these
    // cases are covered by HomeRulesTest#backTarget / rememberedIndexFor; the moves
    // themselves need a device or emulator run.
    @org.junit.Ignore("needs device: Robolectric does not settle programmatic focus")
    fun `up from the first row reaches the active chip and remembers the cell`() {
        show()
        rule.onNodeWithTag("home-media-cell-1").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-1").performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        // The active chip is ALL; its parent is the focusable chip container.
        rule.onNodeWithText("ALL").onParent().assertIsFocused()

        // Down returns to the remembered cell rather than to [0,0].
        rule.onNodeWithText("ALL").onParent().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-1").assertIsFocused()
    }

    // ── 后退键 ────────────────────────────────────────────────────────────────

    @Test
    // Robolectric does not reliably apply *programmatic* focus requests here: key-driven
    // moves assert fine, but a requestFocus() issued from Back dispatch or from a
    // focusProperties edge does not land before the assertion. The RULES behind these
    // cases are covered by HomeRulesTest#backTarget / rememberedIndexFor; the moves
    // themselves need a device or emulator run.
    @org.junit.Ignore("needs device: Robolectric does not settle programmatic focus")
    fun `back from cell zero returns to the selected chat`() {
        show()
        rule.onNodeWithTag("home-media-cell-0").requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { back.dispatch() }
        rule.waitForIdle()
        rule.onNodeWithTag("home-chat-row-1").assertIsFocused()
    }

    @Test
    // Robolectric does not reliably apply *programmatic* focus requests here: key-driven
    // moves assert fine, but a requestFocus() issued from Back dispatch or from a
    // focusProperties edge does not land before the assertion. The RULES behind these
    // cases are covered by HomeRulesTest#backTarget / rememberedIndexFor; the moves
    // themselves need a device or emulator run.
    @org.junit.Ignore("needs device: Robolectric does not settle programmatic focus")
    fun `back from a deeper cell goes to cell zero`() {
        show()
        rule.onNodeWithTag("home-media-cell-3").requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { back.dispatch() }
        rule.waitForIdle()
        rule.onNodeWithTag("home-media-cell-0").assertIsFocused()
    }

    @Test
    // Robolectric does not reliably apply *programmatic* focus requests here: key-driven
    // moves assert fine, but a requestFocus() issued from Back dispatch or from a
    // focusProperties edge does not land before the assertion. The RULES behind these
    // cases are covered by HomeRulesTest#backTarget / rememberedIndexFor; the moves
    // themselves need a device or emulator run.
    @org.junit.Ignore("needs device: Robolectric does not settle programmatic focus")
    fun `back from the chips returns to the selected chat`() {
        show()
        rule.onNodeWithText("Video").onParent().requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { back.dispatch() }
        rule.waitForIdle()
        rule.onNodeWithTag("home-chat-row-1").assertIsFocused()
    }

    @Test
    // Robolectric does not reliably apply *programmatic* focus requests here: key-driven
    // moves assert fine, but a requestFocus() issued from Back dispatch or from a
    // focusProperties edge does not land before the assertion. The RULES behind these
    // cases are covered by HomeRulesTest#backTarget / rememberedIndexFor; the moves
    // themselves need a device or emulator run.
    @org.junit.Ignore("needs device: Robolectric does not settle programmatic focus")
    fun `back from the chat list returns to the top bar`() {
        show()
        rule.onNodeWithTag("home-chat-row-1").requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { back.dispatch() }
        rule.waitForIdle()
        rule.onNodeWithTag(TOP_BAR_TAG).assertIsFocused()
    }
}
