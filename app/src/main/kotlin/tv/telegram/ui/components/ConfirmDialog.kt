@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.telegram.ui.focus.dpadNavigationSounds

/**
 * JetStream confirmation dialog — Figma 1114:8669 ("Delete account?").
 *
 * Tokens read off the node:
 *  - surface = `sys/dark/inverse-surface` (#E3E2E6): the dialog is LIGHT against
 *    the dark UI. Radius 4dp, Elevation Dark/4.
 *  - padding 24 vertical / 32 horizontal, 12dp between title, body and buttons,
 *    content 348 wide (so the frame is 412).
 *  - title: `headline/small` (Inter 400 24/32) in `sys/dark/surface`.
 *  - body: `body/medium` (Inter 400 14/20) in `sys/dark/surface-variant`.
 *  - buttons 40dp tall, radius 4, 14dp apart. The action button is hug-width on
 *    the LEFT (`sys/dark/inverse-on-surface` fill, Elevation Dark/2, label
 *    `sys/dark/on-surface`); the safe one fills the rest on the RIGHT (10% black
 *    fill, 1dp `sys/dark/outline` border, label `sys/dark/surface-variant`).
 *  - button labels are `title/small` = Inter Medium 14/20, which in this app is
 *    `labelLarge` — its own `titleSmall` is 12/16 Regular, so it is not the match.
 *
 * Decisions the file does not settle (all spec 2026-09-13):
 *  - focus starts on the SAFE button: an accidental D-pad OK must not run a
 *    destructive action (the chat confirm dialog already worked this way).
 *  - focused buttons take `secondaryContainer` for fill and
 *    `onSecondaryContainer` for the label, so the focus reads at couch distance
 *    and the label stays legible on it.
 *  - Compose allows one shadow per layer, so Elevation Dark/4 and Elevation
 *    Dark/2 land as 8dp and 2dp.
 *
 * Either button may be omitted: the clear-cache dialog shows progress with no
 * buttons at all, then a single "Done".
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    cancelLabel: String? = null,
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    // The dialog window focuses its first focusable child as it opens, so retry
    // the intended button for a few frames (same dance the chat dialog did).
    LaunchedEffect(confirmLabel, cancelLabel) {
        withFrameNanos { }
        repeat(5) {
            val target = if (cancelLabel != null) cancelFocus else confirmFocus
            try { target.requestFocus() } catch (_: IllegalStateException) {}
            delay(60)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .dpadNavigationSounds()
                .shadow(8.dp, RoundedCornerShape(4.dp))
                .background(
                    MaterialTheme.colorScheme.inverseSurface,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .width(348.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.surface,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.surfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (confirmLabel != null || cancelLabel != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.width(348.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (confirmLabel != null) {
                        ConfirmDialogButton(
                            label = confirmLabel,
                            primary = true,
                            onClick = { onConfirm?.invoke() },
                            focusRequester = confirmFocus,
                        )
                    }
                    if (cancelLabel != null) {
                        ConfirmDialogButton(
                            label = cancelLabel,
                            primary = false,
                            onClick = onDismiss,
                            focusRequester = cancelFocus,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One 40dp dialog button: the design's hug-width action, or the filled safe one. */
@Composable
private fun ConfirmDialogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        colors = CardDefaults.colors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                Color.Black.copy(alpha = 0.1f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = if (primary) {
            CardDefaults.border(Border.None, Border.None, Border.None)
        } else {
            val outline = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.border))
            CardDefaults.border(outline, outline, outline)
        },
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { focused = it.hasFocus }
            .focusRequester(focusRequester),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = when {
                    focused -> MaterialTheme.colorScheme.onSecondaryContainer
                    primary -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
