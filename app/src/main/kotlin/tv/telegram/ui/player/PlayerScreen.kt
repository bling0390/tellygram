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

// here because tv-material3 exposes no slot for it in the colour scheme.
// Playhead handle geometry: a 16dp disc with a 4dp white ring, filled with the
// theme's outline colour (JetStream dark/outline = #8E9099). Drawn only while
// the seek bar has focus — see Seeker().
private val SeekHandleSize = 16.dp
private val SeekHandleBorder = 4.dp

// 8dp gap, the bar, 8dp gap, 48dp time slot. Pinning the bar to the leftover
// 732dp (and both time slots to 48dp) keeps the track from re-measuring while
// the time strings grow — 0:00 -> 1:08:24 used to stretch it mid-playback.
private val SeekBarWidth = 732.dp
private val SeekTimeSlotWidth = 48.dp

// How long PlayerScreen waits before letting the heavy player composition run.
// Covers the page-transition window so ExoPlayer construction never lands in the
// middle of the enter/exit animation.
private const val PLAYER_INIT_DELAY_MS = 250L

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    index: Int,
    onClose: () -> Unit,
    onNavigateTo: (Int) -> Unit,
    /**
     * Items already filtered to the types this screen plays. Null means the chat's loaded media.
     */
    items: List<MediaItem>? = null,
) {
    val context = LocalContext.current
    val ownItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    // A handed-over list wins: prev/next then stays inside what the user opened from.
    val mediaItems = items ?: ownItems
    val speed by viewModel.playerPlaybackSpeed.collectAsStateWithLifecycle()
    val resumeMap by viewModel.playerResumePositions.collectAsStateWithLifecycle()
    val mediaExhausted by viewModel.mediaExhausted.collectAsStateWithLifecycle()

    // Cross-page continuous playback: when playing near the end of the
    // loaded media list, prefetch the next search page so the playlist
    // keeps growing past the current data boundary.
    LaunchedEffect(index, mediaItems.size, mediaExhausted) {
        // Only the chat's own list pages; a handed-over list is already complete.
        if (items == null && !mediaExhausted && index >= mediaItems.size - 8) {
            viewModel.loadMoreMedia()
        }
    }

    if (index !in mediaItems.indices) {

        LaunchedEffect(Unit) { onClose() }
        return
    }

    val current = mediaItems[index]

    // Record the return target when the player opens, not only when our Back action runs
    // and the grid then had no card to focus on return. Keyed on the item so walking to the
    // next video updates it.
    LaunchedEffect(current.messageId) {
        android.util.Log.i("HomeBack", "player open: hint=${current.messageId}")
        viewModel.setPlayerReturnFocus(current.messageId)
    }

    // Exit hook: record the messageId being played so the chats screen can
    // hand focus back to this exact media card on return (instead of the
    // sidebar's "Archived Chats" row). Both player-exit paths (Back, and
    // playback ENDED with no next video) go through here.
    val closePlayer = {
        viewModel.setPlayerReturnFocus(current.messageId)
        onClose()
    }

    // The videos this screen walks with prev/next. The type is a parameter because the audio
    val videoIndices = remember(mediaItems) { walkIndices(mediaItems, MediaType.Video) }
    val hasPrevVideo = hasPreviousIn(videoIndices, index)
    val hasNextVideo = hasNextIn(videoIndices, index)
    // The strip walks the same list the prev/next buttons do.
    val videos = remember(mediaItems, videoIndices) { videoIndices.mapNotNull { mediaItems.getOrNull(it) } }
    val videoIndex = videoIndices.indexOf(index)

    fun neighborVideo(delta: Int): Int? = neighbourIn(videoIndices, index, delta)

    // Reactive file state: collect the repo's state map so this screen
    // recomposes when a download completes (path appears) or fails. The old
    // one-shot stateFor() read never recomposed, so a file that had to be
    // currentPath == null forever → the prepare effect never ran → infinite
    // spinner / black screen.
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val currentFileState = fileStates[current.fileId]
    val currentPath = (currentFileState as? FileDownloadState.Local)?.path
    // Progressive playback: only videos the server marked supportsStreaming
    // are streamed (moov/faststart guaranteed) — and only when the file is
    // NOT already fully downloaded. A fully-local file must go through the
    // plain file:// path: TdDataSource.awaitStreamPath waits on updateFile
    // events, but a completed download never fires one, so streaming an
    // already-downloaded file would time out and fail to play.
    // Flipped when the stream cannot be parsed: play a complete local copy instead of the
    // progressive td:// source (covers moov-at-end MP4s and re-opens from a non-zero offset).
    var forceLocalPlayback by remember(current.fileId) { mutableStateOf(false) }
    // Every video streams now, whether Telegram marked it streamable or not: the tail probe
    // fetches an end-of-file 'moov', so a non-faststart MP4 no longer has to be downloaded in
    // full before it can start. Files that still fail to parse fall back to a complete local
    // playback on device, so only Telegram's streamable videos use td:// again. The tail
    // prefetch stays in the tree, disabled, until it can be verified on hardware.
    val canStream = current.type == MediaType.Video && current.supportsStreaming &&
        currentPath == null && !forceLocalPlayback
    // retryTick: bumped by the error overlay's retry action so both effects
    // below re-run (re-trigger download / re-prepare playback).
    var retryTick by remember(current.fileId) { mutableIntStateOf(0) }
    LaunchedEffect(current.fileId, retryTick) {
        if (canStream) {
            viewModel.fileRepo.startStreaming(current.fileId, priority = 32)
        } else if (currentPath == null) {
            viewModel.ensureMediaFile(current.fileId, priority = 32)
        }
    }

    // Cancel the previous file's download when switching to another video
    // or leaving the player. Without this, TDLib keeps downloading the
    // abandoned streaming file in the background (priority 32, same as the
    // current video) and competes for bandwidth.
    DisposableEffect(current.fileId) {
        onDispose {
            viewModel.fileRepo.cancelDownload(current.fileId)
        }
    }

    // Everything below (ExoPlayer build, PlayerView inflation, renderer and
    // MediaCodec warm-up) is main-thread work, and it used to run while the page
    // transition was still animating — which dropped frames when opening a video
    // from the media grid. Compose a plain shell first and let the player in
    // once that window has passed. No poster here any more: artwork flashing up
    var playerReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PLAYER_INIT_DELAY_MS)
        playerReady = true
    }
    if (!playerReady) {
        // backdrop for the ~250ms before the player is built.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val exo = remember(current.fileId) {
        ExoPlayer.Builder(context)
            // Let MediaCodec fall back to the platform's software decoder when
            // the hardware one refuses the format. TV boxes reject anything
            // past 8-bit 4:2:0, so an H.264 High 4:4:4 clip fails init outright
            // decoder accepts; slow-but-playing beats a hard failure when it
            // does not.
            .setRenderersFactory(
                DefaultRenderersFactory(context).setEnableDecoderFallback(true),
            )
            // Let ExoPlayer take and handle audio focus: another app starting
            // playback pauses us instead of the two mixing, and we duck/yield
            // the same way. Nothing in the app asked for focus before this.
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
        // (TDLib keeps the remote reference; streaming videos re-download
        // almost instantly on next play, non-streaming ones re-download on
        // demand). Prevents finished videos from piling up on tiny TV
        // storage.
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

    // current file's local copy (in addition to the ENDED hook above, which
    // only fires when a video plays to completion). Skipping away mid-video
    // otherwise leaves the half-downloaded file on disk forever.
    DisposableEffect(Unit) {
        val fid = current.fileId
        onDispose {
            viewModel.fileRepo.deleteLocalFile(fid)
        }
    }

    var mediaPrepared by remember(current.fileId) { mutableStateOf(false) }
    // True once the first video frame has actually rendered. The full-screen
    // spinner stays until this fires (not merely until prepare() returns), so
    // streaming videos don't flash black between prepare() and the first
    // frame. MediaPane's hover preview already does this (previewReady).
    var firstFrameRendered by remember(current.fileId) { mutableStateOf(false) }
    // True while ExoPlayer is in STATE_BUFFERING during playback. Drives a
    // lightweight spinner on top of the video — only reachable after the
    // first frame (initial buffering is covered by the full-screen spinner).
    var isBuffering by remember(current.fileId) { mutableStateOf(false) }
    // Keyed on currentPath too: when a non-streaming video needs a
    // (re)download, the path only appears AFTER the download completes —
    // keying on fileId alone meant the effect ran once with path == null and
    // never re-ran, leaving the player stuck on the spinner. retryTick lets
    // the error overlay re-run the whole prepare after a failure.
    LaunchedEffect(current.fileId, currentPath, retryTick) {
        if (mediaPrepared) return@LaunchedEffect
        if (canStream) {
            // td:// URI — TdDataSource blocks inside open() until TDLib has
            // assigned a local path, then streams from the growing file.
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

    // Playback error surfaced to the UI — previously any error left the
    // player silently black (there was no onPlayerError listener at all).
    var playerError by remember(current.fileId) { mutableStateOf<String?>(null) }
    // True when the failure is the device refusing the format rather than a
    var playerErrorUnsupportedCodec by remember(current.fileId) { mutableStateOf(false) }
    // One automatic recovery for a stream ExoPlayer cannot recognise — the usual cause is a
    // stale "completed" download whose file has no bytes left. Restarting the download from 0
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

    // Live play state: mirror playWhenReady (user intent) into a State so the
    // play/pause icon flips immediately on toggle. Using playWhenReady (not
    // isPlaying) means seeking doesn't flicker the button — isPlaying goes
    // false during BUFFERING after a seek, playWhenReady doesn't.
    var nowPlaying by remember(current.fileId) { mutableStateOf(exo.playWhenReady) }
    // Codec/bitrate for the info drawer: TDLib never carries them, the player does once
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
                    // How many bytes are actually on disk decides the recovery: with data
                    // present the stream is fine but the extractor got a non-zero start (or
                    // the MP4 keeps its moov at the end) — play a complete local copy. With
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
                // A decoder that refused the format is a device capability
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
    // Info button focus requester — hoisted here so the drawer-close path
    // can hand focus back to it (the drawer steals focus while open;
    // without this, focus is lost after the drawer closes and the control
    // bar can't be re-focused until it's hidden and re-shown).
    val infoButtonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { focusRequester.requestFocus() }
        catch (_: IllegalStateException) {}
    }

    // Hidden by default: entering the player shows pure video. The page
    // Box keeps focus (left/right seek without revealing the controller);
    // OK / Up / Down reveal the controller with focus on the progress bar.
    var showController by remember { mutableStateOf(false) }
    // Which picker (speed / rotation) is open, if any. Owned here rather than
    // inside PlayerController because the capture-phase Back handler below runs
    // before any child sees the key, so "Back closes the popover first" has to
    // be decided at this level.
    var openPopover by remember { mutableStateOf<PlayerPopover?>(null) }
    // Screen rotation for the video surface: 0 / 90 / 180 / 270 degrees.
    // Portrait videos (very common in TG) play with big black bars on a
    // landscape TV — the rotate button's popover picks the angle. Kept across
    // video switches so a portrait playlist stays rotated.
    var rotation by remember { mutableIntStateOf(0) }
    // Rotation corrects THIS file's shape, so it does not carry over: 90° on a
    // portrait clip is right, but the same 90° on the next landscape video lays
    // it on its side. Keyed on the file id so a recomposition of the same video
    LaunchedEffect(current.fileId) { rotation = 0 }
    // viewing preference, so it survives a video switch while the player is open.
    var aspect by remember { mutableStateOf<VideoAspect?>(null) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var controllerShownBefore by remember { mutableStateOf(false) }
    // Auto-hide applies only while focus sits on the progress bar; focus on
    // the button row keeps the controller up indefinitely.
    var progressFocused by remember { mutableStateOf(false) }
    // Focus handover — keyed ONLY on visibility, so button presses (which
    // bump lastInteractionMs) never re-steal focus from the button row back
    // to the progress bar. Reveal → progress bar (after enter composes);
    // hide → page Box (after fade-out finishes, so vanishing buttons don't
    // eat direction keys during the exit animation).
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
    // Auto-hide: only while focus is on the progress bar, 4s after the last
    // interaction (bump restarts the timer). Focus on buttons → no auto-hide.
    // An open popover pins the controller up while it is showing.
    LaunchedEffect(showController, lastInteractionMs, progressFocused, openPopover) {
        if (showController && progressFocused && openPopover == null) {
            delay(4000L)
            showController = false
        }
    }
    // A popover cannot outlive the controller it hangs off.
    LaunchedEffect(showController) {
        if (!showController) openPopover = null
    }
    val bumpController = {
        showController = true
        lastInteractionMs = System.currentTimeMillis()
    }

    // Media info drawer: queried from TDLib (GetFile) when opened.
    var showInfo by remember { mutableStateOf(false) }
    var fileInfo by remember(current.fileId) { mutableStateOf<TdApi.File?>(null) }
    LaunchedEffect(showInfo, current.fileId) {
        if (showInfo) {
            fileInfo = viewModel.fileRepo.fileInfo(current.fileId)
        }
    }

    // Back hides the controller first; a second Back leaves the player.
    // An open popover first, then the info drawer, then the controller.
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
            // Capture-phase Back: intercept BEFORE the focused button/progress
            // bar can consume it. Priority: info drawer > controller > exit.
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
                    // Hidden mode only: left/right seek. When the controller
                    // is visible these keys belong to the progress bar / button
                    // row (focus system), so don't swallow them here.
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
                    // Reveal the controller; focus lands on the progress bar
                    // (handled by the LaunchedEffect above). Hidden mode only.
                    // With an error showing, OK/Enter retries instead.
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
                    // Physical play keys toggle playback directly, no reveal.
                    // In controller mode they still count as interaction so
                    // the auto-hide timer restarts.
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (exo.isPlaying) exo.pause() else exo.play()
                        if (showController) bumpController()
                        true
                    }
                    // Speed cycle reveals the controller so the new rate shows.
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
        // "avc1.F4001E … NO_EXCEEDS_CAPABILITIES" says the device's decoder
        // cannot handle this profile at all, which deserves a human line rather
        // than the raw codec string.
        val errorMsg = playerError?.let { text ->
            if (playerErrorUnsupportedCodec) {
                stringResource(R.string.player_error_unsupported_codec) + "\n\n" + text
            } else {
                text
            }
        } ?: downloadError?.let { stringResource(R.string.player_download_failed, it) }

        if (errorMsg != null) {
            // controller stays visible underneath and the retry button takes focus.
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
            // Same removal as the shell: no indicator until the PlayerView exists.
            Box(Modifier.fillMaxSize())
        } else {
            // Read the mode in THIS scope, not inside AndroidView's update
            // lambda. The update block runs outside the recompose scope, so a
            // read inside it never registers a dependency: the picker ticked
            // (its read lives in the controller's scope) while the video branch
            // never recomposed and the new mode was never pushed to the view.
            val videoAspect = aspect
            // Classic PlayerView (not the compose PlayerSurface): the compose
            // surface is a bare SurfaceView hookup — it neither applies the
            // video's rotation metadata nor preserves its aspect ratio, so
            // portrait videos (encoded landscape + rotation=90) render
            // stretched full-screen. PlayerView handles both: AspectRatio-
            // FrameLayout + RESIZE_MODE_FIT letterboxes the long edge and
            // black-bars the sides, and it applies unappliedRotationDegrees.
            //
            // User rotation on top: a graphicsLayer turns the whole surface
            // 0/90/180/270° for portrait videos (very common in TG). At
            // 90/270° the surface's aspect flips, so we scale up by the
            // screen's W/H ratio to keep it filling the screen (cover).
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
                        // Inflate the TextureView-configured PlayerView. The
                        // default SurfaceView is composited outside the View
                        // pipeline, so inside this graphicsLayer (user rotation)
                        // it ignores the AspectRatioFrameLayout letterboxing
                        // until a forced relayout — portrait videos render
                        // stretched and only snap correct when the controller
                        // appears. TextureView respects both letterboxing and
                        // rotation immediately.
                        (LayoutInflater.from(ctx).inflate(
                            R.layout.tellygram_player_view, null,
                        ) as PlayerView).apply {
                            useController = false // custom compose controller below
                            // Belt and braces with app:show_buffering="never": our own
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exo
                            // The letterbox around the video matches the app's background
                            setBackgroundColor(HomeSpec.Background.toArgb())
                            // media3's shutter (the panel shown until the first frame) defaults
                            // to BLACK and sits above this background, so buffering read as a
                            setShutterBackgroundColor(HomeSpec.Background.toArgb())
                        }
                    },
                    // Applied on every recomposition of this branch (and this
                    // branch now depends on resizeMode — see videoResizeMode).
                    update = {
                        it.player = exo
                        // Cropping to a ratio means ZOOM inside a box already constrained to
                        // that ratio; "original" letterboxes the whole frame.
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

            // First-frame gate: the spinner OVERLAYS the surface rather than
            // replacing it. The PlayerView must stay composed — if we swap it
            // out for a spinner, ExoPlayer has no surface to render into,
            // onRenderedFirstFrame never fires, and the spinner never clears
            // (the black-screen deadlock). Same overlay pattern as the media
            // grid's hover preview (thumbnail stays under until previewReady).
            if (!firstFrameRendered) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (isBuffering) {
                // Lightweight buffering indicator during playback (after the
                // first frame — never overlaps the initial full-screen spinner).
                // Video keeps rendering underneath; this just signals the
                // remote isn't dead while data catches up.
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
                // The message's own duration can be 0 — animations never carried
                // one, and a video's occasionally arrives as 0 — so hand over
                // what the player knows once the media is prepared.
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

    // Live position/duration. ExoPlayer isn't compose state, so a ticker
    // drives recomposition while the controller is on screen (it stops when
    // AnimatedVisibility removes us from the tree).
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

    // Explicit button-row navigation: one FocusRequester per visible button
    // + selectedIndex. Left/right move between buttons via the row's
    // onKeyEvent; Down returns to the seeker below.
    val prevFocus = remember { FocusRequester() }
    val seekBackFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFwdFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val aspectFocus = remember { FocusRequester() }
    val infoFocus = infoFocusRequester
    // The strip below the seeker: Down from the seeker lands here, Up leaves it.
    val stripFocus = remember { FocusRequester() }

    // Popover focus handover: the Popup window holds focus while a picker is up,
    // so hand the button that opened it back once it closes (the same explicit
    // restore the RightDrawer needs). Guarded on "was open" so the first
    // composition does not steal focus from the seeker.
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
    // Keyed to onPrev/onNext: buttonFocuses is rebuilt when prev/next
    // appear or disappear (first/last video), so the index must rebuild
    // with it or it can point at a button that no longer exists.
    var selectedIndex by remember(onPrev, onNext) { mutableIntStateOf(playIndex) }
    fun select(delta: Int) {
        onInteraction()
        val next = (selectedIndex + delta).coerceIn(0, buttonFocuses.lastIndex)
        if (next != selectedIndex) {
            selectedIndex = next
            buttonFocuses[next].requestFocus()
        }
    }
    // Hand focus to the play button (used when moving up from the seeker).
    fun focusPlay() {
        onInteraction()
        selectedIndex = playIndex
        playFocus.requestFocus()
    }

    // white 2% hairline along its top edge — not the full-frame gradient the
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
        // Actions only: the design's Label column is empty now, the caption
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
                        // Buttons sit above the seeker; Up leaves the controller.
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
            // Design order: replay 10 (left of play), play/pause, forward 10.
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
    // Buffered frontier (what ExoPlayer can play up to without stalling),
    // rendered as a dim layer under the white playhead.
    val bufferedPct = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Report focus state for auto-hide logic.
    LaunchedEffect(isFocused) {
        onProgressFocusChange(isFocused)
    }

    // between; the handle appears only while the bar itself has focus.
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
                            // OK on the seeker: no-op by design (consume it so
                            // it doesn't bubble to the page-level handler).
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
            // Playhead handle: focus affordance only, flush with the played end.
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

/**
 * The video strip under the seeker. Focus moves the highlight only; OK switches the video
 * (which auto-plays and resumes from its saved position). Up returns to the seeker, Down hides
 * the controller.
 */
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
    // Same chain as the photo strip: medium thumbnail first, embedded minithumbnail under it.
    val path = (fileStates[thumbId] as? FileDownloadState.Local)?.path
        ?: item.thumbnailLocalPath
        ?: item.localPath
    LaunchedEffect(thumbId, path) {
        if (path == null && thumbId != 0) ensureThumb(thumbId)
    }
    Box(
        modifier = Modifier
            // 118x90 slot with the 3dp stroke outside the 112x84 picture.
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

    // JetStream circular button: Surface Variant base + On Surface icon, both
    // at the design's 80% node opacity.
    // Focus swaps to the light On Secondary Container fill (#D9E3F8) with an On
    // focused control reads on a 10-foot UI.
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

// Decode TDLib's embedded minithumbnail (raw JPEG bytes — it ships inside the
// message, so nothing is downloaded) into something Compose can draw.
// ── Player popovers ───────────────────────────────────────────────────────
// the design shows for the subtitle picker; the player's pickers reuse it: a
// 216dp column, 16dp radius, background-token fill under the design's elevation
// shadow, 8/12dp list padding and 40dp rows (10/12 padding around the 20dp line
// box). A focused row inverts to the On Surface fill with an Inverse On Surface
// label and grows ~5% (201.6/192 in the design).
private val PlayerPopoverWidth = 216.dp
private val PlayerPopoverRadius = 16.dp
private val PlayerPopoverRowHeight = 40.dp
private val PlayerPopoverListPaddingV = 8.dp
private val PlayerPopoverListPaddingH = 12.dp

// Which picker the controller has open, if any.
private enum class PlayerPopover { Speed, Aspect }

private enum class VideoAspect(val label: String, val ratio: Float) {
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    Wide("16:9", 16f / 9f),
}

// "1.0x" / "1.25x" — the same shape the removed inline speed label used.
private fun formatSpeed(speed: Float): String = "${speed}x"

// "0°" / "90°" — language-neutral, so no new strings are needed.
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
            // Picking closes the popover here rather than at each call site: a
            // call site only owns the state, and the first one that forgot to
            // clear it left a menu sitting over the video with the tick already
            onSelect = { item ->
                onSelect(item)
                onDismiss()
            },
        )
    }
}

// Anchors the popover to the trigger: right-aligned to the button it hangs off
// and sitting above it, clamped into the window so it can never go off-screen.
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
    // Single source of truth for the highlight: the pill follows this index, not
    // the focus system. A Popup window focuses its FIRST focusable child as it
    // opens, so a focus-driven pill painted 1.0x for a frame before the
    // requestFocus below landed on the rate in effect — the "highlight jumps to
    // the selected entry" flicker. Enter/Center is handled here for the same
    // reason: the selection must be whatever the pill shows, wherever real
    // focus happens to sit.
    var focusedIndex by remember { mutableIntStateOf(selectedIndex) }

    // The Popup is a separate window, so the first requestFocus can land before
    // its node is attached — retry a few frames, but stop as soon as the user
    // navigates so a late retry can't yank focus back.
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
            // The popover lives in its own window, so it needs its own copy of
            // the D-pad sounds.
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
            // Trap the D-pad: without this the first Up / last Down walks focus
            // out of the popover into whatever sits behind it.
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
    // The focused look comes from the menu's index state, not from the focus
    // system's own reporting — see SpeedMenu.
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
            // app's labelLarge (its own titleSmall is 12/16 Regular).
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

/** "H.264 · 4.5 Mbps" straight off the prepared track; null until prepare finishes. */
/** "H.264 · 4.5 Mbps" straight off the prepared track; null until prepare finishes. */
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

/** "AAC · 2ch"; the row that explains "picture but no sound". */
/** "AAC · 2ch"; the row that explains "picture but no sound". */
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

/** Sheet content: the five metadata rows, a rule, then the caption (design order). */
/** Sheet content: the five metadata rows, a rule, then the caption (design order). */
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
    // Message metadata first (it is there before playback starts), then what the
    // player measured — otherwise this row shows "—" for anything whose TG
    // duration is 0.
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
    // The caption is the only place the full text is readable; it takes the rest of
    // the 399dp content area and scrolls.
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
