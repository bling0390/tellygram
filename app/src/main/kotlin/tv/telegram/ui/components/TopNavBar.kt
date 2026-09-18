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

@Composable
fun TopNavBar(
    selectedTab: TopNavTab,
    onTabSelected: (TopNavTab) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    // The signed-in user's avatar. Injected rather than loaded here so the bar
    // stays free of data dependencies and can render in previews/tests.
    avatar: @Composable () -> Unit = { TopNavDefaultAvatar() },
) {
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
                selected = selectedTab == TopNavTab.Chat,
                onClick = { onTabSelected(TopNavTab.Chat) },
            )
            Spacer(Modifier.width(8.dp))
            TopNavTabButton(
                label = "Setting",
                selected = selectedTab == TopNavTab.Setting,
                onClick = { onTabSelected(TopNavTab.Setting) },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = TopNavBarHeight)
                    .clip(RoundedCornerShape(4.dp)),
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
private fun TopNavTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(TopNavBarHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) TopNavColors.SecondaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) TopNavColors.OnSecondaryContainer else TopNavColors.OnBackground,
        )
    }
}

private object TopNavColors {
    val OnBackground = Color(0xFFE3E2E6)
    val SecondaryContainer = Color(0x66484459)   // 40% of #484459
    val OnSecondaryContainer = Color(0xFFE5DFF9)
}
