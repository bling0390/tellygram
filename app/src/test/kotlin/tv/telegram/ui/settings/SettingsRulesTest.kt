package tv.telegram.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.telegram.ui.home.HomeSpec

/** Row colour rules from the settings frames (3239:2418 / 3240:2375). */
class SettingsRulesTest {

    @Test
    fun `the selected section is a white pill with the dark label`() {
        val on = sectionItemColors(selected = true)
        assertEquals(HomeSpec.White, on.fill)
        assertEquals(HomeSpec.InverseOnSurface, on.text)
    }

    @Test
    fun `an unselected section is unfilled with the on-surface label`() {
        val off = sectionItemColors(selected = false)
        assertEquals(HomeSpec.OnSurface, off.text)
    }

    @Test
    fun `the actionable row is a surface panel until it is focused`() {
        val idle = actionableRowColors(highlighted = false)
        assertEquals(HomeSpec.SurfaceContainer, idle.fill)
        assertEquals(HomeSpec.OnSurface, idle.text)

        val focused = actionableRowColors(highlighted = true)
        assertEquals(HomeSpec.White, focused.fill)
        assertEquals(HomeSpec.InverseOnSurface, focused.text)
    }

    
    
    @Test
    fun `the dialog's buttons share the focused treatment`() {
        // Focused: both flip to the white pill treatment the rest of the app uses.
        assertEquals(HomeSpec.White, dialogCancelColors(true).fill)
        assertEquals(HomeSpec.InverseOnSurface, dialogCancelColors(true).text)
        assertEquals(HomeSpec.White, dialogConfirmColors(true).fill)
        assertEquals(HomeSpec.InverseOnSurface, dialogConfirmColors(true).text)

        // At rest the cancel is a ghost with the body colour; the confirm is the dark primary.
        assertEquals(androidx.compose.ui.graphics.Color(0x1A000000), dialogCancelColors(false).fill)
        assertEquals(SettingsSpec.DialogBody, dialogCancelColors(false).text)
        assertEquals(HomeSpec.InverseOnSurface, dialogConfirmColors(false).fill)
        assertEquals(HomeSpec.OnSurface, dialogConfirmColors(false).text)
    }
}
