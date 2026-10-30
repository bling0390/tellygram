package tv.telegram.ui.nav

object Routes {
    const val COLD_START = "coldStart"
    const val QR_LOGIN = "qrLogin"
    const val HOME = "home"
    const val HOME_SCREEN = "homeScreen"
    const val HOME_SEARCH = "home/search"
    const val HOME_CHATS = "home/chats"
    const val HOME_SETTINGS = "home/settings"
    const val PLAYER = "player/{index}"

    fun player(index: Int): String = "player/$index"

    const val AUDIO_PLAYER = "audioPlayer/{index}"
    fun audioPlayer(index: Int): String = "audioPlayer/$index"

    const val PHOTO = "photo/{index}"
    fun photo(index: Int): String = "photo/$index"

    const val PLAYER_AT = "playerAt/{messageId}"
    fun playerAt(messageId: Long): String = "playerAt/$messageId"
    const val PHOTO_AT = "photoAt/{messageId}"
    fun photoAt(messageId: Long): String = "photoAt/$messageId"
}
