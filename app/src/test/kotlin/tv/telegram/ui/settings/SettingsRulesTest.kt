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
}
