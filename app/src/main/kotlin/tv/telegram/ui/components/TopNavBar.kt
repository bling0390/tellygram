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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.unit.dp

/**
 * The app's top bar — Figma "Nav" instance (93:1018), 844dp wide and 32dp tall.
 *
 * It lives outside the NavHost (see MainActivity's shell) so it survives
 * navigation: pages below it swap, the bar does not, which keeps its focus and
 * state stable and avoids re-composing it on every route change.
 *
 * Geometry and colours mirror the design: avatar 32dp, 20dp gap, the "Chat" /
 * "Setting" tabs, a 22dp search glyph, and the wordmark pinned right at 70%
 * opacity.
 */
enum class TopNavTab { Chat, Setting }

val TopNavBarHeight = 32.dp
/** Design: bar top edge at y=32, page content starts at y=96. */
val TopNavBarTopMargin = 32.dp
val TopNavBarBottomGap = 32.dp
val TopNavBarSideMargin = 58.dp

/** The bar's focusable entries, in left-to-right order. */
internal enum class TopNavItem { Chat, Setting, Search }

@Composable
fun TopNavBar(
    selectedTab: TopNavTab,
    onTabSelected: (TopNavTab) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    // The signed-in user's avatar. Injected rather than loaded here so the bar
    // stays free of data dependencies and can render in previews/tests.
    avatar: @Composable () -> Unit = { TopNavDefaultAvatar() },
    // (rule lives in activeNavItem below so it can be unit tested)
    // The bar highlights whichever entry holds focus (the menu state follows the
    // D-pad), falling back to the shell's selected tab — Chat, the home — once focus
    // leaves the bar. Nothing here navigates: OK on Setting or the search icon is
    // deliberately inert for now.

    // Focus bridge across the shell boundary. The bar lives outside the NavHost and
    // the chat list inside it, so the shell owns both requesters and hands them to
    // both sides: Down from the bar lands on the selected chat row, and Back from
    // the chat list returns to the bar's selected tab.
    selectedTabFocus: FocusRequester? = null,
    contentFocus: FocusRequester? = null,
) {
    var activeItem by remember { mutableStateOf<TopNavItem?>(null) }
    val active = activeNavItem(activeItem, selectedTab)

    // Blur only clears the item that is still recorded, so a gain/loss pair arriving
    // in either order ends with the newly focused item active.
    fun onItemFocus(item: TopNavItem, focused: Boolean) {
        if (focused) activeItem = item else if (activeItem == item) activeItem = null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopNavBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar: the design shows a 32dp circle holding an image.
            avatar()
            Spacer(Modifier.width(20.dp))
            TopNavTabButton(
                label = "Chat",
                active = active == TopNavItem.Chat,
                onClick = { onTabSelected(TopNavTab.Chat) },
                downFocus = contentFocus,
                requester = selectedTabFocus,
                onItemFocus = ::onItemFocus,
                item = TopNavItem.Chat,
            )
            Spacer(Modifier.width(8.dp))
            TopNavTabButton(
                label = "Setting",
                active = active == TopNavItem.Setting,
                onClick = { onTabSelected(TopNavTab.Setting) },
                downFocus = contentFocus,
                onItemFocus = ::onItemFocus,
                item = TopNavItem.Setting,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = TopNavBarHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active == TopNavItem.Search) TopNavColors.SecondaryContainer else Color.Transparent)
                    .focusProperties {
                        // The bar only moves sideways and down (design annotations
                        // #1-#3): Down leaves for the chat list, Up does nothing.
                        down = contentFocus ?: FocusRequester.Default
                        up = FocusRequester.Cancel
                    }
                    .focusable()
                    .onFocusChanged { onItemFocus(TopNavItem.Search, it.isFocused) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = TopNavColors.OnBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = TopNavColors.OnBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            // TODO(design): the wordmark is Lexend Exa Medium 14, uppercase. That
            //  face is not bundled yet, so it falls back to the theme's Inter.
            Text(
                text = "TELLYGRAM",
                style = MaterialTheme.typography.titleSmall,
                color = TopNavColors.OnBackground.copy(alpha = 0.7f),
            )
        }
    }
}

/** Shown when no avatar is injected (previews, tests). */
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
    active: Boolean,
    onClick: () -> Unit,
    downFocus: FocusRequester? = null,
    requester: FocusRequester? = null,
    onItemFocus: (TopNavItem, Boolean) -> Unit,
    item: TopNavItem,
) {
    Box(
        modifier = Modifier
            .height(TopNavBarHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) TopNavColors.SecondaryContainer else Color.Transparent)
            .focusProperties {
                down = downFocus ?: FocusRequester.Default
                up = FocusRequester.Cancel
            }
            .let { if (requester != null) it.focusRequester(requester) else it }
            .focusable()
            .onFocusChanged { onItemFocus(item, it.isFocused) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) TopNavColors.OnSecondaryContainer else TopNavColors.OnBackground,
        )
    }
}

private object TopNavColors {
    val OnBackground = Color(0xFFE3E2E6)
    val SecondaryContainer = Color(0x66484459)   // 40% of #484459
    val OnSecondaryContainer = Color(0xFFE5DFF9)
}

/**
 * The bar highlights the entry that holds focus; with focus elsewhere it falls back
 * to the shell's selected tab — Chat, the home. Annotated behaviour, unit tested.
 */
internal fun activeNavItem(focused: TopNavItem?, selectedTab: TopNavTab): TopNavItem =
    focused ?: if (selectedTab == TopNavTab.Chat) TopNavItem.Chat else TopNavItem.Setting
