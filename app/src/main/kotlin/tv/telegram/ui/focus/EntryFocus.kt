package tv.telegram.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Hands focus to [target] once, the moment [active] turns true.
 *
 * The shell uses this to land on the top bar's Chat entry when the app enters the shell:
 * cold start on a signed-in account, or the first frame after login. Nothing else requests
 * focus there, and the platform does not pick a first focusable on its own, so without this
 * the D-pad starts with no focus owner at all.
 *
 * Keyed on [active] rather than on composition: moving between the shell's pages keeps it
 * true, so a page switch never re-runs it. That is deliberate — the bar and the settings
 * list both follow "focus is the selection", and neither may yank focus back out of the
 * list the user just moved into.
 *
 * The request waits a frame first: the target attaches during the same composition this
 * effect starts in, so an immediate request finds nothing to focus. One retry covers a
 * slower first frame; past that it gives up silently instead of throwing from a screen
 * that is already gone.
 */
@Composable
fun EntryFocus(active: Boolean, target: FocusRequester?) {
    // `active` false → true again, and focusing the bar then stole the card focus the viewer
    // had just restored. A fresh composition — sign out and back in — starts over, so a real
    // entry still lands on the bar.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active || target == null || entered) return@LaunchedEffect
        entered = true
        withFrameNanos { }
        if (!runCatching { target.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }
}
