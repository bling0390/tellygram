package tv.telegram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bar highlights the focused entry, or the shell's tab when focus is away. */
class TopNavBarRulesTest {

    @Test
    fun `the focused entry is the highlighted one`() {
        assertEquals(TopNavItem.Chat, activeNavItem(TopNavItem.Chat, TopNavTab.Setting))
        assertEquals(TopNavItem.Setting, activeNavItem(TopNavItem.Setting, TopNavTab.Chat))
        assertEquals(TopNavItem.Search, activeNavItem(TopNavItem.Search, TopNavTab.Chat))
    }

    @Test
    fun `focus leaving the bar falls back to the shell's tab, Chat being home`() {
        assertEquals(TopNavItem.Chat, activeNavItem(null, TopNavTab.Chat))
        assertEquals(TopNavItem.Setting, activeNavItem(null, TopNavTab.Setting))
    }

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
}
