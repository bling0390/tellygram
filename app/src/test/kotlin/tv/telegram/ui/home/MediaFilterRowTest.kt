@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package tv.telegram.ui.home

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
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.telegram.td.MediaFilter

/**
 * The chips' D-pad edges, from the design annotations: sideways to the neighbours,
 * Left from the first chip to the selected chat, Down back to the remembered cell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaFilterRowTest {

    @get:Rule
    val rule = createComposeRule()

    private companion object {
        const val CHAT_TAG = "selected-chat"
        const val GRID_TAG = "remembered-grid-cell"
    }

    private var selectedByClick: MediaFilter? = null

    private fun showRow(selected: MediaFilter = MediaFilter.All): HomeFocus {
        val focus = HomeFocus(
            selectedChat = FocusRequester(),
            topBar = FocusRequester(),
            selectedChip = FocusRequester(),
            firstGrid = FocusRequester(),
            rememberedGrid = FocusRequester(),
        ).apply {
            // The chips' Down reads this field at focus time; point it at the cell the
            // test tags as "remembered".
            gridEntry = rememberedGrid
        }
        rule.setContent {
            Column {
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(CHAT_TAG)
                        .focusRequester(focus.selectedChat)
                        .focusable(),
                )
                MediaFilterRow(
                    selected = selected,
                    focus = focus,
                    onRegion = { },
                    onSelect = { picked -> selectedByClick = picked },
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(GRID_TAG)
                        .focusRequester(focus.rememberedGrid)
                        .focusable(),
                )
            }
        }
        return focus
    }

    @Test
    fun `left from the first chip reaches the selected chat`() {
        showRow()
        rule.onNodeWithText("ALL").onParent().requestFocus()
        rule.onNodeWithText("ALL").onParent().assertIsFocused()

        rule.onNodeWithText("ALL").onParent().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithTag(CHAT_TAG).assertIsFocused()
    }

    @Test
    fun `right at the last chip stops there`() {
        showRow()
        rule.onNodeWithText("Text").onParent().requestFocus()

        rule.onNodeWithText("Text").onParent().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Text").onParent().assertIsFocused()
    }

    @Test
    fun `down from a chip reaches the remembered grid cell`() {
        showRow()
        rule.onNodeWithText("Video").onParent().requestFocus()

        rule.onNodeWithText("Video").onParent().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(GRID_TAG).assertIsFocused()
    }
    @Test
    fun `OK on a chip reports the selection`() {
        showRow()
        rule.onNodeWithText("Video").onParent().requestFocus()
        rule.waitForIdle()
        rule.onNodeWithText("Video").onParent().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(MediaFilter.Video, selectedByClick)
    }

}
