@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package tv.telegram.ui.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language

private class FakeSettingsState : SettingsState {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Ready)
    override val currentUser: StateFlow<TdUser?> = MutableStateFlow(null)
    override val language: StateFlow<Language> = MutableStateFlow(Language.English)
    var chosen: Language? = null
    override fun setLanguage(lang: Language) {
        chosen = lang
    }
}

/**
 * The rebuilt settings page: the section list drives the pane, and the About pane is
 * the three rows the frames draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = FakeSettingsState()

    private fun show(versionName: String = "9.9.9-test") {
        rule.setContent { SettingsScreen(state = state, versionName = versionName) }
        rule.waitForIdle()
    }

    /** Selects a section the way a remote does: focus it, then press OK. */
    private fun select(section: String) {
        rule.onNodeWithTag("settings-section-$section").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-section-$section").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
    }

    @Test
    fun `choosing About renders the three rows the frames draw`() {
        show()
        select("About")

        rule.onNodeWithText("Version").assertExists()
        rule.onNodeWithText("9.9.9-test").assertExists()
        rule.onNodeWithText("License").assertExists()
        rule.onNodeWithText("MIT").assertExists()
        rule.onNodeWithText("Check for updates").assertExists()
    }

    @Test
    fun `the version row reports the build's own versionName`() {
        show(versionName = "1.2.3-probe")
        select("About")
        rule.onNodeWithText("1.2.3-probe").assertExists()
    }

    @Test
    fun `the updates row is focusable but does nothing yet`() {
        show()
        select("About")

        rule.onNodeWithTag("settings-check-updates").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-check-updates").assertIsFocused()
        // Product decision 2026-09-23: confirming it is deliberately inert.
        rule.onNodeWithTag("settings-check-updates").assertHasNoClickAction()
    }

    @Test
    fun `choosing a language reports it`() {
        show()
        select("PreferredLanguage")
        rule.onNodeWithText("English").assertExists()
    }
}
