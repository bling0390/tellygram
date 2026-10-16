package tv.telegram.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.telegram.ui.home.HomeSpec
import kotlin.math.PI
import kotlin.math.sin

/**
 * The spectrum's maths, driven by known signals instead of real audio: a sine of a known
 * frequency is the only way to tell a correct band layout from a plausible-looking one
 * (2026-10-16).
 */
class SpectrumRulesTest {

    private val sampleRate = 44_100

    private fun sine(hz: Float, size: Int = SpectrumFftSize): FloatArray =
        FloatArray(size) { i -> sin(2.0 * PI * hz * i / sampleRate).toFloat() }

    @Test
    fun `a tone lands in the band that holds its frequency`() {
        val edges = bandEdges(sampleRate)
        val binHz = sampleRate.toFloat() / SpectrumFftSize
        val levels = normalizeDb(bandLevels(fftMagnitudes(sine(1_000f)), edges))

        val loudest = levels.withIndex().maxByOrNull { it.value }!!.index
        val expected = (0 until edges.size - 1).first { band ->
            1_000f >= edges[band] * binHz && 1_000f < edges[band + 1] * binHz
        }

        assertEquals("1kHz belongs to band $expected", expected, loudest)
    }

    @Test
    fun `the bands are log spaced and climb to the top of the audible range`() {
        val edges = bandEdges(sampleRate)

        assertEquals(SpectrumBands + 1, edges.size)
        assertEquals(0, edges.first())
        for (i in 0 until edges.size - 1) {
            assertTrue("edge $i must not fold back", edges[i] <= edges[i + 1])
        }
        // 16kHz is bin 371 of the 512 the transform produces at this rate.
        assertEquals(371, edges.last())
    }

    @Test
    fun `silence reads as zero rather than as the floor`() {
        val levels = normalizeDb(bandLevels(fftMagnitudes(FloatArray(SpectrumFftSize)), bandEdges(sampleRate)))

        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun `a tone stands well above the floor`() {
        val levels = normalizeDb(bandLevels(fftMagnitudes(sine(1_000f)), bandEdges(sampleRate)))

        assertTrue("expected a clear peak, got ${levels.max()}", levels.max() > 0.5f)
    }

    @Test
    fun `smoothing rises on attack and falls on release`() {
        // Rising targets move by the attack rate, falling ones by the slower release.
        assertEquals(0.5f, smooth(FloatArray(1), floatArrayOf(1f), attack = 0.5f, release = 0.1f)[0], 1e-4f)
        assertEquals(0.9f, smooth(floatArrayOf(1f), floatArrayOf(0f), attack = 0.5f, release = 0.1f)[0], 1e-4f)
    }

    @Test
    fun `a frame keeps its band count and never grows on silence`() {
        val edges = bandEdges(sampleRate)
        val first = spectrumFrame(sine(1_000f), edges, FloatArray(SpectrumBands))

        assertEquals(SpectrumBands, first.size)
        assertTrue(first.any { it > 0f })

        val decayed = spectrumFrame(FloatArray(SpectrumFftSize), edges, first)
        for (band in 0 until SpectrumBands) {
            assertTrue("band $band grew while the input was silent", decayed[band] <= first[band])
        }
    }

    @Test
    fun `the radius never collapses to the centre`() {
        assertEquals(SpectrumMinRadius, radiusFraction(0f), 1e-4f)
        assertEquals(1f, radiusFraction(1f), 1e-4f)
        assertTrue(radiusFraction(0.6f) > radiusFraction(0.3f))
    }

    @Test
    fun `the ring is mirrored about the vertical axis`() {
        // Bands run clockwise from the top, so band i's partner is band (n - i) — the pairing the
        // drawing code relies on (decision D1).
        val levels = FloatArray(SpectrumBands).also { it[3] = 0.8f }
        val mirrored = mirroredLevels(levels)

        assertEquals(0.8f, mirrored[3], 1e-6f)
        assertEquals(0.8f, mirrored[SpectrumBands - 3], 1e-6f)
        for (i in mirrored.indices) {
            assertEquals(
                "band $i and its mirror must agree",
                mirrored[i],
                mirrored[(SpectrumBands - i) % SpectrumBands],
                1e-6f,
            )
        }
        // Only that pair shows: mirroring must not invent energy elsewhere.
        assertEquals(2, mirrored.count { it > 0f })
    }

    @Test
    fun `the colour runs from the primary at rest to the tertiary at the edge`() {
        assertEquals(HomeSpec.Primary, spectrumColor(0f))
        assertEquals(HomeSpec.Tertiary, spectrumColor(1f))
        // A level outside the range is clamped rather than extrapolated.
        assertEquals(HomeSpec.Primary, spectrumColor(-1f))
        assertEquals(HomeSpec.Tertiary, spectrumColor(2f))
    }
}
