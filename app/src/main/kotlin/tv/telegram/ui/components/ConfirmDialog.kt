@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package tv.telegram.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
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

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    cancelLabel: String? = null,
    tagPrefix: String? = null,
    radioLabel: String? = null,
    radioChecked: Boolean = false,
    onRadioClick: (() -> Unit)? = null,
    scrollableBody: Boolean = false,
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val radioFocus = remember { FocusRequester() }
    val bodyScroll = rememberScrollState()
    val bodyScope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 72.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                .let { m ->
                    if (!scrollableBody) {
                        m
                    } else {
                        m.onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionUp -> {
                                    bodyScope.launch { bodyScroll.scrollBy(-stepPx) }
                                    true
                                }
                                Key.DirectionDown -> {
                                    bodyScope.launch { bodyScroll.scrollBy(stepPx) }
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }
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
                modifier = if (scrollableBody) {
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(bodyScroll)
                } else {
                    Modifier
                },
            )
            if (radioLabel != null) {
                Spacer(Modifier.height(2.dp))
                ChatRadioRow(
                    label = radioLabel,
                    checked = radioChecked,
                    onClick = { onRadioClick?.invoke() },
                    focusRequester = radioFocus,
                    up = FocusRequester.Cancel,
                    down = cancelFocus,
                    tagPrefix = tagPrefix,
                )
            }
            if (confirmLabel != null || cancelLabel != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.width(348.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (cancelLabel != null) {
                        ConfirmDialogButton(
                            label = cancelLabel,
                            primary = false,
                            onClick = onDismiss,
                            focusRequester = cancelFocus,
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .focusProperties {
                                    up = if (radioLabel != null) radioFocus else FocusRequester.Cancel
                                },
                            tagPrefix = tagPrefix,
                        )
                    }
                    if (confirmLabel != null) {
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

internal object ConfirmDialogSpec {
    val Panel = Color(0xFFE3E2E6)
    val Title = Color(0xFF121316)
    val Body = Color(0xFF43474E)
    val Outline = Color(0xFF8E9099)
    val Scrim = Color(0x991A1C1E)
}

internal fun confirmDialogButtonColors(primary: Boolean, focused: Boolean): RowColors = when {
    focused -> RowColors(HomeSpec.White, HomeSpec.InverseOnSurface)
    primary -> RowColors(HomeSpec.InverseOnSurface, HomeSpec.OnSurface)
    else -> RowColors(Color(0x1A000000), ConfirmDialogSpec.Body)
}

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
