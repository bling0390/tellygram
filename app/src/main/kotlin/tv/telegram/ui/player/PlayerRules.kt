package tv.telegram.ui.player

import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

/**
 * The list a player walks with prev/next: the indices of the loaded items whose type is [type],
 * in display order.
 *
 * Only what the wall has pulled in so far is in it (decision, 2026-10-16): "next" stops at the
 * end of the loaded list, and it extends by itself when loadMore appends, because callers rebuild
 * this from the same reactive list. The type is a parameter so the video player and the audio
 * player read one rule instead of two that can drift.
 */
internal fun walkIndices(items: List<MediaItem>, type: MediaType): List<Int> =
    items.mapIndexedNotNull { index, item -> index.takeIf { item.type == type } }

/** True when [index] has an earlier entry to step back to. */
internal fun hasPreviousIn(walk: List<Int>, index: Int): Boolean = walk.indexOf(index) > 0

/** True when [index] has a later entry to step forward to. */
internal fun hasNextIn(walk: List<Int>, index: Int): Boolean =
    walk.indexOf(index) in 0 until walk.lastIndex

/**
 * The entry [delta] steps away, or null at either end. An item that is not in the walk at all
 * (its position comes out as -1) keeps the behaviour the video player has always had: +1 lands on
 * the walk's first entry, while -1 has nowhere to go.
 */
internal fun neighbourIn(walk: List<Int>, index: Int, delta: Int): Int? =
    walk.getOrNull(walk.indexOf(index) + delta)
