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
import tv.telegram.ui.components.ConfirmDialog
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.focus.dpadNavigationSounds
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

private const val THUMB_TIMEOUT_MS = 10_000L

@Composable
fun ChatsScreen(
    viewModel: MainViewModel,
    onOpenPlayer: (Int) -> Unit,
    railChatsFocus: FocusRequester? = null,
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
    val playerReturnFocusMessageId by viewModel.playerReturnFocusMessageId.collectAsStateWithLifecycle()
    val returnFocusTarget = playerReturnFocusMessageId?.takeIf { id ->
        mediaItems.any { it.messageId == id }
    }

    val context = LocalContext.current
    LaunchedEffect(chatListError) {
        if (chatListError != null) {
            Toast.makeText(context, chatListError, Toast.LENGTH_LONG).show()
        }
    }

    val selectedChatFocus = remember { FocusRequester() }
    var sidebarFocusTick by remember { mutableIntStateOf(0) }

    var sidebarFocused by remember { mutableStateOf(false) }
    var mediaFocused by remember { mutableStateOf(false) }
    BackRegistration(BackPriority.CHATS, enabled = mediaFocused || sidebarFocused) {
        when {
            mediaFocused -> sidebarFocusTick++
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
            chatsListRightFocus = chatsListRightFocus,
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
    selectedChatFocus: FocusRequester,
    sidebarFocusTick: Int,
    railChatsFocus: FocusRequester? = null,
    chatsListRightFocus: FocusRequester? = null,
    suppressInitialFocus: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // ── Long-press context menu ──
    var menuChat by remember { mutableStateOf<ChatItem?>(null) }
    var confirm by remember { mutableStateOf<ChatConfirmAction?>(null) }
    val menuReturnFocus = remember { FocusRequester() }
    var menuReturnChatId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(menuChat, confirm) {
        if (menuChat == null && confirm == null) {
            val id = menuReturnChatId ?: return@LaunchedEffect
            menuReturnChatId = null
            delay(150)
            val restored = try {
                menuReturnFocus.requestFocus(); true
            } catch (_: IllegalStateException) { false }
            if (!restored) {
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

    LaunchedEffect(sidebarFocusTick, selectedChatId, viewingArchive) {
        if (sidebarFocusTick > 0) {
            val offset = when {
                viewingArchive -> 1
                archiveCount > 0 -> 1
                else -> 0
            }
            val idx = chats.indexOfFirst { it.id == selectedChatId }
            if (idx >= 0) {
                val targetIndex = idx + offset
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
                val fr = if (isFirst) firstFocus else null
                val extraFr = if (isSelected) selectedChatFocus else null
                val isRightEntry = if (selectedChatId != null) isSelected
                    else chat.id == chats.firstOrNull()?.id
                val listRightFr = if (isRightEntry) chatsListRightFocus else null
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
                    blockUp = isFirst,
                )
            }
        }
    }

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
                    confirm = ChatConfirmAction.Archive(chat, archived = true)
                } else {
                    viewModel.toggleChatArchive(chat.id, archived = false)
                }
            },
            onTogglePin = {
                menuChat = null
                viewModel.toggleChatPin(chat.id, inArchive = viewingArchive, pinned = !chat.isPinned)
            },
        )
    }

    confirm?.let { action ->
        ChatConfirmDialog(
            action = action,
            onConfirm = {
                when (action) {
                    is ChatConfirmAction.Delete ->
                    viewModel.deleteChat(action.chat, revoke = false)
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
    railChatsFocus: FocusRequester? = null,
    blockUp: Boolean = false,
) {
    val ctx = LocalContext.current
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
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
                        text = chat.lastMessageText ?: "",
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
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

private fun ChatType.typeIcon(): ImageVector? = when (this) {
    ChatType.Private -> Icons.Default.Person
    ChatType.Group -> Icons.Default.Groups
    ChatType.Channel -> Icons.Default.Campaign
    else -> null
}

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
    returnFocusMessageId: Long? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {

    var openedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var photoReturnMessageId by remember { mutableStateOf<Long?>(null) }
    val photoCardFocus = remember { FocusRequester() }
    var leftEdgeFocused by remember { mutableStateOf(false) }

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

    val returnCardFocus = remember { FocusRequester() }
    LaunchedEffect(returnFocusMessageId, items.firstOrNull()?.messageId) {
        val target = returnFocusMessageId ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.messageId == target }
        if (idx >= 0) {
            var focused = false
            for (attempt in 1..5) {
                focused = focusGridItem(gridState, idx, returnCardFocus)
                if (focused) break
                delay(100)
            }
            viewModel.consumePlayerReturnFocus()
        }
    }

    LaunchedEffect(openedIndex, photoReturnMessageId, items.firstOrNull()?.messageId) {
        val target = photoReturnMessageId ?: return@LaunchedEffect
        if (openedIndex != null) return@LaunchedEffect
        val idx = items.indexOfFirst { it.messageId == target }
        if (idx >= 0) {
            focusGridItem(gridState, idx, photoCardFocus)
            photoReturnMessageId = null
        }
    }

    val context = LocalContext.current
    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }
    DisposableEffect(Unit) {
        onDispose {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { previewPlayer.release() },
                600L,
            )
        }
    }
    var focusedMessageId by remember { mutableStateOf<Long?>(null) }
    var previewingMessageId by remember { mutableStateOf<Long?>(null) }
    var previewLoadingMessageId by remember { mutableStateOf<Long?>(null) }
    var previewReadyMessageId by remember { mutableStateOf<Long?>(null) }

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
        previewPlayer.stop()
        previewingMessageId = null
        previewLoadingMessageId = null
        previewReadyMessageId = null
        val id = focusedMessageId
        if (id == null) return@LaunchedEffect
        val item = items.firstOrNull { it.messageId == id } ?: return@LaunchedEffect
        if (item.type != MediaType.Video) return@LaunchedEffect
        delay(2500)
        if (focusedMessageId != id) return@LaunchedEffect
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

    LaunchedEffect(openedIndex, items.size) {
        val idx = openedIndex ?: return@LaunchedEffect
        if (idx >= items.size - 8 && !viewModel.mediaExhausted.value) {
            viewModel.loadMoreMedia()
        }
    }

    if (openedIndex != null) {
        val idx = openedIndex!!
        if (idx in items.indices) {
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
            contentPadding = PaddingValues(vertical = 20.dp),
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { ev ->
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
                val isReturnTarget = item.messageId == returnFocusMessageId
                val isPhotoReturnTarget = item.messageId == photoReturnMessageId
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
    blockUp: Boolean = false,
) {
    val ctx = LocalContext.current
    val thumbId = item.thumbnailFileId
    val fileStates by viewModel.fileRepo.states.collectAsStateWithLifecycle()
    val thumbState = if (thumbId != null) {
        (fileStates[thumbId] as? FileDownloadState.Local)?.path
    } else null
    val thumbRequest = remember(thumbState) {
        thumbState?.let {
            ImageRequest.Builder(ctx).data(File(it)).crossfade(true).build()
        }
    }
    var thumbTimedOut by remember(item.messageId) { mutableStateOf(false) }
    LaunchedEffect(thumbId, thumbState) {
        thumbTimedOut = false
        if (thumbId == null) {
            thumbTimedOut = true
            return@LaunchedEffect
        }
        if (thumbState != null) return@LaunchedEffect
        viewModel.ensureMediaFile(thumbId, priority = 16)
        delay(THUMB_TIMEOUT_MS)
        if (viewModel.fileStateFor(thumbId) !is FileDownloadState.Local) {
            thumbTimedOut = true
        }
    }
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.10f),
        glow = CardDefaults.glow(
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
            if (item.type == MediaType.Video && !previewing && thumbState != null) {
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
            .dpadNavigationSounds()
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

private sealed interface ChatConfirmAction {
    data class Delete(val chat: ChatItem) : ChatConfirmAction
    data class Archive(val chat: ChatItem, val archived: Boolean) : ChatConfirmAction
}

private fun Key.isConfirmKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

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
    var confirmArmed by remember { mutableStateOf(false) }
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
                .dpadNavigationSounds()
                .onPreviewKeyEvent { ev ->
                    if (!ev.key.isConfirmKey()) return@onPreviewKeyEvent false
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                confirmArmed = true
                                false
                            } else {
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

@Composable
private fun ChatConfirmDialog(
    action: ChatConfirmAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDelete = action is ChatConfirmAction.Delete
    ConfirmDialog(
        title = stringResource(
            if (isDelete) R.string.chat_menu_delete_confirm_title
            else R.string.chat_menu_archive_confirm_title
        ),
        text = stringResource(
            if (isDelete) R.string.chat_menu_delete_confirm_text
            else R.string.chat_menu_archive_confirm_text
        ),
        confirmLabel = stringResource(R.string.chat_menu_confirm),
        onConfirm = onConfirm,
        cancelLabel = stringResource(R.string.chat_menu_cancel),
        onDismiss = onCancel,
    )
}
