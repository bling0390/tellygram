@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package tv.telegram.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bar's D-pad rules: sideways inside the bar, Down leaving for the content.
 * Focus is asserted through semantics, so these run on the JVM via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TopNavBarTest {

    @get:Rule
    val rule = createComposeRule()

    private companion object {
        const val CONTENT_TAG = "content-entry"
    }

    private fun showBar(): FocusRequester {
        val content = FocusRequester()
        val chat = FocusRequester()
        rule.setContent {
            Column {
                TopNavBar(
                    selectedTab = TopNavTab.Chat,
                    onTabSelected = { },
                    onSearchClick = { },
                    selectedTabFocus = chat,
                    contentFocus = content,
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(CONTENT_TAG)
                        .focusRequester(content)
                        .focusable(),
                )
            }
        }
        return chat
    }

    @Test
    fun `focus enters on Chat and moves sideways to Setting`() {
        val chat = showBar()
        rule.runOnIdle { chat.requestFocus() }
        rule.onNodeWithText("Chat").onParent().assertIsFocused()

        rule.onNodeWithText("Chat").onParent().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Setting").onParent().assertIsFocused()
    }

    @Test
    fun `down from the bar lands on the content, not the chat list's neighbours`() {
        val chat = showBar()
        rule.runOnIdle { chat.requestFocus() }

        rule.onNodeWithText("Chat").onParent().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(CONTENT_TAG).assertIsFocused()
    }
}
