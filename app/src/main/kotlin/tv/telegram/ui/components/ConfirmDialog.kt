@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import tv.telegram.ui.focus.dpadNavigationSounds
import androidx.compose.ui.platform.testTag
import tv.telegram.ui.home.HomeSpec
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.tv.material3.Icon
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

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
    /**
     * When set, the panel and buttons carry `"$tagPrefix-dialog"`, `-cancel` and `-confirm`
     * tags — the UI tests locate the dialog through them.
     */
    tagPrefix: String? = null,
    /**
     * Optional toggle above the buttons. The private delete dialog uses it for "also delete
     * for the other side"; the default is unchecked (product, 2026-09-27).
     */
    radioLabel: String? = null,
    radioChecked: Boolean = false,
    onRadioClick: (() -> Unit)? = null,
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val radioFocus = remember { FocusRequester() }

    // No focus dance: the design puts the safe button FIRST in the row, so the dialog
    // window focuses it on open by itself. The old loop re-requested focus for ~300ms,
    // which meant anything the user (or a test) focused in that window got yanked back.

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // usePlatformDefaultWidth = false makes the dialog window fill the screen, but the
        // window's own dim turned out to be invisible here — the panel was all there was, so
        // nothing covered the top bar or the shell's side margins. Paint the scrim ourselves.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ConfirmDialogSpec.Scrim)
                .let { if (tagPrefix != null) it.testTag("$tagPrefix-scrim") else it },
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = modifier
                .dpadNavigationSounds()
                .let { if (tagPrefix != null) it.testTag("$tagPrefix-dialog") else it }
                .shadow(8.dp, RoundedCornerShape(4.dp))
                .background(ConfirmDialogSpec.Panel, RoundedCornerShape(4.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .width(348.dp),
        ) {
            Text(
                text = title,
                color = ConfirmDialogSpec.Title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                color = ConfirmDialogSpec.Body,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (radioLabel != null) {
                Spacer(Modifier.height(2.dp))
                ChatRadioRow(
                    label = radioLabel,
                    checked = radioChecked,
                    onClick = { onRadioClick?.invoke() },
                    focusRequester = radioFocus,
                    // Sealed to the dialog: Down reaches Cancel, Up has nowhere to go.
                    up = FocusRequester.Cancel,
                    down = cancelFocus,
                    tagPrefix = tagPrefix,
                )
            }
            if (confirmLabel != null || cancelLabel != null) {
                Spacer(Modifier.height(12.dp))
                // Per the frames: the safe button hugs the LEFT and the action fills the
                // rest on the RIGHT (the older node had them the other way round).
                Row(
                    modifier = Modifier.width(348.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (cancelLabel != null) {
                        // The button's content is Box(fillMaxSize), which reports the incoming
                        // max width as its own — so an unconstrained button swallows the whole
                        // row and squeezes the other to zero. Pin the safe one to its content.
                        ConfirmDialogButton(
                            label = cancelLabel,
                            primary = false,
                            onClick = onDismiss,
                            focusRequester = cancelFocus,
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                // Up reaches the toggle when there is one; otherwise it stops
                                // instead of wandering out of the dialog.
                                .focusProperties {
                                    up = if (radioLabel != null) radioFocus else FocusRequester.Cancel
                                },
                            tagPrefix = tagPrefix,
                        )
                    }
                    if (confirmLabel != null) {
                        // The weight goes on a plain Box: a tv Card given Modifier.weight
                        // laid out zero-width here, so the action button vanished and
                        // could not take focus.
                        Box(modifier = Modifier.weight(1f)) {
                            ConfirmDialogButton(
                                label = confirmLabel,
                                primary = true,
                                onClick = { onConfirm?.invoke() },
                                focusRequester = confirmFocus,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties {
                                        up = if (radioLabel != null) radioFocus else FocusRequester.Cancel
                                    },
                                tagPrefix = tagPrefix,
                            )
                        }
                    }
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
    tagPrefix: String? = null,
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
            containerColor = confirmDialogButtonColors(primary, focused = false).fill,
            focusedContainerColor = confirmDialogButtonColors(primary, focused = true).fill,
        ),
        border = if (primary) {
            CardDefaults.border(Border.None, Border.None, Border.None)
        } else {
            val outline = Border(BorderStroke(1.dp, ConfirmDialogSpec.Outline))
            CardDefaults.border(outline, outline, outline)
        },
        modifier = modifier
            .height(40.dp)
            .let {
                if (tagPrefix != null) {
                    it.testTag(if (primary) "$tagPrefix-confirm" else "$tagPrefix-cancel")
                } else {
                    it
                }
            }
            .onFocusChanged { focused = it.hasFocus }
            .focusRequester(focusRequester),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = confirmDialogButtonColors(primary, focused).text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The dialog's palette, straight from the frames (not from theme roles, which drift). */
internal object ConfirmDialogSpec {
    val Panel = Color(0xFFE3E2E6)
    val Title = Color(0xFF121316)
    val Body = Color(0xFF43474E)
    val Outline = Color(0xFF8E9099)
    val Scrim = Color(0x991A1C1E)
}

/**
 * The two button states. The action button is the dark primary at rest and flips to white
 * on focus; the safe one is a ghost that does the same. Facts from the frames, unit tested.
 */
internal fun confirmDialogButtonColors(primary: Boolean, focused: Boolean): RowColors = when {
    focused -> RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
    primary -> RowColors(HomeSpec.InverseOnSurface, HomeSpec.OnSurface)
    else -> RowColors(Color(0x1A000000), ConfirmDialogSpec.Body)
}

/** The dialog's optional toggle: a 16dp glyph, the label, and nothing that escapes. */
@Composable
private fun ChatRadioRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    tagPrefix: String?,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .let { if (tagPrefix != null) it.testTag("$tagPrefix-radio") else it }
            .background(
                if (focused) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                this.up = up
                this.down = down
            }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = ConfirmDialogSpec.Body,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = ConfirmDialogSpec.Body,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}
