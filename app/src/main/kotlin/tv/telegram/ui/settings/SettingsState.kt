package tv.telegram.ui.settings

import kotlinx.coroutines.flow.StateFlow
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language

internal interface SettingsState {
    val authState: StateFlow<AuthState>
    val currentUser: StateFlow<TdUser?>
    val language: StateFlow<Language>
    fun setLanguage(lang: Language)
    fun logOut()
}
