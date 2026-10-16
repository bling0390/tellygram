@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.home.HomeSpec
import tv.telegram.ui.home.formatDuration
import tv.telegram.ui.player.buildAudioPlayer
import tv.telegram.ui.player.hasNextIn
import tv.telegram.ui.player.hasPreviousIn
import tv.telegram.ui.player.neighbourIn
import tv.telegram.ui.player.walkIndices

/** How often the transport line refreshes while the screen is up. */
private const val PositionPollMs = 500L

/**
 * The audio player (decision B, 2026-10-16): a track gets its own screen instead of the
 * video-shaped one.
 *
 * Decided shape: transport is play/pause, +/-10s, previous/next and a linear progress line — no
 * repeat, no shuffle, no queue counter; the queue is the audio the wall has loaded, so "next"
 * stops where the loaded list stops. Focus lands on play/pause, Back stops playback and hands
 * focus back to the card. A finished track drops its local copy, like video does.
 *
 * The circular spectrum goes in the empty upper area; it is its own step (the PCM tap), so this
 * screen is deliberately the plain version first.
 */
@Composable
fun AudioPlayerScreen(
    viewModel: MainViewModel,
    index: Int,
    onClose: () -> Unit,
    onNavigateTo: (Int) -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()

    if (index !in items.indices) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val current = items[index]

    // The queue is the audio the wall has loaded (decision 5 / B2). loadMore grows the list and
    // this rebuilds with it; nothing is prefetched (B3).
    val queue = remember(items) { walkIndices(items, MediaType.Audio) }
    val hasPrev = hasPreviousIn(queue, index)
    val hasNext = hasNextIn(queue, index)
    val step: (Int) -> Unit = { delta -> neighbourIn(queue, index, delta)?.let(onNavigateTo) }

    val fileState = fileStates[current.fileId]
    val path = (fileState as? FileDownloadState.Local)?.path
    val failure = fileState as? FileDownloadState.Failed

    var retryTick by remember(current.fileId) { mutableIntStateOf(0) }
    LaunchedEffect(current.fileId, retryTick) {
        if (path == null) viewModel.ensureMediaFile(current.fileId, priority = 32)
    }

    // The spectrum: the processor sits in the player's audio chain, the analyser runs the FFT on a
    // scope of its own (never on the playback thread), and the ring draws what it measured.
    val analyserScope = rememberCoroutineScope()
    val analyser = remember { SpectrumAnalyzer(analyserScope) }
    LaunchedEffect(analyser) { analyser.start() }
    val spectrum = remember { SpectrumAudioProcessor(onWindow = { window -> analyser.offer(window) }) }
    DisposableEffect(spectrum, analyser) {
        // The band layout follows the stream's own rate, which the processor learns when it is
        // configured. A path whose PCM we cannot read simply never reports (decision E2).
        spectrum.onFormat = { rate -> analyser.adoptSampleRate(rate) }
        onDispose { spectrum.onFormat = null }
    }
    val levels by analyser.bands.collectAsStateWithLifecycle()

    val exo = remember(current.fileId) { buildAudioPlayer(context, viewModel.fileRepo, spectrum) }

    // The listener outlives recompositions (it is keyed on the player), so it reads the queue
    // through these: otherwise a track that ends after loadMore grew the queue would decide
    // "no next" from a stale value.
    val queueHasNext by rememberUpdatedState(hasNext)
    val stepFromQueue by rememberUpdatedState(step)

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 播完即删 (decision 6): the track is done, so the local copy goes — the
                    // remote reference stays in TDLib and a replay re-fetches it.
                    viewModel.fileRepo.deleteLocalFile(current.fileId)
                    if (queueHasNext) stepFromQueue(1)
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Play as soon as the file is local. A failed download surfaces as the retry row below.
    LaunchedEffect(current.fileId, path, retryTick) {
        if (path == null) return@LaunchedEffect
        exo.setMediaItem(ExoMediaItem.fromUri("file://$path"))
        exo.prepare()
        exo.play()
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember(current.fileId) { mutableLongStateOf(current.duration * 1_000L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(exo, current.fileId) {
        while (true) {
            positionMs = exo.currentPosition.coerceAtLeast(0L)
            durationMs = exo.duration.takeIf { it > 0L } ?: (current.duration * 1_000L)
            playing = exo.isPlaying
            delay(PositionPollMs)
        }
    }

    val playPauseFocus = remember { FocusRequester() }
    LaunchedEffect(current.fileId) { runCatching { playPauseFocus.requestFocus() } }

    val exit = {
        exo.stop()
        viewModel.setPlayerReturnFocus(current.messageId)
        onClose()
    }
    // Back stops the track and returns to the card it came from (decision F2).
    BackRegistration(BackPriority.PLAYER) { exit() }

    val artist = audioArtist(current)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeSpec.Background)
            .padding(horizontal = 48.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The spectrum took the cover's place (decisions D1-D3). It only ever draws what the
            // tap measured, so silence, a paused track and an unreadable stream all rest as the
            // ring rather than animating a lie.
            SpectrumRing(
                levels = levels,
                modifier = Modifier
                    .size(220.dp)
                    .testTag("audio-spectrum"),
            )
            Text(
                text = audioTitle(current),
                style = MaterialTheme.typography.headlineMedium,
                color = HomeSpec.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("audio-title"),
            )
            if (artist != null) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = HomeSpec.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            current.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeSpec.OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Linear line, per decision D4.
            LinearProgressIndicator(
                progress = { progressFraction(positionMs, durationMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("audio-progress"),
                color = HomeSpec.Primary,
                trackColor = HomeSpec.SurfaceContainer,
            )
            Text(
                text = "${formatDuration((positionMs / 1_000L).toInt())} / " +
                    formatDuration((durationMs / 1_000L).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = HomeSpec.OnSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TransportButton(
                    label = "⏮",
                    tag = "audio-prev",
                    enabled = hasPrev,
                    onClick = { step(-1) },
                )
                TransportButton(
                    label = "−10s",
                    tag = "audio-back10",
                    onClick = {
                        exo.seekTo(seekTarget(exo.currentPosition, -10, durationMs))
                    },
                )
                TransportButton(
                    label = if (playing) "⏸" else "▶",
                    tag = "audio-play-pause",
                    focusRequester = playPauseFocus,
                    onClick = { if (exo.isPlaying) exo.pause() else exo.play() },
                )
                TransportButton(
                    label = "+10s",
                    tag = "audio-fwd10",
                    onClick = {
                        exo.seekTo(seekTarget(exo.currentPosition, 10, durationMs))
                    },
                )
                TransportButton(
                    label = "⏭",
                    tag = "audio-next",
                    enabled = hasNext,
                    onClick = { step(1) },
                )
            }

            if (failure != null) {
                // Decision E1: the retry lives on this screen instead of behind the card's OK.
                TransportButton(
                    label = "重试 · ${failure.reason}",
                    tag = "audio-retry",
                    onClick = { retryTick++ },
                )
            }
        }
    }
}

/**
 * One transport control. A plain focusable row rather than a TV Button: the design's controls are
 * text-sized, and the focus ring is all the chrome they need.
 */
@Composable
private fun TransportButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> HomeSpec.White
        else -> HomeSpec.SurfaceContainer
    }
    val content = when {
        focused -> HomeSpec.InverseOnSurface
        enabled -> HomeSpec.OnSurface
        else -> HomeSpec.ChipOutline
    }

    Box(
        modifier = Modifier
            .testTag(tag)
            .height(44.dp)
            .background(fill, RoundedCornerShape(HomeSpec.Corner))
            .let { if (focused) it.border(2.dp, HomeSpec.Primary, RoundedCornerShape(HomeSpec.Corner)) else it }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (!enabled) {
                    false
                } else if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
        )
    }
}
