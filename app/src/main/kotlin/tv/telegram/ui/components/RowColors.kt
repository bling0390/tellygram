package tv.telegram.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Colours of one row or button, derived in one place so the UI and tests agree. It lives in
 * the shared components package because both the settings panes and the dialogs use it.
 */
internal data class RowColors(val fill: Color, val text: Color)
