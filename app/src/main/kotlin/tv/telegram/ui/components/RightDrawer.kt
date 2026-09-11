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

/**
 * Right-side drawer overlay: slides in from the right edge over whatever
 * content is underneath, keeping the rest of the screen (and any rail)
 * visible. Extracted from PlayerScreen's media-info drawer so any surface
 * (player info, settings, etc.) gets the same popup behavior.
 *
 * - Slide + fade in/out (250ms), stays composed through the exit slide
 *   then leaves composition so it stops holding focus.
 * - Takes focus when opened (unless [takeFocus] is false, for content that
 *   manages its own focus, e.g. a settings list); hands it back to
 *   [restoreFocus] after the exit slide when closed (or leaves it wherever
 *   it lands if null).
 * - Closes on Back / Left / OK / Enter, handled at the drawer level so
 *   it works no matter which inner element has focus.
 *
 * Caller places it inside a Box and typically passes
 * `modifier = Modifier.align(Alignment.CenterEnd)`.
 */
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
    // True only while focus sits on the drawer container ITSELF (takeFocus =
    // true, e.g. the player info drawer). When it does, Up/Down must be
    // consumed — directional search from the container would otherwise
    // escape into the page behind (controller buttons, media grid), which is
    // still in the focus tree and only visually covered. When focus is on a
    // child (takeFocus = false, settings list), this stays false and the
    // child handles its own edge keys.
    var containerFocused by remember { mutableStateOf(false) }
    // Off-screen target: fully past the right edge (with a small margin).
    val offscreen = width + 60.dp
    // Plain full-height Box + graphicsLayer slide/fade (same approach as the
    // original player drawer): the drawer stays composed through its exit
    // slide, then leaves composition so it stops holding focus.
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
            // Take focus after the node is attached. Content that manages
            // its own focus (e.g. settings' first row) passes takeFocus = false
            // so the drawer container doesn't steal it.
            if (takeFocus) {
                withFrameNanos { }
                try { drawerFocus.requestFocus() }
                catch (_: IllegalStateException) {}
            }
        } else if (wasOpen) {
            wasOpen = false
            // Hand focus back to whatever opened the drawer, once the exit
            // slide has removed it from the focus tree.
            delay(300L)
            restoreFocus?.let {
                try { it.requestFocus() }
                catch (_: IllegalStateException) {}
            }
        }
    }

    // Back closes the drawer regardless of where focus sits inside it.
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
                    // Focus trap for container-focus mode: Back/Left/OK/Enter
                    // close; Up/Down are consumed only while the container
                    // itself is focused (see containerFocused), so the drawer
                    // never escapes into the page behind it. Children (e.g. a
                    // settings list) get full directional freedom and are
                    // responsible for their own edge handling.
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
