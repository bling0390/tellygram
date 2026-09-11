@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.telegram.BuildConfig
import tv.telegram.R
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.ThemeMode
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val lang by viewModel.language.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val cacheClearProgress by viewModel.cacheClearProgress.collectAsStateWithLifecycle()

    var showAbout by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Wait a frame so the node is actually attached — requesting in the
        // same frame as composition can silently no-op.
        withFrameNanos { }
        firstFocus.requestFocus()
    }
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    // List-edge focus trap: while focus sits on the first row, DirectionUp
    // is consumed (stay put); on the last row, DirectionDown is consumed.
    // Without this, Compose's global directional search can escape the
    // drawer into the page behind it (media grid / sidebar).
    var firstRowFocused by remember { mutableStateOf(false) }
    var lastRowFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        when {
                            ev.key == Key.DirectionUp && firstRowFocused -> true
                            ev.key == Key.DirectionDown && lastRowFocused -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            ) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.settings_account),
                        value = accountValue(authState, user),
                        onClick = {  },
                        fr = firstFocus,
                        onFocusChange = { firstRowFocused = it },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Translate,
                        title = stringResource(R.string.settings_language),
                        value = languageLabel(lang),
                        onClick = { viewModel.setLanguage(lang.next()) },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_theme),
                        value = themeLabel(theme),
                        onClick = { viewModel.setTheme(theme.next()) },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_about),
                        value = stringResource(R.string.settings_about_value, BuildConfig.VERSION_NAME),
                        onClick = { showAbout = true },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = stringResource(R.string.settings_clear_cache),
                        value = formatCacheSize(cacheSizeBytes),
                        onClick = { showClearCacheConfirm = true },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Logout,
                        title = stringResource(R.string.settings_signout),
                        value = stringResource(R.string.settings_signout_value),
                        onClick = { showLogoutConfirm = true },
                        danger = true,
                        onFocusChange = { lastRowFocused = it },
                    )
                }
            }
        }

        if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_signout_dialog_title)) },
            text = { Text(stringResource(R.string.settings_signout_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.realSignOut()
                    showLogoutConfirm = false
                }) {
                    Text(stringResource(R.string.settings_signout_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.settings_signout_dialog_cancel))
                }
            },
        )
    }

    if (showClearCacheConfirm) {
        val progress = cacheClearProgress
        val isProgressing = progress != null && progress < 1f
        val isDone = progress == 1f
        val sizeText = formatCacheSize(cacheSizeBytes)
        AlertDialog(
            onDismissRequest = { if (!isProgressing) showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = {
                when {
                    isProgressing -> Text(
                        stringResource(
                            R.string.settings_clear_cache_progress,
                            (progress!! * 100).toInt(),
                        ),
                    )
                    isDone -> Text(stringResource(R.string.settings_clear_cache_done, sizeText))
                    else -> Text(stringResource(R.string.settings_clear_cache_dialog_text, sizeText))
                }
            },
            confirmButton = {
                when {
                    isProgressing -> Unit
                    isDone -> TextButton(onClick = {
                        showClearCacheConfirm = false
                        viewModel.resetCacheClearProgress()
                        viewModel.refreshCacheSize()
                    }) {
                        Text(stringResource(R.string.settings_clear_cache_dialog_done_button))
                    }
                    else -> TextButton(onClick = {
                        viewModel.clearCache()
                    }) {
                        Text(stringResource(R.string.settings_clear_cache_dialog_confirm))
                    }
                }
            },
            dismissButton = {
                if (!isProgressing) {
                    TextButton(onClick = { showClearCacheConfirm = false }) {
                        Text(stringResource(R.string.settings_clear_cache_dialog_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun accountValue(authState: AuthState, user: TdUser?): String =
    when {
        user != null -> {
            val name = user.displayName
            when {
                user.phoneNumber.isNotBlank() -> stringResource(
                    R.string.settings_account_value_signed_in_with_phone,
                    name, user.id, user.phoneNumber,
                )
                else -> stringResource(
                    R.string.settings_account_value_signed_in_with_id,
                    name, user.id,
                )
            }
        }
        authState is AuthState.WaitQrCode -> stringResource(R.string.settings_account_value_not_signed_in)
        else -> stringResource(R.string.settings_account_value_loading)
    }

@Composable
private fun languageLabel(lang: Language): String = when (lang) {
    Language.English -> stringResource(R.string.settings_language_english)
    Language.SimplifiedChinese -> stringResource(R.string.settings_language_simplified_chinese)
    Language.TraditionalChinese -> stringResource(R.string.settings_language_traditional_chinese)
}

@Composable
private fun themeLabel(theme: ThemeMode): String = when (theme) {
    ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
    ThemeMode.Light -> stringResource(R.string.settings_theme_light)
    ThemeMode.System -> stringResource(R.string.settings_theme_system)
}

private fun Language.next(): Language = when (this) {
    Language.English -> Language.SimplifiedChinese
    Language.SimplifiedChinese -> Language.TraditionalChinese
    Language.TraditionalChinese -> Language.English
}

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.Dark -> ThemeMode.Light
    ThemeMode.Light -> ThemeMode.System
    ThemeMode.System -> ThemeMode.Dark
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    value: String,
    onClick: () -> Unit,
    fr: FocusRequester? = null,
    danger: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        // 聚焦效果与侧边栏一致：无缩放、无边框，仅背景色变化（胶囊形填充）。
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (onFocusChange != null) Modifier.onFocusChanged { onFocusChange(it.hasFocus) } else Modifier)
            .let { if (fr != null) it.focusRequester(fr) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(0.6f).height(300.dp),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.about_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.app_full_name),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.about_body),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_repo),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.about_close_hint),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
