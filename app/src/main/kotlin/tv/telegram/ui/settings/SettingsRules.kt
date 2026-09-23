package tv.telegram.ui.settings

import androidx.compose.ui.graphics.Color
import tv.telegram.ui.home.HomeSpec

/** Colours of one settings row, derived in one place so the UI and tests agree. */
internal data class RowColors(val fill: Color, val text: Color)

/**
 * The selected section in the left list: the design fills it white and flips the label
 * to the dark inverse colour — the same treatment the home screen's list uses.
 */
internal fun sectionItemColors(selected: Boolean): RowColors = if (selected) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(Color.Transparent, HomeSpec.OnSurface)
}

/**
 * An actionable row in the right pane (about's "Check for updates"): a surface-container
 * panel by default, white with a dark label while focused. Display-only rows never call
 * this — they stay unfilled, as the design draws them.
 */
internal fun actionableRowColors(highlighted: Boolean): RowColors = if (highlighted) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface)
}
