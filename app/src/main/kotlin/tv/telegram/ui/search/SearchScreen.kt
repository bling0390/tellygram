@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.telegram.R
import tv.telegram.td.ChatItem
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onOpenChats: () -> Unit,
    railSearchFocus: FocusRequester? = null,
) {
    val chats by viewModel.chatList.collectAsStateWithLifecycle()
    val loaded by viewModel.chatListLoaded.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchSearching by viewModel.searchSearching.collectAsStateWithLifecycle()

    var editBuffer by rememberSaveable { mutableStateOf("") }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(editBuffer) {
        if (editBuffer == searchQuery) return@LaunchedEffect
        delay(250L)
        viewModel.setSearchQuery(editBuffer)
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery != editBuffer && !keyboardOpen) {
            editBuffer = searchQuery
        }
    }

    val searchBarFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardOpen) {
        if (!keyboardOpen) {
            withFrameNanos { }
            try { searchBarFocus.requestFocus() }
            catch (_: IllegalStateException) {}
        }
    }

    BackRegistration(BackPriority.KEYBOARD, enabled = keyboardOpen) { keyboardOpen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 40.dp),
        ) {
            SearchBar(
                query = editBuffer,
                searching = searchSearching,
                fr = searchBarFocus,
                railSearchFocus = railSearchFocus,
                onClick = { keyboardOpen = true },
            )
            Spacer(Modifier.height(12.dp))
            val stats = if (searchQuery.isNotEmpty()) {
                stringResource(
                    R.string.search_stats_matches,
                    chats.count { it.title.contains(searchQuery, ignoreCase = true) },
                    chats.size,
                )
            } else {
                stringResource(R.string.search_hint)
            }
            Text(
                stats,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val results = if (searchQuery.isBlank()) emptyList()
                              else chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
                if (results.isEmpty() && searchQuery.isNotBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_no_match, searchQuery),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                } else {
                    ResultsGrid(
                        items = results,
                        searchBarFocus = searchBarFocus,
                        railSearchFocus = railSearchFocus,
                        onSelect = { id ->
                            viewModel.selectSidebarChat(id)
                            onOpenChats()
                        },
                    )
                }
            }
        }

        if (keyboardOpen) {
            DpadKeyboard(
                onChar = { c -> editBuffer = editBuffer + c },
                onBackspace = { if (editBuffer.isNotEmpty()) editBuffer = editBuffer.dropLast(1) },
                onClear = { editBuffer = "" },
                onSearch = {
                    viewModel.setSearchQuery(editBuffer)
                    keyboardOpen = false
                },
                onClose = { keyboardOpen = false },
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    fr: FocusRequester,
    railSearchFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                shape = RoundedCornerShape(4.dp),
            )
            .focusRequester(fr)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusProperties {
                up = FocusRequester.Cancel
                railSearchFocus?.let { left = it }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (query.isEmpty()) stringResource(R.string.search_placeholder) else query,
                color = if (query.isEmpty()) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (focused) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .width(1.5.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.weight(1f))
            if (searching) {
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    items: List<ChatItem>,
    searchBarFocus: FocusRequester,
    railSearchFocus: FocusRequester?,
    onSelect: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, chat -> chat.id }) { index, chat ->
            ResultCard(
                chat = chat,
                onClick = { onSelect(chat.id) },
                upTarget = if (index < 4) searchBarFocus else null,
                leftTarget = if (index % 4 == 0) railSearchFocus else null,
            )
        }
    }
}

@Composable
private fun ResultCard(
    chat: ChatItem,
    onClick: () -> Unit,
    upTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))),
            Border(BorderStroke(3.dp, Color.White)),
            Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))),
        ),
        modifier = Modifier
            .height(96.dp)
            .focusProperties {
                upTarget?.let { up = it }
                leftTarget?.let { left = it }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = chat.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = chat.type.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DpadKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { firstKey.requestFocus() }
        catch (_: IllegalStateException) {}
    }

    var focusedKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val (row, col) = focusedKey
                    ?: return@onKeyEvent ev.key in setOf(
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight,
                    )
                when (ev.key) {
                    Key.DirectionUp -> row == 0
                    Key.DirectionDown -> row == 5
                    Key.DirectionLeft -> col == 0
                    Key.DirectionRight -> col == 4
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(380.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1D2228)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text(
                    stringResource(R.string.search_type_to_search),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                val rows = listOf(
                    listOf("A", "B", "C", "D", "E"),
                    listOf("F", "G", "H", "I", "J"),
                    listOf("K", "L", "M", "N", "O"),
                    listOf("P", "Q", "R", "S", "T"),
                    listOf("U", "V", "W", "X", "Y"),
                )
                rows.forEachIndexed { rowIdx, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEachIndexed { colIdx, letter ->
                            KeyButton(
                                label = letter,
                                onClick = { onChar(letter[0]) },
                                modifier = Modifier.weight(1f),
                                fr = if (rowIdx == 0 && letter == "A") firstKey else null,
                                onFocusChange = { focused -> if (focused) focusedKey = rowIdx to colIdx },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyButton("Z", onClick = { onChar('Z') }, modifier = Modifier.weight(1f), onFocusChange = { if (it) focusedKey = 5 to 0 })
                    KeyButton(stringResource(R.string.key_backspace), onClick = onBackspace, modifier = Modifier.weight(1f), onFocusChange = { if (it) focusedKey = 5 to 1 })
                    KeyButton(stringResource(R.string.key_clear), onClick = onClear, modifier = Modifier.weight(1f), onFocusChange = { if (it) focusedKey = 5 to 2 })
                    KeyButton(stringResource(R.string.key_search), onClick = onSearch, modifier = Modifier.weight(1.2f), accent = true, onFocusChange = { if (it) focusedKey = 5 to 3 })
                    KeyButton(stringResource(R.string.key_close), onClick = onClose, modifier = Modifier.weight(1f), onFocusChange = { if (it) focusedKey = 5 to 4 })
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fr: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    var keyFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.10f),
        colors = CardDefaults.colors(
            containerColor = when {
                accent -> Color(0xFF3A4B5D)
                keyFocused -> Color.White
                else -> Color(0xFF3A434E).copy(alpha = 0.5f)
            },
        ),
        modifier = modifier
            .height(48.dp)
            .onFocusChanged {
                keyFocused = it.hasFocus
                onFocusChange?.invoke(it.hasFocus)
            }
            .let { if (fr != null) it.focusRequester(fr) else it },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = when {
                    accent -> Color.White
                    keyFocused -> Color(0xFF3C4043)
                    else -> Color(0xFFDADCE0)
                },
                fontSize = if (label.length > 1) 12.sp else 16.sp,
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}
