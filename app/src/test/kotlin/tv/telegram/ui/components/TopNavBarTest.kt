@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package tv.telegram.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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

    @Test
    fun `the selection stays on Chat while focus moves sideways`() {
        val chat = showBar()
        rule.runOnIdle { chat.requestFocus() }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-active-Chat", useUnmergedTree = true).assertExists()

        rule.onNodeWithText("Chat").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()

        // The pill is page-driven: focus moved, the selection did not.
        rule.onNodeWithTag("topnav-active-Chat", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topnav-idle-Setting", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the focus indicator follows sideways movement`() {
        val chat = showBar()
        rule.runOnIdle { chat.requestFocus() }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-focus-Chat", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topnav-blur-Setting", useUnmergedTree = true).assertExists()

        rule.onNodeWithText("Chat").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()

        rule.onNodeWithTag("topnav-focus-Setting", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topnav-blur-Chat", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `rendering the settings page selects Setting`() {
        rule.setContent {
            TopNavBar(
                selectedTab = TopNavTab.Setting,
                onTabSelected = { },
                onSearchClick = { },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-active-Setting", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topnav-idle-Chat", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `OK on a tab reports the tab, so the shell can switch pages`() {
        var picked: TopNavTab? = null
        rule.setContent {
            TopNavBar(
                selectedTab = TopNavTab.Chat,
                onTabSelected = { picked = it },
                onSearchClick = { },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-idle-Setting", useUnmergedTree = true).requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-idle-Setting", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(TopNavTab.Setting, picked)
    }

    @Test
    fun `the bar's entries sit 4dp apart`() {
        rule.setContent {
            TopNavBar(
                selectedTab = TopNavTab.Chat,
                onTabSelected = { },
                onSearchClick = { },
            )
        }
        rule.waitForIdle()
        val chat = rule.onNodeWithTag("topnav-active-Chat", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val setting = rule.onNodeWithTag("topnav-idle-Setting", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val search = rule.onNodeWithTag("topnav-idle-search", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertEquals(4f, (setting.left - chat.right).value, 0.5f)
        assertEquals(4f, (search.left - setting.right).value, 0.5f)
    }

    @Test
    fun `right at the bar's last entry does not escape into the row below`() {
        // Mirrors the home screen: the bar sits above a wide row of focusables (the
        // filter chips). Without an explicit edge, the default focus search walks down
        // and to the right into that row.
        rule.setContent {
            Column {
                TopNavBar(
                    selectedTab = TopNavTab.Chat,
                    onTabSelected = { },
                    onSearchClick = { },
                )
                Row {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .size(120.dp, 40.dp)
                                .testTag("decoy-$index")
                                .focusable(),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-idle-search", useUnmergedTree = true).requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-idle-search", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("topnav-idle-search", useUnmergedTree = true).assertIsFocused()
    }
}
