package tv.telegram.td

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPagingRulesTest {

    @Test
    fun `a page that adds nothing is retried, but only a few times`() {
        // The bug: a page with nothing displayable used to mark the whole chat exhausted,
        // so the grid stopped loading. It now keeps pulling, bounded.
        assertFalse(shouldStopPaging(added = 0, extraPages = 0))
        assertFalse(shouldStopPaging(added = 0, extraPages = 1))
        assertFalse(shouldStopPaging(added = 0, extraPages = 2))
        assertTrue(shouldStopPaging(added = 0, extraPages = 3))
    }

    @Test
    fun `a page that adds something ends the round`() {
        assertTrue(shouldStopPaging(added = 1, extraPages = 0))
        assertTrue(shouldStopPaging(added = 50, extraPages = 0))
    }
}
