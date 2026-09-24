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
    // deliberately inert for now.

    // Focus bridge across the shell boundary. The bar lives outside the NavHost and
    // the chat list inside it, so the shell owns both requesters and hands them to
    // both sides: Down from the bar lands on the selected chat row, and Back from
    // the chat list returns to the bar's selected tab.
    selectedTabFocus: FocusRequester? = null,
    contentFocus: FocusRequester? = null,
) {
    val active = selectedNavItem(selectedTab)


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
                cancelLeft = true,
                active = active == TopNavItem.Chat,
                onClick = { onTabSelected(TopNavTab.Chat) },
                downFocus = contentFocus,
                requester = selectedTabFocus,
            )
            // 4dp between the menus (design update, 2026-09-24).
            Spacer(Modifier.width(4.dp))
            TopNavTabButton(
                label = "Setting",
                active = active == TopNavItem.Setting,
                onClick = { onTabSelected(TopNavTab.Setting) },
                downFocus = contentFocus,
            )
            Spacer(Modifier.width(4.dp))

            var searchFocused by remember { mutableStateOf(false) }
            val searchActive = active == TopNavItem.Search
            Box(
                modifier = Modifier
                    .testTag(if (searchActive) "topnav-active-search" else "topnav-idle-search")
                    .size(width = 42.dp, height = TopNavBarHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(navPillColor(searchActive || searchFocused) ?: Color.Transparent)
                    .focusProperties {
                        // The bar only moves sideways and down (design annotations
                        // #1-#3): Down leaves for the chat list, Up does nothing.
                        down = contentFocus ?: FocusRequester.Default
                        up = FocusRequester.Cancel
                        // Last entry: Right stops here instead of escaping to the row below.
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
                    tint = navLabelColor(searchActive || searchFocused),
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
            // TODO(design): the wordmark is Lexend Exa Medium 14, uppercase. That
            //  face is not bundled yet, so it falls back to the theme's Inter.
            Text(
                text = "TELLYGRAM",
                style = MaterialTheme.typography.titleSmall,
                color = TopNavColors.Label,
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
    // The bar's edges: Left at the first entry and Right at the last one stop there,
    // otherwise the default focus search can escape downward into the chips row.
    cancelLeft: Boolean = false,
    cancelRight: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            // Selection tag on the container (one testTag per node, so the focus tag
            // lives on the label below).
            .testTag(if (active) "topnav-active-$label" else "topnav-idle-$label")
            .height(TopNavBarHeight)
            .clip(RoundedCornerShape(4.dp))
            // Focus and selection deliberately share ONE treatment: the entry that
            // holds focus is drawn exactly like the selected one.
            .background(navPillColor(active || focused) ?: Color.Transparent)
            .focusProperties {
                down = downFocus ?: FocusRequester.Default
                up = FocusRequester.Cancel
                if (cancelLeft) left = FocusRequester.Cancel
                if (cancelRight) right = FocusRequester.Cancel
            }
            .let { if (requester != null) it.focusRequester(requester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = navLabelColor(active || focused),
            modifier = Modifier.testTag(if (focused) "topnav-focus-$label" else "topnav-blur-$label"),
        )
    }
}

private object TopNavColors {
    /** Unselected tab and logo label: the design's material-theme/sys/dark/on-surface. */
    val Label = Color(0xFFC7C6CA)
    /** Label sitting on the selected pill: material-theme/sys/dark/inverse-on-surface. */
    val LabelActive = Color(0xFF1A1C1E)
    /** The selected pill itself: material-theme/white, 81x32, 4dp rounded. */
    val Pill = Color(0xFFFFFFFF)
}

/**
 * The bar's two-state rule, kept out of the composables so it can be asserted:
 * the active entry gets the white pill with the dark inverse label; the rest stay
 * unfilled and are labelled on-surface. (Design: Nav in frames 3239:2418 / 3240:2375.)
 */
internal fun navPillColor(active: Boolean): Color? = if (active) TopNavColors.Pill else null

internal fun navLabelColor(active: Boolean): Color =
    if (active) TopNavColors.LabelActive else TopNavColors.Label

/**
 * Which entry the bar shows as selected: the shell's page (or a confirmed switch),
 * NOT focus. Moving sideways leaves Chat's pill in place while the user stays on the
 * home screen. (Product decision, 2026-09-23, revising the focus-follows rule.)
 */
internal fun selectedNavItem(selectedTab: TopNavTab): TopNavItem =
    if (selectedTab == TopNavTab.Chat) TopNavItem.Chat else TopNavItem.Setting
