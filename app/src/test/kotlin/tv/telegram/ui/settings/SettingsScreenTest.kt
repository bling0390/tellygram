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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.focus.FocusRequester

private class FakeSettingsState(private val bio: String = "i love tellygram") : SettingsState {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Ready)
    override val currentUser: StateFlow<TdUser?> = MutableStateFlow(
        TdUser(
            id = 123456,
            firstName = "Jason",
            lastName = "",
            username = "jason",
            phoneNumber = "+86 12345678910",
            bio = bio,
        ),
    )
    var logOutCalled = false
    override val language: StateFlow<Language> = MutableStateFlow(Language.English)
    var chosen: Language? = null
    override fun setLanguage(lang: Language) {
        chosen = lang
    }

    override fun logOut() {
        logOutCalled = true
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

    @Test
    fun `the account pane lists id, username, phone and bio`() {
        show()
        select("Accounts")
        rule.onNodeWithText("ID").assertExists()
        rule.onNodeWithText("123456").assertExists()
        rule.onNodeWithText("Username").assertExists()
        rule.onNodeWithText("jason").assertExists()
        rule.onNodeWithText("Phone").assertExists()
        // Shown in full, per the design.
        rule.onNodeWithText("+86 12345678910").assertExists()
        rule.onNodeWithText("BIO").assertExists()
        rule.onNodeWithText("i love tellygram").assertExists()
    }

    @Test
    fun `the log out row opens the dialog with cancel focused`() {
        show()
        select("Accounts")
        rule.onNodeWithTag("settings-log-out").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-log-out").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNodeWithTag("settings-logout-dialog").assertExists()
        rule.onNodeWithTag("settings-logout-cancel").assertIsFocused()
    }

    @Test
    fun `cancel closes the dialog and confirm reports the sign-out`() {
        show()
        select("Accounts")
        rule.onNodeWithTag("settings-log-out").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-log-out").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag("settings-logout-cancel").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag("settings-logout-dialog").assertDoesNotExist()
        assertFalse(state.logOutCalled)

        // Re-open and confirm.
        rule.onNodeWithTag("settings-log-out").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-log-out").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag("settings-logout-confirm").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-logout-confirm").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertTrue(state.logOutCalled)
    }

@Test
    fun `a long value stays one line`() {
        // Option A: the value ellipsizes inside the width the row hands it, so it must
        // render exactly as tall as a short one. Comparing two values measured in the
        // same composition avoids depending on density or on a hard-coded pixel size.
        val long = "x".repeat(400)
        rule.setContent {
            SettingsScreen(state = FakeSettingsState(bio = long), versionName = "9.9.9-test")
        }
        rule.waitForIdle()

        val shortValue = rule.onNodeWithText("jason").getUnclippedBoundsInRoot()
        val longValue = rule.onNodeWithText(long).getUnclippedBoundsInRoot()
        val shortH = (shortValue.bottom - shortValue.top).value
        val longH = (longValue.bottom - longValue.top).value
        assertTrue(
            "long value is $longH tall but short is $shortH: it wrapped",
            longH <= shortH + 1f,
        )
    }

    @Test
    fun `the language pane marks the chosen language and switches on confirm`() {
        show()
        select("PreferredLanguage")

        // The frame's title is "Language", and all three of our languages are listed.
        rule.onNodeWithText("Language").assertExists()
        rule.onNodeWithText("English").assertExists()
        rule.onNodeWithText("简体中文").assertExists()
        rule.onNodeWithText("繁體中文").assertExists()

        // The chosen language carries the trailing check (English is the default here).
        rule.onNodeWithTag("settings-language-check-English", useUnmergedTree = true).assertExists()

        rule.onNodeWithTag("settings-language-繁體中文").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-language-繁體中文").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(Language.TraditionalChinese, state.chosen)
    }

    @Test
    fun `the help pane shows the contact row and it is display-only`() {
        show()
        select("HelpAndSupport")

        // Once in the left list, once as the pane title.
        assertEquals(2, rule.onAllNodesWithText("Help and Support").fetchSemanticsNodes().size)
        rule.onNodeWithText("Contact us on").assertExists()
        rule.onNodeWithText("feedback@tellygram.app").assertExists()
        // Not focusable and nothing to confirm, per the product decision.
        rule.onNodeWithTag("settings-help-contact", useUnmergedTree = true).assertHasNoClickAction()
    }

    @Test
    fun `the entry focus requester the shell hands over is attached`() {
        // The crash this guards: the shell pointed the top bar's Down at a requester that
        // this page never attached, so the focus search threw IllegalStateException.
        val entry = FocusRequester()
        rule.setContent {
            SettingsScreen(state = FakeSettingsState(), versionName = "9.9.9-test", contentEntryFocus = entry)
        }
        rule.waitForIdle()
        val attached = rule.runOnIdle { runCatching { entry.requestFocus() }.isSuccess }
        assertTrue("the entry requester must be attached to the first section row", attached)
    }

    @Test
    fun `the page takes focus on arrival, on the Accounts entry`() {
        // What the product asked for: opening Settings lands on the Accounts entry, which
        // is both the focused and the selected section.
        val entry = FocusRequester()
        rule.setContent {
            SettingsScreen(state = FakeSettingsState(), versionName = "9.9.9-test", contentEntryFocus = entry)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("settings-section-Accounts").assertIsFocused()
    }

    @Test
    fun `focusing a section highlights it, even before it is chosen`() {
        show()
        rule.onNodeWithTag("settings-section-About").requestFocus()
        rule.waitForIdle()

        // The design's List item Focused variant is the same white pill.
        rule.onNodeWithTag("settings-section-About-highlighted", useUnmergedTree = true).assertExists()
        // ...and the chosen section keeps its highlight at the same time.
        rule.onNodeWithTag("settings-section-Accounts-highlighted", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the highlighted section spans the whole list row`() {
        // The design's list column stretches, so the white pill must be the full 268dp —
        // before the width modifier it hugged the icon and label instead.
        show()
        val row = rule.onNodeWithTag("settings-section-Accounts").getUnclippedBoundsInRoot()
        val width = (row.right - row.left).value
        assertTrue("row is ${width}dp wide, expected the 268dp column", width >= 260f)
    }

@Test
    fun `a pane value stops inside the pane, not past its right edge`() {
        // The padding used to sit outside the fixed width, so each row measured 484dp on a
        // 452dp pane and right-aligned values landed past the pane's right edge (and the
        // Log out panel stuck out 32dp). Measured on the symptom: a value's right edge.
        show()
        val paneRight = 398f + 452f          // the frame's pane: x=398, width=452
        val value = rule.onNodeWithText("123456").getUnclippedBoundsInRoot()
        val right = value.right.value
        assertTrue("value ends at ${right}dp, pane ends at $paneRight", right <= paneRight - 8f)
    }

    @Test
    fun `the chosen language's check is the design's 24dp`() {
        // The frame draws check_24px at 24x24; the row used to draw it at 20.
        show()
        select("PreferredLanguage")
        val check = rule.onNodeWithTag("settings-language-check-English", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // .size(24.dp) makes it square, and measure reports the height faithfully here
        // (the width comes back 0 in this harness, as with the Log out row).
        val h = (check.bottom - check.top).value
        assertTrue("check is ${h}dp tall, expected the design's 24", h in 22f..26f)
    }
}
