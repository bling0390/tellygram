package tv.telegram.ui.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class PcmWindows(private val size: Int = SpectrumFftSize) {
    private val window = FloatArray(size)
    private var filled = 0

    fun feed(samples: FloatArray, count: Int, onWindow: (FloatArray) -> Unit) {
        var read = 0
        while (read < count) {
            val take = minOf(count - read, size - filled)
            System.arraycopy(samples, read, window, filled, take)
            read += take
            filled += take
            if (filled == size) {
                onWindow(window.copyOf())
                filled = 0
            }
        }
    }

    fun clear() {
        filled = 0
    }
}

internal class SpectrumAudioProcessor(
    private val onWindow: (FloatArray) -> Unit,
) : BaseAudioProcessor() {

    private val windows = PcmWindows()
    private var channels = 1
    private var readable = false
    private var mono = FloatArray(0)

    var onFormat: ((Int) -> Unit)? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        readable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        onFormat?.invoke(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (readable) tap(inputBuffer)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun tap(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val start = inputBuffer.position()
        val frames = inputBuffer.remaining() / (2 * channels)
        if (frames <= 0) return
        if (mono.size < frames) mono = FloatArray(frames)

        var i = 0
        while (i < frames) {
            var sum = 0f
            for (c in 0 until channels) sum += inputBuffer.getShort() / 32768f
            mono[i++] = sum / channels
        }
        inputBuffer.position(start)

        windows.feed(mono, frames, onWindow)
    }

    override fun onFlush() {
        windows.clear()
    }

    override fun onReset() {
        windows.clear()
    }
}

internal class SpectrumAnalyzer(private val scope: CoroutineScope) {

    private val window = hannWindow(SpectrumFftSize)
    private val incoming = Channel<FloatArray>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _bands = MutableStateFlow(FloatArray(SpectrumBands))
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    @Volatile
    private var edges = bandEdges(44_100)

    private var previous = FloatArray(SpectrumBands)

    fun offer(window: FloatArray) {
        incoming.trySend(window)
    }

    fun adoptSampleRate(sampleRate: Int) {
        if (sampleRate > 0) edges = bandEdges(sampleRate)
    }

    fun idle() {
        previous = FloatArray(SpectrumBands)
        _bands.value = previous
    }

    fun start() {
        scope.launch {
            for (window in incoming) {
                previous = spectrumFrame(window, edges, previous, window)
                _bands.value = previous
            }
        }
    }
}

internal class SpectrumRenderersFactory(
    context: Context,
    private val spectrum: AudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(spectrum))
        .build()
}
