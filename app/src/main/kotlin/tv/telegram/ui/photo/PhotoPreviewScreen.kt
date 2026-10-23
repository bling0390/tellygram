@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.photo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.home.HomeState

/** Figma 3656:2929 aspect menu: crop the photo to this ratio (Cover). */
private enum class PreviewAspect(val label: String, val ratio: Float) {
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    Wide("16:9", 16f / 9f),
}

/** Figma 3653:2222 rotation menu; display only, never touches the file. */
private val RotationOptions = listOf("0°", "90°", "180°", "270°")

/** Design: content column 836dp inside the full-width bar (960 canvas, 62 a side). */
private val BarContentWidth = 836.dp

/** How long the bar stays up after the last interaction. */
private const val BarAutoHideMs = 4_000L

/** Indices of the photo items, which is what Left/Right step through here. */
internal fun photoIndices(items: List<MediaItem>): List<Int> =
    items.mapIndexedNotNull { i, m -> if (m.type == MediaType.Photo) i else null }

/**
 * Full-screen photo preview (Figma 3656:2929 / 3656:3016 / 3658:3269 / 3658:3117 / 3653:2222).
 *
 * Left/Right walk the photos, Back exits. The bottom bar auto-hides; any key brings it back and
 * hands focus to the current photo's slot in the strip. Aspect crops (Cover), rotation is display
 * only and lasts for this visit, Info is a placeholder for now (2026-10-22 decisions).
 */
@Composable
internal fun PhotoPreviewScreen(
    state: HomeState,
    index: Int,
    onClose: () -> Unit,
    /**
     * Set when the preview was opened by message id (plan (a), 2026-09-30): the list to walk,
     * already filtered to photos. Null means the chat's loaded media.
     */
    overrideItems: List<MediaItem>? = null,
) {
    // The photo being shown, kept locally (2026-10-22): stepping with the D-pad or focusing a
    // strip thumb switches the picture without a route change, so the display settings survive
    // the walk and a thumb renders the moment it takes focus.
    var current by remember { mutableIntStateOf(index) }
    val ownItems by state.mediaItems.collectAsStateWithLifecycle()
    val items = overrideItems ?: ownItems
    val fileStates by state.fileStates.collectAsStateWithLifecycle()
    val imageFocus = remember { FocusRequester() }
    // The current strip slot: the bar hands focus here on open, and Down from the action
    // buttons returns to it (2026-10-22).
    val stripFocus = remember { FocusRequester() }
    // Up from the strip lands here: the first round button of the action row (2026-10-23).
    val actionsFocus = remember { FocusRequester() }

    // Display settings last for this visit only: entering or leaving the preview resets them.
    var rotation by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf<PreviewAspect?>(null) }
    var aspectMenu by remember { mutableStateOf(false) }
    var rotationMenu by remember { mutableStateOf(false) }
    // The buttons' window bounds: each menu floats 16dp above its own button (2026-10-22).
    var aspectAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var rotationAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Hidden on entry: the photo gets the screen, any key reveals the bar (2026-10-22).
    var barVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Once the user moves into the strip or a button the auto-hide timer stops, the way the
    // player's controller stays up while its button row has focus (2026-10-22).
    var interacted by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // Hide the bar and hand focus back after the fade, like the player's controller
    // (2026-10-22). Down from the strip and Up from the buttons call this too.
    val hideBar: () -> Unit = {
        barVisible = false
        interacted = false
        scope.launch {
            delay(400L)
            runCatching { imageFocus.requestFocus() }
        }
        Unit
    }

    LaunchedEffect(current, items.size) {
        if (current !in items.indices) onClose()
    }
    if (current !in items.indices) return
    val item = items[current]

    // Record the return target as the shown photo changes (2026-10-22): Back then lands on the
    // card that was actually being looked at.
    LaunchedEffect(current, items.size) {
        items.getOrNull(current)?.let { state.setPlayerReturnFocus(it.messageId) }
    }

    BackRegistration(BackPriority.PLAYER, enabled = true) {
        when {
            aspectMenu -> aspectMenu = false
            rotationMenu -> rotationMenu = false
            barVisible -> hideBar()
            else -> {
                state.setPlayerReturnFocus(item.messageId)
                onClose()
            }
        }
    }

    // Keep the TV awake while a photo is on screen (the player does the same).
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    val fileState = fileStates[item.fileId]
    // Full-size file only: the thumbnail is a placeholder, never the "photo" (2026-10-22).
    val fullPath = (fileState as? FileDownloadState.Local)?.path ?: item.localPath
    val thumbPath = item.thumbnailLocalPath
    LaunchedEffect(item.fileId, retryTick) {
        if (fullPath == null) state.ensureMediaFile(item.fileId, priority = 16)
    }

    // Auto-hide like the player's controller: the timer only runs while the user has not moved
    // into the bar's controls, and never while a menu is open.
    LaunchedEffect(barVisible, interacted, aspectMenu, rotationMenu, lastInteraction) {
        if (!barVisible || interacted || aspectMenu || rotationMenu) return@LaunchedEffect
        delay(BarAutoHideMs)
        barVisible = false
        // Let the fade-out finish before handing focus back, so vanishing thumbs cannot eat
        // direction keys during the exit (the player waits 400ms too).
        delay(400L)
        runCatching { imageFocus.requestFocus() }
    }

    val photos = remember(items) { photoIndices(items) }
    val position = photos.indexOf(current)

    fun reveal() {
        barVisible = true
        interacted = false
        lastInteraction = System.currentTimeMillis()
    }

    fun step(delta: Int) {
        current = photos.getOrNull(position + delta) ?: return
    }

    val isError = fileState is FileDownloadState.Failed
    val aspectRatio = aspect?.ratio

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeSpec.Background)
            .clipToBounds()
            .focusRequester(imageFocus)
            .focusable()
            // Back with the bar up hides it before anything else sees the press; the second
            // Back (bar hidden) falls through to the controller and exits the preview.
            .onPreviewKeyEvent { event: KeyEvent ->
                if (event.key == Key.Back && barVisible) {
                    hideBar()
                    true
                } else {
                    false
                }
            }
            .onKeyEvent { event: KeyEvent ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        // Left/Right only walk the photos: the bar stays hidden (2026-10-22).
                        if (!barVisible) { step(-1); true } else false
                    }
                    Key.DirectionRight -> {
                        if (!barVisible) { step(1); true } else false
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!barVisible) { reveal(); true } else false
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (!barVisible) { reveal(); true } else false
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isError -> {
                // Figma 3658:3269 / 3658:3117.
                ErrorPane(onRetry = { retryTick++; reveal() })
            }
            fullPath != null -> {
                val ctx = LocalContext.current
                AsyncImage(
                    model = remember(fullPath) {
                        ImageRequest.Builder(ctx).data(File(fullPath)).build()
                    },
                    contentDescription = item.caption,
                    contentScale = if (aspectRatio != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .then(
                            if (aspectRatio != null) Modifier.aspectRatio(aspectRatio)
                            else Modifier.fillMaxSize()
                        )
                        .graphicsLayer { rotationZ = rotation.toFloat() },
                )
            }
            else -> {
                // Placeholder first (the thumbnail file, else the embedded minithumbnail paints
                // instantly), then the 48dp ring with a real percentage (2026-10-22).
                val ctx = LocalContext.current
                if (thumbPath != null) {
                    AsyncImage(
                        model = remember(thumbPath) {
                            ImageRequest.Builder(ctx).data(File(thumbPath)).crossfade(false).build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    item.minithumbnail?.let { mini ->
                        AsyncImage(
                            model = mini,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    val expected = (fileState as? FileDownloadState.Downloading)?.expected ?: 0L
                    val downloaded = (fileState as? FileDownloadState.Downloading)?.downloaded ?: 0L
                    val fraction = if (expected > 0L) (downloaded.toFloat() / expected).coerceIn(0f, 1f) else null
                    if (fraction != null) {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(48.dp),
                            // Figma: the track is white at 8%, the sweep is white (2026-10-22).
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeSpec.OnSurface,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Bottom bar: gradient + shadow per Figma, content column 836dp fixed.
        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo-bar")
                    // Figma 3653:2222: flat rgba(0,0,0,.2), a 1px white 2% hairline along the
                    // top edge, no shadow (2026-10-23).
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .drawBehind {
                        drawRect(
                            color = Color.White.copy(alpha = 0.02f),
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    }
                    .padding(top = 8.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.width(BarContentWidth),
                    // Figma: the counter sits on the row's baseline (align bottom).
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "${position + 1} / ${photos.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = HomeSpec.White,
                        modifier = Modifier.testTag("photo-counter"),
                    )
                    if (!isError) {
                        // Figma: aspect ratio (active when a crop is set), rotation, info.
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RoundIconButton(
                                icon = Icons.Outlined.AspectRatio,
                                requester = actionsFocus,
                                onClick = { aspectMenu = true },
                                onFocus = {
                                    interacted = true
                                    lastInteraction = System.currentTimeMillis()
                                },
                                onBounds = { aspectAnchor = it },
                                downFocus = stripFocus,
                                menuOpen = aspectMenu,
                                testTag = "photo-aspect",
                            )
                            RoundIconButton(
                                icon = Icons.Outlined.ScreenRotation,
                                onClick = { rotationMenu = true },
                                onFocus = {
                                    interacted = true
                                    lastInteraction = System.currentTimeMillis()
                                },
                                onBounds = { rotationAnchor = it },
                                downFocus = stripFocus,
                                menuOpen = rotationMenu,
                                testTag = "photo-rotation",
                            )
                            RoundIconButton(
                                icon = Icons.Outlined.Info,
                                // Placeholder for now (2026-10-22 decision).
                                onClick = { },
                                onFocus = {
                                    interacted = true
                                    lastInteraction = System.currentTimeMillis()
                                },
                                downFocus = stripFocus,
                                menuOpen = false,
                                testTag = "photo-info",
                            )
                        }
                    }
                }

                ThumbStrip(
                    photos = photos,
                    current = current,
                    items = items,
                    state = state,
                    fileStates = fileStates,
                    visible = barVisible,
                    currentFocus = stripFocus,
                    // Up from the strip goes to the first round button; the error pane has no
                    // action row, so there it stays put (2026-10-23).
                    upFocus = if (isError) null else actionsFocus,
                    onSelect = { current = it },
                    onInteraction = {
                        // The strip is the bar's default focus: it only restarts the timer.
                        // Only the buttons (below) stop the auto-hide.
                        lastInteraction = System.currentTimeMillis()
                    },
                )
            }
        }

        if (aspectMenu) {
            // Row 0 restores the original aspect; the rest are the design's crop ratios.
            val options = PreviewAspect.entries
            PreviewMenu(
                rows = listOf("原始比例") + options.map { it.label },
                selected = aspect?.let { options.indexOf(it) + 1 } ?: 0,
                onSelect = { i ->
                    aspect = options.getOrNull(i - 1)
                    aspectMenu = false
                },
                onDismiss = { aspectMenu = false },
                anchor = aspectAnchor,
            )
        }
        if (rotationMenu) {
            PreviewMenu(
                rows = RotationOptions,
                selected = RotationOptions.indexOfFirst { it == "${rotation}°" },
                onSelect = { i ->
                    rotation = (i.coerceIn(0, 3)) * 90
                    rotationMenu = false
                },
                onDismiss = { rotationMenu = false },
                anchor = rotationAnchor,
            )
        }
    }
}

/** Figma 3658:3269: broken-image glyph, "Try again" pill and nothing else over the photo. */
@Composable
private fun ErrorPane(onRetry: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = HomeSpec.OnSurface,
            modifier = Modifier.size(48.dp),
        )
        Box(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .focusRequester(focus)
                .clip(RoundedCornerShape(4.dp))
                .background(if (focused) HomeSpec.White else HomeSpec.SurfaceContainer)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp &&
                        (ev.key == Key.DirectionCenter || ev.key == Key.Enter)
                    ) {
                        onRetry()
                        true
                    } else {
                        false
                    }
                }
                .testTag("photo-retry")
                .padding(horizontal = 32.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            )
        }
    }
}

/** 40dp circle, 20dp glyph; white when active or focused, surface-container otherwise. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    onFocus: () -> Unit = {},
    onBounds: (Rect) -> Unit = {},
    /** Optional external handle so the strip can hand focus up to the first button. */
    requester: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    /** While this button's menu is open the button shows its default look (2026-10-22). */
    menuOpen: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused && !menuOpen
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .let { if (requester != null) it.focusRequester(requester) else it }
            // No selected state: white only while focused, and never while its menu is open.
            .background(if (highlighted) HomeSpec.White else Color(0xCC1E2023))
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            // Down returns to the current strip slot; Up leaves the bar (2026-10-22).
            .focusProperties {
                if (downFocus != null) down = downFocus
                up = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.DirectionCenter || ev.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Figma: 112x84 thumbs, 10dp apart, the current one carrying a 3dp white stroke. */
@Composable
private fun ThumbStrip(
    photos: List<Int>,
    current: Int,
    items: List<MediaItem>,
    state: HomeState,
    fileStates: Map<Int, FileDownloadState>,
    /** Only claim focus while the bar is up; a hidden bar must leave it on the photo. */
    visible: Boolean,
    currentFocus: FocusRequester,
    upFocus: FocusRequester?,
    onSelect: (Int) -> Unit,
    onInteraction: () -> Unit,
) {
    val listState = rememberLazyListState()
    val position = photos.indexOf(current)

    LaunchedEffect(position) {
        if (position >= 0) runCatching { listState.animateScrollToItem(position) }
    }
    LaunchedEffect(position, listState, visible) {
        if (!visible || position < 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { currentFocus.requestFocus() }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.width(BarContentWidth).testTag("photo-strip"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(photos) { i, itemIndex ->
            val member = items.getOrNull(itemIndex) ?: return@itemsIndexed
            ThumbCell(
                member = member,
                isCurrent = i == position,
                requester = if (i == position) currentFocus else null,
                upFocus = upFocus,
                singleItem = photos.size == 1,
                fileStates = fileStates,
                state = state,
                onInteraction = onInteraction,
                onClick = { onSelect(itemIndex) },
            )
        }
    }
}

@Composable
private fun ThumbCell(
    member: MediaItem,
    isCurrent: Boolean,
    requester: FocusRequester?,
    upFocus: FocusRequester?,
    singleItem: Boolean,
    fileStates: Map<Int, FileDownloadState>,
    state: HomeState,
    onInteraction: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val thumbFileId = member.thumbnailFileId ?: 0
    // Medium thumbnail (pickThumbnail), requested like the album tiles: without this the strip
    // only ever had the tiny embedded minithumbnail and looked soft (2026-10-22).
    val localPath = (fileStates[thumbFileId] as? FileDownloadState.Local)?.path
        ?: member.thumbnailLocalPath
        ?: member.localPath
    LaunchedEffect(thumbFileId, localPath) {
        if (localPath == null && thumbFileId != 0) state.ensureMediaFile(thumbFileId, priority = 30)
    }

    Box(
        modifier = Modifier
            // 118x90 slot with the 3dp stroke OUTSIDE the 112x84 picture (2026-10-22).
            .size(width = 118.dp, height = 90.dp)
            .border(
                3.dp,
                if (focused) HomeSpec.White else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .let { if (requester != null) it.focusRequester(requester) else it }
            .focusProperties {
                down = FocusRequester.Cancel
                // Up leaves the strip for the first round button (2026-10-23).
                up = upFocus ?: FocusRequester.Cancel
                // A one-photo strip has nowhere to go right (2026-10-23).
                if (singleItem) right = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                    // Focusing a thumb renders it at once; OK is not needed (2026-10-22).
                    onClick()
                }
            }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyUp) {
                    false
                } else if (ev.key == Key.DirectionCenter || ev.key == Key.Enter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(3.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(HomeSpec.SurfaceContainer)
            .testTag("photo-thumb"),
        contentAlignment = Alignment.Center,
    ) {
        member.minithumbnail?.let { mini ->
            AsyncImage(
                model = mini,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (localPath != null) {
            AsyncImage(
                model = remember(localPath) {
                    ImageRequest.Builder(ctx).data(File(localPath)).crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Focus is shown by the 3dp white border above.
    }
}

/** Menu popup (216 wide, radius 16, background, elevation): rows select with OK. */
@Composable
private fun PreviewMenu(
    rows: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** The button it hangs off, in window coordinates: the menu floats 16dp above it. */
    anchor: Rect?,
) {
    val rowFocus = remember { FocusRequester() }
    // Rows stay non-focusable for one frame: otherwise the popup window auto-focuses the first
    // row before the request lands on the selected one (2026-10-22).
    var armed by remember { mutableStateOf(false) }
    val gapPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        armed = true
        withFrameNanos { }
        // The requester sits on the selected row, so the menu opens on the current choice.
        runCatching { rowFocus.requestFocus() }
    }
    Popup(
        popupPositionProvider = remember(anchor, gapPx) { AboveButtonPosition(anchor, gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(216.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .background(HomeSpec.Background, RoundedCornerShape(16.dp))
                .testTag("photo-menu")
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            rows.forEachIndexed { i, label ->
                val isSelected = i == selected
                var focused by remember { mutableStateOf(false) }
                // Focus owns the white background; selection shows as a white label plus the
                // check (2026-10-22).
                val bg = if (focused) HomeSpec.White else Color.Transparent
                val fg = when {
                    focused -> HomeSpec.InverseOnSurface
                    isSelected -> HomeSpec.White
                    else -> HomeSpec.OnSurface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .let { if (i == selected.coerceAtLeast(0)) it.focusRequester(rowFocus) else it }
                        .onFocusChanged { focused = it.isFocused }
                        .let { if (armed) it.focusable() else it }
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyUp &&
                                (ev.key == Key.DirectionCenter || ev.key == Key.Enter)
                            ) {
                                onSelect(i)
                                true
                            } else {
                                false
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = fg,
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(18.9.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menu position: 16dp above its button, right edges aligned (Figma 3656:2929 / 3653:2222).
 * Falls back to the bottom-right corner when the button bounds are not known yet.
 */
private class AboveButtonPosition(
    private val anchor: Rect?,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val a = anchor
        // Centred on the button's vertical axis (2026-10-22).
        val x = a?.let { (it.center.x - popupContentSize.width / 2f).toInt() }
            ?: (windowSize.width - popupContentSize.width)
        val y = a?.let { (it.top - popupContentSize.height - gapPx).toInt() }
            ?: (windowSize.height - popupContentSize.height)
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
