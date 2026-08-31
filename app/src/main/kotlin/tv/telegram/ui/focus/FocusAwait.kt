package tv.telegram.ui.focus

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Deterministic bring-into-view + focus, replacing the old
 * `scrollToItem + withFrameNanos (fixed count)` guess. The fixed-frame wait
 * is the wrong tool: on a fast device it wastes time, on slow set-top
 * hardware (cold start, a big list) the node isn't placed yet and
 * [FocusRequester.requestFocus] silently no-ops, leaving the D-pad dead with
 * no feedback.
 *
 * Instead: ask for the item, then await *actual placement* — a snapshotFlow
 * over `layoutInfo.visibleItemsInfo` — capped by [timeoutMs] so a
 * pathological case (empty list, bad index, disposed screen) fails fast
 * rather than hanging a coroutine forever. One frame yield, then focus.
 *
 * Returns false when the item never became visible; callers that care can
 * fall back to `FocusManager.moveFocus`. A false result is logged so a
 * "dead remote" is diagnosable instead of silent.
 */

private const val TAG = "FocusAwait"

private suspend fun awaitVisibleThenFocus(
    scroll: suspend () -> Unit,
    isVisible: () -> Boolean,
    anchor: FocusRequester,
    timeoutMs: Long,
    context: String,
): Boolean {
    try { scroll() } catch (t: Throwable) { Log.w(TAG, "$context: scroll threw", t) }
    val present = withTimeoutOrNull(timeoutMs) {
        snapshotFlow { isVisible() }.first { it }
        true
    } ?: false
    if (!present) {
        Log.w(TAG, "$context: item never became visible within ${timeoutMs}ms")
        return false
    }
    withFrameNanos { } // let the freshly-placed node finish attaching focus
    return runCatching { anchor.requestFocus(); true }.getOrDefault(false)
}

/** Scroll a [LazyListState] to [index] and focus [anchor] once it is laid out. */
suspend fun focusListItem(
    state: LazyListState,
    index: Int,
    anchor: FocusRequester,
    timeoutMs: Long = 450L,
): Boolean = awaitVisibleThenFocus(
    scroll = { state.scrollToItem(index) },
    isVisible = { state.layoutInfo.visibleItemsInfo.any { it.index == index } },
    anchor = anchor,
    timeoutMs = timeoutMs,
    context = "focusListItem(index=$index)",
)

/** Scroll a [LazyGridState] to [index] and focus [anchor] once it is laid out. */
suspend fun focusGridItem(
    state: LazyGridState,
    index: Int,
    anchor: FocusRequester,
    timeoutMs: Long = 450L,
): Boolean = awaitVisibleThenFocus(
    scroll = { state.scrollToItem(index) },
    isVisible = { state.layoutInfo.visibleItemsInfo.any { it.index == index } },
    anchor = anchor,
    timeoutMs = timeoutMs,
    context = "focusGridItem(index=$index)",
)

/** True when [index] is fully within the viewport (no scroll needed). */
fun LazyListState.isFullyVisible(index: Int): Boolean {
    val info = layoutInfo
    return info.visibleItemsInfo.any { item ->
        item.index == index &&
            item.offset >= 0 &&
            item.offset + item.size <= info.viewportEndOffset
    }
}
