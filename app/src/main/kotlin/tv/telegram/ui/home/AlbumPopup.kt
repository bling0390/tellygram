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

/** The popup's palette, taken from the frames (3558:2184). */
internal object AlbumPopupSpec {
    val Panel = Color(0xFFE3E2E6)        // inverse-surface
    val Title = Color(0xFF121316)        // surface
    val Body = Color(0xFF43474E)         // surface-variant
    /** The hairline between the caption and the strip: outline at 10%. */
    val Divider = Color(0x1A8E9099)
    /** Same dim as the dialogs: the frames' blur is not used (product, 2026-09-30). */
    val Scrim = Color(0x991A1C1E)
}

/**
 * The album popup: a 672dp light panel, horizontally centred over a dimmed screen, holding a
 * fixed title, the album's caption (with its hairline) and a horizontally scrolling strip of
 * the members. Only Left/Right move inside — there is one row, so Up/Down are cancelled.
 * Confirming a member hands it to the caller, which opens the usual viewer.
 */
@Composable
internal fun AlbumPopup(
    members: List<MediaItem>,
    /** Same card content as the grid: thumbnails, their loading/glyph states and the badges. */
    state: HomeState,
    onOpenMember: (Int) -> Unit,
    onDismiss: () -> Unit,
    caption: String? = null,
    tagPrefix: String = "album-popup",
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hasCaption = !caption.isNullOrBlank()

    // The Dialog is its own window, so the first request can beat the node; retry a frame.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!runCatching { firstFocus.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    // Back closes it explicitly: the same proven pattern as the chat screen's menu.
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
                    modifier = Modifier.width(371.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.album_popup_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlbumPopupSpec.Title,
                    )
                    if (hasCaption) {
                        Text(
                            // Free to wrap: the caption grows the header (and the panel with
                            // it) instead of being clipped to two lines (product, 2026-10-01).
                            text = caption.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlbumPopupSpec.Body,
                        )
                    }
                }
                // No caption means no hairline either (product, 2026-09-30).
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

/** One member: the grid's card content, with the popup's own Left/Right-only focus graph. */
@Composable
private fun AlbumMemberTile(
    member: MediaItem,
    state: HomeState,
    isFirst: Boolean,
    isLast: Boolean,
    firstFocus: FocusRequester,
    onOpen: () -> Unit,
    tag: String,
) {
    var focused by remember { mutableStateOf(false) }
    // Only a FRESH press arms the tile: the long press that opened the popup keeps emitting
    // repeats, and its release would otherwise fire the first member (same gate as the menu).
    var confirmArmed by remember { mutableStateOf(false) }
    MediaCard(
        focused = focused,
        tag = tag,
        // At rest the popup's cards carry a light outline (inverse-surface), not a hidden one.
        borderColor = AlbumPopupSpec.Panel,
        modifier = Modifier
            .focusProperties {
                // One row: only Left/Right move, and the ends stop.
                left = if (isFirst) FocusRequester.Cancel else FocusRequester.Default
                right = if (isLast) FocusRequester.Cancel else FocusRequester.Default
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
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
                            onOpen()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Same content as the grid's cards: thumbnail (loading/glyph states), glyphs, badges.
        MediaCardContent(item = member, state = state)
    }
}
