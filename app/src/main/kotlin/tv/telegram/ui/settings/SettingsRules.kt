package tv.telegram.ui.settings

import androidx.compose.ui.graphics.Color
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.components.RowColors



/** Telegram may not have a value for a field; the pane shows an em dash then. */
internal fun orDash(value: String): String = value.ifBlank { "—" }

/**
 * A section row in the left list: the one holding focus is filled white with the dark
 * inverse label. Focus IS the selection (2026-10-15), so there is no second, chosen-only
 * look — the same rule the top bar follows.
 */
internal fun sectionItemColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(Color.Transparent, HomeSpec.OnSurface)
}

/**
 * The chosen language's trailing check: white on the dark row (the product decision of
 * 2026-09-25), and dark on the focused white row where white would vanish.
 */
internal fun trailingIconColor(focused: Boolean): Color =
    if (focused) HomeSpec.InverseOnSurface else HomeSpec.White

/**
 * An action row's trailing chevron. Unlike the language check, this one matches its label:
 * on-surface at rest, the dark inverse once the row turns white.
 */
internal fun paneChevronColor(focused: Boolean): Color =
    if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface

/**
 * An actionable row in the right pane: a surface-container panel by default, white with a
 * dark label while focused. Display-only rows never call this — they stay unfilled.
 */
internal fun actionableRowColors(highlighted: Boolean): RowColors = if (highlighted) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface)
}



