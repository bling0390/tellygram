package tv.telegram.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

@Composable
fun EntryFocus(active: Boolean, target: FocusRequester?) {
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
