package tv.telegram.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.ui.nav.Routes

/**
 * Which routes still draw the legacy navigation rail.
 *
 * The settings page rendered BOTH the rail and the shell's top bar, because it stayed in
 * the rail's destination list after it became a shell page. This asserts the list.
 */
class ShellLayoutRulesTest {

    @Test
    fun `legacy rail destinations keep the rail`() {
        assertTrue(showsRail(Routes.HOME_CHATS))
        assertTrue(showsRail(Routes.HOME_SEARCH))
    }

    @Test
    fun `the shell pages do not, they bring the top bar`() {
        assertFalse(showsRail(Routes.HOME_SCREEN))
        assertFalse(showsRail(Routes.HOME_SETTINGS))
        assertFalse(showsRail(Routes.PLAYER))
        assertFalse(showsRail(null))
    }
}
