package tv.telegram.ui.focus

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    withFrameNanos { }
    return runCatching { anchor.requestFocus(); true }.getOrDefault(false)
}

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

fun LazyListState.isFullyVisible(index: Int): Boolean {
    val info = layoutInfo
    return info.visibleItemsInfo.any { item ->
        item.index == index &&
            item.offset >= 0 &&
            item.offset + item.size <= info.viewportEndOffset
    }
}
