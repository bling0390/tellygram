package tv.telegram.ui.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import tv.telegram.td.TdDataSourceFactory
import tv.telegram.td.TdFileRepository

/**
 * How a correctly configured player is built (decision A1, 2026-10-16): the one place that knows
 * what our players need — the decoder fallback TV boxes require, the audio focus ExoPlayer takes
 * on our behalf, and the TdDataSource that plays straight out of TDLib.
 *
 * Each screen builds and releases its own instance: playback never outlives its screen (decision
 * 4), so there is no shared instance to leak or to keep in sync. PlayerScreen still builds its
 * player inline; moving it here is its own step (decision A2), verified on a device on its own.
 */
internal fun buildAudioPlayer(
    context: Context,
    fileRepo: TdFileRepository,
): ExoPlayer = ExoPlayer.Builder(context)
    .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
    .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(context).setDataSourceFactory(
            TdDataSourceFactory(fileRepo = fileRepo, fallback = DefaultDataSource.Factory(context)),
        ),
    )
    .build()
