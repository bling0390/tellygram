package tv.telegram.ui.player

import tv.telegram.td.MediaItem
import tv.telegram.td.MediaType

internal fun walkIndices(items: List<MediaItem>, type: MediaType): List<Int> =
    items.mapIndexedNotNull { index, item -> index.takeIf { item.type == type } }

internal fun hasPreviousIn(walk: List<Int>, index: Int): Boolean = walk.indexOf(index) > 0

internal fun hasNextIn(walk: List<Int>, index: Int): Boolean =
    walk.indexOf(index) in 0 until walk.lastIndex

internal fun neighbourIn(walk: List<Int>, index: Int, delta: Int): Int? =
    walk.getOrNull(walk.indexOf(index) + delta)
