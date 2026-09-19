package tv.telegram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import tv.telegram.td.FileDownloadState
import tv.telegram.ui.home.HomeState

/**
 * Telegram-style avatar, shared by the chat list and the top bar.
 *
 * Order of preference, matching the official clients:
 *  1. the downloaded profile photo, cropped to a circle;
 *  2. a coloured circle with the first letter of the name;
 *  3. a type glyph for names with no usable letter (emoji-only titles, and
 *     Saved Messages which has no avatar of its own).
 *
 * The colour comes from Telegram's seven-colour palette indexed by peer id —
 * not by chat type — so a peer keeps the same colour everywhere and two peers of
 * the same kind do not look identical.
 */
private val AvatarColors = listOf(
    Color(0xFFE17076), // red
    Color(0xFF7BC862), // green
    Color(0xFFE5CA77), // yellow
    Color(0xFF65AADD), // blue
    Color(0xFFA695E7), // purple
    Color(0xFFEE7AAE), // pink
    Color(0xFF6EC9CB), // cyan
)

/** Stable colour for a peer id (works for negative ids too, e.g. channels). */
fun avatarColorFor(id: Long): Color {
    val n = AvatarColors.size
    return AvatarColors[(((id % n) + n) % n).toInt()]
}

@Composable
internal fun Avatar(
    photoFileId: Int?,
    name: String,
    id: Long,
    state: HomeState,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fallbackIcon: ImageVector? = null,
) {
    val ctx = LocalContext.current

    // Subscribe to the repo's file states so the photo swaps in the moment its
    // file lands. A one-shot read never recomposed, which made a freshly
    // downloaded avatar appear only when something else happened to recompose
    // the row (it read as the list flashing during focus navigation).
    val fileStates by state.fileStates.collectAsStateWithLifecycle()
    val localPath = photoFileId?.let { (fileStates[it] as? FileDownloadState.Local)?.path }
    var imageFailed by remember(photoFileId, localPath) { mutableStateOf(false) }

    LaunchedEffect(photoFileId) {
        if (photoFileId != null && localPath == null) {
            state.ensureMediaFile(photoFileId, priority = 16)
        }
    }

    val color = remember(id) { avatarColorFor(id) }
    val initial = remember(name) { name.avatarInitial() }
    val glyphSize = remember(size) { size * 0.44f }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        // Memoised on the path: a fresh ImageRequest never equals the previous
        // one, so Coil would restart the load and the avatar would blink.
        val request = remember(localPath) {
            localPath?.let { ImageRequest.Builder(ctx).data(File(it)).build() }
        }

        when {
            request != null && !imageFailed -> AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )

            initial != null -> Text(
                text = initial,
                color = Color.White,
                fontSize = glyphSize.value.sp,
                fontWeight = FontWeight.Bold,
            )

            fallbackIcon != null -> Icon(
                imageVector = fallbackIcon,
                contentDescription = name,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f),
            )

            else -> Text(
                text = "?",
                color = Color.White,
                fontSize = glyphSize.value.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * First letter of a name, uppercased. Scans past leading emoji/punctuation, and
 * returns null when there is no letter at all so the caller can draw a glyph
 * instead of a meaningless character.
 */
private fun String.avatarInitial(): String? =
    firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
