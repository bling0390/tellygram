package tv.telegram.td

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.drinkless.td.libcore.telegram.TdApi

/**
 * The filter chips are server-side queries: each one maps to a TDLib search filter,
 * and the client-side guard keeps the Text chip honest (TDLib has no text filter).
 */
class MediaFilterRulesTest {

    private fun item(type: MediaType) =
        MediaItem(messageId = 1, type = type, fileId = 1)

    @Test
    fun `each chip maps to its query`() {
        assertTrue(MediaFilter.All.searchFilter() is TdApi.SearchMessagesFilterEmpty)
        assertTrue(MediaFilter.Text.searchFilter() is TdApi.SearchMessagesFilterEmpty)
        assertTrue(MediaFilter.Video.searchFilter() is TdApi.SearchMessagesFilterVideo)
        assertTrue(MediaFilter.Image.searchFilter() is TdApi.SearchMessagesFilterPhoto)
        assertTrue(MediaFilter.Audio.searchFilter() is TdApi.SearchMessagesFilterAudio)
    }

    @Test
    fun `All accepts every kind`() {
        MediaType.entries.forEach { assertTrue(item(it).matches(MediaFilter.All)) }
    }

    @Test
    fun `Video accepts videos and animations only`() {
        assertTrue(item(MediaType.Video).matches(MediaFilter.Video))
        assertTrue(item(MediaType.Animation).matches(MediaFilter.Video))
        assertFalse(item(MediaType.Photo).matches(MediaFilter.Video))
    }

    @Test
    fun `Image, Audio and Text match exactly their own kind`() {
        assertTrue(item(MediaType.Photo).matches(MediaFilter.Image))
        assertFalse(item(MediaType.Video).matches(MediaFilter.Image))

        assertTrue(item(MediaType.Audio).matches(MediaFilter.Audio))
        assertFalse(item(MediaType.Photo).matches(MediaFilter.Audio))

        assertTrue(item(MediaType.Text).matches(MediaFilter.Text))
        assertFalse(item(MediaType.Photo).matches(MediaFilter.Text))
    }
}
