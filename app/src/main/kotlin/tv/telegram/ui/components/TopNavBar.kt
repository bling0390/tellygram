@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package tv.telegram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class TopNavTab { Chat, Setting }

val TopNavBarHeight = 32.dp
val TopNavBarTopMargin = 32.dp
val TopNavBarBottomGap = 32.dp
val TopNavBarSideMargin = 58.dp

@Composable
fun TopNavBar(
    onTabSelected: (TopNavTab) -> Unit,
    onTabConfirmed: (TopNavTab) -> Unit = {},
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = { TopNavDefaultAvatar() },

    selectedTabFocus: FocusRequester? = null,
    settingTabFocus: FocusRequester? = null,
    contentFocus: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopNavBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.width(20.dp))
            TopNavTabButton(
                label = "Chat",
                icon = Icons.Outlined.Chat,
                containerTag = "topnav-tab-Chat",
                cancelLeft = true,
                onFocused = { onTabSelected(TopNavTab.Chat) },
                onConfirmed = { onTabConfirmed(TopNavTab.Chat) },
                downFocus = contentFocus,
                requester = selectedTabFocus,
            )
            Spacer(Modifier.width(4.dp))
            TopNavTabButton(
                label = "Setting",
                icon = Icons.Outlined.Settings,
                containerTag = "topnav-tab-Setting",
                onFocused = { onTabSelected(TopNavTab.Setting) },
                onConfirmed = { onTabConfirmed(TopNavTab.Setting) },
                downFocus = contentFocus,
                requester = settingTabFocus,
            )
            Spacer(Modifier.width(4.dp))

            var searchFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .testTag("topnav-search")
                    .size(width = 42.dp, height = TopNavBarHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(navPillColor(searchFocused) ?: Color.Transparent)
                    .focusProperties {
                        down = contentFocus ?: FocusRequester.Default
                        up = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .onFocusChanged { searchFocused = it.isFocused }
                    .focusable()
                    .onKeyEvent { event: KeyEvent ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)
                        ) {
                            onSearchClick()
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = navLabelColor(searchFocused),
                    modifier = Modifier
                        .size(22.dp)
                        .testTag(if (searchFocused) "topnav-focus-search" else "topnav-blur-search"),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = TopNavColors.Label,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "TELLYGRAM",
                style = MaterialTheme.typography.titleSmall,
                color = TopNavColors.Label,
            )
        }
    }
}

@Composable
private fun TopNavDefaultAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A2D31)),
    )
}

@Composable
private fun TopNavTabButton(
    label: String,
    icon: ImageVector,
    containerTag: String,
    onFocused: () -> Unit,
    onConfirmed: () -> Unit,
    downFocus: FocusRequester? = null,
    requester: FocusRequester? = null,
    cancelLeft: Boolean = false,
    cancelRight: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .testTag(containerTag)
            .height(TopNavBarHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(navPillColor(focused) ?: Color.Transparent)
            .focusProperties {
                down = downFocus ?: FocusRequester.Default
                up = FocusRequester.Cancel
                if (cancelLeft) left = FocusRequester.Cancel
                if (cancelRight) right = FocusRequester.Cancel
            }
            .let { if (requester != null) it.focusRequester(requester) else it }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onConfirmed()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = navLabelColor(focused),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = navLabelColor(focused),
                modifier = Modifier.testTag(if (focused) "topnav-focus-$label" else "topnav-blur-$label"),
            )
        }
    }
}

private object TopNavColors {
    val Label = Color(0xFFC7C6CA)
    val LabelActive = Color(0xFF1A1C1E)
    val Pill = Color(0xFFFFFFFF)
}

internal fun navPillColor(focused: Boolean): Color? = if (focused) TopNavColors.Pill else null

internal fun navLabelColor(focused: Boolean): Color =
    if (focused) TopNavColors.LabelActive else TopNavColors.Label
