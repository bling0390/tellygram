@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package tv.telegram.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.telegram.td.ChatItem
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.activity.compose.BackHandler

@Composable
internal fun ChatContextMenu(
    chat: ChatItem,
    archived: Boolean,
    isOwner: Boolean,
    top: Dp,
    onSelect: (ChatMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!runCatching { firstFocus.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Column(
        modifier = Modifier
            .padding(top = top)
            .width(HomeSpec.ListWidth)
            .height(ChatMenuHeightDp.dp)
            .testTag("chat-menu")
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(HomeSpec.SurfaceContainer, RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatMenuAction.entries.forEachIndexed { index, action ->
            ChatMenuItemRow(
                icon = chatMenuIcon(action, chat.isMuted, archived),
                labelRes = if (action == ChatMenuAction.Delete) {
                    chatMenuDestructiveLabelRes(chatDeleteCase(chat.type, isOwner))
                } else {
                    chatMenuLabelRes(action, chat.isPinned, chat.isMuted, archived)
                },
                action = action,
                isFirst = index == 0,
                isLast = index == ChatMenuAction.entries.lastIndex,
                firstFocus = firstFocus,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun ChatMenuItemRow(
    labelRes: Int,
    icon: ImageVector,
    action: ChatMenuAction,
    isFirst: Boolean,
    isLast: Boolean,
    firstFocus: FocusRequester,
    onSelect: (ChatMenuAction) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var confirmArmed by remember { mutableStateOf(false) }
    val label = stringResource(labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatMenuItemHeightDp.dp)
            .testTag("chat-menu-${action.name.lowercase()}")
            .background(if (focused) HomeSpec.White else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(8.dp))
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = if (isFirst) FocusRequester.Cancel else FocusRequester.Default
                down = if (isLast) FocusRequester.Cancel else FocusRequester.Default
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) confirmArmed = false
            }
            .let { if (isFirst) it.focusRequester(firstFocus) else it }
            .focusable()
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) confirmArmed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (confirmArmed) {
                            confirmArmed = false
                            onSelect(action)
                        }
                        true
                    }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))
        Icon16(icon, focused)
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun Icon16(icon: ImageVector, focused: Boolean) {
    androidx.tv.material3.Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
        modifier = Modifier.size(16.dp),
    )
}

internal fun chatMenuIcon(action: ChatMenuAction, muted: Boolean, archived: Boolean): ImageVector =
    when (action) {
        ChatMenuAction.Pin -> Icons.Outlined.PushPin
        ChatMenuAction.Mute -> if (muted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff
        ChatMenuAction.Archive -> if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive
        ChatMenuAction.Delete -> Icons.Outlined.Delete
    }
