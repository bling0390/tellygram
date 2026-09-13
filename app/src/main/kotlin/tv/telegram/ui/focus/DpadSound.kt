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

/**
 * Plays the platform's own key sounds for D-pad presses.
 *
 * Compose's focus system emits none — the View system plays these for free and
 * Compose has no equivalent — so the app has to ask for them. [playSoundEffect]
 * routes through AudioManager's sound-effects channel, which means: no audio
 * assets, the device's "sound effects" setting and the system volume apply, and
 * the sound never mixes into the media stream.
 *
 * [SoundEffectConstants.NAVIGATION_UP] / `_DOWN` / `_LEFT` / `_RIGHT` are the
 * constants the framework itself plays when focus moves under the View system,
 * so this reproduces the stock Android TV feel; OK / Enter gets the click the
 * View system plays for a click.
 *
 * Attach it to whatever sees a surface's key events: the composition root
 * covers the activity window (pages, rail, in-tree overlays), and every Popup
 * or Dialog window needs its own copy because it owns a separate input
 * pipeline. Nothing is consumed, so the focus system still receives every
 * event. Held-key repeats are ignored — otherwise holding a direction machine-
 * guns the sound.
 *
 * Like the View system, this sounds on every press, including presses that find
 * no next focus target (Left on the leftmost item). Switch to detecting the
 * actual focus move if that ever feels wrong.
 */
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
