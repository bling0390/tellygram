@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package tv.telegram.ui.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell's one-shot entry focus: entering the shell hands focus to the bar's Chat entry,
 * and nothing inside the shell takes it back. Runs on the JVM via Robolectric, because the
 * rule is about focus state rather than pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EntryFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private fun target(tag: String, requester: FocusRequester): Modifier =
        Modifier.size(40.dp).testTag(tag).focusRequester(requester).focusable()

    @Test
    fun `entering the shell hands focus to the target`() {
        val chat = FocusRequester()
        val active = mutableStateOf(false)
        rule.setContent {
            Column {
                Box(target("entry", chat))
                EntryFocus(active = active.value, target = chat)
            }
        }
        rule.waitForIdle()
        // Not yet in the shell: nothing has been focused.
        rule.onNodeWithTag("entry", useUnmergedTree = true).assertIsNotFocused()

        rule.runOnIdle { active.value = true }
        rule.waitForIdle()

        rule.onNodeWithTag("entry", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun `a move inside the shell does not take focus back`() {
        // The rule this guards: a page switch inside the shell must leave focus where the
        // user put it, so the effect is keyed on the shell's presence — not on every pass.
        val entry = FocusRequester()
        val other = FocusRequester()
        var tick by mutableStateOf(0)
        rule.setContent {
            Column {
                Box(target("entry", entry))
                Box(target("other", other))
                // A recomposition that keeps the shell active, the way a page switch does.
                Text("tick=$tick")
                EntryFocus(active = true, target = entry)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("entry", useUnmergedTree = true).assertIsFocused()

        rule.onNodeWithTag("other", useUnmergedTree = true).requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { tick++ }
        rule.waitForIdle()

        rule.onNodeWithTag("other", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun `a viewer round trip does not take focus back`() {
        // Back from a full-screen viewer flips the shell active again; that is not an entry,
        // so the focus the viewer restored must survive (2026-10-21).
        val entry = FocusRequester()
        val other = FocusRequester()
        val active = mutableStateOf(true)
        rule.setContent {
            Column {
                Box(target("entry", entry))
                Box(target("other", other))
                EntryFocus(active = active.value, target = entry)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("entry", useUnmergedTree = true).assertIsFocused()

        // Leave the shell (the viewer is on top), park focus elsewhere, then come back.
        rule.runOnIdle { active.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("other", useUnmergedTree = true).requestFocus()
        rule.waitForIdle()
        rule.runOnIdle { active.value = true }
        rule.waitForIdle()

        rule.onNodeWithTag("other", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun `an unattached target never throws`() {
        // The shell hands the bar's requester in; on a screen where the bar is not composed
        // the request has nothing to land on and must stay silent.
        val orphan = FocusRequester()
        rule.setContent { EntryFocus(active = true, target = orphan) }
        rule.waitForIdle()
        rule.onNodeWithTag("nothing", useUnmergedTree = true).assertDoesNotExist()
    }
}
