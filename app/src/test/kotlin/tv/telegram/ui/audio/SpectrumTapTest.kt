package tv.telegram.ui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The tap's audio-thread half (2026-10-16): windows fill in order, and the processor neither
 * alters nor swallows what the decoder produced — a spectrum must never cost the sound.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpectrumTapTest {

    private fun pcm16(vararg samples: Short): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { buffer.putShort(it) }
        buffer.flip()
        return buffer
    }

    private fun bytesOf(buffer: ByteBuffer): ByteArray =
        ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    private fun format(channels: Int = 1, encoding: Int = C.ENCODING_PCM_16BIT) =
        AudioProcessor.AudioFormat(44_100, channels, encoding)

    @Test
    fun `windows fill in order and only once full`() {
        val windows = PcmWindows(size = 4)
        val seen = mutableListOf<FloatArray>()

        windows.feed(floatArrayOf(1f, 2f, 3f), 3) { seen += it }
        assertTrue("a partial window must not be handed over", seen.isEmpty())

        windows.feed(floatArrayOf(4f, 5f), 2) { seen += it }
        assertEquals(1, seen.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), seen[0], 1e-6f)

        windows.feed(floatArrayOf(6f), 1) { seen += it }
        assertEquals("the next window is still half full", 1, seen.size)
    }

    @Test
    fun `a flush drops the half-filled window`() {
        val windows = PcmWindows(size = 4)
        val seen = mutableListOf<FloatArray>()

        windows.feed(floatArrayOf(1f, 2f), 2) { seen += it }
        windows.clear()
        windows.feed(floatArrayOf(3f, 4f), 2) { seen += it }

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `the processor passes its input through untouched`() {
        val processor = SpectrumAudioProcessor(onWindow = { })
        processor.configure(format())
        val input = pcm16(100, -100, 200, -200)
        val before = bytesOf(input)

        processor.queueInput(input)

        assertArrayEquals(before, bytesOf(processor.output))
    }

    @Test
    fun `the processor hands over mono windows scaled to floats`() {
        val seen = mutableListOf<FloatArray>()
        val processor = SpectrumAudioProcessor(onWindow = { seen += it })
        processor.configure(format())
        val half = ShortArray(SpectrumFftSize) { 16_384 }

        processor.queueInput(pcm16(*half))

        assertEquals(1, seen.size)
        assertEquals(SpectrumFftSize, seen[0].size)
        // 16384 of a 16-bit full scale is half, at both ends of the window.
        assertEquals(0.5f, seen[0][0], 1e-4f)
        assertEquals(0.5f, seen[0][SpectrumFftSize - 1], 1e-4f)
    }

    @Test
    fun `a format we cannot read is passed through and never analysed`() {
        val seen = mutableListOf<FloatArray>()
        val processor = SpectrumAudioProcessor(onWindow = { seen += it })
        processor.configure(format(encoding = C.ENCODING_PCM_FLOAT))
        val input = pcm16(1, 2, 3, 4)
        val before = bytesOf(input)

        processor.queueInput(input)

        assertTrue("an unreadable path must leave the ring resting (E2)", seen.isEmpty())
        assertArrayEquals("and must not cost the sound", before, bytesOf(processor.output))
    }

    @Test
    fun `the stream's rate reaches the listener so the bands can follow it`() {
        var rate = 0
        val processor = SpectrumAudioProcessor(onWindow = { })
        processor.onFormat = { rate = it }

        processor.configure(format())

        assertEquals(44_100, rate)
    }
}
