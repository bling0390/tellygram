package tv.telegram.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.delay
import tv.telegram.R
import tv.telegram.td.FileDownloadState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.components.Avatar

/**
 * HomeScreen — Figma node 1243:1724 ("HomeScreen").
 *
 * Geometry comes straight from the design: a 960x540 TV canvas with 58dp side
 * margins, a 32dp-tall nav bar whose top edge sits at y=32, a 268dp chat list
 * starting at y=96, and a 544x344 media grid of 160x120 cells with 20dp
 * gutters. The design places those children absolutely; here they are expressed
 * as ordinary Compose layout, which lands the boxes in the same places without
 * hard-coded offsets (so the screen still behaves if a TV reports a slightly
 * different width).
 */
private object HomeSpec {
    // Figma colour variables (material-theme/sys/dark/* and the raw fills).
    val Background = Color(0xFF1A1C1E)
    val OnSurface = Color(0xFFC7C6CA)
    val OnBackground = Color(0xFFE3E2E6)
    val InverseOnSurface = Color(0xFF1A1C1E)
    val Tertiary = Color(0xFFDCBCE1)          // unread dot, default row
    val TertiaryFixed = Color(0xFFF9D8FE)     // unread dot, highlighted row
    val SecondaryContainer = Color(0x66484459) // 40% of #484459 — selected "Chat" tab
    val OnSecondaryContainer = Color(0xFFE5DFF9)
    val RowFill = Color(0x1AD9D9D9)           // rgba(217,217,217,0.1)
    val RowFillStrong = Color(0x99D9D9D9)     // rgba(217,217,217,0.6) — pinned row
    val ChipOutline = Color(0x33FFFFFF)       // rgba(255,255,255,0.2)
    val White = Color(0xFFFFFFFF)
    // Figma "primary-fixed": the verified badge switches to this while the
    // row is focused. The unfocused/pinned colour is the theme's primary
    // (#A8C8FF), which is what the design draws in those rows.
    val PrimaryFixed = Color(0xFFD6E3FF)

    val ListWidth = 268.dp
    val ListHeight = 412.dp
    val ListGap = 4.dp
    val GutterBetweenColumns = 32.dp
    val ChipsHeight = 36.dp                  // all chips fixed; the design's ALL chip hugs to this
    val ChipsToGrid = 32.dp                  // 96 + 36 + 32 = 164, so the grid keeps its design Y

    val GridWidth = 544.dp
    val GridHeight = 344.dp
    val CellWidth = 160.dp
    val CellHeight = 120.dp
    val CellGap = 20.dp
    val FocusBorder = 3.dp
    val Corner = 4.dp
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenPlayer: (Int) -> Unit = {},
) {
    val chats by viewModel.chatList.collectAsStateWithLifecycle()
    val media by viewModel.mediaItems.collectAsStateWithLifecycle()

    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // The top bar is provided by the app shell (MainActivity) so it survives
        // navigation; this screen only owns the lower part of the design — the
        // chat list and the media pane — and starts at the shell's content edge
        // (y=96 in the design).
        Column(modifier = Modifier.fillMaxSize()) {
            Row {
                ChatList(
                    chats = chats,
                    selectedChatId = selectedChatId,
                    viewModel = viewModel,
                    onSelect = { chat ->
                        selectedChatId = chat.id
                        // A chat opens on the unfiltered first page, so the chip state and the
                        // query state stay in step.
                        filter = MediaFilter.All
                        viewModel.openChat(chat.id)
                    },
                )

                Spacer(Modifier.width(HomeSpec.GutterBetweenColumns))

                Column(modifier = Modifier.width(HomeSpec.GridWidth)) {
                    MediaFilterRow(
                        selected = filter,
                        onSelect = { picked ->
                            // Chips are server-side queries: switching one re-queries the wall
                            // from page one instead of sifting whatever happens to be loaded.
                            if (picked != filter) {
                                filter = picked
                                viewModel.setMediaFilter(picked)
                            }
                        },
                    )
                    Spacer(Modifier.height(HomeSpec.ChipsToGrid))
                    MediaGrid(
                        items = media,
                        viewModel = viewModel,
                        onOpen = onOpenPlayer,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat list — Figma "List" (3209:1934): 268x412 at (58,96), 4dp gaps
// ---------------------------------------------------------------------------

@Composable
private fun ChatList(
    chats: List<ChatItem>,
    selectedChatId: Long?,
    viewModel: MainViewModel,
    onSelect: (ChatItem) -> Unit,
) {
    // The design's 268x412 box is exactly eight rows of (48 + 4), so anything
    // past seven chats has to scroll. It is a LazyColumn for that reason only —
    // no scrollbar, since the design draws none, and moving focus down brings the
    // focused row into view on its own. "Archived Chats" stays the first item so
    // it scrolls with the list, as the design's column implies.
    LazyColumn(
        modifier = Modifier
            .width(HomeSpec.ListWidth)
            .height(HomeSpec.ListHeight),
        verticalArrangement = Arrangement.spacedBy(HomeSpec.ListGap),
    ) {
        item(key = "archived") { ArchivedChatsRow() }
        items(items = chats, key = { it.id }) { chat ->
            ChatRow(
                chat = chat,
                selected = chat.id == selectedChatId,
                viewModel = viewModel,
                onClick = { onSelect(chat) },
            )
        }
    }
}

@Composable
private fun ArchivedChatsRow() {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxSizeWidth()
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(if (focused) HomeSpec.OnSurface else HomeSpec.RowFill)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Archive,
            contentDescription = null,
            tint = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Archived Chats",
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatItem,
    selected: Boolean,
    viewModel: MainViewModel,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Figma shows three row states: plain, pinned (60% fill, dark text) and
    // focused/selected (full fill, dark text).
    val highlighted = focused || selected
    val fill = when {
        highlighted -> HomeSpec.OnSurface
        else -> HomeSpec.RowFill
    }
    val textColor = when {
        highlighted -> HomeSpec.InverseOnSurface
        else -> HomeSpec.OnSurface
    }

    // A Box rather than a Row: the design positions the pin absolutely
    // (right: 16dp, vertically centred), so it overlays the row instead of taking
    // part in the text flow — hence no spacer in front of it.
    Box(
        modifier = Modifier
            .fillMaxSizeWidth()
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(fill)
            .focusable()
            .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            // Measured from the design's exported rows (4x PNG, 268x48dp): the avatar
            // ink sits at y 8..39 (centre 23.9 = the row's centre) while the title and
            // its trailing icons sit at y 11..24 (centre ~18) — i.e. the name line is
            // pinned to the TOP of the 32dp content box, not centred on the avatar.
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                Avatar(
                    photoFileId = chat.photoSmallFileId,
                    name = chat.title,
                    id = chat.id,
                    viewModel = viewModel,
                    fallbackIcon = chat.type.typeIcon(),
                )
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // The design puts the dot at (18,10) inside a row whose
                            // content starts at (16,8) and whose avatar is 32dp, so
                            // it sits 2dp inside the avatar's top-left corner.
                            .offset(x = 2.dp, y = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (highlighted) HomeSpec.TertiaryFixed else HomeSpec.Tertiary),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The design caps the name at 130dp ("最长130dp超过省略"), which is
                    // also what keeps it clear of the absolutely positioned pin.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 130.dp),
                )
                // Order and spacing follow the design's three row examples, where the
                // icons sit at fixed x offsets: Verified first, then the chat-type
                // glyph, then mute — 12dp apart on 12dp icons, i.e. a 2dp gap.
                if (chat.isVerified) {
                    Spacer(Modifier.width(2.dp))
                    // Verified keeps its own colour scale: primary while the row is
                    // plain or pinned, primary-fixed once it is focused or selected.
                    Icon(
                        Icons.Outlined.Verified,
                        null,
                        tint = if (highlighted) HomeSpec.PrimaryFixed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                chat.type.typeIcon()?.let {
                    Spacer(Modifier.width(2.dp))
                    Icon(it, null, tint = textColor, modifier = Modifier.size(12.dp))
                }
                if (chat.isMuted) {
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.VolumeOff, null, tint = textColor, modifier = Modifier.size(12.dp))
                }
            }
        }

        if (chat.isPinned) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = textColor,
                // Absolute placement per the design: 16dp from the row's right edge,
                // vertically centred, 16dp glyph rotated -45°. (16 * sqrt(2) = 22.63 —
                // that bounding box is where Figma's "22.63 x 22.63" reading came from.)
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(16.dp)
                    .rotate(45f),
            )
        }
    }
}

private fun ChatType.typeIcon() = when (this) {
    ChatType.Private -> Icons.Outlined.Person
    ChatType.Group -> Icons.Outlined.Groups
    ChatType.Channel -> Icons.Outlined.Campaign
    else -> null
}

// ---------------------------------------------------------------------------
// Filter chips — Figma row 1243:1727 at (358,96), 12dp gaps
// ---------------------------------------------------------------------------

@Composable
private fun MediaFilterRow(selected: MediaFilter, onSelect: (MediaFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaFilter.entries.forEach { entry ->
            FilterChip(
                label = entry.label,
                selected = entry == selected,
                showCheck = entry == MediaFilter.All && entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        modifier = Modifier
            .height(HomeSpec.ChipsHeight)
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(if (active) HomeSpec.White else Color.Transparent)
            .border(
                width = if (active) 0.dp else 1.dp,
                color = if (active) Color.Transparent else HomeSpec.ChipOutline,
                shape = RoundedCornerShape(HomeSpec.Corner),
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            // Horizontal padding only. The chip is a fixed 32dp tall as drawn and
            // the content is centred inside it; keeping the design's 10dp of
            // vertical padding on top of that left a 12dp content box for a 16dp
            // text line, so the label was taller than its own box and got clipped
            // (the descender in "Image" was the visible casualty).
            .padding(
                start = if (showCheck) 16.dp else 20.dp,
                end = 20.dp,
            ),
        // Vertical centring: the design says alignItems=center. The row width
        // hugs its content, so the chip is centred horizontally by construction.
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showCheck) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = HomeSpec.InverseOnSurface,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) HomeSpec.InverseOnSurface else HomeSpec.White,
        )
    }
}

// ---------------------------------------------------------------------------
// Media grid — Figma "Grid" (1243:1726): 544x344 at (358,164), 160x120 cells
// ---------------------------------------------------------------------------

@Composable
private fun MediaGrid(items: List<MediaItem>, viewModel: MainViewModel, onOpen: (Int) -> Unit) {
    val gridState = rememberLazyGridState()
    val exhausted by viewModel.mediaExhausted.collectAsStateWithLifecycle()
    val loadingMore by viewModel.mediaLoadingMore.collectAsStateWithLifecycle()

    // The repository pages 100 messages at a time, so without this the grid would
    // simply stop at the first page and read as "that is all the media there is".
    // Load the next page as soon as the last item comes into view; the exhausted
    // flag stops the requests once the chat runs out.
    LaunchedEffect(gridState, exhausted) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 1 && info.totalItemsCount > 0
        }
            .distinctUntilChanged()
            .collect { atEnd ->
                if (atEnd && !exhausted && !loadingMore) viewModel.loadMoreMedia()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .width(HomeSpec.GridWidth)
            .height(HomeSpec.GridHeight),
        horizontalArrangement = Arrangement.spacedBy(HomeSpec.CellGap),
        verticalArrangement = Arrangement.spacedBy(HomeSpec.CellGap),
        contentPadding = PaddingValues(0.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.messageId }) { index, item ->
            MediaCell(item = item, viewModel = viewModel, onClick = { onOpen(index) })
        }
    }
}

/** How a media card is drawn. Album carries its member count for the "+N". */
private sealed interface MediaCellKind {
    data object Photo : MediaCellKind
    data object Video : MediaCellKind
    data object Audio : MediaCellKind
    data object Text : MediaCellKind
    data class Album(val count: Int) : MediaCellKind
}

/**
 * Repository item -> card kind.
 *
 * TdMediaRepository only produces photo / video / animation items today, so the
 * audio, text and album kinds are fully implemented in the UI but cannot be
 * reached until the repository surfaces those messages: audio and document need
 * their own collection pass, and album membership needs the mediaAlbumId
 * grouping. The mapping below is the only place that has to change then.
 */
private fun MediaItem.cellKind(): MediaCellKind = when {
    // The repository collapses whole albums into one item, so a member count
    // above one is what turns a card into an album card with its "+N".
    albumSize > 1 -> MediaCellKind.Album(albumSize)
    type == MediaType.Audio -> MediaCellKind.Audio
    type == MediaType.Text -> MediaCellKind.Text
    type == MediaType.Video || type == MediaType.Animation -> MediaCellKind.Video
    else -> MediaCellKind.Photo
}

/** How long a card waits for its thumbnail before falling back to the glyph. */
private const val ThumbnailTimeoutMs = 6_000L

@Composable
private fun MediaCell(
    item: MediaItem,
    viewModel: MainViewModel,
    onClick: () -> Unit,
) {
    val kind = remember(item.messageId, item.type, item.albumSize) { item.cellKind() }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = HomeSpec.CellWidth, height = HomeSpec.CellHeight)
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(HomeSpec.RowFill)
            .then(
                if (focused) {
                    Modifier.border(HomeSpec.FocusBorder, HomeSpec.White, RoundedCornerShape(HomeSpec.Corner))
                } else {
                    Modifier
                },
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused },
    ) {
        when (kind) {
            // Placeholder-only kinds: the design draws one glyph, centred.
            // Audio is the same glyph as Material's AudioFile (measured: IoU
            // 99.8%), so it needs no bundled asset; text does (IoU 36.8% against
            // Material's Description — a different drawing), so that one ships as
            // a vector converted from the design.
            is MediaCellKind.Audio -> MediaGlyph(Icons.Outlined.AudioFile)
            is MediaCellKind.Text -> MediaGlyphFromRes(R.drawable.ic_media_text_snippet)

            // Album: Collections glyph with the member count underneath.
            is MediaCellKind.Album -> MediaAlbumCell(kind.count)

            // Photo / video: the thumbnail is the card; the Image / Video file
            // glyph only stands in when there is nothing to show.
            is MediaCellKind.Photo -> MediaThumbnail(item, viewModel, isVideo = false)
            is MediaCellKind.Video -> MediaThumbnail(item, viewModel, isVideo = true)
        }
    }
}

@Composable
private fun BoxScope.MediaGlyph(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = HomeSpec.OnSurface,
        modifier = Modifier
            .align(Alignment.Center)
            .size(48.dp),
    )
}

/** The one glyph that genuinely differs from Material's set, so it ships as an asset. */
@Composable
private fun BoxScope.MediaGlyphFromRes(@DrawableRes res: Int) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = HomeSpec.OnSurface,
        modifier = Modifier
            .align(Alignment.Center)
            .size(48.dp),
    )
}

/** Album card: Collections glyph + "+N" (design: 48dp glyph, 8dp gap, 14/20 label). */
@Composable
private fun BoxScope.MediaAlbumCell(count: Int) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Collections,
            contentDescription = null,
            tint = HomeSpec.OnSurface,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "+" + count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = HomeSpec.OnSurface,
        )
    }
}

/**
 * Photo / video card. The thumbnail is preferred; the design's Image / Video file
 * glyph is the fallback for the cases the spec calls "abnormal": no thumbnail to
 * fetch at all, a fetch that has not landed within [ThumbnailTimeoutMs], or an
 * image that failed to decode. While a fetch is simply in flight the card stays
 * empty rather than showing the glyph, so a slow thumbnail does not read as
 * a broken one.
 */
@Composable
private fun BoxScope.MediaThumbnail(item: MediaItem, viewModel: MainViewModel, isVideo: Boolean) {
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val thumbFileId = item.thumbnailFileId ?: item.fileId
    val localPath = (fileStates[thumbFileId] as? FileDownloadState.Local)?.path
        ?: item.thumbnailLocalPath
        ?: item.localPath

    var imageFailed by remember(item.messageId, localPath) { mutableStateOf(false) }
    var waitOver by remember(item.messageId) { mutableStateOf(false) }

    LaunchedEffect(thumbFileId) {
        if (localPath == null) viewModel.ensureMediaFile(thumbFileId, priority = 24)
    }
    LaunchedEffect(item.messageId) {
        delay(ThumbnailTimeoutMs)
        waitOver = true
    }

    // Three states: the thumbnail, a spinner while it is on its way, and the type
    // glyph once it is known not to arrive. Compose's icon set has no
    // progress_activity (that is a Material Symbols glyph), and a static loading
    // icon would read as a frozen card, so the spinner is the M3 indicator that
    // PlayerScreen already uses — and it is bounded by the same timeout, so a card
    // can never spin forever.
    val showImage = localPath != null && !imageFailed
    val showGlyph = !showImage && (imageFailed || waitOver || thumbFileId == 0)
    val showLoading = !showImage && !showGlyph

    // Read the context in the composable scope: reading a CompositionLocal inside
    // the remember lambda is not a composable context.
    val ctx = LocalContext.current

    if (showImage) {
        AsyncImage(
            model = remember(localPath) { ImageRequest.Builder(ctx).data(File(localPath!!)).crossfade(false).build() },
            contentDescription = item.caption,
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = HomeSpec.CellWidth, height = HomeSpec.CellHeight),
        )
    } else if (showLoading) {
        // Material Symbols' progress_activity, spun by hand: the icon set bundled
        // with Compose predates it, so it ships as a vector and the rotation is
        // ours. Still bounded by the same timeout above — a card can never spin
        // forever.
        val spin by rememberInfiniteTransition(label = "cellSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
            label = "cellSpinAngle",
        )
        Icon(
            painter = painterResource(R.drawable.ic_progress_activity),
            contentDescription = null,
            tint = HomeSpec.OnSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .rotate(spin),
        )
    } else if (showGlyph) {
        Icon(
            imageVector = if (isVideo) Icons.Outlined.VideoFile else Icons.Outlined.Image,
            contentDescription = null,
            tint = HomeSpec.OnSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp),
        )
    }

    // Play affordance + duration ride on top of the thumbnail only: the design's
    // glyph-only card carries neither.
    if (isVideo && showImage) {
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            tint = HomeSpec.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp),
        )
        if (item.duration > 0) {
            Text(
                text = formatDuration(item.duration),
                style = MaterialTheme.typography.labelSmall,
                color = HomeSpec.OnSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 6.dp),
            )
        }
    }
}

/** Seconds -> "m:ss", or "h:mm:ss" past an hour (matches the design's 01:59:59). */
private fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Every list row in the design spans the full 268dp column. */
private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.ListWidth))
