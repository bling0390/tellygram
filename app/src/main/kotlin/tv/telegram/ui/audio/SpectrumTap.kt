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

/**
 * Assembles whole analysis windows out of a stream of mono samples. Whole windows, not a sliding
 * one: the ring refreshes ~43 times a second at 44.1kHz, which is more than the eye needs, and
 * non-overlapping windows keep the audio thread's work to an array copy.
 */
internal class PcmWindows(private val size: Int = SpectrumFftSize) {
    private val window = FloatArray(size)
    private var filled = 0

    /** Adds [count] samples, handing over a fresh window whenever one fills (at most one here). */
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

    /** Drops a partial window: called when the stream is flushed or reset. */
    fun clear() {
        filled = 0
    }
}

/**
 *
 * It sits in the decoder's audio chain, lets every sample through untouched, and hands whole
 * windows of mono PCM to [onWindow]. That callback runs on the PLAYBACK thread, so it must only
 * post the window — analysing it there would stall audio. [SpectrumAnalyzer] is the other half.
 *
 * Anything that is not 16-bit PCM is passed through and simply never analysed: some TV audio
 * paths hand us a format we cannot read, and that must cost the ring, never the sound
 * (decision E2).
 */
internal class SpectrumAudioProcessor(
    private val onWindow: (FloatArray) -> Unit,
) : BaseAudioProcessor() {

    private val windows = PcmWindows()
    private var channels = 1
    private var readable = false
    private var mono = FloatArray(0)

    /** Told the stream's sample rate once it is known, so the band layout can follow it. */
    var onFormat: ((Int) -> Unit)? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        readable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        onFormat?.invoke(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (readable) tap(inputBuffer)
        // Pass-through: BaseAudioProcessor publishes whatever replaceOutputBuffer hands back, so
        // the sink still receives exactly what the decoder produced.
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
        // Leave the caller's buffer exactly as we found it.
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

/**
 * The analysis half: it takes windows off the playback thread and turns them into the ring's band
 * levels on [scope], so the FFT never sits in the audio path. One frame per window.
 */
internal class SpectrumAnalyzer(private val scope: CoroutineScope) {

    private val window = hannWindow(SpectrumFftSize)
    private val incoming = Channel<FloatArray>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _bands = MutableStateFlow(FloatArray(SpectrumBands))
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    /** The band layout follows the stream's rate; the edges are recomputed when it is known. */
    @Volatile
    private var edges = bandEdges(44_100)

    private var previous = FloatArray(SpectrumBands)

    /** Called on the playback thread: post and return. */
    fun offer(window: FloatArray) {
        incoming.trySend(window)
    }

    fun adoptSampleRate(sampleRate: Int) {
        if (sampleRate > 0) edges = bandEdges(sampleRate)
    }

    /** E2: no readable PCM means the resting ring, never an error. */
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

/**
 * A renderers factory whose audio sink carries [spectrum].
 *
 * `buildAudioSink` is the only hook that reaches the audio chain, and the body below mirrors
 * Media3 1.7's own implementation exactly (its two builder calls, verified against the bytecode)
 * with the processor added — a sink configured any other way would quietly change how TV boxes
 * pick an output.
 */
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
