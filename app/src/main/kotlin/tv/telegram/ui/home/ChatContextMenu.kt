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

/**
 * The long-press popover: a 268x188 panel (radius 16, Elevation Dark/4) holding four fixed
 * rows — pin, mute, archive, delete — whose labels and glyphs follow the chat's state.
 * Focus starts on the first row, the edges stop instead of wrapping, and Back closes
 * (see the screen's Back rule).
 */
@Composable
internal fun ChatContextMenu(
    chat: ChatItem,
    archived: Boolean,
    top: Dp,
    onSelect: (ChatMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }

    // The row that opened it is still focused when the menu composes, so wait a frame and
    // retry once — the same dance the dialog uses.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!runCatching { firstFocus.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            // The screen's own origin is already the list's left edge (the shell insets it).
            .padding(top = top)
            // Unhandled keys bubble up here, so Back closes the popover from anywhere in it.
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
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
                action = action,
                chat = chat,
                archived = archived,
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
    action: ChatMenuAction,
    chat: ChatItem,
    archived: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    firstFocus: FocusRequester,
    onSelect: (ChatMenuAction) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(chatMenuLabelRes(action, chat.isPinned, chat.isMuted, archived))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatMenuItemHeightDp.dp)
            .testTag("chat-menu-${action.name.lowercase()}")
            .background(if (focused) HomeSpec.White else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(8.dp))
            .focusProperties {
                // Sealed to the menu: Up/Down move inside it, the ends stop, and Left/Right
                // are cancelled outright. Without those two the geometric search would hop
                // out of the popover onto a chat row or a media cell behind it.
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = if (isFirst) FocusRequester.Cancel else FocusRequester.Default
                down = if (isLast) FocusRequester.Cancel else FocusRequester.Default
            }
            .onFocusChanged { focused = it.isFocused }
            .let { if (isFirst) it.focusRequester(firstFocus) else it }
            .focusable()
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                // Act on the RELEASE only: holding OK past the long-press threshold keeps
                // sending repeats, and those must not run the action again.
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onSelect(action)
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))
        Icon16(chatMenuIcon(action, chat.isMuted, archived), focused)
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

/** Pin, mute and archive flip their glyph with the state; delete never does. */
internal fun chatMenuIcon(action: ChatMenuAction, muted: Boolean, archived: Boolean): ImageVector =
    when (action) {
        ChatMenuAction.Pin -> Icons.Outlined.PushPin
        ChatMenuAction.Mute -> if (muted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff
        ChatMenuAction.Archive -> if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive
        ChatMenuAction.Delete -> Icons.Outlined.Delete
    }
