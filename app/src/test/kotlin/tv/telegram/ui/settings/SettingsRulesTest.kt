package tv.telegram.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.Language
import tv.telegram.ui.toBcp47
import org.junit.Assert.assertTrue
import tv.telegram.ui.components.ConfirmDialogSpec
import tv.telegram.ui.components.confirmDialogButtonColors

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
    fun `the shared dialog's buttons follow the design's two states`() {
        // Cancel (ghost) and the primary (dark), both flipping to white with the dark label
        // once focused — the frames' behaviour, now inside components/ConfirmDialog.kt.
        assertEquals(
            androidx.compose.ui.graphics.Color(0x1A000000),
            confirmDialogButtonColors(primary = false, focused = false).fill,
        )
        assertEquals(ConfirmDialogSpec.Body, confirmDialogButtonColors(primary = false, focused = false).text)
        assertEquals(HomeSpec.InverseOnSurface, confirmDialogButtonColors(primary = true, focused = false).fill)
        assertEquals(HomeSpec.OnSurface, confirmDialogButtonColors(primary = true, focused = false).text)
        assertEquals(HomeSpec.White, confirmDialogButtonColors(primary = true, focused = true).fill)
        assertEquals(HomeSpec.InverseOnSurface, confirmDialogButtonColors(primary = true, focused = true).text)
        assertEquals(HomeSpec.White, confirmDialogButtonColors(primary = false, focused = true).fill)
    }

    @Test
    fun `a pane row's trailing icon is white at rest and dark once focused`() {
        // On the dark row the check reads white; on the focused white row it stays dark.
        assertEquals(HomeSpec.White, trailingIconColor(focused = false))
        assertEquals(HomeSpec.InverseOnSurface, trailingIconColor(focused = true))
    }

    @Test
    fun `an action row's chevron follows its label, not the white check`() {
        // The check goes white at rest; the chevron deliberately stays on-surface.
        assertEquals(HomeSpec.OnSurface, paneChevronColor(focused = false))
        assertEquals(HomeSpec.InverseOnSurface, paneChevronColor(focused = true))
        assertEquals(HomeSpec.White, trailingIconColor(focused = false))
    }

    @Test
    fun `every language tag points at a resource folder that exists`() {
        // The bug this guards: "zh-Hans"/"zh-Hant" resolve to nothing on this setup, so the
        // app silently fell back to values/ (English). The tags must match the folders.
        val forTag = { tag: String ->
            val parts = tag.split("-")
            when {
                parts.size == 1 && parts[0] == "en" -> "values"
                parts.size == 1 -> "values-" + parts[0]
                else -> "values-" + parts[0] + "-r" + parts[1]
            }
        }
        assertEquals("en", Language.English.toBcp47())
        assertEquals("zh-CN", Language.SimplifiedChinese.toBcp47())
        assertEquals("zh-TW", Language.TraditionalChinese.toBcp47())

        for (lang in Language.entries) {
            val folder = forTag(lang.toBcp47())
            assertTrue(
                "$lang resolves to $folder, which is missing",
                java.io.File("src/main/res/$folder").isDirectory,
            )
        }
    }
}
