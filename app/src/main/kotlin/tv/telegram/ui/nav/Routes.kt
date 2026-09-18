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
}
