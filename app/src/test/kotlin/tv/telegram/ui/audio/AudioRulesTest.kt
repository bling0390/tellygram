package tv.telegram.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

/**
 * The audio screen's rules: what a track is called (decision D5's fallback chain) and where the
 * transport lands (decision C2's ten-second steps) (2026-10-16).
 */
class AudioRulesTest {

    private fun item(
        title: String? = null,
        performer: String? = null,
        fileName: String? = null,
    ) = MediaItem(
        messageId = 1L,
        type = MediaType.Audio,
        fileId = 1,
        title = title,
        performer = performer,
        fileName = fileName,
    )

    @Test
    fun `the ID3 title wins when the track has one`() {
        assertEquals("Track", audioTitle(item(title = "Track", fileName = "track.mp3")))
    }

    @Test
    fun `without a title the file name carries it, minus its extension`() {
        assertEquals("track", audioTitle(item(fileName = "track.mp3")))
        assertEquals("no-extension", audioTitle(item(fileName = "no-extension")))
    }

    @Test
    fun `a track with neither reads as the dash, not as an empty line`() {
        assertEquals(AudioDash, audioTitle(item()))
        assertEquals(AudioDash, audioTitle(item(title = "   ", fileName = "  ")))
    }

    @Test
    fun `the artist line disappears when the track has none`() {
        assertEquals("Artist", audioArtist(item(performer = "Artist")))
        assertNull(audioArtist(item(performer = "  ")))
        assertNull(audioArtist(item()))
    }

    @Test
    fun `a ten second jump lands on the second and stops at both ends`() {
        assertEquals(30_000L, seekTarget(positionMs = 20_000L, deltaSeconds = 10, durationMs = 60_000L))
        assertEquals(10_000L, seekTarget(positionMs = 20_000L, deltaSeconds = -10, durationMs = 60_000L))
        assertEquals(0L, seekTarget(positionMs = 3_000L, deltaSeconds = -10, durationMs = 60_000L))
        assertEquals(60_000L, seekTarget(positionMs = 55_000L, deltaSeconds = 10, durationMs = 60_000L))
    }

    @Test
    fun `before the duration is known a jump is not clamped by it`() {
        // A stream/decoder that has not reported a duration yet must not swallow the jump.
        assertEquals(30_000L, seekTarget(positionMs = 20_000L, deltaSeconds = 10, durationMs = 0L))
    }

    @Test
    fun `progress reads as a fraction, and as zero while the duration is unknown`() {
        assertEquals(0.25f, progressFraction(15_000L, 60_000L), 1e-4f)
        assertEquals(1f, progressFraction(90_000L, 60_000L), 1e-4f)
        assertEquals(0f, progressFraction(15_000L, 0L), 1e-4f)
    }
}
