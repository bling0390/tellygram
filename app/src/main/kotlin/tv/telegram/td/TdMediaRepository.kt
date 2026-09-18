package tv.telegram.td

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.td.libcore.telegram.TdApi

class TdMediaRepository(
    private val client: TdClient = TdClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId.asStateFlow()

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Set of messageIds currently in _items, so live-append dedup is O(1)
    // instead of mapping the whole list on every new message.
    private val knownMessageIds = HashSet<Long>()

    // Pagination cursor: the oldest RAW message id we have pulled, not the oldest
    // kept item. Albums are completed by pulling extra messages, and a page can
    // legitimately keep nothing (service messages), so deriving the cursor from
    // the item list would either skip messages or loop forever on the same page.
    private var oldestFetchedMessageId: Long = 0L

    // Which chip is driving the queries. Every page is asked for with the matching
    // TDLib filter, so a chip switch starts a fresh query instead of re-filtering
    // what happens to be loaded.
    private var currentFilter: MediaFilter = MediaFilter.All

    private fun MediaFilter.searchFilter(): TdApi.SearchMessagesFilter = when (this) {
        MediaFilter.All, MediaFilter.Text -> TdApi.SearchMessagesFilterEmpty()
        MediaFilter.Video -> TdApi.SearchMessagesFilterVideo()
        MediaFilter.Image -> TdApi.SearchMessagesFilterPhoto()
        MediaFilter.Audio -> TdApi.SearchMessagesFilterAudio()
    }

    /**
     * Belt-and-braces type guard. The server already scopes everything except
     * Text, and Text is precisely the case that needs a client-side check.
     */
    private fun MediaItem.matches(filter: MediaFilter): Boolean = when (filter) {
        MediaFilter.All -> true
        MediaFilter.Video -> type == MediaType.Video || type == MediaType.Animation
        MediaFilter.Image -> type == MediaType.Photo
        MediaFilter.Audio -> type == MediaType.Audio
        MediaFilter.Text -> type == MediaType.Text
    }

    init {
        scope.launch {
            client.updates.collect { obj -> dispatch(obj) }
        }
    }

    private fun dispatch(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateNewMessage -> handleNewMessage(obj.message)

            else -> {  }
        }
    }

    private fun handleNewMessage(message: TdApi.Message) {
        val chatId = _currentChatId.value ?: return
        // Only live-append messages from the currently open chat. Without
        // this check, media sent to ANY chat on another device would leak
        // into the open chat's media list.
        if (message.chatId != chatId) return
        val item = parseMessage(message, chatId) ?: return
        if (!item.matches(currentFilter)) return
        // A message that belongs to the album already on screen grows that card
        // instead of adding a near-duplicate cell next to it.
        val albumId = message.mediaAlbumId
        if (albumId != 0L) {
            val idx = _items.value.indexOfFirst { it.albumId == albumId }
            if (idx >= 0) {
                val updated = _items.value.toMutableList()
                updated[idx] = updated[idx].copy(albumSize = updated[idx].albumSize + 1)
                _items.value = updated
                knownMessageIds.add(item.messageId)
                return
            }
        }
        if (knownMessageIds.add(item.messageId)) {
            _items.value = listOf(item) + _items.value
        }
    }

    suspend fun openAndLoad(
        chatId: Long,
        filter: MediaFilter = MediaFilter.All,
        limit: Int = 100,
    ) {
        Log.i(TAG, "openAndLoad(chatId=$chatId, limit=$limit)")
        _currentChatId.value = chatId
        _items.value = emptyList()
        knownMessageIds.clear()
        oldestFetchedMessageId = 0L
        currentFilter = filter
        _loaded.value = false
        _exhausted.value = false
        _error.value = null

        client.send(TdApi.OpenChat(chatId))

        // Paginated per filter: fromMessageId=0 starts at the newest message and
        // each later page advances the cursor. The filter is what makes a chip
        // switch a fresh query rather than a re-filter of loaded items.
        val resp = when (
            val r = client.execute(
                TdApi.SearchChatMessages(
                    chatId, "", null, 0L, 0, limit,
                    filter.searchFilter(), 0L,
                ),
                timeoutMs = 10_000L,
            )
        ) {
            is TdResult.Ok -> r.value
            is TdResult.TdError -> { _error.value = "Error: ${r.message}"; _loaded.value = true; return }
            is TdResult.Timeout -> { _error.value = "Loading timed out. Press OK to retry."; _loaded.value = true; return }
            is TdResult.TransportError -> { _error.value = "Error: ${r.cause.message}"; _loaded.value = true; return }
        }
        if (resp !is TdApi.Messages) {
            Log.w(TAG, "searchChatMessages returned ${resp.javaClass.simpleName}")
            _error.value = "Error: unexpected response ${resp.javaClass.simpleName}"
            _loaded.value = true
            return
        }
        val raw = resp.messages.toMutableList()
        completeTrailingAlbum(chatId, raw)
        oldestFetchedMessageId = raw.lastOrNull()?.id ?: 0L
        val items = groupAlbums(raw, chatId).filter { it.matches(filter) }
        Log.i(TAG, "Loaded ${items.size} items (albums collapsed) from ${raw.size} messages (page 1, filter=$filter)")
        _items.value = items
        knownMessageIds.addAll(items.map { it.messageId })
        _loaded.value = true
        if (raw.size < limit) {
            _exhausted.value = true
        }

        // A filter can land on a page that shows nothing: "Text" has no server-side
        // filter, and ALL passes every message through the type guard. Keep pulling
        // until something shows or the chat runs out (bounded, so a chat full of
        // service messages cannot spin here).
        var extraPages = 0
        while (_items.value.isEmpty() && !_exhausted.value && extraPages < 3) {
            extraPages++
            loadMore(limit)
        }

        // Opening a chat = reading it (TV OK is an explicit "I'm looking at
        // this chat" action, not a hover). Mark the newest message as viewed
        // so TDLib emits UpdateChatReadInbox → the unread dot clears.
        val newestId = resp.messages.firstOrNull()?.id
        if (newestId != null) {
            client.send(TdApi.ViewMessages(chatId, 0L, longArrayOf(newestId), true))
            Log.i(TAG, "openAndLoad: marked chat $chatId read (newest msg $newestId)")
        }
    }

    suspend fun loadMore(limit: Int = 100) {
        if (_loadingMore.value) {
            Log.d(TAG, "loadMore: already in flight")
            return
        }
        val chatId = _currentChatId.value
        if (chatId == null) {
            Log.w(TAG, "loadMore: no current chat")
            return
        }
        if (_exhausted.value) {
            Log.d(TAG, "loadMore: already exhausted")
            return
        }
        val current = _items.value
        if (current.isEmpty() && oldestFetchedMessageId == 0L) {
            Log.w(TAG, "loadMore: no items yet; call openAndLoad first")
            return
        }
        val oldestMessageId = if (oldestFetchedMessageId != 0L) {
            oldestFetchedMessageId
        } else {
            current.last().messageId
        }
        _loadingMore.value = true
        try {
            // Advance the search cursor past the oldest loaded media message.
            // offset=0 returns matches before fromMessageId; the seen-set below
            // dedupes just in case a message comes back twice.
            val resp = client.execute(
                TdApi.SearchChatMessages(
                    chatId, "", null, oldestMessageId, 0, limit,
                    currentFilter.searchFilter(), 0L,
                ),
            ).valueOrNull<TdApi.Messages>()
            if (resp == null) {
                Log.w(TAG, "loadMore: searchChatMessages failed")
                return
            }
            val raw = resp.messages.toMutableList()
            completeTrailingAlbum(chatId, raw)
            if (raw.isNotEmpty()) oldestFetchedMessageId = raw.last().id
            val newItems = groupAlbums(raw, chatId).filter { it.matches(currentFilter) }
            Log.i(TAG, "loadMore: got ${newItems.size} items (albums collapsed) from ${raw.size} messages")
            val seen = current.map { it.messageId }.toHashSet()
            val merged = current + newItems.filter { it.messageId !in seen }
            _items.value = merged
            knownMessageIds.addAll(newItems.map { it.messageId })
            if (newItems.isEmpty() || resp.messages.size < limit) {
                _exhausted.value = true
            }
        } finally {
            _loadingMore.value = false
        }
    }

    fun close() {
        val chatId = _currentChatId.value ?: return
        client.send(TdApi.CloseChat(chatId))
        _currentChatId.value = null
        _items.value = emptyList()
        knownMessageIds.clear()
        oldestFetchedMessageId = 0L
        currentFilter = MediaFilter.All
        _loaded.value = false
        _error.value = null
    }

    private fun parseMessage(message: TdApi.Message, chatId: Long): MediaItem? {
        return when (val c = message.content) {
            is TdApi.MessagePhoto -> {
                val photo = c.photo
                val largest = pickLargest(photo.sizes) ?: return null
                MediaItem(
                    messageId = message.id,
                    type = MediaType.Photo,
                    fileId = largest.photo.id,
                    thumbnailFileId = pickSmallestPhoto(photo.sizes)?.photo?.id,
                    minithumbnail = photo.minithumbnail?.data,
                    width = largest.width,
                    height = largest.height,
                    caption = c.caption.text,
                    date = message.date,
                    chatId = chatId,
                )
            }
            is TdApi.MessageVideo -> {
                val video = c.video
                val thumbFile = video.thumbnail?.file
                MediaItem(
                    messageId = message.id,
                    type = MediaType.Video,
                    fileId = video.video.id,
                    thumbnailFileId = thumbFile?.id,
                    minithumbnail = video.minithumbnail?.data,
                    width = video.width,
                    height = video.height,
                    caption = c.caption.text,
                    date = message.date,
                    chatId = chatId,
                    supportsStreaming = video.supportsStreaming,
                    duration = video.duration,
                )
            }
            is TdApi.MessageAnimation -> {
                val anim = c.animation
                val thumbFile = anim.thumbnail?.file
                MediaItem(
                    messageId = message.id,
                    type = MediaType.Animation,
                    fileId = anim.animation.id,
                    thumbnailFileId = thumbFile?.id,
                    minithumbnail = anim.minithumbnail?.data,
                    width = anim.width,
                    height = anim.height,
                    caption = c.caption.text,
                    date = message.date,
                    chatId = chatId,
                    duration = anim.duration,
                )
            }
            is TdApi.MessageAudio -> {
                val audio = c.audio
                MediaItem(
                    messageId = message.id,
                    type = MediaType.Audio,
                    fileId = audio.audio.id,
                    caption = c.caption.text,
                    date = message.date,
                    chatId = chatId,
                    duration = audio.duration,
                )
            }
            // Text messages: exactly "content is MessageText" (media captions are
            // not MessageText — they ride along on their own content type). Blank
            // bodies are dropped rather than shown as empty cards.
            is TdApi.MessageText -> {
                val body = c.text.text
                if (body.isBlank()) return null
                MediaItem(
                    messageId = message.id,
                    type = MediaType.Text,
                    fileId = 0,
                    caption = body,
                    date = message.date,
                    chatId = chatId,
                )
            }
            else -> null
        }
    }

    /**
     * Albums are contiguous, and a page boundary can cut one in half — which would
     * both under-count its "+N" and make the next page repeat members already
     * shown. If the page ends inside an album, pull a little more until the album
     * closes. Albums hold at most ten messages, so two extra pulls of ten bound it.
     */
    private suspend fun completeTrailingAlbum(chatId: Long, page: MutableList<TdApi.Message>) {
        val albumId = page.lastOrNull()?.mediaAlbumId ?: 0L
        if (albumId == 0L) return
        repeat(2) {
            val cursor = page.lastOrNull()?.id ?: return
            val extra = client.execute(
                TdApi.SearchChatMessages(
                    chatId, "", null, cursor, 0, 10,
                    TdApi.SearchMessagesFilterEmpty(), 0L,
                ),
            ).valueOrNull<TdApi.Messages>()?.messages ?: return
            if (extra.isEmpty()) return
            page += extra
            if (extra.last().mediaAlbumId != albumId) return
        }
    }

    /**
     * Collapses each contiguous run of messages that share a mediaAlbumId into a
     * single item carrying albumId + albumSize; everything else maps one-to-one.
     */
    private fun groupAlbums(messages: List<TdApi.Message>, chatId: Long): List<MediaItem> {
        val out = ArrayList<MediaItem>(messages.size)
        var i = 0
        while (i < messages.size) {
            val albumId = messages[i].mediaAlbumId
            if (albumId == 0L) {
                parseMessage(messages[i], chatId)?.let { out += it }
                i++
                continue
            }
            var j = i
            while (j < messages.size && messages[j].mediaAlbumId == albumId) j++
            val run = messages.subList(i, j)
            run.firstNotNullOfOrNull { parseMessage(it, chatId) }
                ?.let { out += it.copy(albumId = albumId, albumSize = run.size) }
            i = j
        }
        return out
    }

    private fun pickLargest(sizes: Array<TdApi.PhotoSize>): TdApi.PhotoSize? =
        sizes.maxByOrNull { it.width * it.height }

    private fun pickSmallestPhoto(sizes: Array<TdApi.PhotoSize>): TdApi.PhotoSize? =
        sizes.filter { it.photo.id != 0 }
            .minByOrNull { it.width * it.height }

    companion object {
        private const val TAG = "TdMediaRepo"
    }
}
