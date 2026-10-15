package tv.telegram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bar fills the entry that holds focus, and only that one: since 2026-10-15 focus
 * IS selection, so there is no second, page-driven "selected" look to feed in.
 */
class TopNavBarRulesTest {

    @Test
    fun `the focused entry gets the white pill, the rest stay unfilled`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), navPillColor(focused = true))
        assertNull(navPillColor(focused = false))
    }

    @Test
    fun `the label flips to the dark inverse colour on the pill`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC7C6CA), navLabelColor(focused = false))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1A1C1E), navLabelColor(focused = true))
    }
}
