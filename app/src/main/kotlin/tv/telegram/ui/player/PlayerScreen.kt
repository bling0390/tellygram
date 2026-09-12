@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.player

import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import tv.telegram.R
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.td.TdDataSource
import tv.telegram.td.TdDataSourceFactory
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.components.RightDrawer
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import org.drinkless.td.libcore.telegram.TdApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val TAG = "PlayerScreen"

// JetStream seeker handle colour (Figma dark-palette "Outline" tone). Lives
// here because tv-material3 exposes no slot for it in the colour scheme.
private val SeekHandleColor = Color(0xFF938F99)

// Figma's handle diameter (14 design px). Drawn only while the seek bar has
// focus — see Seeker().
private val SeekHandleSize = 14.dp

// How long PlayerScreen waits before letting the heavy player composition run.
// Covers the page-transition window so ExoPlayer construction never lands in the
// middle of the enter/exit animation.
private const val PLAYER_INIT_DELAY_MS = 250L

private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.Video -> "Video"
    MediaType.Photo -> "Photo"
    MediaType.Animation -> "Animation"
    MediaType.Unknown -> "Media"
}

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    index: Int,
    onClose: () -> Unit,
    onNavigateTo: (Int) -> Unit,
) {
    val context = LocalContext.current
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val speed by viewModel.playerPlaybackSpeed.collectAsStateWithLifecycle()
    val resumeMap by viewModel.playerResumePositions.collectAsStateWithLifecycle()
    val mediaExhausted by viewModel.mediaExhausted.collectAsStateWithLifecycle()

    // Cross-page continuous playback: when playing near the end of the
    // loaded media list, prefetch the next search page so the playlist
    // keeps growing past the current data boundary.
    LaunchedEffect(index, mediaItems.size, mediaExhausted) {
        if (!mediaExhausted && index >= mediaItems.size - 8) {
            viewModel.loadMoreMedia()
        }
    }

    if (index !in mediaItems.indices) {

        LaunchedEffect(Unit) { onClose() }
        return
    }

    val current = mediaItems[index]

    // JetStream-style info header: title = first caption line (fallback to
    // media type), subtitle = "year • type" (mirrors "2011 • Action/Fantasy").
    val playerTitle = remember(current.messageId, current.caption) {
        current.caption?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            ?: mediaTypeLabel(current.type)
    }
    val playerSubtitle = remember(current.messageId, current.date, current.type) {
        val year = if (current.date > 0) {
            java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(current.date * 1000L))
        } else null
        buildString {
            if (year != null) { append(year); append(" • ") }
            append(mediaTypeLabel(current.type))
        }
    }

    // Exit hook: record the messageId being played so the chats screen can
    // hand focus back to this exact media card on return (instead of the
    // sidebar's "Archived Chats" row). Both player-exit paths (Back, and
    // playback ENDED with no next video) go through here.
    val closePlayer = {
        viewModel.setPlayerReturnFocus(current.messageId)
        onClose()
    }

    val videoIndices = remember(mediaItems) {
        mediaItems.mapIndexedNotNull { i, m -> if (m.type == MediaType.Video) i else null }
    }
    val posInVideos = remember(videoIndices, index) { videoIndices.indexOf(index) }
    val hasPrevVideo = posInVideos > 0
    val hasNextVideo = posInVideos in 0 until videoIndices.lastIndex

    fun neighborVideo(delta: Int): Int? = videoIndices.getOrNull(posInVideos + delta)

    // Reactive file state: collect the repo's state map so this screen
    // recomposes when a download completes (path appears) or fails. The old
    // one-shot stateFor() read never recomposed, so a file that had to be
    // re-downloaded (方案 A deletes the local copy on player exit) kept
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
    val canStream = current.type == MediaType.Video && current.supportsStreaming && currentPath == null
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

    // Poster sources (Figma CinematicBackground). Built before the player so the
    // deferred first composition below has artwork to show: the embedded
    // minithumbnail is available with zero latency, and the downloaded thumbnail
    // is sharper and takes over as soon as its file is Local.
    val posterPath = current.thumbnailFileId?.let {
        (fileStates[it] as? FileDownloadState.Local)?.path
    }
    val posterBitmap by produceState<ImageBitmap?>(null, current.messageId, current.minithumbnail) {
        // Keep the decode off the UI thread — the poster is built inside the
        // navigation window.
        value = withContext(Dispatchers.Default) { decodeMinithumbnail(current.minithumbnail) }
    }

    // Everything below (ExoPlayer build, PlayerView inflation, renderer and
    // MediaCodec warm-up) is main-thread work, and it used to run while the page
    // transition was still animating — which dropped frames when opening a video
    // from the media grid. Compose a poster-only shell first and let the player
    // in once that window has passed.
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
            contentAlignment = Alignment.Center,
        ) {
            PosterLayer(posterPath = posterPath, posterBitmap = posterBitmap, alpha = 1f)
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val exo = remember(current.fileId) {
        ExoPlayer.Builder(context)
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
        // 方案 A：播完即删 — video reaches its end → drop the local copy
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

    // 方案 A：退出播放器即删 — leaving the player screen also drops the
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
    val retryPlayback = {
        playerError = null
        mediaPrepared = false
        firstFrameRendered = false
        isBuffering = false
        retryTick++
    }

    // Live play state: mirror playWhenReady (user intent) into a State so the
    // play/pause icon flips immediately on toggle. Using playWhenReady (not
    // isPlaying) means seeking doesn't flicker the button — isPlaying goes
    // false during BUFFERING after a seek, playWhenReady doesn't.
    var nowPlaying by remember(current.fileId) { mutableStateOf(exo.playWhenReady) }
    LaunchedEffect(exo) {
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                nowPlaying = playWhenReady
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback error for file ${current.fileId}", error)
                playerError = error.errorCodeName + ": " + (error.message ?: "")
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
        })
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
    // Screen rotation for the video surface: 0 / 90 / 180 / 270 degrees.
    // Portrait videos (very common in TG) play with big black bars on a
    // landscape TV — one rotate press turns them full-screen. Kept across
    // video switches so a portrait playlist stays rotated; press the button
    // three more times to cycle back.
    var rotation by remember { mutableIntStateOf(0) }
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
    LaunchedEffect(showController, lastInteractionMs, progressFocused) {
        if (showController && progressFocused) {
            delay(4000L)
            showController = false
        }
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
    // Info drawer takes priority when open.
    BackRegistration(BackPriority.PLAYER) {
        when {
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
        val errorMsg = playerError
            ?: downloadError?.let { stringResource(R.string.player_download_failed, it) }

        // ── CinematicBackground poster (Figma 1117:8996) ────────────────────
        // The design layers a Poster under a Scrim. It fades out on the first
        // rendered frame and comes back while an error is on screen, so
        // failures sit on artwork instead of black. Deliberately NOT rotated
        // with the surface: by the time a user rotates (a deliberate button
        // press) the poster is long faded out.
        LaunchedEffect(current.messageId, current.thumbnailFileId) {
            val thumbId = current.thumbnailFileId ?: return@LaunchedEffect
            if (viewModel.fileStateFor(thumbId) !is FileDownloadState.Local) {
                viewModel.ensureMediaFile(thumbId, priority = 24)
            }
        }
        val posterAlpha by animateFloatAsState(
            targetValue = if (firstFrameRendered && errorMsg == null) 0f else 1f,
            animationSpec = tween(durationMillis = 300),
            label = "posterAlpha",
        )
        if (posterAlpha > 0.01f) {
            PosterLayer(posterPath = posterPath, posterBitmap = posterBitmap, alpha = posterAlpha)
        }

        if (errorMsg != null) {
            // Error overlay — replaces the silent black screen: shows what
            // failed, OK retries (re-download / re-prepare), Back exits.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.player_error_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else if (!mediaPrepared) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
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
                            R.layout.tvgram_player_view, null,
                        ) as PlayerView).apply {
                            useController = false // custom compose controller below
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exo
                        }
                    },
                    update = { it.player = exo },
                    modifier = Modifier.fillMaxSize(),
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
                title = playerTitle,
                subtitle = playerSubtitle,
                positionMs = { exo.currentPosition },
                durationMs = { exo.duration.coerceAtLeast(0L) },
                bufferedMs = { exo.bufferedPosition.coerceAtLeast(0L) },
                isPlaying = { nowPlaying },
                speed = speed,
                progressFocusRequester = progressFocusRequester,
                infoFocusRequester = infoButtonFocus,
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
                onSpeedCycle = {
                    viewModel.cyclePlayerSpeed()
                    bumpController()
                },
                onRotate = {
                    rotation = (rotation + 90) % 360
                    bumpController()
                },
                onPrev = if (hasPrevVideo) {
                    { neighborVideo(-1)?.let { onNavigateTo(it) }; bumpController() }
                } else null,
                onNext = if (hasNextVideo) {
                    { neighborVideo(+1)?.let { onNavigateTo(it) }; bumpController() }
                } else null,
                onInfo = {
                    showInfo = !showInfo
                    bumpController()
                },
            )
        }

        // Media info drawer: right-side slide-in overlay, shown on demand.
        // Shared RightDrawer component — same popup behavior used by the
        // settings drawer.
        RightDrawer(
            visible = showInfo,
            onClose = { showInfo = false },
            restoreFocus = infoButtonFocus,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            MediaInfoDrawer(
                item = current,
                fileInfo = fileInfo,
            )
        }
    }
}

@Composable
private fun PlayerController(
    title: String,
    subtitle: String,
    positionMs: () -> Long,
    durationMs: () -> Long,
    bufferedMs: () -> Long,
    isPlaying: () -> Boolean,
    speed: Float,
    progressFocusRequester: FocusRequester,
    infoFocusRequester: FocusRequester,
    onProgressFocusChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onHideController: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onSpeedCycle: () -> Unit,
    onRotate: () -> Unit,
    onInfo: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
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
    val rotateFocus = remember { FocusRequester() }
    val infoFocus = infoFocusRequester
    val buttonFocuses = remember(onPrev, onNext) {
        buildList {
            if (onPrev != null) add(prevFocus)
            add(seekBackFocus)
            add(playFocus)
            add(seekFwdFocus)
            if (onNext != null) add(nextFocus)
            add(speedFocus)
            add(rotateFocus)
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

    // JetStream-style controller: a dark scrim gradient over the poster, a
    // title block on the left, circular action buttons on the right, and a
    // slim seeker (current time + bar + end time) underneath.
    // JetStream (Figma 685:1449): the Scrim is a FULL-FRAME gradient —
    // rgba(0,0,0,0.1) at the top deepening to 0.8 at the bottom — rather than
    // a band behind the block, so the content sits on it exactly like the
    // design. Content is inset 58dp ((960 - 844) / 2) each side and the block
    // is anchored 48px above the frame's bottom edge.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.1f),
                        Color.Black.copy(alpha = 0.8f),
                    ),
                ),
            )
            .padding(start = 58.dp, end = 58.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Figma: align-items flex-end, and the Label column is a fixed 484px
        // inside the 844px content width.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 484.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Figma headline/medium = Inter 400 / 28sp / 36.
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 16sp line (JetStream's "2011 • Action/Fantasy").
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.onKeyEvent { ev ->
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
                ControllerButton(
                    icon = Icons.Default.Speed,
                    contentDescription = stringResource(R.string.player_btn_speed),
                    onClick = onSpeedCycle,
                    modifier = Modifier.focusRequester(speedFocus),
                )
                Spacer(Modifier.width(12.dp))
                ControllerButton(
                    icon = Icons.Default.ScreenRotation,
                    contentDescription = stringResource(R.string.player_btn_rotate),
                    onClick = onRotate,
                    modifier = Modifier.focusRequester(rotateFocus),
                )
                Spacer(Modifier.width(12.dp))
                ControllerButton(
                    icon = Icons.Default.Info,
                    contentDescription = stringResource(R.string.player_btn_info),
                    onClick = onInfo,
                    modifier = Modifier.focusRequester(infoFocus),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Seeker(
            positionMs = nowPos,
            durationMs = nowDur,
            bufferedMs = nowBuffered,
            speed = speed,
            focusRequester = progressFocusRequester,
            onProgressFocusChange = onProgressFocusChange,
            onSeekBack = onSeekBack,
            onSeekFwd = onSeekFwd,
            onMoveUp = { focusPlay() },
            onHide = onHideController,
            onInteraction = onInteraction,
        )
    }
}

@Composable
private fun Seeker(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    focusRequester: FocusRequester,
    onProgressFocusChange: (Boolean) -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onMoveUp: () -> Unit,
    onHide: () -> Unit,
    onInteraction: () -> Unit,
) {
    val pct = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Buffered frontier (what ExoPlayer can play up to without stalling),
    // rendered as a dim layer under the white playhead.
    val bufferedPct = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Figma 685:1449: bar 3dp at rest, thickening to 5dp on focus as the TV
    // focus affordance (the design itself defines no focus state).
    val barHeight = if (isFocused) 5.dp else 3.dp
    // Report focus state for auto-hide logic.
    LaunchedEffect(isFocused) {
        onProgressFocusChange(isFocused)
    }

    // JetStream seeker: current time on the left, end time on the right,
    // slim bar + dot handle in between.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMs(positionMs),
            // Figma: #FFFFFF at 80% opacity.
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(8.dp))
        // Fixed-height wrapper: bar grows inside without shifting the row.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .focusRequester(focusRequester)
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft -> { onInteraction(); onSeekBack(); true }
                            Key.DirectionRight -> { onInteraction(); onSeekFwd(); true }
                            Key.DirectionUp -> { onInteraction(); onMoveUp(); true }
                            Key.DirectionDown -> { onInteraction(); onHide(); true }
                            // OK on the seeker: no-op by design (consume it so
                            // it doesn't bubble to the page-level handler).
                            Key.DirectionCenter, Key.Enter -> { onInteraction(); true }
                            else -> false
                        }
                    },
            ) {
                // Figma 685:1449: track 20% white, buffered 45% white, played
                // solid white — all sharing the bar height and a 2dp radius.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedPct.coerceAtLeast(pct))
                            .height(barHeight)
                            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct)
                            .height(barHeight)
                            .background(Color.White, RoundedCornerShape(2.dp)),
                    )
                }
            }
            // Playhead handle: drawn only while the bar itself has focus — it is
            // a focus affordance, not a permanent marker (the controller's own
            // visibility already gates it, since the seeker only composes inside
            // PlayerController). Figma centres it on the played edge, so it
            // straddles the boundary half and half.
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = SeekHandleSize / 2)
                            .size(SeekHandleSize)
                            .background(SeekHandleColor, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMs(durationMs),
            // Figma: #FFFFFF at 80% opacity.
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${speed}x",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
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

    // JetStream circular button: Surface Variant base + On Surface icon.
    // Focus swaps to the Primary accent so the focused control reads clearly
    // on a 10-foot UI.
    // Figma: Surface Variant at 80% opacity with an On Surface icon at 80%.
    // Focus keeps the Primary accent fully opaque so the focused control still
    // reads clearly on a 10-foot UI.
    val bg = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    }
    val iconTint = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
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
private fun decodeMinithumbnail(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: IllegalArgumentException) {
        null
    }
}

// Full-bleed poster for the player's CinematicBackground: prefers the real
// (downloaded) thumbnail and falls back to the message's embedded minithumbnail.
@Composable
private fun PosterLayer(posterPath: String?, posterBitmap: ImageBitmap?, alpha: Float) {
    val modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { this.alpha = alpha }
    val ctx = LocalContext.current
    // Memoised on the path: a fresh ImageRequest is never equal to the previous
    // one, so Coil restarted the load on every recomposition and the poster
    // blinked.
    val posterRequest = remember(posterPath) {
        posterPath?.let { ImageRequest.Builder(ctx).data(File(it)).build() }
    }
    when {
        posterRequest != null -> AsyncImage(
            model = posterRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        posterBitmap != null -> Image(
            bitmap = posterBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
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
private fun MediaInfoDrawer(
    item: MediaItem,
    fileInfo: TdApi.File?,
) {

    val typeLabel = when (item.type) {
        MediaType.Video -> "Video"
        MediaType.Photo -> "Photo"
        MediaType.Animation -> "Animation/GIF"
        else -> "Unknown"
    }
    val sizeBytes = fileInfo?.size?.toLong()?.takeIf { it > 0 }
        ?: fileInfo?.expectedSize?.toLong()?.takeIf { it > 0 }
    val dateText = if (item.date > 0) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(item.date * 1000L))
    } else {
        "—"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.player_info_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(20.dp))

            InfoRow(stringResource(R.string.player_info_type), typeLabel)
            InfoRow(
                stringResource(R.string.player_info_resolution),
                if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else "—",
            )
            InfoRow(
                stringResource(R.string.player_info_size),
                if (sizeBytes != null) formatBytes(sizeBytes) else "—",
            )
            InfoRow(
                stringResource(R.string.player_info_streaming),
                if (item.supportsStreaming) "Yes" else "No",
            )
            InfoRow(stringResource(R.string.player_info_date), dateText)
            InfoRow(stringResource(R.string.player_info_file_id), item.fileId.toString())

            if (!item.caption.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = item.caption!!,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
