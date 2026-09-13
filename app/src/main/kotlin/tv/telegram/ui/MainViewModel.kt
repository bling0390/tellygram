package tv.telegram.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import tv.telegram.BuildConfig
import tv.telegram.TgTvApp
import tv.telegram.td.AuthState
import tv.telegram.td.ChatItem
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.TdAuth
import tv.telegram.td.TdChatRepository
import tv.telegram.td.TdClient
import tv.telegram.td.TdFileRepository
import tv.telegram.td.TdMediaRepository
import tv.telegram.td.TdUser

sealed class NavEvent {
    data object GoToQrCode : NavEvent()
    data object GoToHome : NavEvent()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val auth = TdAuth(client = TdClient, scope = viewModelScope)
    val chatRepo = TdChatRepository(client = TdClient, scope = viewModelScope)
    val mediaRepo = TdMediaRepository(client = TdClient, scope = viewModelScope)
    val fileRepo = TdFileRepository(
        client = TdClient,
        scope = viewModelScope,
        // Windowed streaming, SmartTube-style: buffer ≈ device RAM / 18,
        // clamped to [64MB, 256MB]. Multi-GB videos never fully land on the
        // TV's tiny storage — the download only runs a window ahead of the
        // playhead (extended on demand as playback advances).
        windowBytes = streamWindowBytes(app),
        filesDirectory = java.io.File(app.filesDir, "tdlib-files").absolutePath,
    )

    val authState: StateFlow<AuthState> = auth.state
    val chatList = chatRepo.items
    val chatListLoaded = chatRepo.loaded
    val chatListError = chatRepo.error
    val archiveChats = chatRepo.archiveChats
    val archiveCount = chatRepo.archiveCount
    val viewingArchive = chatRepo.viewingArchive

    fun retryLoadChats() {
        viewModelScope.launch {
            chatRepo.loadAllChats()
            chatRepo.loadArchiveChats()
        }
    }

    fun setViewingArchive(value: Boolean) {
        chatRepo.setViewingArchive(value)
    }

    fun toggleChatMute(chatId: Long, muted: Boolean) {
        viewModelScope.launch { chatRepo.setChatMuted(chatId, muted) }
    }

    fun toggleChatPin(chatId: Long, inArchive: Boolean, pinned: Boolean) {
        viewModelScope.launch { chatRepo.setChatPinned(chatId, inArchive, pinned) }
    }

    fun toggleChatArchive(chatId: Long, archived: Boolean) {
        viewModelScope.launch { chatRepo.setChatArchived(chatId, archived) }
    }

    fun deleteChat(chat: ChatItem) {
        viewModelScope.launch {
            chatRepo.deleteChat(chat)
            // If the deleted chat was selected, clear the media pane.
            if (_sidebarSelectedChatId.value == chat.id) {
                selectSidebarChat(null)
            }
        }
    }

    private val _navEvents = MutableSharedFlow<NavEvent>()
    val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

    private fun emitNav(event: NavEvent) {
        viewModelScope.launch { _navEvents.emit(event) }
    }

    val searchQuery = chatRepo.searchQuery
    val searchSearching = chatRepo.searching

    val cacheClearProgress: StateFlow<Float?> = TdClient.cacheClearProgress
    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<TgTvApp>()
            val dir = java.io.File(app.filesDir, "tdlib-files")
            _cacheSizeBytes.value = TdClient.cacheSize(dir.absolutePath)
        }
    }

    /**
     * Windowed-streaming buffer size derived from device RAM (SmartTube
     * heuristic: ram/18), clamped to a sane [64MB, 256MB] band for small TV
     * storage. Falls back to 128MB if RAM can't be queried.
     */
    private fun streamWindowBytes(app: Application): Long {
        val memInfo = ActivityManager.MemoryInfo()
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)
        val ram = memInfo.totalMem
        if (ram <= 0L) return 128L * 1024 * 1024
        return (ram / 18L).coerceIn(64L * 1024 * 1024, 256L * 1024 * 1024)
    }

    fun clearCache() {
        val app = getApplication<TgTvApp>()
        val dir = java.io.File(app.filesDir, "tdlib-files")
        TdClient.clearCache(dir.absolutePath)
    }

    fun resetCacheClearProgress() {
        TdClient.resetCacheClearProgress()
    }

    val mediaItems = mediaRepo.items
    val mediaLoaded = mediaRepo.loaded
    val mediaError = mediaRepo.error
    val mediaLoadingMore = mediaRepo.loadingMore
    val mediaExhausted = mediaRepo.exhausted
    val currentChatId = mediaRepo.currentChatId

    private val _playerPlaybackSpeed = MutableStateFlow(1.0f)
    val playerPlaybackSpeed: StateFlow<Float> = _playerPlaybackSpeed.asStateFlow()

    private val _playerResumePositions = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val playerResumePositions: StateFlow<Map<Int, Long>> = _playerResumePositions.asStateFlow()

    private val _sidebarSelectedChatId = MutableStateFlow<Long?>(null)
    val sidebarSelectedChatId: StateFlow<Long?> = _sidebarSelectedChatId.asStateFlow()

    // Last played media messageId — set when the player exits so the chats
    // screen can hand focus back to that exact media card (instead of the
    // sidebar's first row "Archived Chats"). Consumed (cleared) by
    // ChatsScreen once it has scrolled to and focused the card.
    private val _playerReturnFocusMessageId = MutableStateFlow<Long?>(null)
    val playerReturnFocusMessageId: StateFlow<Long?> = _playerReturnFocusMessageId.asStateFlow()

    fun setPlayerReturnFocus(messageId: Long) {
        _playerReturnFocusMessageId.value = messageId
    }

    fun consumePlayerReturnFocus() {
        _playerReturnFocusMessageId.value = null
    }

    private val _themeMode = MutableStateFlow(ThemeMode.Dark)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(Language.English)
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _currentUser = MutableStateFlow<TdUser?>(null)
    val currentUser: StateFlow<TdUser?> = _currentUser.asStateFlow()

    fun selectSidebarChat(chatId: Long?) {
        _sidebarSelectedChatId.value = chatId
        if (chatId != null) {
            openChat(chatId)
        } else {
            closeChat()
        }
    }

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
        SettingsRepository.setTheme(getApplication(), mode)
    }

    fun setLanguage(lang: Language) {
        _language.value = lang
        SettingsRepository.setLanguage(getApplication(), lang)
    }

    fun logout() {
        closeChat()
        _sidebarSelectedChatId.value = null
        _currentUser.value = null
        auth.cancelQrLogin()
    }

    fun realSignOut() {
        closeChat()
        _sidebarSelectedChatId.value = null
        _currentUser.value = null
        emitNav(NavEvent.GoToQrCode)
        val app = getApplication<TgTvApp>()
        TdClient.realSignOut(
            context = app,
            apiId = BuildConfig.TG_API_ID,
            apiHash = BuildConfig.TG_API_HASH,
            databaseDirectory = java.io.File(app.filesDir, "tdlib").absolutePath,
            filesDirectory = java.io.File(app.filesDir, "tdlib-files").absolutePath,
        )
        // client was restarted by realSignOut; re-apply proxy for the fresh instance
        TdClient.enableProxy(
            host     = BuildConfig.PROXY_HOST,
            port     = BuildConfig.PROXY_PORT,
            type     = BuildConfig.PROXY_TYPE,
            username = BuildConfig.PROXY_USER,
            password = BuildConfig.PROXY_PASS,
        )
    }

    fun refreshMe() {
        viewModelScope.launch {
            val user = auth.getMe()
            _currentUser.value = user
        }
    }

    /** Playback rates offered by the player's speed popover, slowest first. */
    val playerSpeeds: List<Float> = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    fun setPlayerSpeed(speed: Float) {
        _playerPlaybackSpeed.value = speed
    }

    fun cyclePlayerSpeed(): Float {
        val speeds = playerSpeeds
        // indexOf(-1) (a rate set from outside the list) falls back to the
        // first entry, so cycling always lands on a listed rate.
        val next = speeds[(speeds.indexOf(_playerPlaybackSpeed.value) + 1) % speeds.size]
        _playerPlaybackSpeed.value = next
        return next
    }

    fun savePlayerPosition(fileId: Int, positionMs: Long) {
        if (positionMs <= 0L) return
        _playerResumePositions.value =
            _playerResumePositions.value + (fileId to positionMs)
    }

    fun clearPlayerPosition(fileId: Int) {
        _playerResumePositions.value = _playerResumePositions.value - fileId
    }

    init {

        val (theme, lang) = SettingsRepository.hydrate(getApplication())
        _themeMode.value = theme
        _language.value = lang
        SettingsRepository.applyLocale(getApplication(), lang)

        viewModelScope.launch {
            auth.state.collect { st ->
                if (st is AuthState.Ready) {
                    refreshMe()
                    // Explicitly load the chat list on Ready. TdChatRepository only
                    // auto-loads on the UpdateAuthorizationState(Ready) event, which
                    // TDLib emits once per authorization. If the Activity is recreated
                    // while the process survives (e.g. TV home button → low memory),
                    // a fresh repository never sees that event and would stay on
                    // "loading chats…" forever. These calls are idempotent.
                    chatRepo.loadAllChats()
                    chatRepo.loadArchiveChats()
                }
            }
        }

        viewModelScope.launch {
            var wasReady = false
            auth.state.collect { st ->
                val isReady = st is AuthState.Ready
                if (wasReady && !isReady) {
                    Log.i("MainViewModel", "auth left Ready (${st.javaClass.simpleName}); clearing chat selection")
                    closeChat()
                    _sidebarSelectedChatId.value = null
                    _currentUser.value = null
                }
                wasReady = isReady
            }
        }

        viewModelScope.launch {
            var prev: AuthState? = null
            var coldStartHandled = false
            var wasReady = false
            auth.state.collect { st ->
                val isReady = st is AuthState.Ready
                val wasInit = prev is AuthState.WaitTdlibParams || prev is AuthState.WaitEncryptionKey
                val nowInit = st is AuthState.WaitTdlibParams || st is AuthState.WaitEncryptionKey
                if (!coldStartHandled && wasInit && !nowInit) {
                    // 冷启动：离开初始化态 → 首次定向（唯一一次）
                    coldStartHandled = true
                    when {
                        isReady -> { emitNav(NavEvent.GoToHome); wasReady = true }
                        st !is AuthState.Error -> emitNav(NavEvent.GoToQrCode)
                        else -> { /* 初始化阶段 Error：留在冷启动页兑底 */ }
                    }
                } else if (isReady && !wasReady && !(prev is AuthState.WaitTdlibParams || prev is AuthState.WaitEncryptionKey)) {
                    // 非冷启动路径进入 Ready（扫码成功等）
                    emitNav(NavEvent.GoToHome)
                }
                wasReady = isReady
                prev = st
            }
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch { mediaRepo.openAndLoad(chatId) }
    }

    fun loadMoreMedia() {
        viewModelScope.launch { mediaRepo.loadMore() }
    }

    fun closeChat() {
        mediaRepo.close()
    }

    fun setSearchQuery(query: String) {
        chatRepo.setSearchQuery(query)
    }

    fun fileStateFor(fileId: Int): FileDownloadState? = fileRepo.stateFor(fileId)

    fun ensureMediaFile(fileId: Int, priority: Int = 16) {
        viewModelScope.launch { fileRepo.ensureLocal(fileId, priority) }
    }

    /** Download only the first chunk of a file — enough for a hover preview. */
    suspend fun ensurePreviewFile(fileId: Int): String? = fileRepo.ensurePreview(fileId)
}
