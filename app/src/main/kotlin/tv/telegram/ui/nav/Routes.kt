package tv.telegram.ui.nav

object Routes {
    const val COLD_START = "coldStart"
    const val QR_LOGIN = "qrLogin"
    const val HOME = "home"
    // New Figma-driven home (node 1243:1724): its own top nav, no rail.
    const val HOME_SCREEN = "homeScreen"
    const val HOME_SEARCH = "home/search"
    const val HOME_CHATS = "home/chats"
    const val HOME_SETTINGS = "home/settings"
    const val PLAYER = "player/{index}"

    fun player(index: Int): String = "player/$index"

    // Full-screen photo preview, opened by confirming an image card.
    const val PHOTO = "photo/{index}"
    fun photo(index: Int): String = "photo/$index"

    // Addressing a viewer by MESSAGE instead of by index into the chat's loaded media.
    // The album popup needs this: only the album's first message is in that list, the rest
    // live in albumMembers (plan (a), 2026-09-30). The list to walk is handed over through
    // the view model, so prev/next stay consistent with what was opened.
    const val PLAYER_AT = "playerAt/{messageId}"
    fun playerAt(messageId: Long): String = "playerAt/$messageId"
    const val PHOTO_AT = "photoAt/{messageId}"
    fun photoAt(messageId: Long): String = "photoAt/$messageId"
}
