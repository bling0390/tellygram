@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.telegram.BuildConfig
import tv.telegram.R
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language
import tv.telegram.ui.focus.dpadNavigationSounds
import tv.telegram.ui.home.HomeSpec

/**
 * Settings as a page, rebuilt from the design frames `3239:2418` (default) and
 * `3240:2375` (the "Check for updates" row focused).
 *
 * Geometry is the frames': the list is 268 wide at x=58 with 4dp between items and
 * 14/16 item padding, the pane is 452 wide at x=398, and 72dp separates them. The
 * shell supplies the 58dp side margin, so this screen only adds the 10dp that lifts
 * its content from the shell's 96dp origin to the frame's 106dp.
 *
 * Row rules, straight from the frames: the selected section is a white pill with the
 * dark inverse label; display-only rows (Version, License) are unfilled; the
 * actionable row ("Check for updates") sits on a surface-container panel and turns
 * white while focused. Everything is 4dp rounded.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    /**
     * Shown as the Version row's value. Defaults to the build's own versionName so the
     * page reports what is actually installed; tests inject a fixed value.
     */
    versionName: String = BuildConfig.VERSION_NAME,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(SettingsSection.Accounts) }
    val authState by state.authState.collectAsStateWithLifecycle()
    val user by state.currentUser.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()

    Row(
        // The frame's 10dp: shell content starts at 96, the frame draws from 106.
        modifier = modifier.fillMaxSize().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(HomeSpec.SettingsPaneGap),
    ) {
        Column(
            modifier = Modifier.width(HomeSpec.SettingsListWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsSection.entries.forEach { section ->
                SectionItem(
                    section = section,
                    selected = section == selected,
                    onSelect = { selected = section },
                )
            }
        }

        Column(
            modifier = Modifier.width(HomeSpec.SettingsPaneWidth).dpadNavigationSounds(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selected) {
                SettingsSection.Accounts -> {
                    PaneTitle(stringResource(R.string.settings_account))
                    PaneBody(accountValue(authState, user))
                }

                SettingsSection.About -> {
                    PaneTitle(stringResource(R.string.settings_about))
                    VersionRow(versionName)
                    LicenseRow()
                    CheckForUpdatesRow()
                }

                SettingsSection.PreferredLanguage -> {
                    PaneTitle(stringResource(R.string.settings_preferred_language))
                    Language.entries.forEach { option ->
                        LanguageRow(
                            label = languageLabel(option),
                            selected = option == language,
                            onSelect = { state.setLanguage(option) },
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
}

private enum class SettingsSection(val labelRes: Int, val icon: ImageVector) {
    Accounts(R.string.settings_account, Icons.Default.Person),
    About(R.string.settings_about, Icons.Default.Info),
    PreferredLanguage(R.string.settings_preferred_language, Icons.Default.Translate),
    HelpAndSupport(R.string.settings_help, Icons.Default.HelpOutline),
}

@Composable
private fun SectionItem(section: SettingsSection, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = sectionItemColors(selected)
    Row(
        modifier = Modifier
            .testTag("settings-section-${section.name}")
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onSelect()
                    true
                } else {
                    false
                }
            }
            .onFocusState { focused = it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(section.labelRes),
            color = colors.text,
            style = MaterialTheme.typography.labelLarge,
        )
        if (focused && !selected) {
            Spacer(Modifier.width(0.dp))
        }
    }
}

/** A display-only row: label on the left, value on the right, no fill (per the frame). */
@Composable
private fun DisplayRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxSizeWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = HomeSpec.OnSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun VersionRow(versionName: String) =
    DisplayRow(stringResource(R.string.settings_about_row_version), versionName)

@Composable
private fun LicenseRow() =
    DisplayRow(stringResource(R.string.settings_about_row_license), stringResource(R.string.settings_about_license_value))

/**
 * The frame's actionable row. Focusable, and deliberately inert: pressing OK does
 * nothing yet (product decision, 2026-09-23).
 */
@Composable
private fun CheckForUpdatesRow() {
    var focused by remember { mutableStateOf(false) }
    val colors = actionableRowColors(focused)
    Row(
        modifier = Modifier
            .testTag("settings-check-updates")
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .focusable()
            .onFocusState { focused = it }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxSizeWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_about_row_check_updates),
            color = colors.text,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = actionableRowColors(focused || selected)
    Row(
        modifier = Modifier
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onSelect()
                    true
                } else {
                    false
                }
            }
            .onFocusState { focused = it }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxSizeWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PaneTitle(text: String) {
    Text(
        text = text,
        color = HomeSpec.OnSurface,
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun PaneBody(text: String) {
    Text(
        text = text,
        color = HomeSpec.OnSurfaceVariant.copy(alpha = 0.8f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun accountValue(authState: AuthState, user: TdUser?): String = when {
    user != null && user.phoneNumber.isNotBlank() -> stringResource(
        R.string.settings_account_value_signed_in_with_phone,
        user.displayName, user.id, user.phoneNumber,
    )
    user != null -> stringResource(
        R.string.settings_account_value_signed_in_with_id,
        user.displayName, user.id,
    )
    authState is AuthState.Ready -> stringResource(R.string.settings_account_value_not_signed_in)
    else -> stringResource(R.string.settings_account_value_loading)
}

@Composable
private fun languageLabel(lang: Language): String = when (lang) {
    Language.English -> stringResource(R.string.settings_language_english)
    Language.SimplifiedChinese -> stringResource(R.string.settings_language_simplified_chinese)
    Language.TraditionalChinese -> stringResource(R.string.settings_language_traditional_chinese)
}

/** Focus tracking helper: keeps every row's focus bookkeeping identical. */
private fun Modifier.onFocusState(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { state -> onChange(state.isFocused) }

/** Rows span the pane's 452dp column. */
private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.SettingsPaneWidth))
