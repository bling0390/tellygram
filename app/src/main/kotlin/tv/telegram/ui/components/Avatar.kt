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

private val AvatarColors = listOf(
    Color(0xFFE17076),
    Color(0xFF7BC862),
    Color(0xFFE5CA77),
    Color(0xFF65AADD),
    Color(0xFFA695E7),
    Color(0xFFEE7AAE),
    Color(0xFF6EC9CB),
)

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

private fun String.avatarInitial(): String? =
    firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
