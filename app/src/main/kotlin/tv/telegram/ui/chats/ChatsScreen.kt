@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import tv.telegram.R
import tv.telegram.td.ChatItem
import tv.telegram.td.ChatType
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.focus.focusGridItem
import tv.telegram.ui.focus.focusListItem
import tv.telegram.ui.focus.isFullyVisible
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.io.File

// Media grid thumbnails: if a thumbnail still isn't local after this long
// (download stalled/failed, or the message has no thumbnail), stop spinning
// and show a static type icon instead of an endless loading spinner.
private const val THUMB_TIMEOUT_MS = 10_000L

@Composable
fun ChatsScreen(
    viewModel: MainViewModel,
    onOpenPlayer: (Int) -> Unit,
    // Focus target for the rail's Chats item — the chat list routes Left
    // here explicitly (see ChatSidebar), so Left never lands on the
    // rail's Search/Settings icon via directional search.
    railChatsFocus: FocusRequester? = null,
    // Focus target the rail's Chats item hands Right to: the selected chat
    // (or the first chat when nothing is selected). Attached to exactly one
    // chat row in ChatSidebar.
    chatsListRightFocus: FocusRequester? = null,
) {
    val chats by viewModel.chatList.collectAsStateWithLifecycle()
    val archiveChats by viewModel.archiveChats.collectAsStateWithLifecycle()
    val viewingArchive by viewModel.viewingArchive.collectAsStateWithLifecycle()
    val archiveCount by viewModel.archiveCount.collectAsStateWithLifecycle()
    val loaded by viewModel.chatListLoaded.collectAsStateWithLifecycle()
    val chatListError by viewModel.chatListError.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.sidebarSelectedChatId.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val mediaLoaded by viewModel.mediaLoaded.collectAsStateWithLifecycle()
    // Set by the player on exit (Back / playback ended): the messageId of
    // the card that was playing. ChatsScreen hands focus back to that
    // exact media card instead of the sidebar's first row.
    val playerReturnFocusMessageId by viewModel.playerReturnFocusMessageId.collectAsStateWithLifecycle()
    // Only treat it as a real return target when the card actually exists
    // in the current grid — otherwise keep the sidebar's normal initial
    // focus behavior.
    val returnFocusTarget = playerReturnFocusMessageId?.takeIf { id ->
        mediaItems.any { it.messageId == id }
    }

    // Toast 即时提醒：聊天列表加载失败时弹一次
    val context = LocalContext.current
    LaunchedEffect(chatListError) {
        if (chatListError != null) {
            Toast.makeText(context, chatListError, Toast.LENGTH_LONG).show()
        }
    }

    // Media grid Left at left edge → focus the currently selected chat in
    // the sidebar (not some random neighbour from global focus search).
    // sidebarFocusTick increments on each such Left press; ChatSidebar
    // watches it, scrolls the selected chat into view and requests focus.
    val selectedChatFocus = remember { FocusRequester() }
    var sidebarFocusTick by remember { mutableIntStateOf(0) }

    // Back-key hierarchy on the home screen: Back inside the media grid
    // returns focus to the selected chat in the sidebar; Back inside the
    // chat list returns focus to the rail's Chats item. Without this, Back
    // on HOME_CHATS exits the app (MainActivity's BackHandler only covers
    // SEARCH/settings). Focus-region tracking comes from onFocusChange
    // callbacks on ChatSidebar / MediaPane (hasFocus bubbles up from any
    // focused row / card).
    var sidebarFocused by remember { mutableStateOf(false) }
    var mediaFocused by remember { mutableStateOf(false) }
    BackRegistration(BackPriority.CHATS, enabled = mediaFocused || sidebarFocused) {
        when {
            // Media grid → selected chat in the sidebar (same path as the
            // grid's left-edge Left key: bump the tick, ChatSidebar scrolls
            // the selected chat into view and focuses it).
            mediaFocused -> sidebarFocusTick++
            // Chat list → rail's Chats item (same target as the list's
            // Left key).
            sidebarFocused -> {
                try { railChatsFocus?.requestFocus() }
                catch (_: IllegalStateException) {}
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {

        ChatSidebar(
            chats = if (viewingArchive) archiveChats else chats,
            loaded = loaded,
            error = chatListError,
            onRetry = { viewModel.retryLoadChats() },
            selectedChatId = selectedChatId,
            archiveCount = archiveCount,
            viewingArchive = viewingArchive,
            onSelect = { viewModel.selectSidebarChat(it) },
            onShowArchive = { viewModel.setViewingArchive(true) },
            onShowMain = { viewModel.setViewingArchive(false) },
            viewModel = viewModel,
            selectedChatFocus = selectedChatFocus,
            sidebarFocusTick = sidebarFocusTick,
            railChatsFocus = railChatsFocus,
            // Rail Chats → Right lands on the selected / first chat row.
            chatsListRightFocus = chatsListRightFocus,
            // Returning from the player: don't grab initial focus on the
            // first sidebar row ("Archived Chats") — the media grid takes
            // it instead (see MediaPane's returnFocus).
            suppressInitialFocus = returnFocusTarget != null,
            onFocusChange = { sidebarFocused = it },
            modifier = Modifier
                .width(296.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp, horizontal = 8.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            if (selectedChatId == null) {
                EmptyMediaPane(modifier = Modifier.fillMaxSize())
            } else {
                MediaPane(
                    items = mediaItems,
                    loaded = mediaLoaded,
                    onOpenPlayer = onOpenPlayer,
                    viewModel = viewModel,
                    selectedChatId = selectedChatId!!,
                    onLeftToSelectedChat = { sidebarFocusTick++ },
                    returnFocusMessageId = returnFocusTarget,
                    onFocusChange = { mediaFocused = it },
                )
            }
        }
    }
}

@Composable
private fun ChatSidebar(
    chats: List<ChatItem>,
    loaded: Boolean,
    error: String?,
    onRetry: () -> Unit,
    selectedChatId: Long?,
    archiveCount: Int,
    viewingArchive: Boolean,
    onSelect: (Long) -> Unit,
    onShowArchive: () -> Unit,
    onShowMain: () -> Unit,
    viewModel: MainViewModel,
    // Focus target for the currently selected chat row. The media grid
    // hands focus back to the sidebar (Left at grid's left edge) and this
    // requester makes sure the selected chat — not some random neighbour —
    // receives it.
    selectedChatFocus: FocusRequester,
    // Monotonic tick: each media-grid Left transfer bumps it, and the
    // sidebar scrolls the selected chat into view and focuses it.
    sidebarFocusTick: Int,
    // Left from a chat row lands on the rail's Chats item instead of
    // whichever rail icon directional search happens to pick.
    railChatsFocus: FocusRequester? = null,
    // Rail Chats → Right lands here: the selected chat (if any), else the
    // first chat in the list.
    chatsListRightFocus: FocusRequester? = null,
    // True when returning from the player: skip the initial first-row
    // focus grab so the media grid can take focus (returnFocusMessageId).
    suppressInitialFocus: Boolean = false,
    // Back-key hierarchy: reports whether focus sits inside the chat list
    // (ChatsScreen uses it to decide where Back should move focus).
    onFocusChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // ── Long-press context menu ──
    // menuChat != null → context menu dialog is showing for that chat.
    // confirm != null → destructive-action confirmation dialog is showing.
    var menuChat by remember { mutableStateOf<ChatItem?>(null) }
    var confirm by remember { mutableStateOf<ChatConfirmAction?>(null) }
    // The row that was long-pressed; its requester is re-requested once the
    // menu / confirm dialogs are fully closed so the D-pad lands back on it
    // (a Dialog is a separate window — focus is otherwise lost).
    val menuReturnFocus = remember { FocusRequester() }
    var menuReturnChatId by remember { mutableStateOf<Long?>(null) }

    // Return focus to the long-pressed row once every dialog is closed.
    LaunchedEffect(menuChat, confirm) {
        if (menuChat == null && confirm == null) {
            val id = menuReturnChatId ?: return@LaunchedEffect
            menuReturnChatId = null
            delay(150)
            val restored = try {
                menuReturnFocus.requestFocus(); true
            } catch (_: IllegalStateException) { false }
            if (!restored) {
                // Row was removed (delete/archive); land on the first row.
                try { firstFocus.requestFocus() } catch (_: IllegalStateException) {}
            }
        }
    }

    LaunchedEffect(viewingArchive) {
        if (suppressInitialFocus) return@LaunchedEffect
        withFrameNanos { }
        try { firstFocus.requestFocus() }
        catch (_: IllegalStateException) {}
    }

    // Media grid pressed Left at the grid's left edge: focus the selected
    // chat in the sidebar. Only scroll when the selected chat is NOT fully
    // visible — an unconditional scrollToItem makes the list jump (the top
    // "Archived Chats" entry slides out of view) and then the focus
    // system's bringIntoView animates it back, which reads as a flicker.
    LaunchedEffect(sidebarFocusTick, selectedChatId, viewingArchive) {
        if (sidebarFocusTick > 0) {
            val offset = when {
                viewingArchive -> 1 // "Back to Chats" entry sits at index 0
                archiveCount > 0 -> 1 // "Archived Chats" entry sits at index 0
                else -> 0
            }
            val idx = chats.indexOfFirst { it.id == selectedChatId }
            if (idx >= 0) {
                val targetIndex = idx + offset
                // Only scroll when the selected chat is NOT fully visible —
                // an unconditional scrollToItem makes the list jump (the top
                // "Archived Chats" entry slides out of view) and then the
                // focus system's bringIntoView animates it back, which reads
                // as a flicker. focusListItem awaits real placement instead
                // of a fixed-frame guess.
                if (listState.isFullyVisible(targetIndex)) {
                    try { selectedChatFocus.requestFocus() }
                    catch (_: IllegalStateException) {}
                } else {
                    focusListItem(listState, targetIndex, selectedChatFocus)
                }
            }
        }
    }

    Column(modifier = modifier) {
        if (error != null) {
            ChatListError(
                error = error,
                onRetry = onRetry,
            )
            return@Column
        }
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (chats.isEmpty() && !viewingArchive) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chats_empty),
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.onFocusChanged { onFocusChange?.invoke(it.hasFocus) },
        ) {
            if (viewingArchive) {
                item(key = "back-to-main") {
                    ArchiveEntry(
                        label = "Back to Chats",
                        icon = Icons.Default.NorthWest,
                        onClick = onShowMain,
                        fr = firstFocus,
                        railChatsFocus = railChatsFocus,
                    )
                }
            } else if (archiveCount > 0) {
                item(key = "show-archive") {
                    ArchiveEntry(
                        label = "Archived Chats ($archiveCount)",
                        icon = Icons.Default.VisibilityOff,
                        onClick = onShowArchive,
                        fr = firstFocus,
                        railChatsFocus = railChatsFocus,
                    )
                }
            }
            items(chats, key = { it.id }) { chat ->
                val isSelected = chat.id == selectedChatId
                val isFirst = chat.id == chats.firstOrNull()?.id && viewingArchive.not() && archiveCount == 0
                // The selected chat also carries selectedChatFocus so the
                // media grid's Left transfer can land exactly on it. When
                // the selected chat IS the first item, keep firstFocus as
                // the primary requester and attach selectedChatFocus as an
                // extra (a node may carry several focus requesters).
                val fr = if (isFirst) firstFocus else null
                val extraFr = if (isSelected) selectedChatFocus else null
                // Rail Chats → Right entry: the selected chat if any, else
                // the first chat (firstFocus's row is the archive entry when
                // an archive exists, so use chats.first() directly here).
                val isRightEntry = if (selectedChatId != null) isSelected
                    else chat.id == chats.firstOrNull()?.id
                val listRightFr = if (isRightEntry) chatsListRightFocus else null
                // The long-pressed row carries menuReturnFocus so focus can
                // come back to it after the menu closes.
                val menuReturnFr = if (chat.id == menuReturnChatId) menuReturnFocus else null
                SidebarItem(
                    chat = chat,
                    selected = isSelected,
                    onClick = { onSelect(chat.id) },
                    onLongClick = {
                        menuReturnChatId = chat.id
                        menuChat = chat
                    },
                    fr = fr,
                    extraFr = extraFr,
                    listRightFr = listRightFr,
                    menuReturnFr = menuReturnFr,
                    viewModel = viewModel,
                    railChatsFocus = railChatsFocus,
                    // First chat row (no archive entry above it) is the top
                    // of the list — Up stays put instead of escaping to the
                    // rail.
                    blockUp = isFirst,
                )
            }
        }
    }

    // Long-press context menu.
    menuChat?.let { chat ->
        ChatContextMenu(
            chat = chat,
            viewingArchive = viewingArchive,
            onDismiss = { menuChat = null },
            onDelete = { menuChat = null; confirm = ChatConfirmAction.Delete(chat) },
            onToggleMute = {
                menuChat = null
                viewModel.toggleChatMute(chat.id, muted = !chat.isMuted)
            },
            onToggleArchive = {
                menuChat = null
                val targetArchived = !viewingArchive
                if (targetArchived) {
                    // Archiving (hiding the chat) — ask for confirmation.
                    confirm = ChatConfirmAction.Archive(chat, archived = true)
                } else {
                    // Unarchiving (restoring it to the main list) — reversible
                    // and harmless, apply directly without a confirm step.
                    viewModel.toggleChatArchive(chat.id, archived = false)
                }
            },
            onTogglePin = {
                menuChat = null
                viewModel.toggleChatPin(chat.id, inArchive = viewingArchive, pinned = !chat.isPinned)
            },
        )
    }

    // Destructive-action confirmation dialog.
    confirm?.let { action ->
        ChatConfirmDialog(
            action = action,
            onConfirm = {
                when (action) {
                    is ChatConfirmAction.Delete -> viewModel.deleteChat(action.chat)
                    is ChatConfirmAction.Archive ->
                        viewModel.toggleChatArchive(action.chat.id, archived = action.archived)
                }
                confirm = null
            },
            onCancel = { confirm = null },
        )
    }
}

@Composable
private fun ChatListError(
    error: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Card(
                onClick = onRetry,
                colors = CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = "Retry",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchiveEntry(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    fr: FocusRequester? = null,
    // Left from the archive entry goes to the rail's Chats item (it's the
    // top of the list, same boundary as the chat rows below it).
    railChatsFocus: FocusRequester? = null,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // Top of the list: Up stays put (no rail above the list's first
            // item). Left is the deliberate path back to the rail.
            .focusProperties {
                up = FocusRequester.Cancel
                railChatsFocus?.let { left = it }
            }
            .let { if (fr != null) it.focusRequester(fr) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    chat: ChatItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    fr: FocusRequester? = null,
    extraFr: FocusRequester? = null,
    listRightFr: FocusRequester? = null,
    menuReturnFr: FocusRequester? = null,
    viewModel: MainViewModel,
    // Left from a chat row goes to the rail's Chats item.
    railChatsFocus: FocusRequester? = null,
    // First chat row (no archive entry above it): Up stays put.
    blockUp: Boolean = false,
) {
    val ctx = LocalContext.current
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer // 胶囊高亮（与侧边栏一致）
        // 置顶会话空闲态浅底色，仅用于区分；聚焦/选中时被覆盖。
        chat.isPinned -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // Cross-zone boundaries declared declaratively instead of
            // tracking first-row focus: Up at the top of the list stays put;
            // Left always lands on the rail's Chats item (not whichever rail
            // icon geometric search happens to pick).
            .focusProperties {
                if (blockUp) up = FocusRequester.Cancel
                railChatsFocus?.let { left = it }
            }
            .let { if (fr != null) it.focusRequester(fr) else it }
            .let { if (extraFr != null) it.focusRequester(extraFr) else it }
            .let { if (listRightFr != null) it.focusRequester(listRightFr) else it }
            .let { if (menuReturnFr != null) it.focusRequester(menuReturnFr) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar + unread badge overlaid on its top-start corner.
            Box {
                AvatarPlaceholder(chat = chat, viewModel = viewModel)
                if (chat.unreadCount > 0) {
                    UnreadDot(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 1.5.dp, y = 1.5.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.title,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    val typeIcon = chat.type.typeIcon()
                    if (typeIcon != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    if (chat.isMuted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val thumbId = chat.lastMessageThumbFileId
                    val thumbState = if (thumbId != null) {
                        (viewModel.fileStateFor(thumbId) as? FileDownloadState.Local)?.path
                    } else null
                    LaunchedEffect(thumbId) {
                        if (thumbId != null && thumbState == null) {
                            viewModel.ensureMediaFile(thumbId, priority = 8)
                        }
                    }
                    // One request per path. Rebuilding it inline handed Coil a
                    // brand-new (non-equal) request on every recomposition, which
                    // restarted the load and made the thumbnail blink.
                    val thumbRequest = remember(thumbState) {
                        thumbState?.let { ImageRequest.Builder(ctx).data(File(it)).build() }
                    }
                    if (thumbRequest != null) {
                        AsyncImage(
                            model = thumbRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        // Empty string keeps the row height stable when the last
                        // message is not photo/video, so the title never shifts.
                        text = chat.lastMessageText ?: "",
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            // Pinned indicator sits at the row's far end, vertically centered
            // across both title and subtitle lines.
            if (chat.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun AvatarPlaceholder(chat: ChatItem, viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val photoId = chat.photoSmallFileId
    // Reactive avatar state: subscribe to the repo's state map so the avatar
    // swaps in the moment its file lands. The old one-shot fileStateFor() read
    // never recomposed, so a freshly downloaded avatar only appeared when some
    // unrelated change (a focus move in the list) happened to recompose the row
    // — which read as the list "flashing" during focus navigation.
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val localPath = photoId?.let {
        (fileStates[it] as? FileDownloadState.Local)?.path
    }
    var imageFailed by remember(photoId, localPath) { mutableStateOf(false) }
    LaunchedEffect(photoId) {
        if (photoId != null && localPath == null) {
            viewModel.ensureMediaFile(photoId, priority = 16)
        }
    }
    val color = when (chat.type) {
        ChatType.Channel -> Color(0xFF4A90E2)
        ChatType.Group -> Color(0xFF50C878)
        ChatType.Private -> Color(0xFFE67E22)
        else -> Color(0xFF888888)
    }
    val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color, CircleShape)
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Memoised on the path: a fresh ImageRequest is never equal to the
        // previous one, so Coil restarted the load and the avatar blinked.
        val avatarRequest = remember(localPath) {
            localPath?.let { ImageRequest.Builder(ctx).data(File(it)).build() }
        }
        if (avatarRequest != null && !imageFailed) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = chat.title,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape),
            )
        } else {
            Text(initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
    )
}

/** Chat-type glyph for the title row: Person (private) / Groups (group) / Campaign (channel). */
private fun ChatType.typeIcon(): ImageVector? = when (this) {
    ChatType.Private -> Icons.Default.Person
    ChatType.Group -> Icons.Default.Groups
    ChatType.Channel -> Icons.Default.Campaign
    else -> null
}

/** Seconds → "m:ss", or "h:mm:ss" once the duration reaches an hour. */
private fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, sec)
    } else {
        "%d:%02d".format(m, sec)
    }
}

@Composable
private fun EmptyMediaPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.chats_select_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.chats_select_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MediaPane(
    items: List<MediaItem>,
    loaded: Boolean,
    onOpenPlayer: (Int) -> Unit,
    viewModel: MainViewModel,
    selectedChatId: Long,
    onLeftToSelectedChat: () -> Unit,
    // Set when returning from the player: scroll to and focus the card
    // that was playing (instead of the sidebar grabbing "Archived Chats").
    returnFocusMessageId: Long? = null,
    // Back-key hierarchy: reports whether focus sits inside the media grid
    // (ChatsScreen uses it to decide where Back should move focus).
    onFocusChange: ((Boolean) -> Unit)? = null,
) {

    var openedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // When the fullscreen photo dialog closes, focus must return to the
    // card that was being viewed — the dialog is a separate window, and
    // without an explicit handoff focus falls to the rail's Search icon.
    // Recorded on close, consumed (cleared) once the grid has scrolled
    // to and focused the card.
    var photoReturnMessageId by remember { mutableStateOf<Long?>(null) }
    val photoCardFocus = remember { FocusRequester() }
    // True while focus sits on a card in the grid's left column
    // (index % 3 == 0). Left here would make Compose's global focus search
    // jump to an arbitrary chat in the sidebar — instead we consume the key
    // and hand focus explicitly to the currently selected chat. This one
    // stays an explicit handler (not focusProperties) because the target
    // (selected chat) may be scrolled off-screen and need bring-into-view
    // first — a plain `left = FocusRequester` can't do that.
    var leftEdgeFocused by remember { mutableStateOf(false) }

    // Switch-chat focus: when a DIFFERENT chat's media actually arrives,
    // scroll the grid back to the top and focus the first media card (like
    // ChatSidebar's firstFocus).
    //
    // Keyed on (selectedChatId, first messageId) with a chatId check so this
    // fires exactly once per chat switch — and NOT on:
    //   - loadMore: item[0] stays stable, the key doesn't change;
    //   - returning from the player: lastResetChatId is saveable, so it
    //     survives the back-stack restore, matches the current chat, and the
    //     grid keeps its restored scroll position (focus isn't yanked back
    //     to the top). The old effect keyed on the first messageId ran on
    //     EVERY composition entry, defeating restoreState.
    //
    // scrollToItem(0) is essential: the grid reuses its state across chats,
    // so the old scroll offset persists — without scrolling back, the first
    // card may not even be composed and the focus request silently no-ops.
    val gridState = rememberLazyGridState()
    val firstCardFocus = remember { FocusRequester() }
    var lastResetChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedChatId, items.firstOrNull()?.messageId) {
        val first = items.firstOrNull()
        if (first != null && first.chatId == selectedChatId && lastResetChatId != selectedChatId) {
            lastResetChatId = selectedChatId
            focusGridItem(gridState, 0, firstCardFocus)
        }
    }

    // Return-from-player focus: scroll to the exact card that was playing
    // and focus it. Unlike the chat-switch effect above (keyed on
    // lastResetChatId), this fires when the SAME chat is still selected —
    // returning from the player keeps the chat, so that effect no-ops.
    val returnCardFocus = remember { FocusRequester() }
    LaunchedEffect(returnFocusMessageId, items.firstOrNull()?.messageId) {
        val target = returnFocusMessageId ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.messageId == target }
        if (idx >= 0) {
            // Returning from the player removes the player's focused node, and
            // Compose's default focus restoration can land on the rail's Search
            // icon before this coroutine's request runs. Retry so the card
            // reliably wins the race instead of leaving focus stranded.
            var focused = false
            for (attempt in 1..5) {
                focused = focusGridItem(gridState, idx, returnCardFocus)
                if (focused) break
                delay(100)
            }
            viewModel.consumePlayerReturnFocus()
        }
    }

    // Photo-dialog close focus: same idea as returnCardFocus but fully
    // local — the dialog lives inside this composable, so no ViewModel
    // hop needed. Fires once the dialog has left composition (openedIndex
    // back to null) and the grid is rendering again.
    LaunchedEffect(openedIndex, photoReturnMessageId, items.firstOrNull()?.messageId) {
        val target = photoReturnMessageId ?: return@LaunchedEffect
        if (openedIndex != null) return@LaunchedEffect // dialog still open
        val idx = items.indexOfFirst { it.messageId == target }
        if (idx >= 0) {
            focusGridItem(gridState, idx, photoCardFocus)
            photoReturnMessageId = null // one-shot
        }
    }

    // Hover preview: one shared muted ExoPlayer reused across all cards.
    // Focus on a video card for 2.5s → play the first chunk of the file
    // inline; losing focus stops it and restores the thumbnail.
    val context = LocalContext.current
    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Releasing an ExoPlayer is main-thread work, and releasing it inline
            // put a full release in the same frame window as PlayerScreen's own
            // player construction (press OK on a video card → visible stutter).
            // Hand the release to the main looper one page-transition later so
            // that window stays clear.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { previewPlayer.release() },
                600L,
            )
        }
    }
    var focusedMessageId by remember { mutableStateOf<Long?>(null) }
    var previewingMessageId by remember { mutableStateOf<Long?>(null) }
    // Set while the preview file is being downloaded (before playback starts),
    // so the card can show a loading spinner in place of the play icon.
    var previewLoadingMessageId by remember { mutableStateOf<Long?>(null) }
    // Set only once the preview player has actually rendered its first frame.
    // Until then the card keeps showing the thumbnail on top of the surface,
    // so hover → load → play never flashes a black screen.
    var previewReadyMessageId by remember { mutableStateOf<Long?>(null) }

    // First-frame signal from the shared preview player (player is created on
    // the main thread, so the callback also lands on main — safe to touch state).
    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                previewReadyMessageId = previewingMessageId
            }
        }
        previewPlayer.addListener(listener)
        onDispose { previewPlayer.removeListener(listener) }
    }

    LaunchedEffect(focusedMessageId) {
        // Any focus change: stop the previous preview first.
        previewPlayer.stop()
        previewingMessageId = null
        previewLoadingMessageId = null
        previewReadyMessageId = null
        val id = focusedMessageId
        if (id == null) return@LaunchedEffect
        val item = items.firstOrNull { it.messageId == id } ?: return@LaunchedEffect
        if (item.type != MediaType.Video) return@LaunchedEffect
        delay(2500)
        if (focusedMessageId != id) return@LaunchedEffect // focus moved away
        previewLoadingMessageId = id
        val path = viewModel.ensurePreviewFile(item.fileId)
        previewLoadingMessageId = null
        if (path == null || focusedMessageId != id) return@LaunchedEffect
        previewingMessageId = id
        previewPlayer.setMediaItem(ExoMediaItem.fromUri("file://$path"))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(items.size) {
        snapshotFlow { nearEnd }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMoreMedia() }
    }

    // Photo fullscreen paging: when viewing near the end of the loaded
    // list, prefetch the next search page so next/prev keeps working
    // past the current data boundary.
    LaunchedEffect(openedIndex, items.size) {
        val idx = openedIndex ?: return@LaunchedEffect
        if (idx >= items.size - 8 && !viewModel.mediaExhausted.value) {
            viewModel.loadMoreMedia()
        }
    }

    if (openedIndex != null) {
        val idx = openedIndex!!
        if (idx in items.indices) {
            // True fullscreen photo viewer: a dedicated Dialog window that
            // covers the whole screen (NavRail + chat sidebar included),
            // instead of replacing only the media pane.
            Dialog(
                onDismissRequest = {
                    photoReturnMessageId = items[idx].messageId
                    openedIndex = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
            ) {
                PhotoFullscreen(
                    item = items[idx],
                    hasPrev = idx > 0,
                    hasNext = idx < items.size - 1,
                    onPrev = { openedIndex = idx - 1 },
                    onNext = { openedIndex = idx + 1 },
                    onBack = {
                        photoReturnMessageId = items[idx].messageId
                        openedIndex = null
                    },
                    viewModel = viewModel,
                )
            }
        } else {
            openedIndex = null
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { onFocusChange?.invoke(it.hasFocus) },
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loaded) {
                    Text(
                        stringResource(R.string.chats_media_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            // Top/bottom padding gives the 8% focus-scale room to expand
            // without clipping the first/last row against the viewport.
            contentPadding = PaddingValues(vertical = 20.dp),
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { ev ->
                    // Left on the left column: consume and hand focus to the
                    // selected chat in the sidebar (avoids Compose's global
                    // search landing on some random chat). Up at the first
                    // row is handled declaratively on the card itself (see
                    // SidebarMediaCard blockUp).
                    if (ev.type == KeyEventType.KeyDown) {
                        when {
                            ev.key == Key.DirectionLeft && leftEdgeFocused -> {
                                onLeftToSelectedChat()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
        ) {
            gridItemsIndexed(items, key = { _, item -> item.messageId }) { index, item ->
                val previewing = previewingMessageId == item.messageId
                val previewLoading = previewLoadingMessageId == item.messageId
                val previewReady = previewReadyMessageId == item.messageId
                val onFocusChange: (Boolean) -> Unit = { focused ->
                    if (focused) focusedMessageId = item.messageId
                    else if (focusedMessageId == item.messageId) focusedMessageId = null
                }
                val isFirstRow = index < 3
                val isLeftEdge = index % 3 == 0
                val isHome = index == 0
                // The card that was playing before the player opened — it
                // carries returnCardFocus so the return-from-player effect
                // can land exactly on it.
                val isReturnTarget = item.messageId == returnFocusMessageId
                // The card the photo dialog was viewing before it closed —
                // carries photoCardFocus for the local dialog-close focus.
                val isPhotoReturnTarget = item.messageId == photoReturnMessageId
                // Track focus for the grid-boundary key handling above:
                // first row (Up) and left column (Left). isHome only
                // decides which card owns the initial grid focus requester.
                Box(
                    Modifier.onFocusChanged { focused ->
                        if (isLeftEdge) leftEdgeFocused = focused.hasFocus
                    },
                ) {
                    SidebarMediaCard(
                        item = item,
                        onClick = {
                            if (item.type == MediaType.Video) {
                                onOpenPlayer(index)
                            } else {
                                openedIndex = index
                            }
                        },
                        viewModel = viewModel,
                        previewing = previewing,
                        previewLoading = previewLoading,
                        previewReady = previewReady,
                        previewPlayer = previewPlayer,
                        onFocusChange = onFocusChange,
                        // First row has no upward candidate — Up stays put
                        // (declarative, no firstRowFocused tracking).
                        blockUp = isFirstRow,
                        fr = when {
                            isPhotoReturnTarget -> photoCardFocus
                            isReturnTarget -> returnCardFocus
                            isHome -> firstCardFocus
                            else -> null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    viewModel: MainViewModel,
    previewing: Boolean = false,
    previewLoading: Boolean = false,
    previewReady: Boolean = false,
    previewPlayer: ExoPlayer? = null,
    onFocusChange: (Boolean) -> Unit = {},
    fr: FocusRequester? = null,
    // First-row card: Up has no upward candidate, so it stays put
    // (declarative boundary — no firstRowFocused state tracking).
    blockUp: Boolean = false,
) {
    val ctx = LocalContext.current
    val thumbId = item.thumbnailFileId
    // Reactive thumbnail state: the old one-shot fileStateFor() read never
    // recomposed when the download landed, so a card that fetched its
    // thumbnail kept spinning forever (same bug PlayerScreen already fixed
    // for its own file). Collect the repo state map so the card recomposes
    // the moment the thumbnail becomes Local.
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val thumbState = if (thumbId != null) {
        (fileStates[thumbId] as? FileDownloadState.Local)?.path
    } else null
    // Memoised on the path: rebuilding the request inline made every
    // recomposition hand Coil a non-equal request, which restarted the load
    // and blinked the card.
    val thumbRequest = remember(thumbState) {
        thumbState?.let {
            ImageRequest.Builder(ctx).data(File(it)).crossfade(true).build()
        }
    }
    // Fallback: if the thumbnail still isn't local after THUMB_TIMEOUT_MS
    // (download stalled / failed, or the message has no thumbnail at all),
    // stop spinning and show a static type icon instead.
    var thumbTimedOut by remember(item.messageId) { mutableStateOf(false) }
    LaunchedEffect(thumbId, thumbState) {
        thumbTimedOut = false
        if (thumbId == null) {
            thumbTimedOut = true // no thumbnail to fetch
            return@LaunchedEffect
        }
        if (thumbState != null) return@LaunchedEffect // already local
        viewModel.ensureMediaFile(thumbId, priority = 16)
        delay(THUMB_TIMEOUT_MS)
        // Re-read at the deadline — this effect is keyed on thumbState, so a
        // mid-wait arrival restarts it and cancels the delay; reaching here
        // means it's still null.
        if (viewModel.fileStateFor(thumbId) !is FileDownloadState.Local) {
            thumbTimedOut = true
        }
    }
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.10f),
        glow = CardDefaults.glow(
            // NOTE: CardGlow order is (enabled, focused, pressed) — the glow
            // must go in slot #2 (focused) so only the focused card shows it.
            Glow.None,
            Glow(elevationColor = Color.White.copy(alpha = 0.15f), elevation = 3.dp),
            Glow.None,
        ),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .focusProperties { if (blockUp) up = FocusRequester.Cancel }
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .let { if (fr != null) it.focusRequester(fr) else it },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (previewing && previewPlayer != null) {
                // Classic PlayerView, not the compose PlayerSurface: the
                // compose surface is a bare SurfaceView hookup that ignores
                // rotation metadata and aspect ratio, so portrait videos
                // (encoded landscape + rotation=90) render stretched. PlayerView
                // applies rotation and letterboxes via AspectRatioFrameLayout.
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = previewPlayer
                        }
                    },
                    update = { it.player = previewPlayer },
                    modifier = Modifier.fillMaxSize(),
                )
                // Keep the thumbnail on top until the player has rendered its
                // first frame — the live surface only appears once there is
                // actually something to show.
                if (!previewReady && thumbState != null) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = item.caption ?: "Media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (thumbState != null) {
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = item.caption ?: "Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (thumbTimedOut) {
                // Thumbnail never arrived (download stalled/failed, or the
                // message has no thumbnail) — show a static type icon.
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.type == MediaType.Photo)
                            Icons.Default.Image else Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            } else {
                // Thumbnail not downloaded yet — show a centered spinner
                // instead of the old "Photo"/"Video" placeholder.
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            // Duration badge (top-left) — Video only. The ▶ play chip owns
            // the bottom-right corner, so duration sits top-left where it
            // won't collide with the preview spinner. Always visible while
            // the thumbnail is shown (even during hover preview — the surface
            // is letterboxed, the badge sits over it harmlessly).
            if (item.type == MediaType.Video && item.duration > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        formatDuration(item.duration),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            // Bottom-right badge only when the thumbnail is ready — no point
            // showing the play chip over a spinner.
            if (item.type == MediaType.Video && !previewing && thumbState != null) {
                // No dark chip behind the loading spinner — it sits directly on
                // the thumbnail. The idle play icon keeps its chip.
                val badgeModifier = if (previewLoading) {
                    Modifier
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .then(badgeModifier)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    if (previewLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFullscreen(
    item: MediaItem,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel,
) {
    BackHandler(enabled = true) { onBack() }
    val focusRequester = remember { FocusRequester() }
    // Box must be focusable or onKeyEvent never fires; retry the focus
    // request a few frames until the Dialog window is attached.
    LaunchedEffect(item.fileId) {
        withFrameNanos { }
        repeat(5) {
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {}
            delay(60)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft, Key.MediaPrevious -> { if (hasPrev) onPrev(); true }
                    Key.DirectionRight, Key.MediaNext -> { if (hasNext) onNext(); true }
                    else -> false
                }
            },
    ) {
        val ctx = LocalContext.current
        var localPath by remember(item.fileId) { mutableStateOf<String?>(null) }
        var error by remember(item.fileId) { mutableStateOf<String?>(null) }
        val downloadTimedOut = stringResource(R.string.download_timed_out)
        LaunchedEffect(item.fileId) {
            try {
                val p = viewModel.fileRepo.ensureLocal(item.fileId, priority = 32, timeoutMs = 90_000L)
                if (p != null) localPath = p else error = downloadTimedOut
            } catch (e: Throwable) { error = e.message }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Text(
                    stringResource(R.string.error_prefix, error ?: ""),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                localPath == null -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
                else -> AsyncImage(
                    model = remember(localPath) {
                        localPath?.let { ImageRequest.Builder(ctx).data(File(it)).build() }
                    },
                    contentDescription = item.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Always-visible translucent arrows; hidden at the first/last edge.
        if (hasPrev) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
                    .size(48.dp),
            )
        }
        if (hasNext) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
                    .size(48.dp),
            )
        }
    }
}

/** A destructive chat action that needs a second confirmation before running. */
private sealed interface ChatConfirmAction {
    data class Delete(val chat: ChatItem) : ChatConfirmAction
    data class Archive(val chat: ChatItem, val archived: Boolean) : ChatConfirmAction
}

/** The D-pad / keyboard keys that fire a click on a focused item (the "OK" button). */
private fun Key.isConfirmKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

/**
 * Long-press context menu for a chat row. A centered Dialog listing the four
 * chat actions (delete / mute / archive / pin), each a focusable Card so the
 * D-pad moves up/down and OK fires it. Back or OK on a dimmed backdrop closes
 * the menu; the caller restores focus to the long-pressed row.
 */
@Composable
private fun ChatContextMenu(
    chat: ChatItem,
    viewingArchive: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onTogglePin: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val firstFocus = remember { FocusRequester() }
    // The long-press that opened this menu is still held when the dialog
    // appears; its release (KeyUp) would otherwise land on the freshly
    // focused first item and fire it immediately (auto-pinning on open).
    //
    // tv-material3 fires onLongClick on the FIRST key repeat (repeatCount==1)
    // rather than a timer, so the opening press keeps emitting repeats
    // (repeatCount>=2) into the menu for as long as the key is held. We must
    // therefore only arm on a *fresh* press (repeatCount==0); swallow every
    // repeat and the opening release, then let a fresh press's KeyUp through.
    var confirmArmed by remember { mutableStateOf(false) }
    // Retry a few frames: the Dialog is a separate window, so the first
    // request can land before its node is attached (same pattern as
    // PhotoFullscreen).
    LaunchedEffect(chat.id) {
        withFrameNanos { }
        repeat(5) {
            try { firstFocus.requestFocus() } catch (_: IllegalStateException) {}
            delay(60)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev ->
                    if (!ev.key.isConfirmKey()) return@onPreviewKeyEvent false
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                // A genuinely new press — arm the menu and
                                // let it reach the focused item.
                                confirmArmed = true
                                false
                            } else {
                                // Key-repeat leftover from the opening
                                // long-press — swallow it.
                                true
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (confirmArmed) { confirmArmed = false; false } else true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) {
                ChatMenuRow(
                    icon = Icons.Default.PushPin,
                    label = stringResource(
                        if (chat.isPinned) R.string.chat_menu_unpin else R.string.chat_menu_pin
                    ),
                    focusRequester = firstFocus,
                    onClick = onTogglePin,
                )
                ChatMenuRow(
                    icon = Icons.Default.VolumeOff,
                    label = stringResource(
                        if (chat.isMuted) R.string.chat_menu_unmute else R.string.chat_menu_mute
                    ),
                    onClick = onToggleMute,
                )
                ChatMenuRow(
                    icon = Icons.Default.Archive,
                    label = stringResource(
                        if (viewingArchive) R.string.chat_menu_unarchive else R.string.chat_menu_archive
                    ),
                    onClick = onToggleArchive,
                )
                ChatMenuRow(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.chat_menu_delete),
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ChatMenuRow(
    icon: ImageVector? = null,
    label: String,
    destructive: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Confirmation dialog for destructive actions (delete / archive). Default
 * focus lands on Cancel so an accidental OK doesn't run the action; OK on
 * Confirm runs it.
 */
@Composable
private fun ChatConfirmDialog(
    action: ChatConfirmAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDelete = action is ChatConfirmAction.Delete
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(action) {
        withFrameNanos { }
        repeat(5) {
            try { cancelFocus.requestFocus() } catch (_: IllegalStateException) {}
            delay(60)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(
                    if (isDelete) R.string.chat_menu_delete_confirm_title
                    else R.string.chat_menu_archive_confirm_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (isDelete) R.string.chat_menu_delete_confirm_text
                    else R.string.chat_menu_archive_confirm_text
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.chat_menu_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.focusRequester(cancelFocus),
            ) {
                Text(stringResource(R.string.chat_menu_cancel))
            }
        },
    )
}
