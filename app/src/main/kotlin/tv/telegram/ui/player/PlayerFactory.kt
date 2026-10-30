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

internal fun buildAudioPlayer(
    context: Context,
    fileRepo: TdFileRepository,
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
