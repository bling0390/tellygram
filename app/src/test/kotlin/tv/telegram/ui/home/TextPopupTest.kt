@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package tv.telegram.ui.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TextPopupTest {

    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private var dismissed = false
    private val body = "Text message content here, if there too many words do not ellipsis."

    private val state = FakeHomeState()

    private fun show(senderName: String? = "jason") {
        dismissed = false
        rule.setContent {
            TextPopup(
                text = body,
                senderName = senderName,
                avatarFileId = null,
                senderId = 42L,
                state = state,
                onDismiss = { dismissed = true },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `it shows the sender and the whole body`() {
        show()
        rule.onNodeWithTag("text-popup-panel").assertExists()
        // Avatar slot + the sender's name with its colon (frames 3588:2282).
        rule.onNodeWithTag("text-popup-avatar").assertExists()
        rule.onNodeWithText("jason:").assertExists()
        // No ellipsis: the full body is in the tree (it scrolls when it is long).
        rule.onNodeWithText(body).assertExists()
    }

    @Test
    fun `without a name it falls back to the literal`() {
        show(senderName = null)
        rule.onNodeWithText("Sender:").assertExists()
    }

    @Test
    fun `back dismisses it`() {
        show()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertTrue(dismissed)
    }
}
