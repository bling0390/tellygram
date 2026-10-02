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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.LazyGridState
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.components.Avatar
import androidx.compose.material.icons.outlined.Done
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import tv.telegram.ui.components.ConfirmDialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The visual half of a media card, shared by the grid and the album popup so the two cannot
 * drift (2026-09-30): the thumbnail with its loading/glyph states, the type glyphs and the
 * album's "+N". Focus and key handling stay with each caller — the grid drives a four-way
 * graph, the popup only Left/Right.
 */
@Composable
internal fun BoxScope.MediaCardContent(item: MediaItem, state: HomeState) {
    when (val kind = item.cellKind()) {
        // Placeholder-only kinds: the design draws one glyph, centred.
        is MediaCellKind.Audio -> MediaGlyph(Icons.Outlined.AudioFile)
        is MediaCellKind.Text -> MediaGlyphFromRes(R.drawable.ic_media_text_snippet)
        // Album: Collections glyph with the member count underneath.
        is MediaCellKind.Album -> MediaAlbumCell(kind.count)
        // Photo / video: the thumbnail is the card; the file glyph stands in when there is
        // nothing to show.
        is MediaCellKind.Photo -> MediaThumbnail(item, state, isVideo = false)
        is MediaCellKind.Video -> MediaThumbnail(item, state, isVideo = true)
    }
}

/** How long a card waits for its thumbnail before falling back to the glyph. */
internal const val ThumbnailTimeoutMs = 6_000L

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

@Composable
private fun BoxScope.MediaThumbnail(item: MediaItem, state: HomeState, isVideo: Boolean) {
    val fileStates by state.fileStates.collectAsStateWithLifecycle()
    val thumbFileId = item.thumbnailFileId ?: item.fileId
    val localPath = (fileStates[thumbFileId] as? FileDownloadState.Local)?.path
        ?: item.thumbnailLocalPath
        ?: item.localPath

    var imageFailed by remember(item.messageId, localPath) { mutableStateOf(false) }
    var waitOver by remember(item.messageId) { mutableStateOf(false) }

    LaunchedEffect(thumbFileId) {
        if (localPath == null) state.ensureMediaFile(thumbFileId, priority = 24)
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
            // Fit the long edge instead of cropping: the whole frame stays visible and the
            // card's fill shows beside it (product, 2026-10-01).
            contentScale = ContentScale.Fit,
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

/**
 * The card's frame, shared so the grid and the popup cannot drift: 160x120, 4dp corners, the
 * surface-container fill, and the focused ring drawn OUTSIDE the card (the design's focused
 * card measures 166x126 = 160 + 3 + 3). Focus and key handling stay with the caller, which
 * passes its own chain through [modifier].
 */
@Composable
internal fun MediaCard(
    focused: Boolean,
    tag: String? = null,
    /** The ring's tint while unfocused: the card's own fill by default, a light outline in the popup. */
    borderColor: Color = HomeSpec.SurfaceContainer,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            // The SLOT is the box model now: 166x126 holds the 3dp ring plus the 160x120 card,
            // so the ring lives inside a layout box and no ancestor's clip can cut it (2026-10-01).
            .size(
                width = HomeSpec.CellWidth + HomeSpec.FocusBorder * 2,
                height = HomeSpec.CellHeight + HomeSpec.FocusBorder * 2,
            )
            .let { if (tag != null) it.testTag(tag) else it }
            // The ring is drawn BEFORE the clip. Compose's clip() also clips the drawing of the
            // modifiers after it, and the ring sits entirely OUTSIDE the card — so drawing it
            // after the clip left only its corners visible (2026-10-01 report). Drawn first, the
            // card paints over its inner half and the outer 3dp is the visible edge.
            .focusStroke(
                draw = true,
                width = HomeSpec.FocusBorder,
                color = if (focused) HomeSpec.White else borderColor,
                corner = HomeSpec.Corner,
            )
            .then(modifier)
            // Inside the slot: the ring's band, then the visual card itself.
            .padding(HomeSpec.FocusBorder)
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(HomeSpec.SurfaceContainer, RoundedCornerShape(HomeSpec.Corner)),
        content = content,
    )
}

/**
 * The focused ring, entirely outside the node: a 3dp stroke centred on a path inflated by
 * half the width, so the card's outer bounds grow by the full stroke (Compose's
 * Modifier.border draws inside instead). The corner radius grows with it to stay concentric.
 */
internal fun Modifier.focusStroke(
    draw: Boolean,
    width: Dp,
    color: Color,
    corner: Dp,
): Modifier = if (!draw) {
    this
} else {
    drawWithContent {
        drawContent()
        // Inset by half the stroke so the ring's OUTER edge is flush with the slot's bounds.
        val half = width.toPx() / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(half, half),
            size = Size(
                (size.width - width.toPx()).coerceAtLeast(0f),
                (size.height - width.toPx()).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(corner.toPx() + half),
            style = Stroke(width = width.toPx()),
        )
    }
}
