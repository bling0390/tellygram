package tv.telegram.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class BackController {

    private class Entry(val priority: Int, val action: () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    val hasActive: Boolean get() = entries.isNotEmpty()

    internal fun register(priority: Int, action: () -> Unit): () -> Unit {
        val entry = Entry(priority, action)
        entries += entry
        return { entries -= entry }
    }

    fun dispatch(): Boolean {
        val top = entries.maxByOrNull { it.priority } ?: return false
        top.action()
        return true
    }
}

val LocalBackController = staticCompositionLocalOf<BackController> {
    error("BackController not provided")
}

object BackPriority {
    const val DRAWER = 80
    const val PLAYER = 70
    const val KEYBOARD = 60
    const val CHATS = 50
    const val RAIL_EXIT = 40
    const val SEARCH_NAV = 30
}

@Composable
fun BackRegistration(
    priority: Int,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    val controller = LocalBackController.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(controller, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val unregister = controller.register(priority) { currentAction() }
        onDispose { unregister() }
    }
}
