package tv.telegram.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.td.AuthState
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

    @Test
    fun `the cold start route follows the authorization state`() {
        assertEquals(Routes.HOME_SCREEN, authStartRoute(AuthState.Ready))
        assertEquals(Routes.COLD_START, authStartRoute(AuthState.Idle))
        assertEquals(Routes.COLD_START, authStartRoute(AuthState.WaitTdlibParams))
        assertEquals(Routes.COLD_START, authStartRoute(AuthState.WaitEncryptionKey))
        // Anything else is the login flow — including the states a log-out lands in.
        assertEquals(Routes.QR_LOGIN, authStartRoute(AuthState.WaitQrCode("tg://qr")))
        assertEquals(Routes.QR_LOGIN, authStartRoute(AuthState.LoggingIn))
        assertEquals(Routes.QR_LOGIN, authStartRoute(AuthState.Closed))
        assertEquals(Routes.QR_LOGIN, authStartRoute(AuthState.Error("x")))
    }

    @Test
    fun `only our own sign-out restarts the closed client`() {
        // Closed after we asked for a log-out: restart it, or the QR flow can never start.
        assertTrue(shouldRestartClosedClient(AuthState.Closed, signOutRequested = true))
        // A Closed we did not cause must be left alone.
        assertFalse(shouldRestartClosedClient(AuthState.Closed, signOutRequested = false))
        assertFalse(shouldRestartClosedClient(AuthState.Ready, signOutRequested = true))
        assertFalse(shouldRestartClosedClient(AuthState.WaitQrCode("tg://qr"), signOutRequested = true))
    }
}
