@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package tv.telegram.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.telegram.R
import tv.telegram.td.MediaItem
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

internal object AlbumPopupSpec {
    val Panel = Color(0xFFE3E2E6)
    val Title = Color(0xFF121316)
    val Body = Color(0xFF43474E)
    val Divider = Color(0x1A8E9099)
    val Scrim = Color(0x991A1C1E)
}

@Composable
internal fun AlbumPopup(
    members: List<MediaItem>,
    state: HomeState,
    onOpenMember: (Int) -> Unit,
    onDismiss: () -> Unit,
    caption: String? = null,
    tagPrefix: String = "album-popup",
    focusMessageId: Long? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hasCaption = !caption.isNullOrBlank()
    val focusIndex = remember(members, focusMessageId) { albumFocusIndex(members, focusMessageId) }

    LaunchedEffect(Unit) {
        runCatching { listState.scrollToItem(focusIndex.coerceAtLeast(0)) }
        withFrameNanos { }
        if (!runCatching { firstFocus.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlbumPopupSpec.Scrim)
                .let { if (tagPrefix != null) it.testTag("$tagPrefix-scrim") else it },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(672.dp)
                    .testTag("$tagPrefix-panel")
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .background(AlbumPopupSpec.Panel, RoundedCornerShape(4.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.album_popup_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlbumPopupSpec.Title,
                    )
                    if (hasCaption) {
                        Text(
                            text = caption.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlbumPopupSpec.Body,
                        )
                    }
                }
                if (hasCaption) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .testTag("$tagPrefix-divider")
                            .background(AlbumPopupSpec.Divider),
                    )
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp),
                    horizontalArrangement = Arrangement.spacedBy(slotGap(20.dp, HomeSpec.FocusBorder)),
                    contentPadding = PaddingValues(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(members) { index, member ->
                        AlbumMemberTile(
                            member = member,
                            isEntry = index == focusIndex,
                            isFirst = index == 0,
                            isLast = index == members.lastIndex,
                            firstFocus = firstFocus,
                            state = state,
                            onOpen = { onOpenMember(index) },
                            tag = "$tagPrefix-member-$index",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumMemberTile(
    member: MediaItem,
    state: HomeState,
    isEntry: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    firstFocus: FocusRequester,
    onOpen: () -> Unit,
    tag: String,
) {
    var focused by remember { mutableStateOf(false) }
    var confirmArmed by remember { mutableStateOf(false) }
    MediaCard(
        focused = focused,
        tag = tag,
        borderColor = AlbumPopupSpec.Panel,
        modifier = Modifier
            .focusProperties {
                left = if (isFirst) FocusRequester.Cancel else FocusRequester.Default
                right = if (isLast) FocusRequester.Cancel else FocusRequester.Default
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) confirmArmed = false
            }
            .let { if (isEntry) it.focusRequester(firstFocus) else it }
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
                            onOpen()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        MediaCardContent(item = member, state = state)
    }
}
