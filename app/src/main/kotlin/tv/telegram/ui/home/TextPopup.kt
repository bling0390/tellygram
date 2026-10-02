@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package tv.telegram.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.telegram.R
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import tv.telegram.td.FileDownloadState
import tv.telegram.ui.components.Avatar
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** The text popup's palette and cap, from the frames (3588:2212). */
internal object TextPopupSpec {
    val Panel = Color(0xFFE3E2E6)   // inverse-surface
    val Title = Color(0xFF121316)   // surface
    val Body = Color(0xFF43474E)    // surface-variant
    /** Same dim as the dialogs: the frames' blur is not used (product, 2026-10-02). */
    val Scrim = Color(0x991A1C1E)
    /** Shown behind the avatar while its file is on its way (or when there is none). */
    val AvatarFallback = Color(0xFFC4C6CF)

    /** The body stops growing here and scrolls inside instead (product, 2026-10-02). */
    val MaxTextHeight = 400.dp
}

/**
 * A text message's popup: a 412dp light panel, horizontally centred over the same dim the
 * dialogs use, with a fixed title and the message body. The body is never ellipsised — it
 * scrolls inside a capped area instead, so a very long message cannot push the panel
 * off-screen. There are no buttons: Back (or a click outside) closes it.
 */
@Composable
internal fun TextPopup(
    text: String,
    /** Resolved lazily by the screen; null falls back to the literal "Sender:". */
    senderName: String?,
    avatarFileId: Int?,
    /** The sender's id: the shared Avatar uses it for the fallback colour. */
    senderId: Long,
    state: HomeState,
    onDismiss: () -> Unit,
    tagPrefix: String = "text-popup",
) {
    val bodyFocus = remember { FocusRequester() }
    val bodyScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    // The Dialog is a separate window: the first requestFocus can land before its node is
    // attached, so retry a few frames — the same dance the player's popup uses, and there is
    // only one focusable here, so a late retry cannot yank focus away from anything.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        repeat(5) {
            try { bodyFocus.requestFocus() } catch (_: IllegalStateException) { }
            kotlinx.coroutines.delay(40L)
        }
    }

    // Back closes it explicitly: the same proven pattern as the other popups here.
    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TextPopupSpec.Scrim)
                .testTag("$tagPrefix-scrim"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(412.dp)
                    .testTag("$tagPrefix-panel")
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .background(TextPopupSpec.Panel, RoundedCornerShape(4.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Avatar + sender name (frames 3588:2282), replacing the fixed title.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Avatar(
                            photoFileId = avatarFileId,
                            name = senderName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.text_popup_sender_fallback),
                            id = senderId,
                            state = state,
                            modifier = Modifier.testTag("$tagPrefix-avatar"),
                        )
                        Text(
                            text = (senderName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.text_popup_sender_fallback)) + ":",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPopupSpec.Title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("$tagPrefix-sender"),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = TextPopupSpec.MaxTextHeight)
                            .testTag("$tagPrefix-body")
                            .verticalScroll(bodyScroll)
                            // Belt and braces: turn D-pad Up/Down into scroll requests even if
                            // the focus has not landed yet (2026-10-02 report).
                            .onPreviewKeyEvent { ev: androidx.compose.ui.input.key.KeyEvent ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (ev.key) {
                                    Key.DirectionUp -> {
                                        scope.launch { bodyScroll.scrollBy(-stepPx) }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        scope.launch { bodyScroll.scrollBy(stepPx) }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            // Focusable so the TV remote's Up/Down scroll it: verticalScroll
                            // alone only handles drag/wheel. The popup focuses it on open
                            // (product, 2026-10-02).
                            .focusRequester(bodyFocus)
                            .focusable(),
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPopupSpec.Body,
                        )
                    }
                }
            }
        }
    }
}

