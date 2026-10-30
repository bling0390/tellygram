@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package tv.telegram.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import tv.telegram.td.MediaFilter
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.delay
import tv.telegram.R
import tv.telegram.td.FileDownloadState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.LazyGridState
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.LocalBackController
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.components.Avatar
import androidx.compose.material.icons.outlined.Done
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import tv.telegram.ui.components.ConfirmDialog
import tv.telegram.td.SenderInfo

internal object HomeSpec {
    val Background = Color(0xFF1A1C1E)
    val OnSurface = Color(0xFFC7C6CA)
    val OnBackground = Color(0xFFE3E2E6)
    val InverseOnSurface = Color(0xFF1A1C1E)
    val Tertiary = Color(0xFFDCBCE1)
    val TertiaryFixed = Color(0xFFF9D8FE)
    val SecondaryContainer = Color(0x66484459)
    val OnSecondaryContainer = Color(0xFFE5DFF9)
    val ChipOutline = Color(0x33FFFFFF)
    val White = Color(0xFFFFFFFF)
    val SurfaceContainer = Color(0xFF1E2023)
    val OnSurfaceVariant = Color(0xFFC4C6CF)

    val SettingsListWidth = 268.dp
    val SettingsPaneWidth = 452.dp
    val SettingsPaneGap = 72.dp
    val Primary = Color(0xFFA8C8FF)
    val PrimaryFixed = Color(0xFFD6E3FF)

    val ListWidth = 268.dp
    val ListHeight = 412.dp
    val ListGap = 4.dp
    val GutterBetweenColumns = 32.dp
    val ChipsToGrid = 32.dp

    val GridWidth = 544.dp
    val GridHeight = 344.dp
    val CellWidth = 160.dp
    val CellHeight = 120.dp
    val CellGap = 20.dp
    val FocusBorder = 3.dp
    val Corner = 4.dp
}

internal enum class HomeRegion { ChatList, Chips, Grid }

internal class HomeFocus(
    val entry: FocusRequester,
    val selectedChat: FocusRequester,
    val topBar: FocusRequester?,
    val selectedChip: FocusRequester,
    val firstGrid: FocusRequester,
    val rememberedGrid: FocusRequester,
    val gridContainer: FocusRequester,
    val archivedRow: FocusRequester,
    val returnGrid: FocusRequester = FocusRequester(),
) {
    var gridEntry: FocusRequester = firstGrid

    var selectedChatAttached: Boolean = false
}

@Composable
internal fun HomeScreen(
    state: HomeState,
    onOpenPlayer: (Int) -> Unit = {},
    onOpenPhoto: (Int) -> Unit = {},
    onOpenAudio: (Int) -> Unit = {},
    onOpenMediaItem: (MediaItem) -> Unit = {},
    contentEntryFocus: FocusRequester? = null,
    topBarFocus: FocusRequester? = null,
) {
    val chats by state.chatList.collectAsStateWithLifecycle()
    val archivedChats by state.archiveChats.collectAsStateWithLifecycle()
    var showingArchived by rememberSaveable { mutableStateOf(false) }

    var menuChat by remember { mutableStateOf<ChatItem?>(null) }
    var returnFocusChatId by remember { mutableStateOf<Long?>(null) }
    var hadMenu by remember { mutableStateOf(false) }
    val menuReturnFocus = remember { FocusRequester() }
    var menuIsOwner by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatItem?>(null) }
    var albumTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumMemberId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumTarget by remember { mutableStateOf<MediaItem?>(null) }
    var textTarget by remember { mutableStateOf<MediaItem?>(null) }
    var textSender by remember { mutableStateOf<SenderInfo?>(null) }
    var deleteRadioChecked by remember { mutableStateOf(false) }

    textTarget?.let { item ->
        val sender = textSender
        TextPopup(
            text = item.caption.orEmpty(),
            senderName = sender?.name,
            avatarFileId = sender?.avatarFileId,
            senderId = item.senderId,
            state = state,
            onDismiss = {
                state.setPlayerReturnFocus(item.messageId)
                textTarget = null
                textSender = null
            },
        )
    }

    albumTarget?.let { album ->
        AlbumPopup(
            members = album.albumMembers,
            state = state,
            caption = albumHeaderCaption(album.albumMembers),
            focusMessageId = albumMemberId,
            onOpenMember = { index ->
                album.albumMembers.getOrNull(index)?.let { member ->
                    state.openViewerByMessage(album.albumMembers, member.messageId)
                    onOpenMediaItem(member)
                    albumMemberId = member.messageId
                }
            },
            onDismiss = {
                state.setPlayerReturnFocus(album.messageId)
                albumTarget = null
                albumTargetId = null
                albumMemberId = null
            },
        )
    }

    deleteTarget?.let { chat ->
        val case = chatDeleteCase(chat.type, menuIsOwner)
        ConfirmDialog(
            title = stringResource(chatDeleteTitleRes(case)),
            text = stringResource(chatDeleteBodyRes(case), chat.title),
            confirmLabel = stringResource(chatDeleteConfirmRes(case)),
            cancelLabel = stringResource(R.string.chat_delete_cancel),
            radioLabel = if (chatDeleteHasRadio(chat.type, menuIsOwner)) {
                stringResource(R.string.chat_delete_radio, chat.title)
            } else {
                null
            },
            radioChecked = deleteRadioChecked,
            onRadioClick = { deleteRadioChecked = !deleteRadioChecked },
            onConfirm = {
                state.deleteChat(
                    chat,
                    chatDeleteRevokes(chat.type, menuIsOwner, deleteRadioChecked),
                )
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            tagPrefix = "chat-delete",
        )
    }

    LaunchedEffect(menuChat) {
        if (menuChat != null) {
            hadMenu = true
        } else if (hadMenu) {
            hadMenu = false
            if (returnFocusChatId == null) {
                withFrameNanos { }
                runCatching { menuReturnFocus.requestFocus() }
            }
        }
    }
    val media by state.mediaItems.collectAsStateWithLifecycle()

    LaunchedEffect(albumTargetId, media) {
        albumTarget = albumTargetId?.let { id -> media.firstOrNull { it.messageId == id } }
    }

    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }
    var pendingOpen by remember { mutableStateOf<Job?>(null) }
    val openedChatId by state.currentChatId.collectAsStateWithLifecycle()

    val entryFocus = remember(contentEntryFocus) { contentEntryFocus ?: FocusRequester() }
    val selectedChatFocus = remember { FocusRequester() }
    val gridContainerFocus = remember { FocusRequester() }
    val archivedRowFocus = remember { FocusRequester() }
    val selectedChipFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }
    val rememberedGridFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var region by remember { mutableStateOf<HomeRegion?>(null) }
    var gridIndex by remember { mutableStateOf(0) }
    var rememberedGridIndex by remember { mutableStateOf(0) }
    LaunchedEffect(openedChatId) { rememberedGridIndex = 0 }
    var returnFocusIndex by remember { mutableStateOf(-1) }
    val returnHint by state.playerReturnFocusMessageId.collectAsStateWithLifecycle()

    val focus = remember(contentEntryFocus, topBarFocus) {
        HomeFocus(
            entry = entryFocus,
            selectedChat = selectedChatFocus,
            topBar = topBarFocus,
            selectedChip = selectedChipFocus,
            firstGrid = firstGridFocus,
            rememberedGrid = rememberedGridFocus,
            gridContainer = gridContainerFocus,
            archivedRow = archivedRowFocus,
        )
    }
    focus.gridEntry = gridContainerFocus

    fun jumpToGrid(index: Int) {
        val target = { if (index == 0) firstGridFocus else rememberedGridFocus }
        if (runCatching { target().requestFocus() }.isSuccess) {
            android.util.Log.i("HomeBack", "jumpToGrid($index): direct ok")
            return
        }
        android.util.Log.w("HomeBack", "jumpToGrid($index): direct failed, scrolling")
        scope.launch {
            runCatching { gridState.scrollToItem(index.coerceAtLeast(0)) }
            repeat(8) {
                withFrameNanos { }
                if (runCatching { target().requestFocus() }.isSuccess) {
                    android.util.Log.i("HomeBack", "jumpToGrid($index): ok on a retry frame")
                    return@launch
                }
            }
            android.util.Log.w("HomeBack", "jumpToGrid($index): gave up after 8 frames")
            if (index != 0) runCatching { firstGridFocus.requestFocus() }
        }
    }

    LaunchedEffect(returnHint, media.size) {
        val id = returnHint ?: return@LaunchedEffect
        val index = gridIndexForMessage(media, id)
        if (index < 0) {
            android.util.Log.i("HomeBack", "return: hint=$id not in media(${media.size})")
            return@LaunchedEffect
        }
        if (albumTargetId != null) {
            android.util.Log.i("HomeBack", "return: album popup owns it (albumTarget=$albumTargetId)")
            state.consumePlayerReturnFocus()
            return@LaunchedEffect
        }
        returnFocusIndex = index
        repeat(8) { attempt ->
            withFrameNanos { }
            val landed = runCatching { focus.returnGrid.requestFocus() }.isSuccess
            android.util.Log.i("HomeBack", "return: attempt=$attempt index=$index landed=$landed")
            if (landed) {
                returnFocusIndex = -1
                state.consumePlayerReturnFocus()
                return@LaunchedEffect
            }
            if (attempt == 0) runCatching { gridState.scrollToItem(index) }
        }
        android.util.Log.w("HomeBack", "return: gave up on index=$index")
        returnFocusIndex = -1
    }

    BackRegistration(BackPriority.CHATS, enabled = region != null) {
        val target = backTarget(region, gridIndex)
        android.util.Log.i("HomeBack", "dispatch region=$region gridIndex=$gridIndex -> $target")
        when (target) {
            HomeBackTarget.SelectedChat -> {
                android.util.Log.i("HomeBack", "back -> selected chat")
                runCatching { selectedChatFocus.requestFocus() }
            }
            HomeBackTarget.FirstGridCell -> jumpToGrid(0)
            HomeBackTarget.TopBar -> focus.topBar?.let { runCatching { it.requestFocus() } }
            null -> Unit
        }
    }
    LaunchedEffect(region) {
        android.util.Log.i("HomeBack", "region -> $region")
    }
    val backController = LocalBackController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeSpec.Background)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                    val handled = backController.dispatch()
                    android.util.Log.i("HomeBack", "preview Back -> dispatch handled=$handled")
                    handled
                } else {
                    false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row {
                ChatList(

                    archivedChats = archivedChats,

                    showingArchived = showingArchived,
        onToggleArchived = { showingArchived = !showingArchived },
        canEnterGrid = selectedChatId != null && media.isNotEmpty(),
                    chats = chats,
                    selectedChatId = selectedChatId,
                    state = state,
                    focus = focus,
                    onRegion = { region = it },
                    menuChat = menuChat,
                    menuIsOwner = menuIsOwner,
                    menuReturnFocus = menuReturnFocus,
                    returnFocusChatId = returnFocusChatId,
                    onReturnFocusHandled = { returnFocusChatId = null },
                    onMenuOpen = { chat ->
                        scope.launch {
                            menuIsOwner = state.isGroupOwner(chat.id)
                            menuChat = chat
                        }
                    },
                    onMenuClose = { menuChat = null },
                    onMenuAction = { action ->
                        menuChat?.let { chat ->
                            when (action) {
                                ChatMenuAction.Pin -> {
                                    state.toggleChatPin(chat.id, showingArchived, !chat.isPinned)
                                    returnFocusChatId = chat.id
                                }
                                ChatMenuAction.Mute -> {
                                    state.toggleChatMute(chat.id, !chat.isMuted)
                                    returnFocusChatId = chat.id
                                }
                                ChatMenuAction.Archive -> {
                                    state.toggleChatArchive(chat.id, !showingArchived)
                                    returnFocusChatId = null
                                }
                                ChatMenuAction.Delete -> {
                                    deleteRadioChecked = false
                                    deleteTarget = chat
                                }
                            }
                        }
                        menuChat = null
                    },
                    onSelect = { chat ->
                        selectedChatId = chat.id
                        if (chat.id != openedChatId) {
                            pendingOpen?.cancel()
                            pendingOpen = scope.launch {
                                delay(ChatOpenDebounceMs)
                                filter = MediaFilter.All
                                state.openChat(chat.id)
                            }
                        }
                    },
                    onConfirm = { jumpToGrid(0) },
                )

                Spacer(Modifier.width(HomeSpec.GutterBetweenColumns))

                Column(modifier = Modifier.width(HomeSpec.GridWidth)) {
                    if (selectedChatId != null) {
                    MediaFilterRow(
                        selected = filter,
                        focus = focus,
                        onRegion = { region = it },
                        onSelect = { picked ->
                            if (picked != filter) {
                                filter = picked
                                state.setMediaFilter(picked)
                            }
                        },
                    )
                    }
                    Spacer(Modifier.height(HomeSpec.ChipsToGrid))
                    MediaGrid(
                        items = media,
                        state = state,
                        focus = focus,
                        gridState = gridState,
                        rememberedIndex = rememberedGridIndex,
                        returnIndex = returnFocusIndex,
                        onGridFocus = { index ->
                            region = HomeRegion.Grid
                            gridIndex = index
                            rememberedGridIndex = index
                        },
                        onOpen = onOpenPlayer,
                        onPreview = onOpenPhoto,
                        onOpenAudio = onOpenAudio,
                        onOpenAlbum = {
                            albumTarget = it
                            albumTargetId = it.messageId
                            albumMemberId = null
                        },
                        onOpenText = { item ->
                            scope.launch {
                                textSender = state.senderInfoFor(item)
                                textTarget = item
                            }
                        },
                        onEnterGrid = { jumpToGrid(gridEntryIndex(rememberedGridIndex, media.size)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatList(
    chats: List<ChatItem>,
    archivedChats: List<ChatItem>,
    showingArchived: Boolean,
    onToggleArchived: () -> Unit,
    canEnterGrid: Boolean,
    selectedChatId: Long?,
    state: HomeState,
    focus: HomeFocus,
    onRegion: (HomeRegion) -> Unit,
    menuChat: ChatItem?,
    menuIsOwner: Boolean,
    menuReturnFocus: FocusRequester,
    returnFocusChatId: Long?,
    onReturnFocusHandled: () -> Unit,
    onMenuOpen: (ChatItem) -> Unit,
    onMenuClose: () -> Unit,
    onMenuAction: (ChatMenuAction) -> Unit,
    onSelect: (ChatItem) -> Unit,
    onConfirm: () -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleChats = if (showingArchived) archivedChats else chats
    val entryId = entryChatId(selectedChatId, visibleChats)
    focus.selectedChatAttached = listState.layoutInfo.visibleItemsInfo.any { it.key == entryId }

    LaunchedEffect(returnFocusChatId, visibleChats, menuChat) {
        if (menuChat != null) return@LaunchedEffect
        val index = returnFocusIndex(visibleChats, returnFocusChatId) ?: return@LaunchedEffect
        runCatching { listState.scrollToItem(index) }
        withFrameNanos { }
        if (runCatching { menuReturnFocus.requestFocus() }.isSuccess) onReturnFocusHandled()
    }
    val listScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .width(HomeSpec.ListWidth)
            .height(HomeSpec.ListHeight)
            .focusRequester(focus.entry)
            .onFocusChanged { st ->
                if (st.isFocused) {
                    listScope.launch {
                        if (!runCatching { focus.archivedRow.requestFocus() }.isSuccess) {
                            runCatching { listState.scrollToItem(0) }
                            withFrameNanos { }
                            runCatching { focus.archivedRow.requestFocus() }
                        }
                    }
                }
            }
            .focusable(),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(HomeSpec.ListWidth)
            .height(HomeSpec.ListHeight),
        verticalArrangement = Arrangement.spacedBy(HomeSpec.ListGap),
    ) {
        item(key = "archived") {
            ArchivedChatsRow(
                showingArchived = showingArchived,
                onClick = onToggleArchived,
                focus = focus,
            )
        }
        items(items = visibleChats, key = { it.id }) { chat ->
            val isEntry = chat.id == entryId
            val isFirst = chat.id == chats.firstOrNull()?.id
            ChatRow(
                chat = chat,
                state = state,
                focus = focus,
                isEntry = isEntry,
                isFirst = isFirst,
                canEnterGrid = canEnterGrid,
                onRegion = onRegion,
                onLongPress = { onMenuOpen(chat) },
                menuReturn = if (chat.id == (menuChat?.id ?: returnFocusChatId)) menuReturnFocus else null,
                onFocused = { onSelect(chat) },
                onConfirmed = onConfirm,
            )
        }
    }
    menuChat?.let { chat ->
        val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == chat.id }?.offset
        val top = offset?.let { with(LocalDensity.current) { chatMenuTop(it.toDp(), HomeSpec.ListHeight, ChatMenuHeightDp.dp) } } ?: 0.dp
        ChatContextMenu(
            chat = chat,
            archived = showingArchived,
            isOwner = menuIsOwner,
            top = top,
            onSelect = onMenuAction,
            onDismiss = onMenuClose,
        )
    }
    }
}

@Composable
private fun ArchivedChatsRow(
    showingArchived: Boolean,
    onClick: () -> Unit,
    focus: HomeFocus,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .testTag("home-archived-row")
            .fillMaxSizeWidth()
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(if (focused) HomeSpec.White else HomeSpec.SurfaceContainer)
            .focusProperties { up = focus.topBar ?: FocusRequester.Default }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focus.archivedRow)
            .focusable()
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (showingArchived) Icons.AutoMirrored.Outlined.ArrowBackIos else Icons.Outlined.Archive,
            contentDescription = null,
            tint = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (showingArchived) R.string.home_archived_back else R.string.home_archived_chats),
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) HomeSpec.InverseOnSurface else HomeSpec.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatItem,
    state: HomeState,
    focus: HomeFocus,
    isEntry: Boolean,
    isFirst: Boolean,
    canEnterGrid: Boolean,
    onRegion: (HomeRegion) -> Unit,
    onLongPress: () -> Unit,
    menuReturn: FocusRequester?,
    onFocused: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressed by remember { mutableStateOf(false) }
    val highlighted = focused
    val pinned = chat.isPinned
    val colors = chatRowColors(highlighted = highlighted, pinned = pinned)

    Box(
        modifier = Modifier
            .fillMaxSizeWidth()
            .testTag("home-chat-row-${chat.id}")
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(colors.fill)
            .focusProperties {
                left = FocusRequester.Cancel
                right = if (canEnterGrid) focus.gridEntry else FocusRequester.Cancel
                up = FocusRequester.Default
            }
            .onFocusChanged { st ->
                focused = st.isFocused
                if (st.isFocused) {
                    longPressed = false
                    longPressJob?.cancel()
                    longPressJob = null
                    onRegion(HomeRegion.ChatList)
                    onFocused()
                }
            }
            .let { if (isEntry) it.focusRequester(focus.selectedChat) else it }
            .let { if (menuReturn != null) it.focusRequester(menuReturn) else it }.focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                when {
                    event.type == KeyEventType.KeyDown && ok -> {
                        if (longPressJob == null && !longPressed) {
                            longPressJob = scope.launch {
                                delay(ChatMenuLongPressMs)
                                longPressed = true
                                onLongPress()
                            }
                        }
                        true
                    }
                    event.type == KeyEventType.KeyUp && ok -> {
                        longPressJob?.cancel()
                        longPressJob = null
                        if (longPressed) longPressed = false else onConfirmed()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                Avatar(
                    photoFileId = chat.photoSmallFileId,
                    name = chat.title,
                    id = chat.id,
                    state = state,
                    fallbackIcon = chat.type.typeIcon(),
                )
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.unreadDot),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 130.dp),
                )
                if (chat.isVerified) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Outlined.Verified,
                        null,
                        tint = verifiedIconColor(highlighted),
                        modifier = Modifier.size(12.dp),
                    )
                }
                chat.type.typeIcon()?.let {
                    Spacer(Modifier.width(2.dp))
                    Icon(it, null, tint = colors.text, modifier = Modifier.size(12.dp))
                }
                if (chat.isMuted) {
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.VolumeOff, null, tint = colors.text, modifier = Modifier.size(12.dp))
                }
            }
        }

        if (chat.isPinned) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(16.dp)
                    .rotate(45f),
            )
        }
    }
}

private fun ChatType.typeIcon() = when (this) {
    ChatType.Private -> Icons.Outlined.Person
    ChatType.Group -> Icons.Outlined.Groups
    ChatType.Channel -> Icons.Outlined.Campaign
    else -> null
}

@Composable
internal fun MediaFilterRow(
    selected: MediaFilter,
    focus: HomeFocus,
    onRegion: (HomeRegion) -> Unit,
    onSelect: (MediaFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaFilter.entries.forEachIndexed { index, entry ->
            FilterChip(
                label = entry.label,
                selected = entry == selected,
                isFirst = index == 0,
                isLast = index == MediaFilter.entries.lastIndex,
                focus = focus,
                onRegion = onRegion,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    focus: HomeFocus,
    onRegion: (HomeRegion) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = filterChipColors(focused)
    Row(
        modifier = Modifier
            .testTag("home-filter-chip-$label")
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(colors.fill)
            .border(
                width = if (focused) 0.dp else 1.dp,
                color = if (focused) Color.Transparent else HomeSpec.ChipOutline,
                shape = RoundedCornerShape(HomeSpec.Corner),
            )
            .focusProperties {
                left = if (isFirst && focus.selectedChatAttached) focus.selectedChat else FocusRequester.Default
                right = if (isLast) FocusRequester.Cancel else FocusRequester.Default
                up = FocusRequester.Cancel
                down = focus.gridEntry
            }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onRegion(HomeRegion.Chips) }
            .let { if (selected) it.focusRequester(focus.selectedChip) else it }.focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                if (event.type == KeyEventType.KeyUp &&

                    (event.key == Key.DirectionCenter || event.key == Key.Enter)

                ) {

                    onClick()

                    true

                } else {

                    false

                }

            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier
                    .size(14.dp)
                    .testTag("home-filter-check-$label"),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text,
        )
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    state: HomeState,
    focus: HomeFocus,
    gridState: LazyGridState,
    rememberedIndex: Int,
    returnIndex: Int,
    onPreview: (Int) -> Unit,
    onGridFocus: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onOpenAudio: (Int) -> Unit = {},
    onOpenAlbum: (MediaItem) -> Unit = {},
    onOpenText: (MediaItem) -> Unit = {},
    onEnterGrid: () -> Unit,
) {
    val exhausted by state.mediaExhausted.collectAsStateWithLifecycle()
    val loadingMore by state.mediaLoadingMore.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(gridState, loadingMore, exhausted) {
        if (loadingMore || exhausted) {
            android.util.Log.i(
                "HomePaging",
                "parked loading=$loadingMore exhausted=$exhausted " +
                    "total=${gridState.layoutInfo.totalItemsCount}",
            )
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 1 && info.totalItemsCount > 0
        }
            .collect { atEnd ->
                android.util.Log.i(
                    "HomePaging",
                    "atEnd=$atEnd total=${gridState.layoutInfo.totalItemsCount} " +
                        "items=${items.size} loading=$loadingMore exhausted=$exhausted",
                )
                if (atEnd) state.loadMoreMedia()
            }
    }

    Box(
        modifier = Modifier
            .width(HomeSpec.GridWidth)
            .height(HomeSpec.GridHeight)
            .focusRequester(focus.gridContainer)
        .onFocusChanged { st -> if (st.isFocused) scope.launch { onEnterGrid() } }
            .focusable(),
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .width(HomeSpec.GridWidth)
            .height(HomeSpec.GridHeight),
        horizontalArrangement = Arrangement.spacedBy(slotGap(HomeSpec.CellGap, HomeSpec.FocusBorder)),
        verticalArrangement = Arrangement.spacedBy(slotGap(HomeSpec.CellGap, HomeSpec.FocusBorder)),
        contentPadding = PaddingValues(0.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.messageId }) { index, item ->
            MediaCell(
                item = item,
                state = state,
                index = index,
                isFirstCol = isFirstColumn(index),
                isLastCol = isLastColumn(index),
                isFirstRow = isFirstRow(index),
                isFirst = index == 0,
                isRemembered = rememberedIndex != 0 && index == rememberedIndex,
                isReturn = index == returnIndex,
                focus = focus,
                onGridFocus = onGridFocus,
                onOpen = { onOpen(index) },
                onPreview = { onPreview(index) },
                onOpenAudio = { onOpenAudio(index) },
                onOpenAlbum = { onOpenAlbum(item) },
                onOpenText = { onOpenText(item) },
            )
        }
    }
    }
}

@Composable
private fun MediaCell(
    item: MediaItem,
    state: HomeState,
    index: Int,
    isFirstCol: Boolean,
    isLastCol: Boolean,
    isFirstRow: Boolean,
    isFirst: Boolean,
    isRemembered: Boolean,
    isReturn: Boolean,
    focus: HomeFocus,
    onGridFocus: (Int) -> Unit,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onOpenAudio: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onOpenText: () -> Unit = {},
) {
    val kind = remember(item.messageId, item.type, item.albumSize) { item.cellKind() }
    var focused by remember { mutableStateOf(false) }

    MediaCard(
        focused = focused,
        tag = "home-media-cell-$index",
        borderColor = HomeSpec.Background,
        modifier = Modifier
            .focusProperties {
                left = if (isFirstCol && focus.selectedChatAttached) focus.selectedChat else FocusRequester.Default
                right = if (isLastCol) FocusRequester.Cancel else FocusRequester.Default
                up = if (isFirstRow) focus.selectedChip else FocusRequester.Default
            }
            .let { m ->
                when {
                    isReturn -> m.focusRequester(focus.returnGrid)
                    isRemembered -> m.focusRequester(focus.rememberedGrid)
                    isFirst -> m.focusRequester(focus.firstGrid)
                    else -> m
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    android.util.Log.i("HomeBack", "cell $index focused (return=$isReturn)")
                    onGridFocus(index)
                }
            }
            .focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                if (event.type == KeyEventType.KeyUp &&

                    (event.key == Key.DirectionCenter || event.key == Key.Enter)

                ) {

                    if (kind is MediaCellKind.Text) {
                        onOpenText()
                    } else if (kind is MediaCellKind.Album && item.albumMembers.isNotEmpty()) {
                        onOpenAlbum()
                    } else {                    when (mediaOpenTarget(kind)) {
                                            MediaOpenTarget.PhotoPreview -> onPreview()
                                            MediaOpenTarget.AudioPlayer -> onOpenAudio()
                                            MediaOpenTarget.Player -> onOpen()
                                        }
                    }

                    true

                } else {

                    false

                }

            },
    ) {
        MediaCardContent(item = item, state = state)
    }
}

@Composable
private fun BoxScope.MediaGlyph(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = HomeSpec.OnSurface,
        modifier = Modifier
            .align(Alignment.Center)
            .size(48.dp),
    )
}

@Composable
private fun BoxScope.MediaGlyphFromRes(@DrawableRes res: Int) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = HomeSpec.OnSurface,
        modifier = Modifier
            .align(Alignment.Center)
            .size(48.dp),
    )
}

@Composable
private fun BoxScope.MediaAlbumCell(count: Int) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Collections,
            contentDescription = null,
            tint = HomeSpec.OnSurface,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "+" + count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = HomeSpec.OnSurface,
        )
    }
}

private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.ListWidth))
