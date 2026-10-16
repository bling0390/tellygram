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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
            archivedRow = FocusRequester(),
            gridContainer = FocusRequester(),
            entry = FocusRequester(),
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

    @Test
    fun `the check marks the filter in effect, whichever one it is`() {
        // Design 3608:2203 draws the check on ALL (focused AND applied) and on Video (applied
        // but outlined). The marker therefore follows the applied filter instead of being an
        // ALL-only badge (2026-10-16).
        showRow(selected = MediaFilter.Video)

        rule.onNodeWithTag("home-filter-check-Video", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("home-filter-check-ALL", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the chips pad 20 left and right and 10 top and bottom`() {
        // Design update 2026-10-16: both variants share one padding, so carrying the check no
        // longer narrows a chip. The height then comes from the padding rather than a fixed box.
        showRow(selected = MediaFilter.Video)

        val chip = rule.onNodeWithTag("home-filter-chip-Video", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val check = rule.onNodeWithTag("home-filter-check-Video", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        // The check starts exactly 20dp inside the chip's left edge...
        assertEquals(20f, (check.left - chip.left).value, 0.5f)
        // ...and the chip's height is its label plus 10dp top and bottom, so there is no fixed
        // box any more. Asserted as a relationship rather than the frame's absolute 36dp: the
        // JVM harness's text metrics are not the device's (a 16sp line measures ~36dp here,
        // where on a device it is the design's 16dp and the chip lands on 36).
        val label = rule.onNodeWithText("Video", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            (label.bottom - label.top).value + 20f,
            (chip.bottom - chip.top).value,
            0.5f,
        )
    }

}
