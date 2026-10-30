package tv.telegram.ui.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import tv.telegram.td.TdDataSourceFactory
import tv.telegram.td.TdFileRepository
import tv.telegram.ui.audio.SpectrumRenderersFactory

/**
 * Builds a player with exactly what our screens need — the decoder fallback TV boxes require,
 * the audio focus ExoPlayer takes on our behalf, and the TdDataSource that plays straight out
 * of TDLib.
 *
 * Each screen builds and releases its own instance: playback never outlives its screen (decision
 * 4), so there is no shared instance to leak or to keep in sync. PlayerScreen still builds its
 * player inline; moving it here is its own step (decision A2), verified on a device on its own.
 */
internal fun buildAudioPlayer(
    context: Context,
    fileRepo: TdFileRepository,
    /**
     * Optional audio processor for the spectrum ring. Null builds a plain player, so a caller
     * that does not want the ring pays nothing for it.
     */
    spectrum: AudioProcessor? = null,
): ExoPlayer = ExoPlayer.Builder(context)
    .setRenderersFactory(
        (
            if (spectrum == null) {
                DefaultRenderersFactory(context)
            } else {
                SpectrumRenderersFactory(context, spectrum)
            }
            ).setEnableDecoderFallback(true),
    )
    .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(context).setDataSourceFactory(
            TdDataSourceFactory(fileRepo = fileRepo, fallback = DefaultDataSource.Factory(context)),
        ),
    )
    .build()
