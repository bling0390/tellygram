package tv.telegram.ui.home

import androidx.compose.ui.unit.Dp
import tv.telegram.R
import tv.telegram.td.ChatType
import kotlin.math.min

/**
 * The long-press menu's four actions, in the frames' fixed order (3268:1934 / 3268:2692).
 * The set never changes — only the labels and glyphs follow the chat's state.
 */
internal enum class ChatMenuAction { Pin, Mute, Archive, Delete }

/** One row's height and the menu's own height, straight from the frames. */
internal const val ChatMenuItemHeightDp = 40f
internal const val ChatMenuHeightDp = 188f

/** How long OK must be held before the popover opens. */
internal const val ChatMenuLongPressMs = 500L

/**
 * The label for an action in its current state: 置顶/取消置顶, 静音/取消静音,
 * 归档/取消归档. Delete is constant.
 */
internal fun chatMenuLabelRes(
    action: ChatMenuAction,
    pinned: Boolean,
    muted: Boolean,
    archived: Boolean,
): Int = when (action) {
    ChatMenuAction.Pin -> if (pinned) R.string.chat_menu_unpin else R.string.chat_menu_pin
    ChatMenuAction.Mute -> if (muted) R.string.chat_menu_unmute else R.string.chat_menu_mute
    ChatMenuAction.Archive -> if (archived) R.string.chat_menu_unarchive else R.string.chat_menu_archive
    ChatMenuAction.Delete -> R.string.chat_menu_delete
}

/**
 * Where the menu's top edge goes: level with the long-pressed row, but pulled up so it
 * never spills past the bottom of the list (product decision, 2026-09-27).
 */
internal fun chatMenuTop(rowTop: Dp, listHeight: Dp, menuHeight: Dp): Dp =
    min(rowTop.value, (listHeight.value - menuHeight.value).coerceAtLeast(0f)).let { Dp(it) }

/** Which copy a destructive action needs: private, a group I own, a group I'm in, or a channel. */
internal enum class ChatDeleteCase { Private, GroupOwner, GroupMember, Channel }

/**
 * Channel never distinguishes the owner (product, 2026-09-27) — leaving is all anyone gets.
 * Groups do: the creator's frame says "Delete and leave".
 */
internal fun chatDeleteCase(type: ChatType, isOwner: Boolean): ChatDeleteCase = when (type) {
    ChatType.Private -> ChatDeleteCase.Private
    ChatType.Channel -> ChatDeleteCase.Channel
    ChatType.Group -> if (isOwner) ChatDeleteCase.GroupOwner else ChatDeleteCase.GroupMember
    // Saved Messages and any future type: nothing to leave, treat like the private case.
    else -> ChatDeleteCase.Private
}

/** The menu's fourth row — always destructive, worded per case. */
internal fun chatMenuDestructiveLabelRes(case: ChatDeleteCase): Int = when (case) {
    ChatDeleteCase.Private -> R.string.chat_menu_delete
    ChatDeleteCase.GroupOwner -> R.string.chat_menu_delete_and_leave
    ChatDeleteCase.GroupMember, ChatDeleteCase.Channel -> R.string.chat_menu_leave
}

internal fun chatDeleteTitleRes(case: ChatDeleteCase): Int = when (case) {
    ChatDeleteCase.Private -> R.string.chat_delete_title_private
    ChatDeleteCase.GroupOwner -> R.string.chat_delete_title_group_owner
    ChatDeleteCase.GroupMember -> R.string.chat_delete_title_group_member
    ChatDeleteCase.Channel -> R.string.chat_delete_title_channel
}

/** Private and channel bodies take no name; the owner's group body names the group. */
internal fun chatDeleteBodyRes(case: ChatDeleteCase): Int = when (case) {
    ChatDeleteCase.Private -> R.string.chat_delete_body_private
    ChatDeleteCase.GroupOwner -> R.string.chat_delete_body_group_owner
    ChatDeleteCase.GroupMember -> R.string.chat_delete_body_group_member
    ChatDeleteCase.Channel -> R.string.chat_delete_body_channel
}

internal fun chatDeleteConfirmRes(case: ChatDeleteCase): Int = when (case) {
    ChatDeleteCase.Private -> R.string.chat_delete_confirm_private
    ChatDeleteCase.GroupOwner -> R.string.chat_delete_confirm_group_owner
    ChatDeleteCase.GroupMember -> R.string.chat_delete_confirm_group_member
    ChatDeleteCase.Channel -> R.string.chat_delete_confirm_channel
}

/**
 * Only a real private chat carries the "delete for the other side" toggle: Saved Messages has
 * no other side (product, 2026-09-27), and a channel or group has no single counterpart.
 */
internal fun chatDeleteHasRadio(type: ChatType, isOwner: Boolean): Boolean =
    type == ChatType.Private

/**
 * Whether the history is wiped for everyone: the private toggle decides it, a group owner
 * always does (their frame says so), and leaving a group or channel never does.
 */
internal fun chatDeleteRevokes(type: ChatType, isOwner: Boolean, radioChecked: Boolean): Boolean = when {
    // The toggle decides whether the other side loses the history too.
    type == ChatType.Private -> radioChecked
    // A group owner's frame promises the whole history goes; their server-side delete does it.
    type == ChatType.Group -> isOwner
    // A channel owner cannot leave: their "Leave" deletes the channel (product, 2026-09-27).
    type == ChatType.Channel -> isOwner
    else -> false
}
