package tv.telegram.ui.home

import androidx.compose.ui.unit.Dp
import tv.telegram.R
import tv.telegram.td.ChatType
import kotlin.math.min

internal enum class ChatMenuAction { Pin, Mute, Archive, Delete }

internal const val ChatMenuItemHeightDp = 40f
internal const val ChatMenuHeightDp = 188f

internal const val ChatMenuLongPressMs = 500L

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

internal const val ChatMenuAnchorGapDp = 4f

internal const val ChatRowHeightDp = 48f

internal fun chatMenuTop(rowTop: Dp, listHeight: Dp, menuHeight: Dp): Dp {
    val below = rowTop.value + ChatRowHeightDp + ChatMenuAnchorGapDp
    return if (below + menuHeight.value <= listHeight.value) {
        Dp(below)
    } else {
        Dp((rowTop.value - ChatMenuAnchorGapDp - menuHeight.value).coerceAtLeast(0f))
    }
}

internal enum class ChatDeleteCase { Private, GroupOwner, GroupMember, Channel }

internal fun chatDeleteCase(type: ChatType, isOwner: Boolean): ChatDeleteCase = when (type) {
    ChatType.Private -> ChatDeleteCase.Private
    ChatType.Channel -> ChatDeleteCase.Channel
    ChatType.Group -> if (isOwner) ChatDeleteCase.GroupOwner else ChatDeleteCase.GroupMember
    else -> ChatDeleteCase.Private
}

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

internal fun chatDeleteHasRadio(type: ChatType, isOwner: Boolean): Boolean =
    type == ChatType.Private

internal fun chatDeleteRevokes(type: ChatType, isOwner: Boolean, radioChecked: Boolean): Boolean = when {
    type == ChatType.Private -> radioChecked
    type == ChatType.Group -> isOwner
    type == ChatType.Channel -> isOwner
    else -> false
}
