package tv.telegram.ui.settings

import androidx.compose.ui.graphics.Color
import tv.telegram.ui.home.HomeSpec

/** Colours of one settings row, derived in one place so the UI and tests agree. */
internal data class RowColors(val fill: Color, val text: Color)


/** Telegram may not have a value for a field; the pane shows an em dash then. */
internal fun orDash(value: String): String = value.ifBlank { "—" }

/**
 * The selected section in the left list: the design fills it white and flips the label to
 * the dark inverse colour — the same treatment the home screen's list uses.
 */
internal fun sectionItemColors(selected: Boolean): RowColors = if (selected) {
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



