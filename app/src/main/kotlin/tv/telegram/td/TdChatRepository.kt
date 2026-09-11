package tv.telegram.td

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.drinkless.td.libcore.telegram.TdApi

class TdChatRepository(
    private val client: TdClient = TdClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _allChats = MutableStateFlow<List<ChatItem>>(emptyList())

    private val _archiveChats = MutableStateFlow<List<ChatItem>>(emptyList())
    val archiveChats: StateFlow<List<ChatItem>> = _archiveChats.asStateFlow()

    private val _archiveCount = MutableStateFlow(0)
    val archiveCount: StateFlow<Int> = _archiveCount.asStateFlow()

    private val _viewingArchive = MutableStateFlow(false)
    val viewingArchive: StateFlow<Boolean> = _viewingArchive.asStateFlow()

    fun setViewingArchive(value: Boolean) {
        _viewingArchive.value = value
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Cooldown for live-update refreshes. UpdateChatPosition fires on every
    // reorder (each new message changes position), so a full list reload per
    // event would be wasteful; coalesce bursts into at most one per window.
    private var lastLiveRefreshMs = 0L
    private val liveRefreshCooldownMs = 2_000L

    init {
        scope.launch {
            client.updates.collect { obj -> dispatchUpdate(obj) }
        }
    }

    private fun dispatchUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                if (obj.authorizationState is TdApi.AuthorizationStateReady) {
                    Log.i(TAG, "Auth Ready → loading chat list")
                    scope.launch { loadAllChats() }
                    scope.launch { loadArchiveChats() }
                }
            }
            is TdApi.UpdateNewChat, is TdApi.UpdateChatPosition, is TdApi.UpdateChatLastMessage -> {
                if (_loaded.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastLiveRefreshMs >= liveRefreshCooldownMs) {
                        lastLiveRefreshMs = now
                        Log.i(TAG, "Chat list change (${obj.javaClass.simpleName}) → refreshing")
                        scope.launch { loadAllChats(force = true) }
                        scope.launch { loadArchiveChats() }
                    }
                }
            }
            is TdApi.UpdateChatReadInbox -> {
                applyUnreadCount(obj.chatId, obj.unreadCount)
            }
            is TdApi.UpdateChatNotificationSettings -> {
                applyMuted(obj.chatId, obj.notificationSettings)
            }
            else -> {  }
        }
    }

    /**
     * Live-update the unread count for a chat without a full list reload.
     * Only the dot indicator (present/absent) is rendered from this value,
     * so we just patch the matching ChatItem in every published list.
     */
    private fun applyUnreadCount(chatId: Long, count: Int) {
        fun patch(list: List<ChatItem>): List<ChatItem> =
            list.map { if (it.id == chatId) it.copy(unreadCount = count) else it }
        _allChats.value = patch(_allChats.value)
        _archiveChats.value = patch(_archiveChats.value)
        _items.value = patch(_items.value)
    }

    /** Patch a chat's muted state from a notification-settings update. */
    private fun applyMuted(chatId: Long, settings: TdApi.ChatNotificationSettings) {
        val muted = isMuted(settings)
        fun patch(list: List<ChatItem>): List<ChatItem> =
            list.map { if (it.id == chatId) it.copy(isMuted = muted) else it }
        _allChats.value = patch(_allChats.value)
        _archiveChats.value = patch(_archiveChats.value)
        _items.value = patch(_items.value)
    }

    private fun isMuted(settings: TdApi.ChatNotificationSettings): Boolean =
        !settings.useDefaultMuteFor && settings.muteFor > 0

    /**
     * Load the main chat list. With [force]=false the already-loaded guard
     * skips redundant reloads; live updates (chat added/removed/reordered)
     * pass force=true so the list actually refreshes.
     */
    suspend fun loadAllChats(limit: Int = 200, force: Boolean = false) {
        if (!force && _loaded.value && _allChats.value.isNotEmpty()) {
            Log.d(TAG, "loadAllChats: already loaded (${_allChats.value.size}); skipping")
            return
        }
        Log.i(TAG, "loadAllChats: requesting top $limit chats")
        _error.value = null

        client.send(TdApi.LoadChats(TdApi.ChatListMain(), limit))

        val chatsObj = when (
            val r = client.execute(TdApi.GetChats(TdApi.ChatListMain(), limit), timeoutMs = 10_000L)
        ) {
            is TdResult.Ok -> r.value
            is TdResult.TdError -> { _error.value = "${r.code}: ${r.message}"; return }
            is TdResult.Timeout -> { _error.value = "Loading timed out. Press OK to retry."; return }
            is TdResult.TransportError -> { _error.value = "Connection error: ${r.cause.message}"; return }
        }
        if (chatsObj !is TdApi.Chats) {
            val msg = "Unexpected response: ${chatsObj.javaClass.simpleName}"
            Log.w(TAG, "getChats failed: $msg")
            _error.value = msg
            return
        }
        val ids = chatsObj.chatIds
        if (ids.isEmpty()) {
            Log.i(TAG, "getChats returned empty list")
            _loaded.value = true
            return
        }
        Log.i(TAG, "getChats returned ${ids.size} chat IDs; fetching each")
        val items: List<ChatItem> = fetchChatItems(ids)
        Log.i(TAG, "Projected to ${items.size} ChatItems")
        _allChats.value = items
        _loaded.value = true
        if (_searchQuery.value.isNotEmpty()) {
            applyFilter(_searchQuery.value)
        } else {
            _items.value = items
        }
    }

    suspend fun loadArchiveChats(limit: Int = 100) {
        try {
            Log.i(TAG, "loadArchiveChats: requesting top $limit archived chats")
            client.send(TdApi.LoadChats(TdApi.ChatListArchive(), limit))
            val chatsObj = client.execute(
                TdApi.GetChats(TdApi.ChatListArchive(), limit),
                timeoutMs = 10_000L,
            ).valueOrNull<TdApi.Chats>()
            if (chatsObj == null) {
                Log.w(TAG, "getChats(archive) failed or returned unexpected type")
                return
            }
            val ids = chatsObj.chatIds
            val items: List<ChatItem> = fetchChatItems(ids)
            _archiveChats.value = items
            _archiveCount.value = items.size
            Log.i(TAG, "Loaded ${items.size} archived chats")
        } catch (e: Throwable) {
            Log.w(TAG, "loadArchiveChats failed", e)
        }
    }

    fun setSearchQuery(query: String) {
        val q = query.trim()
        if (q == _searchQuery.value) return
        _searchQuery.value = q
        if (q.isEmpty()) {
            _items.value = _allChats.value
            return
        }
        _searching.value = true
        scope.launch { runSearch(q) }
    }

    private suspend fun runSearch(query: String) {
        try {
            val resp = client.execute(
                TdApi.SearchChats(query, 50),
                timeoutMs = 3_000L,
            ).valueOrNull<TdApi.Chats>()
            if (resp == null) {
                Log.w(TAG, "searchChats($query) failed; falling back")
                applyFilter(query)
                return
            }
            val ids = resp.chatIds
            if (ids.isEmpty()) {
                Log.i(TAG, "searchChats($query): 0 hits; using in-memory fallback")
                if (_allChats.value.isEmpty() && !_loaded.value) {
                    Log.d(TAG, "runSearch: waiting for loadAllChats to populate _allChats")
                    val deadline = System.currentTimeMillis() + 5_000L
                    while (_allChats.value.isEmpty() && System.currentTimeMillis() < deadline) {
                        kotlinx.coroutines.delay(100L)
                    }
                }
                applyFilter(query)
                return
            }
            val results: List<ChatItem> = ids.toList().mapNotNull { id: Long ->
                _allChats.value.firstOrNull { it.id == id } ?: fetchChatItem(id)
            }
            val seen: HashSet<Long> = results.map { it.id }.toHashSet()
            val merged: List<ChatItem> = results + _allChats.value
                .filter { it.id !in seen && it.title.contains(query, ignoreCase = true) }
            _items.value = merged
        } finally {
            _searching.value = false
        }
    }

    private fun applyFilter(query: String) {
        _items.value = _allChats.value.filter {
            it.title.contains(query, ignoreCase = true)
        }
    }

    private suspend fun fetchChatItem(chatId: Long): ChatItem? {
        val resp = client.execute(TdApi.GetChat(chatId), timeoutMs = 5_000L).valueOrNull<TdApi.Chat>() ?: run {
            Log.w(TAG, "getChat($chatId) failed or timed out")
            return null
        }
        val title = resp.title.ifEmpty { "Unnamed chat" }
        val unread = resp.unreadCount
        val muted = resp.notificationSettings?.let { isMuted(it) } ?: false
        val lastMessageText = resp.lastMessage?.let { messageText(it) }
        val lastMessageThumbFileId = resp.lastMessage?.let { messageThumbFileId(it) }
        val lastMessageDate = resp.lastMessage?.date ?: 0

        val type = when (val t = resp.type) {
            is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> ChatType.Private
            is TdApi.ChatTypeBasicGroup -> ChatType.Group
            is TdApi.ChatTypeSupergroup ->
                if (t.isChannel) ChatType.Channel else ChatType.Group
            else -> ChatType.Unknown
        }

        // Verified flag lives on User / Supergroup in this TDLib version,
        // not on Chat — fetch it per chat type (both hit local DB first).
        val verified = when (val t = resp.type) {
            is TdApi.ChatTypePrivate ->
                client.execute(TdApi.GetUser(t.userId), timeoutMs = 3_000L).valueOrNull<TdApi.User>()?.isVerified ?: false
            is TdApi.ChatTypeSecret ->
                client.execute(TdApi.GetUser(t.userId), timeoutMs = 3_000L).valueOrNull<TdApi.User>()?.isVerified ?: false
            is TdApi.ChatTypeSupergroup ->
                client.execute(TdApi.GetSupergroup(t.supergroupId), timeoutMs = 3_000L).valueOrNull<TdApi.Supergroup>()?.isVerified ?: false
            else -> false
        }

        val photoSmallFileId: Int? = resp.photo?.small?.id
        val pinned = resp.positions.any { it.isPinned }

        return ChatItem(
            id = chatId,
            title = title,
            type = type,
            unreadCount = unread,
            isMuted = muted,
            isPinned = pinned,
            isVerified = verified,
            lastMessageText = lastMessageText,
            lastMessageDate = lastMessageDate,
            lastMessageThumbFileId = lastMessageThumbFileId,
            photoSmallFileId = photoSmallFileId,
        )
    }

    /**
     * Summary for the chat list second line. tvgram is a photo/video-focused
     * TV app, so only media messages produce a summary; everything else
     * returns null and the UI falls back to the chat type name.
     */
    private fun messageText(message: TdApi.Message): String? = when (val c = message.content) {
        is TdApi.MessagePhoto -> "Photo"
        is TdApi.MessageVideo -> "Video"
        else -> null
    }

    /** Thumbnail file id of the last media message, for the small preview before the summary text. */
    private fun messageThumbFileId(message: TdApi.Message): Int? = when (val c = message.content) {
        is TdApi.MessagePhoto -> c.photo.sizes
            .filter { it.photo.id != 0 }
            .minByOrNull { it.width * it.height }
            ?.photo?.id
        is TdApi.MessageVideo -> c.video.thumbnail?.file?.id
        else -> null
    }

    /**
     * Fetch ChatItems for many ids with bounded concurrency. Serial GetChat +
     * GetUser/GetSupergroup over a 200-chat list is the cold-start bottleneck
     * (worst case 400–600 round trips); a semaphore-capped fan-out turns it
     * into a handful of parallel waves without flooding TDLib.
     */
    private suspend fun fetchChatItems(ids: LongArray): List<ChatItem> {
        val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)
        return coroutineScope {
            ids.toList().map { id ->
                async { semaphore.withPermit { fetchChatItem(id) } }
            }.awaitAll()
        }.mapNotNull { it }
    }

    /**
     * Mute or unmute a chat, preserving its other notification settings
     * (sound / preview / mentions). Muting sets muteFor to the TDLib
     * "forever" sentinel (muteFor > 1 week is treated as permanent);
     * unmuting sets it back to 0. The local list is patched immediately
     * (same path as UpdateChatNotificationSettings) so the menu label and
     * the mute icon reflect the change without a full reload.
     */
    suspend fun setChatMuted(chatId: Long, muted: Boolean) {
        val chat = client.execute(TdApi.GetChat(chatId), timeoutMs = 5_000L).valueOrNull<TdApi.Chat>()
        val current = chat?.notificationSettings ?: TdApi.ChatNotificationSettings()
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultMuteFor = false
        settings.muteFor = if (muted) Int.MAX_VALUE else 0
        settings.useDefaultSound = current.useDefaultSound
        settings.sound = current.sound
        settings.useDefaultShowPreview = current.useDefaultShowPreview
        settings.showPreview = current.showPreview
        settings.useDefaultDisablePinnedMessageNotifications = current.useDefaultDisablePinnedMessageNotifications
        settings.disablePinnedMessageNotifications = current.disablePinnedMessageNotifications
        settings.useDefaultDisableMentionNotifications = current.useDefaultDisableMentionNotifications
        settings.disableMentionNotifications = current.disableMentionNotifications
        client.execute(TdApi.SetChatNotificationSettings(chatId, settings), timeoutMs = 5_000L)
        applyMuted(chatId, settings)
    }

    /**
     * Pin or unpin a chat in its current list (main or archive). The
     * [inArchive] flag selects which ChatList TDLib should operate on —
     * a chat lives in exactly one of Main/Archive, and pinning is scoped
     * per list.
     */
    suspend fun setChatPinned(chatId: Long, inArchive: Boolean, pinned: Boolean) {
        val list: TdApi.ChatList = if (inArchive) TdApi.ChatListArchive() else TdApi.ChatListMain()
        client.execute(TdApi.ToggleChatIsPinned(list, chatId, pinned), timeoutMs = 5_000L)
        refreshLists()
    }

    /**
     * Archive or unarchive a chat. AddChatToList moves it between the Main
     * and Archive lists (TDLib removes it from the other list automatically),
     * so one call covers both directions.
     */
    suspend fun setChatArchived(chatId: Long, archived: Boolean) {
        val list: TdApi.ChatList = if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        client.execute(TdApi.AddChatToList(chatId, list), timeoutMs = 5_000L)
        refreshLists()
    }

    /**
     * Remove a chat from the user's list. Private chats have no "leave" —
     * delete history for self and drop it from the list; groups and channels
     * are left. Both fire live-update events, but we reload explicitly for
     * determinism.
     */
    suspend fun deleteChat(chat: ChatItem) {
        when (chat.type) {
            ChatType.Private -> client.execute(
                TdApi.DeleteChatHistory(chat.id, true, false),
                timeoutMs = 5_000L,
            )
            else -> client.execute(TdApi.LeaveChat(chat.id), timeoutMs = 5_000L)
        }
        refreshLists()
    }

    private suspend fun refreshLists() {
        loadAllChats(force = true)
        loadArchiveChats()
    }

    companion object {
        private const val TAG = "TdChatRepo"
        private const val MAX_CONCURRENT_FETCHES = 16
    }
}
