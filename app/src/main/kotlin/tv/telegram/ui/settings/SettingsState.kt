package tv.telegram.ui.settings

import kotlinx.coroutines.flow.StateFlow
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language

/** What the settings page needs from the app, so tests can drive it with fakes. */
internal interface SettingsState {
    val authState: StateFlow<AuthState>
    val currentUser: StateFlow<TdUser?>
    val language: StateFlow<Language>
    fun setLanguage(lang: Language)
    /** Real sign-out: TDLib clears its local database, so the next start needs a QR scan. */
    fun logOut()
}
