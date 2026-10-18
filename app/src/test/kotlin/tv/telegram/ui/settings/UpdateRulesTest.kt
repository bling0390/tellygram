package tv.telegram.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The About pane's update rules, kept pure so they run on the JVM (2026-10-18): the tag GitHub
 * publishes, the numeric comparison against the installed version, and the Markdown reduction
 * the TV dialog shows.
 */
class UpdateRulesTest {

    @Test
    fun `release tags become versions`() {
        assertEquals("1.0.129", releaseVersion("v1.0.129"))
        assertEquals("1.0.129", releaseVersion("V1.0.129"))
        assertEquals("1.0.129", releaseVersion("1.0.129-debug"))
        assertEquals("1.0.129", releaseVersion(" v1.0.129 "))
        // Anything that is not a dotted number cannot be compared: no update is offered.
        assertNull(releaseVersion("nightly"))
        assertNull(releaseVersion(""))
        assertNull(releaseVersion("v"))
    }

    @Test
    fun `version comparison is numeric, segment by segment`() {
        assertTrue(isNewerVersion("1.0.129", "1.0.128"))
        assertFalse(isNewerVersion("1.0.128", "1.0.128"))
        assertFalse(isNewerVersion("1.0.9", "1.0.128"))
        // Not lexicographic: 100 > 99.
        assertTrue(isNewerVersion("1.0.100", "1.0.99"))
        // Missing segments count as zero.
        assertTrue(isNewerVersion("1.1", "1.0.128"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun `release notes lose their markdown`() {
        val notes = plainChangelog(
            """
            ## What's new

            - **Fix**: Back from a deep cell
            - `loadMore` keeps pulling

            See [the page](https://example.com).
            """.trimIndent(),
        )

        assertEquals(
            "What's new\n\n• Fix: Back from a deep cell\n• loadMore keeps pulling\n\nSee the page.",
            notes,
        )
    }
}
