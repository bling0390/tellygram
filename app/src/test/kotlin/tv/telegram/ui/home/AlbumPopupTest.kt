@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package tv.telegram.ui.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class AlbumPopupTest {

    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val state = FakeHomeState()

    private var opened: Int? = null
    private var dismissed = false

    private fun item(id: Long) = MediaItem(messageId = id, type = MediaType.Photo, fileId = 0)

    private fun show(
        members: List<MediaItem> = listOf(item(1), item(2), item(3)),
        caption: String? = "Some album caption contents here",
    ) {
        opened = null
        dismissed = false
        rule.setContent {
            AlbumPopup(
                members = members,
                state = state,
                caption = caption,
                onOpenMember = { opened = it },
                onDismiss = { dismissed = true },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `it shows the strip and focuses the first member`() {
        // The strip is a LazyRow: only what fits is composed, the rest scrolls in.
        show()
        rule.onNodeWithTag("album-popup-member-0").assertExists()
        rule.onNodeWithTag("album-popup-member-0").assertIsFocused()
        rule.onNodeWithTag("album-popup-member-1").assertExists()
    }

    @Test
    fun `right walks the strip and the end stops`() {
        show()
        rule.onNodeWithTag("album-popup-member-0").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-1").assertIsFocused()
        rule.onNodeWithTag("album-popup-member-1").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-2").assertIsFocused()
        // Past the last member focus stays put instead of escaping the popup.
        rule.onNodeWithTag("album-popup-member-2").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-2").assertIsFocused()
    }

    @Test
    fun `up and down do nothing`() {
        show()
        rule.onNodeWithTag("album-popup-member-0").performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-0").assertIsFocused()
        rule.onNodeWithTag("album-popup-member-0").performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-0").assertIsFocused()
    }

    @Test
    fun `confirming a member reports its index`() {
        show()
        // Bring it into composition first, then confirm it.
        rule.onNodeWithTag("album-popup-member-0").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("album-popup-member-1").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun `without a caption the body and the divider are gone`() {
        show(caption = null)
        rule.onNodeWithText("Some album caption contents here").assertDoesNotExist()
        rule.onNodeWithTag("album-popup-divider").assertDoesNotExist()
    }

    @Test
    fun `with a caption both are there`() {
        show()
        rule.onNodeWithText("Some album caption contents here").assertExists()
        rule.onNodeWithTag("album-popup-divider").assertExists()
    }

    @Test
    fun `back dismisses the popup`() {
        show()
        // Back is a window/dispatcher affair, not a key the popup sees: drive the real one.
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertTrue(dismissed)
    }
}
