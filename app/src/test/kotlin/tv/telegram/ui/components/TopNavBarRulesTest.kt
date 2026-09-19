package tv.telegram.ui.components

import org.junit.Assert.assertEquals
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
}
