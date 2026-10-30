package tv.telegram.ui.focus

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView

@Composable
fun Modifier.dpadNavigationSounds(): Modifier {
    val view = LocalView.current
    return onPreviewKeyEvent { ev ->
        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) {
            soundEffectFor(ev.key)?.let { view.playSoundEffect(it) }
        }
        false
    }
}

private fun soundEffectFor(key: Key): Int? = when (key) {
    Key.DirectionUp -> SoundEffectConstants.NAVIGATION_UP
    Key.DirectionDown -> SoundEffectConstants.NAVIGATION_DOWN
    Key.DirectionLeft -> SoundEffectConstants.NAVIGATION_LEFT
    Key.DirectionRight -> SoundEffectConstants.NAVIGATION_RIGHT
    Key.DirectionCenter, Key.Enter -> SoundEffectConstants.CLICK
    else -> null
}
