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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.LazyGridState
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.components.Avatar
import androidx.compose.material.icons.outlined.Done
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import tv.telegram.ui.components.ConfirmDialog

/**
 * HomeScreen — Figma node 1243:1724 ("HomeScreen").
 *
 * Geometry comes straight from the design: a 960x540 TV canvas with 58dp side
 * margins, a 32dp-tall nav bar whose top edge sits at y=32, a 268dp chat list
 * starting at y=96, and a 544x344 media grid of 160x120 cells with 20dp
 * gutters. The design places those children absolutely; here they are expressed
 * as ordinary Compose layout, which lands the boxes in the same places without
 * hard-coded offsets (so the screen still behaves if a TV reports a slightly
 * different width).
 */
internal object HomeSpec {
    // Figma colour variables (material-theme/sys/dark/* and the raw fills).
    val Background = Color(0xFF1A1C1E)
    val OnSurface = Color(0xFFC7C6CA)
    val OnBackground = Color(0xFFE3E2E6)
    val InverseOnSurface = Color(0xFF1A1C1E)
    val Tertiary = Color(0xFFDCBCE1)          // unread dot, default row
    val TertiaryFixed = Color(0xFFF9D8FE)     // unread dot, highlighted row
    val SecondaryContainer = Color(0x66484459) // 40% of #484459 — selected "Chat" tab
    val OnSecondaryContainer = Color(0xFFE5DFF9)
    val ChipOutline = Color(0x33FFFFFF)       // rgba(255,255,255,0.2)
    val White = Color(0xFFFFFFFF)
    // Figma material-theme/sys/dark/surface-container: the panel an actionable
    // settings row sits on before it is focused.
    val SurfaceContainer = Color(0xFF1E2023)
    // Figma material-theme/sys/dark/on-surface-variant — the value column in the
    // settings pane. Kept as a constant, not the theme role: theme roles have
    // not matched the design values at runtime here before.
    val OnSurfaceVariant = Color(0xFFC4C6CF)

    // Settings page (Figma 3239:2418): list 268 at x=58, pane 452 at x=398, 72 between.
    val SettingsListWidth = 268.dp
    val SettingsPaneWidth = 452.dp
    val SettingsPaneGap = 72.dp
    // The verified badge's two colours as constants, not theme roles: the design is
    // dark-only, and colorScheme.primary would turn steel-blue (#5288C1) under the
    // light theme. They mirror material-theme/sys/dark/primary and .../primary-fixed.
    val Primary = Color(0xFFA8C8FF)
    val PrimaryFixed = Color(0xFFD6E3FF)

    val ListWidth = 268.dp
    val ListHeight = 412.dp
    val ListGap = 4.dp
    val GutterBetweenColumns = 32.dp
    val ChipsHeight = 36.dp                  // all chips fixed; the design's ALL chip hugs to this
    val ChipsToGrid = 32.dp                  // 96 + 36 + 32 = 164, so the grid keeps its design Y

    val GridWidth = 544.dp
    val GridHeight = 344.dp
    val CellWidth = 160.dp
    val CellHeight = 120.dp
    val CellGap = 20.dp
    val FocusBorder = 3.dp
    val Corner = 4.dp
}

/** Which of the home screen's three content regions currently holds focus. */
internal enum class HomeRegion { ChatList, Chips, Grid }

/**
 * Focus targets for the D-pad graph the design annotates. `selectedChat` and
 * `topBar` are supplied by the shell: the top bar lives outside the NavHost, so
 * the two sides have to share the same requester objects.
 */
internal class HomeFocus(
    /** What the top bar's Down lands on: the Archived Chats / Back row. */
    val entry: FocusRequester,
    val selectedChat: FocusRequester,
    val topBar: FocusRequester?,
    val selectedChip: FocusRequester,
    val firstGrid: FocusRequester,
    val rememberedGrid: FocusRequester,
    /**
     * A requester on the grid's own container. Unlike a cell's, this node exists whenever the
     * pane does — so directional moves can target it and let it hand focus on, instead of
     * aiming at a recycled cell's requester (which throws "FocusRequester is not
     * initialized" the moment its cell scrolls out of view).
     */
    val gridContainer: FocusRequester,
    /**
     * The Archived Chats row itself. Focus moves here through the list container rather than
     * directly: the row is item 0 of a LazyColumn and gets recycled once it scrolls away, and
     * a directional key aimed at a recycled node throws ("FocusRequester is not initialized").
     */
    val archivedRow: FocusRequester,
    /** The card a closed viewer asked us to focus again. */
    val returnGrid: FocusRequester = FocusRequester(),
) {
    /**
     * The cell the chips' Down returns to. A plain field rather than a constructor
     * value on purpose: focusProperties blocks run outside composition, so a lambda
     * that captured a recreated HomeFocus would keep pointing at the old target.
     * Reading the field at focus time always sees the current one.
     */
    var gridEntry: FocusRequester = firstGrid
}

@Composable
internal fun HomeScreen(
    state: HomeState,
    onOpenPlayer: (Int) -> Unit = {},
    onOpenPhoto: (Int) -> Unit = {},
    /** A member picked inside the album popup: the shell routes it by type. */
    onOpenMediaItem: (MediaItem) -> Unit = {},
    // Focus bridge from the shell: Down from the top bar lands on the selected chat
    // row, and Back from the chat list returns to the bar's selected tab.
    contentEntryFocus: FocusRequester? = null,
    topBarFocus: FocusRequester? = null,
) {
    val chats by state.chatList.collectAsStateWithLifecycle()
    val archivedChats by state.archiveChats.collectAsStateWithLifecycle()
    // Which half of the list the Archived Chats / Back row is currently showing.
    var showingArchived by remember { mutableStateOf(false) }

    // Long-press popover: which chat it belongs to, and where focus goes when it closes.
    var menuChat by remember { mutableStateOf<ChatItem?>(null) }
    var hadMenu by remember { mutableStateOf(false) }
    val menuReturnFocus = remember { FocusRequester() }
    // Resolved on the long press (one cached call for groups; false for private/channels).
    var menuIsOwner by remember { mutableStateOf(false) }
    // The confirm dialog's target and its private-chat toggle, unchecked by default.
    var deleteTarget by remember { mutableStateOf<ChatItem?>(null) }
    // The album popup's target (frames 3558:2184); null when closed.
    var albumTarget by remember { mutableStateOf<MediaItem?>(null) }
    var deleteRadioChecked by remember { mutableStateOf(false) }

    // Closing the popover hands focus back to the row it came from.
    albumTarget?.let { album ->
        AlbumPopup(
            members = album.albumMembers,
            state = state,
            caption = album.caption,
            onOpenMember = { index ->
                album.albumMembers.getOrNull(index)?.let { member ->
                    state.openViewerByMessage(album.albumMembers, member.messageId)
                    onOpenMediaItem(member)
                    albumTarget = null
                }
            },
            onDismiss = {
                state.setPlayerReturnFocus(album.messageId)
                albumTarget = null
            },
        )
    }

    deleteTarget?.let { chat ->
        val case = chatDeleteCase(chat.type, menuIsOwner)
        ConfirmDialog(
            title = stringResource(chatDeleteTitleRes(case)),
            // Only the owner's group body names the group; extra args are ignored elsewhere.
            text = stringResource(chatDeleteBodyRes(case), chat.title),
            confirmLabel = stringResource(chatDeleteConfirmRes(case)),
            cancelLabel = stringResource(R.string.chat_delete_cancel),
            radioLabel = if (chatDeleteHasRadio(chat.type, menuIsOwner)) {
                // The counterpart's name is the private chat's title.
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
            withFrameNanos { }
            runCatching { menuReturnFocus.requestFocus() }
        }
    }
    val media by state.mediaItems.collectAsStateWithLifecycle()

    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }

    // D-pad graph: the edges of each region are routed explicitly rather than left to
    // Compose's nearest-candidate search (design annotations 1-4).
    // Two separate targets. `entry` is what the shell's top bar drops onto — the Archived
    // Chats / Back row, the list's first focusable. `selectedChat` is the row that Back from
    // the grid or chips returns to, which is a different thing entirely.
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
    // Index of the card a viewer asked focus back on; -1 when none.
    var returnFocusIndex by remember { mutableStateOf(-1) }
    val returnHint by state.playerReturnFocusMessageId.collectAsStateWithLifecycle()


    // Row 0's cell doubles as the "remembered" target, so point the chips' Down at the
    // same requester when the remembered cell is [0,0].
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
    // Re-pointed every recomposition; focusProperties lambdas read it live.
    // Read live from focusProperties; the container is always attached.
    focus.gridEntry = gridContainerFocus

    // A requester whose cell is scrolled away is not attached, and requesting one
    // throws; scrolling first also makes the cell exist. Every grid jump goes here.
    fun jumpToGrid(index: Int) {
        // Straight there when the cell is already composed — the common case, and the
        // one the D-pad needs to feel instant. Only a cell that is scrolled away (its
        // requester therefore unattached) needs the scroll-then-request detour.
        val target = { if (index == 0) firstGridFocus else rememberedGridFocus }
        if (runCatching { target().requestFocus() }.isSuccess) return
        scope.launch {
            runCatching { gridState.scrollToItem(index.coerceAtLeast(0)) }
            delay(16L)
            val ok = runCatching { target().requestFocus() }.isSuccess
            if (!ok) runCatching { firstGridFocus.requestFocus() }
        }
    }

    // Back rules (design): grid [0,0] -> selected chat, any other grid cell -> [0,0],
    // chips -> selected chat, chat list -> the bar's selected tab.
    // The card a closed viewer asked focus back on, from the shell.

    // Coming back from the player or the photo preview: hand focus to the card that
    // was open instead of resetting to the first cell. The hint is consumed so the
    // move happens once, and the index is cleared once focus has landed so the cell
    // re-attaches its normal entry requester.
    LaunchedEffect(returnHint, media.size) {
        val id = returnHint ?: return@LaunchedEffect
        val index = indexOfMessage(media, id)
        if (index < 0) return@LaunchedEffect
        state.consumePlayerReturnFocus()
        returnFocusIndex = index
        withFrameNanos { }
        if (!runCatching { focus.returnGrid.requestFocus() }.isSuccess) {
            runCatching { gridState.scrollToItem(index) }
            delay(16L)
            runCatching { focus.returnGrid.requestFocus() }
        }
        returnFocusIndex = -1
    }

    BackRegistration(BackPriority.CHATS, enabled = region != null) {
        when (backTarget(region, gridIndex)) {
            HomeBackTarget.SelectedChat -> runCatching { selectedChatFocus.requestFocus() }
            HomeBackTarget.FirstGridCell -> jumpToGrid(0)
            HomeBackTarget.TopBar -> focus.topBar?.let { runCatching { it.requestFocus() } }
            null -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // The top bar is provided by the app shell (MainActivity) so it survives
        // navigation; this screen only owns the lower part of the design — the
        // chat list and the media pane — and starts at the shell's content edge
        // (y=96 in the design).
        Column(modifier = Modifier.fillMaxSize()) {
            Row {
                ChatList(

                    archivedChats = archivedChats,

                    showingArchived = showingArchived,
        onToggleArchived = { showingArchived = !showingArchived },
        // With nothing loaded on the right there is no cell to move into: the row's Right
        // is cancelled instead of aiming at a requester that is not attached.
        canEnterGrid = selectedChatId != null && media.isNotEmpty(),
                    chats = chats,
                    selectedChatId = selectedChatId,
                    state = state,
                    focus = focus,
                    onRegion = { region = it },
                    menuChat = menuChat,
                    menuIsOwner = menuIsOwner,
                    menuReturnFocus = menuReturnFocus,
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
                                ChatMenuAction.Pin -> state.toggleChatPin(chat.id, showingArchived, !chat.isPinned)
                                ChatMenuAction.Mute -> state.toggleChatMute(chat.id, !chat.isMuted)
                                ChatMenuAction.Archive -> state.toggleChatArchive(chat.id, !showingArchived)
                                // Destructive: never immediate — open the confirm dialog first.
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
                        // A chat opens on the unfiltered first page, so the chip state and the
                        // query state stay in step.
                        filter = MediaFilter.All
                        state.openChat(chat.id)
                    },
                )

                Spacer(Modifier.width(HomeSpec.GutterBetweenColumns))

                Column(modifier = Modifier.width(HomeSpec.GridWidth)) {
                    // With no chat selected there is nothing to filter: the row is not drawn at all.
                    if (selectedChatId != null) {
                    MediaFilterRow(
                        selected = filter,
                        focus = focus,
                        onRegion = { region = it },
                        onSelect = { picked ->
                            // Chips are server-side queries: switching one re-queries the wall
                            // from page one instead of sifting whatever happens to be loaded.
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
                            rememberedIndexFor(index)?.let { rememberedGridIndex = it }
                        },
                        onOpen = onOpenPlayer,
                        onPreview = onOpenPhoto,
                        onOpenAlbum = { albumTarget = it },
                        onEnterGrid = { jumpToGrid(rememberedGridIndex) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat list — Figma "List" (3209:1934): 268x412 at (58,96), 4dp gaps
// ---------------------------------------------------------------------------

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
    onMenuOpen: (ChatItem) -> Unit,
    onMenuClose: () -> Unit,
    onMenuAction: (ChatMenuAction) -> Unit,
    onSelect: (ChatItem) -> Unit,
) {
    // The design's 268x412 box is exactly eight rows of (48 + 4), so anything
    // past seven chats has to scroll. It is a LazyColumn for that reason only —
    // no scrollbar, since the design draws none, and moving focus down brings the
    // focused row into view on its own. "Archived Chats" stays the first item so
    // it scrolls with the list, as the design's column implies.
    // The popover is hosted here: this Box owns the list's coordinate space, so the menu can
    // sit level with the long-pressed row and be pulled up when it would spill past the bottom.
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    // The entry target is the container, not the Archived row: the row can be recycled by the
    // LazyColumn, and aiming a key at a recycled requester crashes (2026-09-28 report). The
    // container forwards, scrolling the row back in first if it has to — same dance as the grid.
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
        items(items = if (showingArchived) archivedChats else chats, key = { it.id }) { chat ->
            // The row the bar's Down lands on and Back returns to: the selected chat,
            // or the first row before anything is selected.
            val isEntry = chat.id == selectedChatId || (selectedChatId == null && chat.id == chats.firstOrNull()?.id)
            val isFirst = chat.id == chats.firstOrNull()?.id
            ChatRow(
                chat = chat,
                selected = chat.id == selectedChatId,
                state = state,
                focus = focus,
                isEntry = isEntry,
                isFirst = isFirst,
                canEnterGrid = canEnterGrid,
                onRegion = onRegion,
                onLongPress = { onMenuOpen(chat) },
                menuReturn = if (chat.id == menuChat?.id) menuReturnFocus else null,
                onClick = { onSelect(chat) },
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
    // The frames draw the focused row as the white pill with the dark label.
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
            // In the archived list the same row turns into "Back" with an arrow.
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
    selected: Boolean,
    state: HomeState,
    focus: HomeFocus,
    isEntry: Boolean,
    isFirst: Boolean,
    canEnterGrid: Boolean,
    onRegion: (HomeRegion) -> Unit,
    onLongPress: () -> Unit,
    menuReturn: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressed by remember { mutableStateOf(false) }
    // Figma shows three row states: plain, pinned (60% fill, dark text) and
    // focused/selected (full fill, dark text).
    val highlighted = focused || selected
    val pinned = chat.isPinned
    // Only focus/selection fills a row (the design annotates the chat-name row that
    // way); a pinned chat keeps the plain styling and shows its state through the pin.
    val colors = chatRowColors(highlighted = highlighted, pinned = pinned)

    // A Box rather than a Row: the design positions the pin absolutely
    // (right: 16dp, vertically centred), so it overlays the row instead of taking
    // part in the text flow — hence no spacer in front of it.
    Box(
        modifier = Modifier
            .fillMaxSizeWidth()
            // Stable handle for the UI tests that drive the D-pad graph.
            .testTag("home-chat-row-${chat.id}")
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(colors.fill)
            .focusProperties {
                // Chat list: up/down plus Right into the media grid; Left does nothing.
                // Up from the FIRST chat reaches the Archived Chats row above it — the bar
                // itself is only reached from that row (it used to jump straight to the bar).
                left = FocusRequester.Cancel
                // Right only exists once the pane has cells; otherwise it would target an
                // unattached requester (and the focus search throws on those).
                right = if (canEnterGrid) focus.gridEntry else FocusRequester.Cancel
                // Geometric search: the Archived row sits right above the first chat, and when
                // it is scrolled away the search simply finds nothing and focus stays put.
                up = FocusRequester.Default
            }
            .onFocusChanged { st ->
                focused = st.isFocused
                if (st.isFocused) {
                    // Focus returns when the popover closes, but the key-up that ended the
                    // long press went to the menu — clear the leftover state here or the
                    // next tap on this row would be swallowed as "still held".
                    longPressed = false
                    longPressJob?.cancel()
                    longPressJob = null
                    onRegion(HomeRegion.ChatList)
                }
            }
            .let { if (isEntry) it.focusRequester(focus.selectedChat) else it }
            .let { if (menuReturn != null) it.focusRequester(menuReturn) else it }.focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                // TV OK arrives as DPAD_CENTER; Enter covers keyboards and emulators.
                // HOLDING it opens the long-press popover; a tap is still a plain click.
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
                        if (longPressed) longPressed = false else onClick()
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
            // Measured from the design's exported rows (4x PNG, 268x48dp): the avatar
            // ink sits at y 8..39 (centre 23.9 = the row's centre) while the title and
            // its trailing icons sit at y 11..24 (centre ~18) — i.e. the name line is
            // pinned to the TOP of the 32dp content box, not centred on the avatar.
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
                            // The design puts the dot at (18,10) inside a row whose
                            // content starts at (16,8) and whose avatar is 32dp, so
                            // it sits 2dp inside the avatar's top-left corner.
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
                    // The design caps the name at 130dp ("最长130dp超过省略"), which is
                    // also what keeps it clear of the absolutely positioned pin.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 130.dp),
                )
                // Order and spacing follow the design's three row examples, where the
                // icons sit at fixed x offsets: Verified first, then the chat-type
                // glyph, then mute — 12dp apart on 12dp icons, i.e. a 2dp gap.
                if (chat.isVerified) {
                    Spacer(Modifier.width(2.dp))
                    // Verified keeps its own colour scale: primary while the row is
                    // plain or pinned, primary-fixed once it is focused or selected.
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
                // Absolute placement per the design: 16dp from the row's right edge,
                // vertically centred, 16dp glyph rotated -45°. (16 * sqrt(2) = 22.63 —
                // that bounding box is where Figma's "22.63 x 22.63" reading came from.)
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

// ---------------------------------------------------------------------------
// Filter chips — Figma row 1243:1727 at (358,96), 12dp gaps
// ---------------------------------------------------------------------------

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
                showCheck = entry == MediaFilter.All && entry == selected,
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
    showCheck: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    focus: HomeFocus,
    onRegion: (HomeRegion) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        modifier = Modifier
            .height(HomeSpec.ChipsHeight)
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(if (active) HomeSpec.White else Color.Transparent)
            .border(
                width = if (active) 0.dp else 1.dp,
                color = if (active) Color.Transparent else HomeSpec.ChipOutline,
                shape = RoundedCornerShape(HomeSpec.Corner),
            )
            .focusProperties {
                // Chips: sideways plus Down to the remembered grid cell. The first chip
                // reaches the selected chat, the last one stops (annotations 4, 5).
                left = if (isFirst) focus.selectedChat else FocusRequester.Default
                right = if (isLast) FocusRequester.Cancel else FocusRequester.Default
                up = FocusRequester.Cancel
                down = focus.gridEntry
            }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onRegion(HomeRegion.Chips) }
            .let { if (selected) it.focusRequester(focus.selectedChip) else it }.focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                // TV OK arrives as DPAD_CENTER; Enter covers keyboards and emulators.

                if (event.type == KeyEventType.KeyUp &&

                    (event.key == Key.DirectionCenter || event.key == Key.Enter)

                ) {

                    onClick()

                    true

                } else {

                    false

                }

            }
            // Horizontal padding only. The chip is a fixed 32dp tall as drawn and
            // the content is centred inside it; keeping the design's 10dp of
            // vertical padding on top of that left a 12dp content box for a 16dp
            // text line, so the label was taller than its own box and got clipped
            // (the descender in "Image" was the visible casualty).
            .padding(
                start = if (showCheck) 16.dp else 20.dp,
                end = 20.dp,
            ),
        // Vertical centring: the design says alignItems=center. The row width
        // hugs its content, so the chip is centred horizontally by construction.
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showCheck) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = null,
                tint = HomeSpec.InverseOnSurface,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) HomeSpec.InverseOnSurface else HomeSpec.White,
        )
    }
}

// ---------------------------------------------------------------------------
// Media grid — Figma "Grid" (1243:1726): 544x344 at (358,164), 160x120 cells
// ---------------------------------------------------------------------------

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
    /** Confirming an album card opens the popup instead of a viewer. */
    onOpenAlbum: (MediaItem) -> Unit = {},
    /** Land focus on the remembered cell; HomeScreen owns the scroll-then-request dance. */
    onEnterGrid: () -> Unit,
) {
    val exhausted by state.mediaExhausted.collectAsStateWithLifecycle()
    val loadingMore by state.mediaLoadingMore.collectAsStateWithLifecycle()
    // Used to hand focus on to a cell after the container takes it.
    val scope = rememberCoroutineScope()

    // The repository pages 100 messages at a time, so without this the grid would
    // simply stop at the first page and read as "that is all the media there is".
    // Load the next page as soon as the last item comes into view; the exhausted
    // flag stops the requests once the chat runs out.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 1 && info.totalItemsCount > 0
        }
            .distinctUntilChanged()
            .collect { atEnd ->
                // Read the flags live: a snapshot taken at composition time goes stale, and
                // after the first page the effect is not restarted (no key changes), so the
                // row could sit at the bottom with nothing ever requesting the next page.
                if (atEnd && !state.mediaExhausted.value && !state.mediaLoadingMore.value) {
                    state.loadMoreMedia()
                }
            }
    }

    Box(
        modifier = Modifier
            .width(HomeSpec.GridWidth)
            .height(HomeSpec.GridHeight)
            .focusRequester(focus.gridContainer)
            // Deferred a frame: a focus request made inside a focus callback is ignored.
        .onFocusChanged { st -> if (st.isFocused) scope.launch { onEnterGrid() } }
            .focusable(),
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .width(HomeSpec.GridWidth)
            .height(HomeSpec.GridHeight),
        horizontalArrangement = Arrangement.spacedBy(HomeSpec.CellGap),
        verticalArrangement = Arrangement.spacedBy(HomeSpec.CellGap),
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
                onOpenAlbum = { onOpenAlbum(item) },
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
    onOpenAlbum: () -> Unit = {},
) {
    val kind = remember(item.messageId, item.type, item.albumSize) { item.cellKind() }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = HomeSpec.CellWidth, height = HomeSpec.CellHeight)
            // Stable handle for the UI tests that drive the D-pad graph.
            .testTag("home-media-cell-$index")
            .clip(RoundedCornerShape(HomeSpec.Corner))
            .background(HomeSpec.SurfaceContainer)
            .then(
                if (focused) {
                    Modifier.border(HomeSpec.FocusBorder, HomeSpec.White, RoundedCornerShape(HomeSpec.Corner))
                } else {
                    Modifier
                },
            )
            .focusProperties {
                // Media grid: four-way with explicit edges (annotation 3) — column 0
                // reaches the selected chat, column 2 stops, row 0 goes up to the
                // active chip.
                left = if (isFirstCol) focus.selectedChat else FocusRequester.Default
                right = if (isLastCol) FocusRequester.Cancel else FocusRequester.Default
                up = if (isFirstRow) focus.selectedChip else FocusRequester.Default
            }
            // One requester per cell, most recent intent first: a focus request only
            // honours whichever requester is attached, so they must not stack.
            .let { m ->
                when {
                    isReturn -> m.focusRequester(focus.returnGrid)
                    isRemembered -> m.focusRequester(focus.rememberedGrid)
                    isFirst -> m.focusRequester(focus.firstGrid)
                    else -> m
                }
            }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onGridFocus(index) }
            .focusable()

            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->

                // TV OK arrives as DPAD_CENTER; Enter covers keyboards and emulators.

                if (event.type == KeyEventType.KeyUp &&

                    (event.key == Key.DirectionCenter || event.key == Key.Enter)

                ) {


                    // An album card opens the popup; everything else keeps its viewer.
                    if (kind is MediaCellKind.Album && item.albumMembers.isNotEmpty()) {
                        onOpenAlbum()
                    } else {                    when (mediaOpenTarget(kind)) {
                                            MediaOpenTarget.PhotoPreview -> onPreview()
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

/** The one glyph that genuinely differs from Material's set, so it ships as an asset. */
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

/** Album card: Collections glyph + "+N" (design: 48dp glyph, 8dp gap, 14/20 label). */
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

/**
 * Photo / video card. The thumbnail is preferred; the design's Image / Video file
 * glyph is the fallback for the cases the spec calls "abnormal": no thumbnail to
 * fetch at all, a fetch that has not landed within [ThumbnailTimeoutMs], or an
 * image that failed to decode. While a fetch is simply in flight the card stays
 * empty rather than showing the glyph, so a slow thumbnail does not read as
 * a broken one.
 */



/** Every list row in the design spans the full 268dp column. */
private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.ListWidth))
