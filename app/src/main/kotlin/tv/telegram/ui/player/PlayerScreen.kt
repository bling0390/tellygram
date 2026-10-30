@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.player

import android.util.Log
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.telegram.R
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.td.TdDataSource
import tv.telegram.td.TdDataSourceFactory
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.focus.dpadNavigationSounds
import org.drinkless.td.libcore.telegram.TdApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.outlined.Done
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.compose.foundation.layout.ColumnScope
import tv.telegram.ui.components.RightEdgeSheet
import tv.telegram.ui.components.RightSheetInk
import tv.telegram.ui.components.RightSheetRule

private const val TAG = "PlayerScreen"

private val SeekHandleSize = 16.dp
private val SeekHandleBorder = 4.dp

private val SeekBarWidth = 732.dp
private val SeekTimeSlotWidth = 48.dp

private const val PLAYER_INIT_DELAY_MS = 250L

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    index: Int,
    onClose: () -> Unit,
    onNavigateTo: (Int) -> Unit,
    items: List<MediaItem>? = null,
) {
    val context = LocalContext.current
    val ownItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val mediaItems = items ?: ownItems
    val speed by viewModel.playerPlaybackSpeed.collectAsStateWithLifecycle()
    val resumeMap by viewModel.playerResumePositions.collectAsStateWithLifecycle()
    val mediaExhausted by viewModel.mediaExhausted.collectAsStateWithLifecycle()

    LaunchedEffect(index, mediaItems.size, mediaExhausted) {
        if (items == null && !mediaExhausted && index >= mediaItems.size - 8) {
            viewModel.loadMoreMedia()
        }
    }

    if (index !in mediaItems.indices) {

        LaunchedEffect(Unit) { onClose() }
        return
    }

    val current = mediaItems[index]

    LaunchedEffect(current.messageId) {
        android.util.Log.i("HomeBack", "player open: hint=${current.messageId}")
        viewModel.setPlayerReturnFocus(current.messageId)
    }

    val closePlayer = {
        viewModel.setPlayerReturnFocus(current.messageId)
        onClose()
    }

    val videoIndices = remember(mediaItems) { walkIndices(mediaItems, MediaType.Video) }
    val hasPrevVideo = hasPreviousIn(videoIndices, index)
    val hasNextVideo = hasNextIn(videoIndices, index)
    val videos = remember(mediaItems, videoIndices) { videoIndices.mapNotNull { mediaItems.getOrNull(it) } }
    val videoIndex = videoIndices.indexOf(index)

    fun neighborVideo(delta: Int): Int? = neighbourIn(videoIndices, index, delta)

    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val currentFileState = fileStates[current.fileId]
    val currentPath = (currentFileState as? FileDownloadState.Local)?.path
    var forceLocalPlayback by remember(current.fileId) { mutableStateOf(false) }
    val canStream = current.type == MediaType.Video && current.supportsStreaming &&
        currentPath == null && !forceLocalPlayback
    var retryTick by remember(current.fileId) { mutableIntStateOf(0) }
    LaunchedEffect(current.fileId, retryTick) {
        if (canStream) {
            viewModel.fileRepo.startStreaming(current.fileId, priority = 32)
        } else if (currentPath == null) {
            viewModel.ensureMediaFile(current.fileId, priority = 32)
        }
    }

    DisposableEffect(current.fileId) {
        onDispose {
            viewModel.fileRepo.cancelDownload(current.fileId)
        }
    }

    var playerReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PLAYER_INIT_DELAY_MS)
        playerReady = true
    }
    if (!playerReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val exo = remember(current.fileId) {
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setEnableDecoderFallback(true),
            )
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(
                        TdDataSourceFactory(
                            fileRepo = viewModel.fileRepo,
                            fallback = DefaultDataSource.Factory(context),
                        ),
                    ),
            )
            .build().apply {
                playWhenReady = true
            }
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.fileRepo.deleteLocalFile(current.fileId)
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    DisposableEffect(Unit) {
        val fid = current.fileId
        onDispose {
            viewModel.fileRepo.deleteLocalFile(fid)
        }
    }

    var mediaPrepared by remember(current.fileId) { mutableStateOf(false) }
    var firstFrameRendered by remember(current.fileId) { mutableStateOf(false) }
    var isBuffering by remember(current.fileId) { mutableStateOf(false) }
    LaunchedEffect(current.fileId, currentPath, retryTick) {
        if (mediaPrepared) return@LaunchedEffect
        if (canStream) {
            exo.setMediaItem(ExoMediaItem.fromUri(TdDataSource.uriFor(current.fileId)))
            exo.prepare()
            val resume = resumeMap[current.fileId]
            if (resume != null && resume > 0L) {
                exo.seekTo(resume)
            }
            mediaPrepared = true
        } else if (currentPath != null) {
            exo.setMediaItem(ExoMediaItem.fromUri("file://$currentPath"))
            exo.prepare()
            val resume = resumeMap[current.fileId]
            if (resume != null && resume > 0L) {
                exo.seekTo(resume)
            }
            mediaPrepared = true
        }
    }

    LaunchedEffect(speed) {
        exo.setPlaybackSpeed(speed)
    }

    LaunchedEffect(exo, current.fileId) {
        while (true) {
            delay(2000L)
            if (exo.isPlaying) {
                viewModel.savePlayerPosition(current.fileId, exo.currentPosition)
            }
        }
    }

    var playerError by remember(current.fileId) { mutableStateOf<String?>(null) }
    var playerErrorUnsupportedCodec by remember(current.fileId) { mutableStateOf(false) }
    var unrecognizedRetries by remember(current.fileId) { mutableIntStateOf(0) }
    val retryPlayback = {
        playerError = null
        playerErrorUnsupportedCodec = false
        mediaPrepared = false
        firstFrameRendered = false
        isBuffering = false
        retryTick++
        Unit
    }

    var nowPlaying by remember(current.fileId) { mutableStateOf(exo.playWhenReady) }
    var videoCodecInfo by remember(current.fileId) { mutableStateOf<String?>(null) }
    var audioCodecInfo by remember(current.fileId) { mutableStateOf<String?>(null) }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                nowPlaying = playWhenReady
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback error for file ${current.fileId}", error)
                val unrecognised = generateSequence(error.cause) { it.cause }
                    .any { it is UnrecognizedInputFormatException }
                if (unrecognised && unrecognizedRetries < 1) {
                    unrecognizedRetries++
                    val path = viewModel.fileRepo.streamState(current.fileId)?.path
                        ?: (fileStates[current.fileId] as? FileDownloadState.Local)?.path
                    val onDisk = path?.let { File(it).length() } ?: 0L
                    if (onDisk > 0L) {
                        Log.w(
                            TAG,
                            "stream not recognised for ${current.fileId} with $onDisk bytes on disk; " +
                                "switching to a full download",
                        )
                        forceLocalPlayback = true
                    } else {
                        Log.w(
                            TAG,
                            "stream not recognised for ${current.fileId} with no bytes; " +
                                "restarting from 0",
                        )
                        viewModel.fileRepo.resetStreamWindow(current.fileId, 0)
                    }
                    mediaPrepared = false
                    firstFrameRendered = false
                    isBuffering = false
                    retryTick++
                    return
                }
                playerError = error.errorCodeName + ": " + (error.message ?: "")
                playerErrorUnsupportedCodec =
                    error.errorCodeName.startsWith("ERROR_CODE_DECODER") ||
                        error.message?.contains("NO_EXCEEDS_CAPABILITIES") == true
            }
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    viewModel.clearPlayerPosition(current.fileId)
                    val next = neighborVideo(+1)
                    if (next != null) {
                        onNavigateTo(next)
                    } else {
                        closePlayer()
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                videoCodecInfo = describeVideo(tracks)
                audioCodecInfo = describeAudio(tracks)
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    val focusRequester = remember { FocusRequester() }
    val progressFocusRequester = remember { FocusRequester() }
    val infoButtonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { focusRequester.requestFocus() }
        catch (_: IllegalStateException) {}
    }

    var showController by remember { mutableStateOf(false) }
    var openPopover by remember { mutableStateOf<PlayerPopover?>(null) }
    var rotation by remember { mutableIntStateOf(0) }
    LaunchedEffect(current.fileId) { rotation = 0 }
    var aspect by remember { mutableStateOf<VideoAspect?>(null) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var controllerShownBefore by remember { mutableStateOf(false) }
    var progressFocused by remember { mutableStateOf(false) }
    LaunchedEffect(showController) {
        if (showController) {
            controllerShownBefore = true
            delay(100L)
            try { progressFocusRequester.requestFocus() }
            catch (_: IllegalStateException) {}
        } else if (controllerShownBefore) {
            delay(400L)
            try { focusRequester.requestFocus() }
            catch (_: IllegalStateException) {}
        }
    }
    LaunchedEffect(showController, lastInteractionMs, progressFocused, openPopover) {
        if (showController && progressFocused && openPopover == null) {
            delay(4000L)
            showController = false
        }
    }
    LaunchedEffect(showController) {
        if (!showController) openPopover = null
    }
    val bumpController = {
        showController = true
        lastInteractionMs = System.currentTimeMillis()
    }

    var showInfo by remember { mutableStateOf(false) }
    var fileInfo by remember(current.fileId) { mutableStateOf<TdApi.File?>(null) }
    LaunchedEffect(showInfo, current.fileId) {
        if (showInfo) {
            fileInfo = viewModel.fileRepo.fileInfo(current.fileId)
        }
    }

    BackRegistration(BackPriority.PLAYER) {
        when {
            openPopover != null -> openPopover = null
            showInfo -> showInfo = false
            showController -> showController = false
            else -> closePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Back) {
                    when {
                        openPopover != null -> { openPopover = null; true }
                        showInfo -> { showInfo = false; true }
                        showController -> { showController = false; true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        if (showController) return@onKeyEvent false
                        exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        if (showController) return@onKeyEvent false
                        exo.seekTo(
                            (exo.currentPosition + 10_000L)
                                .coerceAtMost(exo.duration.coerceAtLeast(0L))
                        )
                        true
                    }
                    Key.MediaPrevious -> {
                        neighborVideo(-1)?.let { onNavigateTo(it) }
                        true
                    }
                    Key.MediaNext -> {
                        neighborVideo(+1)?.let { onNavigateTo(it) }
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter -> {
                        if (showController) return@onKeyEvent false
                        if (playerError != null || currentFileState is FileDownloadState.Failed) {
                            retryPlayback()
                            true
                        } else {
                            showController = true
                            true
                        }
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (exo.isPlaying) exo.pause() else exo.play()
                        if (showController) bumpController()
                        true
                    }
                    Key.Menu -> {
                        viewModel.cyclePlayerSpeed()
                        bumpController()
                        true
                    }
                    else -> false
                }
            },
    ) {
        val downloadError = (currentFileState as? FileDownloadState.Failed)?.reason
        val errorMsg = playerError?.let { text ->
            if (playerErrorUnsupportedCodec) {
                stringResource(R.string.player_error_unsupported_codec) + "\n\n" + text
            } else {
                text
            }
        } ?: downloadError?.let { stringResource(R.string.player_download_failed, it) }

        if (errorMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-52).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.player_error_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(400.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    TryAgainButton(onRetry = retryPlayback)
                }
            }
        } else if (!mediaPrepared) {
            Box(Modifier.fillMaxSize())
        } else {
            val videoAspect = aspect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation.toFloat()
                        if (rotation % 180 != 0) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val s = if (w >= h) w / h else h / w
                            scaleX = s
                            scaleY = s
                        }
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(
                            R.layout.tellygram_player_view, null,
                        ) as PlayerView).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exo
                            setBackgroundColor(HomeSpec.Background.toArgb())
                            setShutterBackgroundColor(HomeSpec.Background.toArgb())
                        }
                    },
                    update = {
                        it.player = exo
                        it.resizeMode = if (videoAspect == null) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    modifier = if (videoAspect == null) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.aspectRatio(videoAspect.ratio).clipToBounds()
                    },
                )
            }

            if (!firstFrameRendered) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (isBuffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showController,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerController(
                positionMs = { exo.currentPosition },
                durationMs = { exo.duration.coerceAtLeast(0L) },
                bufferedMs = { exo.bufferedPosition.coerceAtLeast(0L) },
                isPlaying = { nowPlaying },
                speed = speed,
                speeds = viewModel.playerSpeeds,
                aspect = aspect,
                openPopover = openPopover,
                progressFocusRequester = progressFocusRequester,
                infoFocusRequester = infoButtonFocus,
                videos = videos,
                currentIndex = videoIndex,
                fileStates = fileStates,
                ensureThumb = { id -> viewModel.ensureMediaFile(id, priority = 30) },
                onProgressFocusChange = { progressFocused = it },
                onInteraction = bumpController,
                onHideController = { showController = false },
                onPlayPause = {
                    if (exo.isPlaying) exo.pause() else exo.play()
                    bumpController()
                },
                onSeekBack = {
                    exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
                    bumpController()
                },
                onSeekFwd = {
                    exo.seekTo(
                        (exo.currentPosition + 10_000L)
                            .coerceAtMost(exo.duration.coerceAtLeast(0L))
                    )
                    bumpController()
                },
                onOpenPopover = { popover ->
                    bumpController()
                    openPopover = popover
                },
                onDismissPopover = { openPopover = null },
                onSpeedChange = { newSpeed -> viewModel.setPlayerSpeed(newSpeed) },
                onAspectChange = { newAspect ->
                    aspect = newAspect
                    bumpController()
                },
                onInfo = {
                    showInfo = !showInfo
                    bumpController()
                },
                onPrev = if (hasPrevVideo) {
                    { neighborVideo(-1)?.let { onNavigateTo(it) }; bumpController() }
                } else null,
                onNext = if (hasNextVideo) {
                    { neighborVideo(+1)?.let { onNavigateTo(it) }; bumpController() }
                } else null,
                onSelectVideo = { i -> videoIndices.getOrNull(i)?.let { onNavigateTo(it) } },
            )
        }

        RightEdgeSheet(
            visible = showInfo,
            onDismiss = { showInfo = false },
            title = stringResource(R.string.player_info_title),
            restoreFocus = infoButtonFocus,
        ) {
            MediaInfoBody(
                item = current,
                fileInfo = fileInfo,
                playedDurationMs = exo.duration.takeIf { it > 0 } ?: 0L,
                videoCodec = videoCodecInfo,
                audioCodec = audioCodecInfo,
            )
        }
    }
}

@Composable
private fun PlayerController(
    positionMs: () -> Long,
    durationMs: () -> Long,
    bufferedMs: () -> Long,
    isPlaying: () -> Boolean,
    speed: Float,
    speeds: List<Float>,
    aspect: VideoAspect?,
    openPopover: PlayerPopover?,
    progressFocusRequester: FocusRequester,
    infoFocusRequester: FocusRequester,
    videos: List<MediaItem>,
    currentIndex: Int,
    fileStates: Map<Int, FileDownloadState>,
    ensureThumb: (Int) -> Unit,
    onProgressFocusChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onHideController: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onOpenPopover: (PlayerPopover) -> Unit,
    onDismissPopover: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectChange: (VideoAspect?) -> Unit,
    onInfo: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSelectVideo: (Int) -> Unit,
) {
    val playing by remember { derivedStateOf { isPlaying() } }

    var nowPos by remember { mutableLongStateOf(positionMs()) }
    var nowDur by remember { mutableLongStateOf(durationMs()) }
    var nowBuffered by remember { mutableLongStateOf(bufferedMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowPos = positionMs()
            nowDur = durationMs()
            nowBuffered = bufferedMs()
            delay(500L)
        }
    }

    val prevFocus = remember { FocusRequester() }
    val seekBackFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFwdFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val aspectFocus = remember { FocusRequester() }
    val infoFocus = infoFocusRequester
    val stripFocus = remember { FocusRequester() }

    var popoverShownBefore by remember { mutableStateOf<PlayerPopover?>(null) }
    LaunchedEffect(openPopover) {
        if (openPopover != null) {
            popoverShownBefore = openPopover
        } else if (popoverShownBefore != null) {
            val target = when (popoverShownBefore) {
                PlayerPopover.Speed -> speedFocus
                PlayerPopover.Aspect -> aspectFocus
                null -> null
            }
            popoverShownBefore = null
            withFrameNanos { }
            if (target != null) {
                try { target.requestFocus() } catch (_: IllegalStateException) {}
            }
        }
    }
    val buttonFocuses = remember(onPrev, onNext) {
        buildList {
            if (onPrev != null) add(prevFocus)
            add(seekBackFocus)
            add(playFocus)
            add(seekFwdFocus)
            if (onNext != null) add(nextFocus)
            add(speedFocus)
            add(aspectFocus)
            add(infoFocus)
        }
    }
    val playIndex = buttonFocuses.indexOf(playFocus)
    var selectedIndex by remember(onPrev, onNext) { mutableIntStateOf(playIndex) }
    fun select(delta: Int) {
        onInteraction()
        val next = (selectedIndex + delta).coerceIn(0, buttonFocuses.lastIndex)
        if (next != selectedIndex) {
            selectedIndex = next
            buttonFocuses[next].requestFocus()
        }
    }
    fun focusPlay() {
        onInteraction()
        selectedIndex = playIndex
        playFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.02f), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(top = 8.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .width(836.dp)
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> { select(-1); true }
                        Key.DirectionRight -> { select(+1); true }
                        Key.DirectionDown -> {
                            onInteraction()
                            try { progressFocusRequester.requestFocus() }
                            catch (_: IllegalStateException) {}
                            true
                        }
                        Key.DirectionUp -> { onInteraction(); onHideController(); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onPrev != null) {
                ControllerButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_btn_prev),
                    onClick = onPrev,
                    modifier = Modifier.focusRequester(prevFocus),
                )
                Spacer(Modifier.width(12.dp))
            }
            ControllerButton(
                icon = Icons.Default.Replay10,
                contentDescription = stringResource(R.string.player_btn_seek_back),
                onClick = onSeekBack,
                modifier = Modifier.focusRequester(seekBackFocus),
            )
            Spacer(Modifier.width(12.dp))
            ControllerButton(
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.player_btn_pause else R.string.player_btn_play,
                ),
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playFocus),
            )
            Spacer(Modifier.width(12.dp))
            ControllerButton(
                icon = Icons.Default.Forward10,
                contentDescription = stringResource(R.string.player_btn_seek_fwd),
                onClick = onSeekFwd,
                modifier = Modifier.focusRequester(seekFwdFocus),
            )
            if (onNext != null) {
                Spacer(Modifier.width(12.dp))
                ControllerButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_btn_next),
                    onClick = onNext,
                    modifier = Modifier.focusRequester(nextFocus),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box {
                ControllerButton(
                    icon = Icons.Default.Speed,
                    contentDescription = stringResource(R.string.player_btn_speed),
                    onClick = { onOpenPopover(PlayerPopover.Speed) },
                    modifier = Modifier.focusRequester(speedFocus),
                )
                if (openPopover == PlayerPopover.Speed) {
                    PlayerPopoverMenuPopup(
                        items = speeds,
                        current = speed,
                        label = ::formatSpeed,
                        onSelect = onSpeedChange,
                        onDismiss = onDismissPopover,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box {
                ControllerButton(
                    icon = Icons.Default.AspectRatio,
                    contentDescription = stringResource(R.string.player_btn_aspect),
                    onClick = { onOpenPopover(PlayerPopover.Aspect) },
                    modifier = Modifier.focusRequester(aspectFocus),
                )
                if (openPopover == PlayerPopover.Aspect) {
                    val originalLabel = stringResource(R.string.player_aspect_original)
                    PlayerPopoverMenuPopup(
                        items = listOf(null) + VideoAspect.entries,
                        current = aspect,
                        label = { it?.label ?: originalLabel },
                        onSelect = onAspectChange,
                        onDismiss = onDismissPopover,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ControllerButton(
                icon = Icons.Default.Info,
                contentDescription = stringResource(R.string.player_btn_info),
                onClick = onInfo,
                modifier = Modifier.focusRequester(infoFocus),
            )
        }
        Seeker(
            modifier = Modifier.width(836.dp),
            positionMs = nowPos,
            durationMs = nowDur,
            bufferedMs = nowBuffered,
            focusRequester = progressFocusRequester,
            onProgressFocusChange = onProgressFocusChange,
            onSeekBack = onSeekBack,
            onSeekFwd = onSeekFwd,
            onMoveUp = { focusPlay() },
            onMoveDown = {
                onInteraction()
                try { stripFocus.requestFocus() } catch (_: IllegalStateException) {}
            },
            onHide = onHideController,
            onInteraction = onInteraction,
        )
        VideoStrip(
            videos = videos,
            currentIndex = currentIndex,
            fileStates = fileStates,
            ensureThumb = ensureThumb,
            focusRequester = stripFocus,
            onSelect = onSelectVideo,
            onInteraction = onInteraction,
            onMoveUp = {
                onInteraction()
                try { progressFocusRequester.requestFocus() } catch (_: IllegalStateException) {}
            },
            onHide = { onInteraction(); onHideController() },
            modifier = Modifier.width(836.dp),
        )
    }
}

@Composable
private fun Seeker(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    focusRequester: FocusRequester,
    onProgressFocusChange: (Boolean) -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedPct = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        onProgressFocusChange(isFocused)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMs(positionMs),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(SeekTimeSlotWidth),
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(SeekBarWidth)
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .focusRequester(focusRequester)
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft -> { onInteraction(); onSeekBack(); true }
                            Key.DirectionRight -> { onInteraction(); onSeekFwd(); true }
                            Key.DirectionUp -> { onInteraction(); onMoveUp(); true }
                            Key.DirectionDown -> { onInteraction(); onMoveDown(); true }
                            Key.DirectionCenter, Key.Enter -> { onInteraction(); true }
                            else -> false
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedPct.coerceAtLeast(pct))
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct)
                            .height(5.dp)
                            .background(Color.White, RoundedCornerShape(2.dp)),
                    )
                }
            }
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .widthIn(min = SeekHandleSize)
                        .fillMaxWidth(pct)
                        .height(SeekHandleSize),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(SeekHandleSize)
                            .background(MaterialTheme.colorScheme.border, CircleShape)
                            .border(SeekHandleBorder, Color.White, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMs(durationMs),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(SeekTimeSlotWidth),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun VideoStrip(
    videos: List<MediaItem>,
    currentIndex: Int,
    fileStates: Map<Int, FileDownloadState>,
    ensureThumb: (Int) -> Unit,
    focusRequester: FocusRequester,
    onSelect: (Int) -> Unit,
    onInteraction: () -> Unit,
    onMoveUp: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    val listState = rememberLazyListState()
    var selected by remember(currentIndex) {
        mutableIntStateOf(currentIndex.coerceIn(0, videos.lastIndex))
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex in videos.indices) runCatching { listState.scrollToItem(currentIndex) }
    }
    LaunchedEffect(selected) {
        if (selected in videos.indices) runCatching { listState.animateScrollToItem(selected) }
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .height(90.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        onInteraction()
                        selected = (selected - 1).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionRight -> {
                        onInteraction()
                        selected = (selected + 1).coerceAtMost(videos.lastIndex)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onInteraction()
                        if (selected != currentIndex) onSelect(selected)
                        true
                    }
                    Key.DirectionUp -> { onInteraction(); onMoveUp(); true }
                    Key.DirectionDown -> { onInteraction(); onHide(); true }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(videos) { i, item ->
            VideoStripCell(
                item = item,
                highlighted = i == selected,
                fileStates = fileStates,
                ensureThumb = ensureThumb,
            )
        }
    }
}

@Composable
private fun VideoStripCell(
    item: MediaItem,
    highlighted: Boolean,
    fileStates: Map<Int, FileDownloadState>,
    ensureThumb: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val thumbId = item.thumbnailFileId ?: 0
    val path = (fileStates[thumbId] as? FileDownloadState.Local)?.path
        ?: item.thumbnailLocalPath
        ?: item.localPath
    LaunchedEffect(thumbId, path) {
        if (path == null && thumbId != 0) ensureThumb(thumbId)
    }
    Box(
        modifier = Modifier
            .size(width = 118.dp, height = 90.dp)
            .border(
                3.dp,
                if (highlighted) Color.White else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .padding(3.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(HomeSpec.SurfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        item.minithumbnail?.let { mini ->
            AsyncImage(
                model = mini,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (path != null) {
            AsyncImage(
                model = remember(path) {
                    ImageRequest.Builder(ctx).data(File(path)).crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ControllerButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    }
    val iconTint = if (isFocused) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(bg, CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Player popovers ───────────────────────────────────────────────────────
private val PlayerPopoverWidth = 216.dp
private val PlayerPopoverRadius = 16.dp
private val PlayerPopoverRowHeight = 40.dp
private val PlayerPopoverListPaddingV = 8.dp
private val PlayerPopoverListPaddingH = 12.dp

private enum class PlayerPopover { Speed, Aspect }

private enum class VideoAspect(val label: String, val ratio: Float) {
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    Wide("16:9", 16f / 9f),
}

private fun formatSpeed(speed: Float): String = "${speed}x"

private fun formatDegrees(degrees: Int): String = "$degrees°"

@Composable
private fun <T> PlayerPopoverMenuPopup(
    items: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val positionProvider = remember(gapPx) { PlayerPopoverPositionProvider(gapPx) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        PlayerPopoverMenu(
            items = items,
            current = current,
            label = label,
            onSelect = { item ->
                onSelect(item)
                onDismiss()
            },
        )
    }
}

private class PlayerPopoverPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun <T> PlayerPopoverMenu(
    items: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val itemFocuses = remember(items) { items.map { FocusRequester() } }
    val selectedIndex = items.indexOf(current).coerceAtLeast(0)
    var focusedIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        repeat(5) {
            if (focusedIndex != selectedIndex) return@LaunchedEffect
            try { itemFocuses[selectedIndex].requestFocus() } catch (_: IllegalStateException) {}
            delay(40L)
        }
    }

    fun moveFocus(delta: Int) {
        focusedIndex = (focusedIndex + delta).coerceIn(0, itemFocuses.lastIndex)
        try { itemFocuses[focusedIndex].requestFocus() } catch (_: IllegalStateException) {}
    }

    Column(
        modifier = Modifier
            .dpadNavigationSounds()
            .width(PlayerPopoverWidth)
            .shadow(8.dp, RoundedCornerShape(PlayerPopoverRadius))
            .background(
                MaterialTheme.colorScheme.background,
                RoundedCornerShape(PlayerPopoverRadius),
            )
            .padding(
                vertical = PlayerPopoverListPaddingV,
                horizontal = PlayerPopoverListPaddingH,
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { moveFocus(-1); true }
                    Key.DirectionDown -> { moveFocus(+1); true }
                    Key.DirectionCenter, Key.Enter -> {
                        onSelect(items[focusedIndex])
                        true
                    }
                    else -> false
                }
            },
    ) {
        items.forEachIndexed { index, item ->
            PlayerPopoverItem(
                label = label(item),
                selected = index == selectedIndex,
                focused = index == focusedIndex,
                focusRequester = itemFocuses[index],
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun PlayerPopoverItem(
    label: String,
    selected: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fill = if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val fg = if (focused) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayerPopoverRowHeight)
            .focusRequester(focusRequester)
            .graphicsLayer {
                val scale = if (focused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun TryAgainButton(onRetry: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .focusRequester(focus)
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) Color.White else HomeSpec.SurfaceContainer)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRetry,
            )
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
            .padding(horizontal = 32.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.player_error_retry),
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) HomeSpec.InverseOnSurface else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun describeVideo(tracks: Tracks): String? {
    val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return null
    val index = (0 until group.length).firstOrNull { group.isTrackSupported(it) } ?: 0
    val format = group.getTrackFormat(index)
    val name = when (format.sampleMimeType) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—"
    }
    val bitrate = format.bitrate.takeIf { it > 0 }
        ?.let { " · %.1f Mbps".format(it / 1_000_000f) }
        .orEmpty()
    return name + bitrate
}

private fun describeAudio(tracks: Tracks): String? {
    val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO } ?: return null
    val index = (0 until group.length).firstOrNull { group.isTrackSupported(it) } ?: 0
    val format = group.getTrackFormat(index)
    val name = when (format.sampleMimeType) {
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/vnd.dts" -> "DTS"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—"
    }
    val channels = format.channelCount.takeIf { it > 0 }?.let { " · ${it}ch" }.orEmpty()
    return name + channels
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun ColumnScope.MediaInfoBody(
    item: MediaItem,
    fileInfo: TdApi.File?,
    playedDurationMs: Long,
    videoCodec: String?,
    audioCodec: String?,
) {
    val sizeBytes = fileInfo?.size?.toLong()?.takeIf { it > 0 }
        ?: fileInfo?.expectedSize?.toLong()?.takeIf { it > 0 }
    val durationMs = if (item.duration > 0) item.duration * 1000L else playedDurationMs
    InfoLine("时长:", if (durationMs > 0) formatMs(durationMs) else "—", RightSheetInk)
    InfoLine(
        "分辨率:",
        if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else "—",
        RightSheetInk,
    )
    InfoLine("大小:", sizeBytes?.let { formatBytes(it) } ?: "—", RightSheetInk)
    InfoLine("编码:", videoCodec ?: "—", RightSheetInk)
    InfoLine("音频:", audioCodec ?: "—", RightSheetInk)
    Box(Modifier.fillMaxWidth().height(1.dp).background(RightSheetRule))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = item.caption.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = RightSheetInk,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, ink: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(60.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = value, color = ink, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
