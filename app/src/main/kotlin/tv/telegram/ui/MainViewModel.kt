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
import tv.telegram.ui.home.HomeState
import tv.telegram.ui.settings.SettingsState
import tv.telegram.td.AuthState
import tv.telegram.td.ChatItem
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem
import tv.telegram.td.TdAuth
import tv.telegram.td.TdChatRepository
import tv.telegram.td.TdClient
import tv.telegram.td.TdFileRepository
import tv.telegram.td.TdMediaRepository
import tv.telegram.td.TdUser
import tv.telegram.td.SenderInfo

sealed class NavEvent {
    data object GoToQrCode : NavEvent()
    data object GoToHome : NavEvent()
}

class MainViewModel(app: Application) : AndroidViewModel(app), HomeState, SettingsState {

    val auth = TdAuth(client = TdClient, scope = viewModelScope)
    val chatRepo = TdChatRepository(client = TdClient, scope = viewModelScope)
    val mediaRepo = TdMediaRepository(client = TdClient, scope = viewModelScope)
    val fileRepo = TdFileRepository(
        client = TdClient,
        scope = viewModelScope,
        windowBytes = streamWindowBytes(app),
        filesDirectory = java.io.File(app.filesDir, "tdlib-files").absolutePath,
    )

    override val authState: StateFlow<AuthState> = auth.state
    override val chatList = chatRepo.items
    val chatListLoaded = chatRepo.loaded
    val chatListError = chatRepo.error
    override val archiveChats = chatRepo.archiveChats
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

    override fun toggleChatMute(chatId: Long, muted: Boolean) {
        viewModelScope.launch { chatRepo.setChatMuted(chatId, muted) }
    }

    override fun toggleChatPin(chatId: Long, inArchive: Boolean, pinned: Boolean) {
        viewModelScope.launch { chatRepo.setChatPinned(chatId, inArchive, pinned) }
    }

    override fun toggleChatArchive(chatId: Long, archived: Boolean) {
        viewModelScope.launch { chatRepo.setChatArchived(chatId, archived) }
    }

    override suspend fun senderInfoFor(item: MediaItem): SenderInfo? = mediaRepo.senderInfo(item)

    override suspend fun isGroupOwner(chatId: Long): Boolean =
        chatRepo.isGroupOwner(chatId, currentUser.value?.id ?: 0L)

    override fun deleteChat(chat: ChatItem, revoke: Boolean) {
        viewModelScope.launch {
            chatRepo.deleteChat(chat, revoke)
            if (_sidebarSelectedChatId.value == chat.id) {
                selectSidebarChat(null)
            }
        }
    }

    private val _viewerContext = MutableStateFlow<ViewerContext?>(null)
    val viewerContext: StateFlow<ViewerContext?> = _viewerContext.asStateFlow()

    override fun openViewerByMessage(items: List<MediaItem>, messageId: Long) {
        _viewerContext.value = ViewerContext(items, messageId)
    }

    fun clearViewerContext() {
        _viewerContext.value = null
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

    override val fileStates: StateFlow<Map<Int, FileDownloadState>> get() = fileRepo.states

    override val mediaItems = mediaRepo.items
    val mediaLoaded = mediaRepo.loaded
    val mediaError = mediaRepo.error
    override val mediaLoadingMore = mediaRepo.loadingMore
    override val mediaExhausted = mediaRepo.exhausted
    override val currentChatId = mediaRepo.currentChatId

    private val _playerPlaybackSpeed = MutableStateFlow(1.0f)
    val playerPlaybackSpeed: StateFlow<Float> = _playerPlaybackSpeed.asStateFlow()

    private val _playerResumePositions = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val playerResumePositions: StateFlow<Map<Int, Long>> = _playerResumePositions.asStateFlow()

    private val _sidebarSelectedChatId = MutableStateFlow<Long?>(null)
    val sidebarSelectedChatId: StateFlow<Long?> = _sidebarSelectedChatId.asStateFlow()

    private val _playerReturnFocusMessageId = MutableStateFlow<Long?>(null)
    override val playerReturnFocusMessageId: StateFlow<Long?> = _playerReturnFocusMessageId.asStateFlow()

    override fun setPlayerReturnFocus(messageId: Long) {
        _playerReturnFocusMessageId.value = messageId
    }

    override fun consumePlayerReturnFocus() {
        _playerReturnFocusMessageId.value = null
    }

    private val _themeMode = MutableStateFlow(ThemeMode.Dark)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    @Volatile private var signOutRequested = false

    private val _language = MutableStateFlow(Language.English)
    override val language: StateFlow<Language> = _language.asStateFlow()

    private val _currentUser = MutableStateFlow<TdUser?>(null)
    override val currentUser: StateFlow<TdUser?> = _currentUser.asStateFlow()

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

    override fun setLanguage(lang: Language) {
        _language.value = lang
        SettingsRepository.setLanguage(getApplication(), lang)
        SettingsRepository.applyLocale(getApplication(), lang)
    }

    override fun logOut() {
        signOutRequested = true
        auth.logOut()
        closeChat()
        _sidebarSelectedChatId.value = null
        _currentUser.value = null
        auth.cancelQrLogin()
    }

    fun realSignOut() {
        chatRepo.clearOwnerCache()
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

    val playerSpeeds: List<Float> = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    fun setPlayerSpeed(speed: Float) {
        _playerPlaybackSpeed.value = speed
    }

    fun cyclePlayerSpeed(): Float {
        val speeds = playerSpeeds
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
                if (shouldRestartClosedClient(st, signOutRequested)) {
                    signOutRequested = false
                    Log.i("MainViewModel", "sign-out closed the TDLib client; restarting it")
                    realSignOut()
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
                    coldStartHandled = true
                    when {
                        isReady -> { emitNav(NavEvent.GoToHome); wasReady = true }
                        st !is AuthState.Error -> emitNav(NavEvent.GoToQrCode)
                        else -> { /* 初始化阶段 Error：留在冷启动页兑底 */ }
                    }
                } else if (isReady && !wasReady && !(prev is AuthState.WaitTdlibParams || prev is AuthState.WaitEncryptionKey)) {
                    emitNav(NavEvent.GoToHome)
                }
                wasReady = isReady
                prev = st
            }
        }
    }

    override fun openChat(chatId: Long) {
        viewModelScope.launch { mediaRepo.openAndLoad(chatId) }
    }

    override fun loadMoreMedia() {
        viewModelScope.launch { mediaRepo.loadMore() }
    }

    override fun setMediaFilter(filter: MediaFilter) {
        val chatId = mediaRepo.currentChatId.value ?: return
        viewModelScope.launch { mediaRepo.openAndLoad(chatId, filter) }
    }

    fun closeChat() {
        mediaRepo.close()
    }

    fun setSearchQuery(query: String) {
        chatRepo.setSearchQuery(query)
    }

    fun fileStateFor(fileId: Int): FileDownloadState? = fileRepo.stateFor(fileId)

    override fun ensureMediaFile(fileId: Int, priority: Int) {
        viewModelScope.launch { fileRepo.ensureLocal(fileId, priority) }
    }

    suspend fun ensurePreviewFile(fileId: Int): String? = fileRepo.ensurePreview(fileId)
}

internal fun shouldRestartClosedClient(state: AuthState, signOutRequested: Boolean): Boolean =
    signOutRequested && state is AuthState.Closed

data class ViewerContext(
    val items: List<MediaItem>,
    val messageId: Long,
)
