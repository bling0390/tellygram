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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.telegram.BuildConfig
import tv.telegram.R
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language
import tv.telegram.ui.MainViewModel
import tv.telegram.ui.components.ConfirmDialog
import tv.telegram.ui.focus.dpadNavigationSounds

/**
 * Settings as a page, not a drawer — Figma `623:1208` ("settings-about").
 *
 * The frame is a two-pane layout inside the design system's 844 content column:
 *  - left list, 268 wide, 4dp between items, 14/16 item padding, 8dp icon gap,
 *    20dp icons, `title/small` labels (= this app's `labelLarge`: Medium 14/20).
 *    The SELECTED item is the light pill the frame shows focused — on-surface
 *    fill, inverse-on-surface label.
 *  - right pane, 452 wide, 16dp rhythm: `headline/small` title (24/32),
 *    `body/medium` copy (14/20, on-surface-variant at 80%), a 1dp outline
 *    divider at 60%, then `label/medium` (12/16) over `label/large` (14/20).
 *  - list to pane gap: the frame puts the list at x=58 and the pane at x=398,
 *    so 72dp between them.
 *
 * The item list is the operator's call (2026-09-13): Accounts, About, Preferred
 * Language, Help and Support. Theme and Sign out lost their entries there, and
 * Clear cache keeps its whole flow but no longer appears — its dialog state
 * stays below and the row comes back with one line.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val lang by viewModel.language.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val cacheClearProgress by viewModel.cacheClearProgress.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(SettingsSection.Accounts) }
    // Entry removed (2026-09-13), feature kept: nothing sets this true today.
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.width(844.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
        ) {
            SettingsSectionList(
                selected = selected,
                onSelect = { selected = it },
            )
            SettingsPane(
                section = selected,
                viewModel = viewModel,
                authState = authState,
                user = user,
                lang = lang,
            )
        }
    }

    if (showClearCacheConfirm) {
        val progress = cacheClearProgress
        val isProgressing = progress != null && progress < 1f
        val isDone = progress == 1f
        val sizeText = formatCacheSize(cacheSizeBytes)
        val onConfirmAction: (() -> Unit)? = when {
            isProgressing -> null
            isDone -> {
                {
                    showClearCacheConfirm = false
                    viewModel.resetCacheClearProgress()
                    viewModel.refreshCacheSize()
                }
            }
            else -> { { viewModel.clearCache() } }
        }
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_cache_dialog_title),
            text = when {
                isProgressing -> stringResource(
                    R.string.settings_clear_cache_progress,
                    (progress!! * 100).toInt(),
                )
                isDone -> stringResource(R.string.settings_clear_cache_done, sizeText)
                else -> stringResource(R.string.settings_clear_cache_dialog_text, sizeText)
            },
            confirmLabel = when {
                isProgressing -> null
                isDone -> stringResource(R.string.settings_clear_cache_dialog_done_button)
                else -> stringResource(R.string.settings_clear_cache_dialog_confirm)
            },
            onConfirm = onConfirmAction,
            cancelLabel = if (isProgressing) {
                null
            } else {
                stringResource(R.string.settings_clear_cache_dialog_cancel)
            },
            onDismiss = { if (!isProgressing) showClearCacheConfirm = false },
        )
    }
}

/** The operator's list for this page (2026-09-13). */
private enum class SettingsSection(val labelRes: Int, val icon: ImageVector) {
    Accounts(R.string.settings_account, Icons.Default.Person),
    About(R.string.settings_about, Icons.Default.Info),
    PreferredLanguage(R.string.settings_preferred_language, Icons.Default.Translate),
    HelpAndSupport(R.string.settings_help, Icons.Default.HelpOutline),
}

@Composable
private fun SettingsSectionList(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
) {
    // Focus follows the selection, and selecting follows focus: the frame shows
    // the focused row as the one whose pane is on screen.
    val focusers = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        // Wait a frame so the node is attached — requesting in the same frame
        // as composition silently no-ops.
        withFrameNanos { }
        repeat(5) {
            try { focusers[selected]?.requestFocus() } catch (_: IllegalStateException) {}
            delay(60)
        }
    }
    Column(
        modifier = Modifier.width(268.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsSection.entries.forEach { section ->
            SettingsSectionItem(
                section = section,
                onSelect = { onSelect(section) },
                focusRequester = focusers.getValue(section),
            )
        }
    }
}

@Composable
private fun SettingsSectionItem(
    section: SettingsSection,
    onSelect: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(Border.None, Border.None, Border.None),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = if (focused) {
                    MaterialTheme.colorScheme.inverseOnSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(section.labelRes),
                color = if (focused) {
                    MaterialTheme.colorScheme.inverseOnSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsPane(
    section: SettingsSection,
    viewModel: MainViewModel,
    authState: AuthState,
    user: TdUser?,
    lang: Language,
) {
    Column(
        modifier = Modifier.width(452.dp).dpadNavigationSounds(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (section) {
            SettingsSection.Accounts -> {
                PaneTitle(stringResource(R.string.settings_account))
                PaneBody(accountValue(authState, user))
            }
            SettingsSection.About -> {
                PaneTitle(stringResource(R.string.settings_about))
                PaneBody(stringResource(R.string.about_body))
                PaneDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_app_version_label),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            SettingsSection.PreferredLanguage -> {
                PaneTitle(stringResource(R.string.settings_preferred_language))
                Language.entries.forEach { option ->
                    SettingsOptionRow(
                        label = languageLabel(option),
                        selected = option == lang,
                        onClick = { viewModel.setLanguage(option) },
                    )
                }
            }
            SettingsSection.HelpAndSupport -> {
                PaneTitle(stringResource(R.string.settings_help))
                PaneBody(stringResource(R.string.settings_help_body))
            }
        }
    }
}

/** A selectable value inside a pane — same pill focus the list uses. */
@Composable
private fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
            RoundedCornerShape(4.dp),
        ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(Border.None, Border.None, Border.None),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = if (focused) {
                    MaterialTheme.colorScheme.inverseOnSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (focused) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PaneTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun PaneBody(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The frame's 1dp rule: `outline` at 60%. */
@Composable
private fun PaneDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.border.copy(alpha = 0.6f)),
    )
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
        authState is AuthState.Ready -> stringResource(R.string.settings_account_value_not_signed_in)
        else -> stringResource(R.string.settings_account_value_loading)
    }

@Composable
private fun languageLabel(lang: Language): String = when (lang) {
    Language.English -> stringResource(R.string.settings_language_english)
    Language.SimplifiedChinese -> stringResource(R.string.settings_language_simplified_chinese)
    Language.TraditionalChinese -> stringResource(R.string.settings_language_traditional_chinese)
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
