package tv.telegram.td

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun `album members are shown oldest first`() {
        fun m(id: Long) = MediaItem(messageId = id, type = MediaType.Photo, fileId = 0)
        // TDLib returns newest-first (decreasing messageId); the popup reads oldest-first.
        assertEquals(
            listOf(1L, 2L, 3L),
            albumMembersInDisplayOrder(listOf(m(3), m(2), m(1))).map { it.messageId },
        )
    }
}
