package tv.telegram.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Single source of truth for the Back key.
 *
 * Replaces the previous six scattered `BackHandler` call sites, whose
 * priority was implicit (the deepest-composed enabled handler won). With the
 * controller, every layer registers a back action with an explicit
 * [BackPriority]; on Back, [dispatch] runs the highest-priority registered
 * action. Priority is now a number you can read, not a tree-depth you have
 * to simulate.
 *
 * Registration is scoped to composition (see [BackRegistration]), so a
 * screen's local state (drawer open, controller shown, keyboard open, focus
 * region) naturally gates whether its back action is active — no state has
 * to be hoisted to the root.
 */
@Stable
class BackController {

    private class Entry(val priority: Int, val action: () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    /** True while at least one back action is registered. */
    val hasActive: Boolean get() = entries.isNotEmpty()

    internal fun register(priority: Int, action: () -> Unit): () -> Unit {
        val entry = Entry(priority, action)
        entries += entry
        return { entries -= entry }
    }

    /** Run the highest-priority registered action. Returns false if none. */
    fun dispatch(): Boolean {
        val top = entries.maxByOrNull { it.priority } ?: return false
        top.action()
        return true
    }
}

val LocalBackController = staticCompositionLocalOf<BackController> {
    error("BackController not provided")
}

/**
 * Explicit Back priority. Higher wins. Order mirrors the old implicit
 * layer order: drawer (topmost overlay) > player > keyboard > home focus
 * hierarchy > rail exit-confirm > search-to-chats navigation.
 */
object BackPriority {
    const val DRAWER = 80      // settings / player-info drawer
    const val PLAYER = 70      // player info > controller > exit
    const val KEYBOARD = 60    // search on-screen keyboard
    const val CHATS = 50       // home media/sidebar focus hierarchy
    const val RAIL_EXIT = 40   // rail focus → exit confirmation
    const val SEARCH_NAV = 30  // search route → back to chats
}

/**
 * Register [action] as the Back handler at [priority] while [enabled].
 * The registration lives as long as this composable is in composition, so
 * calling it from a screen whose local state gates `enabled` is all that's
 * needed. The latest [action] lambda is always used (state captured inside
 * it stays fresh via [rememberUpdatedState]).
 */
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
