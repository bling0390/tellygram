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
 * An actionable row in the right pane: a surface-container panel by default, white with a
 * dark label while focused. Display-only rows never call this — they stay unfilled.
 */
internal fun actionableRowColors(highlighted: Boolean): RowColors = if (highlighted) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface)
}

/** The log-out dialog's palette, straight from the frames (its own tokens, not HomeSpec's). */
internal object SettingsSpec {
    /** The panel: the design's material-theme/sys/dark/inverse-surface. */
    val DialogPanel = Color(0xFFE3E2E6)
    /** Title on the panel: material-theme/sys/dark/surface. */
    val DialogTitle = Color(0xFF121316)
    /** Body copy: material-theme/sys/dark/surface-variant. */
    val DialogBody = Color(0xFF43474E)
    /** The cancel button's hairline: material-theme/sys/dark/outline. */
    val DialogOutline = Color(0xFF8E9099)
    /** The 60% scrim over the page. */
    val Scrim = Color(0x991A1C1E)
}

/** Cancel is a ghost at rest and turns white (with the dark label) once focused. */
internal fun dialogCancelColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(Color(0x1A000000), SettingsSpec.DialogBody)
}

/** Confirm is the dark primary; focused it flips to white like the other rows do. */
internal fun dialogConfirmColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.InverseOnSurface, HomeSpec.OnSurface)
}
