@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration

@Composable
fun RightDrawer(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 360.dp,
    backgroundColor: Color = Color(0xE6121316),
    cornerRadius: Dp = 4.dp,
    takeFocus: Boolean = true,
    restoreFocus: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val drawerFocus = remember { FocusRequester() }
    var containerFocused by remember { mutableStateOf(false) }
    val offscreen = width + 60.dp
    val drawerX by animateDpAsState(
        targetValue = if (visible) 0.dp else offscreen,
        animationSpec = tween(durationMillis = 250),
        label = "rightDrawerX",
    )
    val drawerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "rightDrawerAlpha",
    )

    var wasOpen by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            wasOpen = true
            if (takeFocus) {
                withFrameNanos { }
                try { drawerFocus.requestFocus() }
                catch (_: IllegalStateException) {}
            }
        } else if (wasOpen) {
            wasOpen = false
            delay(300L)
            restoreFocus?.let {
                try { it.requestFocus() }
                catch (_: IllegalStateException) {}
            }
        }
    }

    BackRegistration(BackPriority.DRAWER, enabled = visible) { onClose() }

    if (visible || drawerX < offscreen) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(width)
                .graphicsLayer {
                    translationX = drawerX.toPx()
                    alpha = drawerAlpha
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(
                        backgroundColor,
                        RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius),
                    )
                    .onFocusChanged { containerFocused = it.isFocused }
                    .focusRequester(drawerFocus)
                    .focusable()
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.Back, Key.DirectionLeft, Key.DirectionCenter, Key.Enter -> {
                                onClose(); true
                            }
                            Key.DirectionUp, Key.DirectionDown -> containerFocused
                            else -> false
                        }
                    },
            ) {
                content()
            }
        }
    }
}
