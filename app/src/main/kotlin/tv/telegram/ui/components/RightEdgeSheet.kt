@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

internal val RightSheetPanel = Color(0xFFE3E2E6)
internal val RightSheetTitle = Color(0xFF121316)
internal val RightSheetInk = Color(0xFF43474E)
internal val RightSheetRule = Color(0xFF8E9099).copy(alpha = 0.1f)

@Composable
internal fun RightEdgeSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    restoreFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            delay(300L)
            restoreFocus?.let { runCatching { it.requestFocus() } }
        }
    }
    if (!visible) return

    val focus = remember { FocusRequester() }
    val topPx = with(LocalDensity.current) { 14.dp.roundToPx() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Popup(
        popupPositionProvider = remember(topPx) { RightEdgePosition(topPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(384.dp)
                .height(512.dp)
                .background(
                    RightSheetPanel,
                    RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                )
                .focusRequester(focus)
                .focusable()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = RightSheetTitle,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(RightSheetRule))
            content()
        }
    }
}

private class RightEdgePosition(private val topPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = topPx,
    )
}
