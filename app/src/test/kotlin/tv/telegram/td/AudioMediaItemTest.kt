package tv.telegram.td

import org.drinkless.td.libcore.telegram.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The audio metadata a card and the player screen read. TDLib reports an absent ID3 field as an
 * empty string rather than a null, and the player's fallback chain (title -> file name ->
 * "unknown track") keys on null, so the mapping normalises the empty case once, here
 * (2026-10-16).
 */
class AudioMediaItemTest {

    private fun audio(
        duration: Int = 215,
        title: String = "Track",
        performer: String = "Artist",
        fileName: String = "track.mp3",
        mimeType: String = "audio/mpeg",
        size: Int = 4_200_000,
    ) = TdApi.Audio(
        duration,
        title,
        performer,
        fileName,
        mimeType,
        null,
        null,
        TdApi.File(
            7,
            size,
            size,
            TdApi.LocalFile("", true, true, false, false, 0, 0, 0),
            TdApi.RemoteFile("remote", "unique", false, false, 0),
        ),
    )

    private fun item(audio: TdApi.Audio) = audioMediaItem(
        messageId = 42L,
        chatId = 99L,
        date = 1_700_000_000,
        senderId = 7L,
        senderIsChat = false,
        audio = audio,
        caption = "the caption",
    )

    @Test
    fun `the track's own metadata reaches the item`() {
        val item = item(audio())

        assertEquals(MediaType.Audio, item.type)
        assertEquals(7, item.fileId)
        assertEquals(215, item.duration)
        assertEquals("Track", item.title)
        assertEquals("Artist", item.performer)
        assertEquals("track.mp3", item.fileName)
        assertEquals("audio/mpeg", item.mimeType)
        assertEquals(4_200_000, item.fileSize)
        // Message-level fields still ride along.
        assertEquals(42L, item.messageId)
        assertEquals(99L, item.chatId)
        assertEquals("the caption", item.caption)
    }

    @Test
    fun `absent ID3 fields become null, not empty strings`() {
        val item = item(audio(title = "", performer = "", mimeType = ""))

        assertNull(item.title)
        assertNull(item.performer)
        assertNull(item.mimeType)
        // The file name is what carries the identity in that case, so it is kept.
        assertEquals("track.mp3", item.fileName)
    }
}
