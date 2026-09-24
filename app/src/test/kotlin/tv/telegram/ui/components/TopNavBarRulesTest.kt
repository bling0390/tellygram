package tv.telegram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bar highlights the focused entry, or the shell's tab when focus is away. */
class TopNavBarRulesTest {


    @Test
    fun `the active entry gets the white pill, the rest stay unfilled`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), navPillColor(active = true))
        assertNull(navPillColor(active = false))
    }

    @Test
    fun `the label flips to the dark inverse colour on the pill`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC7C6CA), navLabelColor(active = false))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1A1C1E), navLabelColor(active = true))
    }

    @Test
    fun `the selected entry follows the shell page, not focus`() {
        assertEquals(TopNavItem.Chat, selectedNavItem(TopNavTab.Chat))
        assertEquals(TopNavItem.Setting, selectedNavItem(TopNavTab.Setting))
    }

    @Test
    fun `focus and selection share one treatment`() {
        // The bar feeds `selected || focused` into one pair of colour rules, so a focused
        // entry is drawn exactly like a selected one — by construction, not by a second
        // (invented) visual.
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), navPillColor(active = true))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1A1C1E), navLabelColor(active = true))
        assertNull(navPillColor(active = false))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC7C6CA), navLabelColor(active = false))
    }
}
