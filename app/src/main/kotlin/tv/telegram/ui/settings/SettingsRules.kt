package tv.telegram.ui.settings

import androidx.compose.ui.graphics.Color
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.components.RowColors

internal fun orDash(value: String): String = value.ifBlank { "—" }

internal fun sectionItemColors(focused: Boolean): RowColors = if (focused) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(Color.Transparent, HomeSpec.OnSurface)
}

internal fun trailingIconColor(focused: Boolean): Color =
    if (focused) HomeSpec.InverseOnSurface else HomeSpec.White

internal fun paneChevronColor(focused: Boolean): Color =
    if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface

internal fun actionableRowColors(highlighted: Boolean): RowColors = if (highlighted) {
    RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
} else {
    RowColors(HomeSpec.SurfaceContainer, HomeSpec.OnSurface)
}
