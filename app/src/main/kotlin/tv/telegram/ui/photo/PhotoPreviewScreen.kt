@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import tv.telegram.td.FileDownloadState
import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.home.HomeState

/** Indices of the photo items, which is what Left/Right step through here. */
internal fun photoIndices(items: List<MediaItem>): List<Int> =
    items.mapIndexedNotNull { i, m -> if (m.type == MediaType.Photo) i else null }

/**
 * Full-screen photo preview: what confirming an image card opens. Left/Right move to
 * the neighbouring photo (never onto a video), Back returns to the grid.
 */
@Composable
internal fun PhotoPreviewScreen(
    state: HomeState,
    index: Int,
    onClose: () -> Unit,
    onNavigateTo: (Int) -> Unit,
) {
    val items by state.mediaItems.collectAsStateWithLifecycle()
    val fileStates by state.fileStates.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(index, items.size) {
        if (index !in items.indices) onClose()
    }
    // Same contract as the player: tell the grid which card was open so focus can go
    // back to it. Out-of-range closes are not a return, so they do not record one.
    if (index !in items.indices) return
    val item = items[index]

    // Same contract as the player: tell the grid which card was open so focus can go
    // back to it. Out-of-range closes are not a return, so they do not record one.
    BackRegistration(BackPriority.PLAYER, enabled = true) {
        state.setPlayerReturnFocus(item.messageId)
        onClose()
    }
    val localPath = (fileStates[item.fileId] as? FileDownloadState.Local)?.path
        ?: item.localPath
        ?: item.thumbnailLocalPath

    LaunchedEffect(item.fileId) {
        if (localPath == null) state.ensureMediaFile(item.fileId, priority = 16)
    }

    fun step(delta: Int) {
        val photos = photoIndices(items)
        val next = photos.getOrNull(photos.indexOf(index) + delta) ?: return
        onNavigateTo(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> { step(-1); true }
                        Key.DirectionRight -> { step(1); true }
                        else -> false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (localPath != null) {
            val ctx = LocalContext.current
            AsyncImage(
                model = remember(localPath) { ImageRequest.Builder(ctx).data(File(localPath)).build() },
                contentDescription = item.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
