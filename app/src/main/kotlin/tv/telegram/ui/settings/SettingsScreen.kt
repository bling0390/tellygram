@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import tv.telegram.BuildConfig
import tv.telegram.R
import tv.telegram.td.AuthState
import tv.telegram.td.TdUser
import tv.telegram.ui.Language
import tv.telegram.ui.focus.dpadNavigationSounds
import tv.telegram.ui.home.HomeSpec
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.HelpOutline
import tv.telegram.ui.components.ConfirmDialog

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    versionName: String = BuildConfig.VERSION_NAME,
    contentEntryFocus: FocusRequester? = null,
    topBarFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    checkForUpdate: suspend (String) -> UpdateCheck = ::fetchLatestRelease,
    openDownloadPage: ((String) -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(SettingsSection.Accounts) }
    var showLogOut by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<UpdateDialog?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedSectionFocus = remember { FocusRequester() }
    val paneEntryFocus = remember { FocusRequester() }
    val paneFirstFocus = remember { FocusRequester() }
    var lastLanguageFocus by remember { mutableStateOf<Language?>(null) }

    val paneEntryOrNull = if (selected == SettingsSection.HelpAndSupport) null else paneEntryFocus

    val authState by state.authState.collectAsStateWithLifecycle()
    val user by state.currentUser.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(HomeSpec.Background)) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(HomeSpec.SettingsPaneGap),
    ) {
        Column(
            modifier = Modifier.width(HomeSpec.SettingsListWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsSection.entries.forEachIndexed { index, section ->
                SectionItem(
                    section = section,
                    onSelect = { selected = section },
                    onConfirmed = {
                        if (section != SettingsSection.HelpAndSupport) {
                            runCatching { paneFirstFocus.requestFocus() }
                        }
                    },
                    requester = if (index == 0) contentEntryFocus else null,
                    upRequester = if (index == 0) topBarFocus else null,
                    sectionFocus = if (section == selected) selectedSectionFocus else null,
                    rightRequester = paneEntryOrNull,
                    cancelDown = section == SettingsSection.entries.last(),
                    cancelLeft = section == SettingsSection.HelpAndSupport,
                    cancelRight = section == SettingsSection.HelpAndSupport,
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
                    DisplayRow(stringResource(R.string.settings_account_row_id), user?.id?.toString() ?: "—")
                    DisplayRow(stringResource(R.string.settings_account_row_username), orDash(user?.username ?: ""))
                    DisplayRow(
                        stringResource(R.string.settings_account_row_phone),
                        orDash(user?.phoneNumber ?: ""),
                    )
                    DisplayRow(stringResource(R.string.settings_account_row_bio), orDash(user?.bio ?: ""))
                    ActionRow(
                        label = stringResource(R.string.settings_account_row_log_out),
                        tag = "settings-log-out",
                        onClick = { showLogOut = true },
                        leftRequester = selectedSectionFocus,
                        entryRequester = paneEntryFocus,
                        firstRequester = paneFirstFocus,
                    )
                }

                SettingsSection.About -> {
                    PaneTitle(stringResource(R.string.settings_about))
                    VersionRow(versionName)
                    LicenseRow()
                    CheckForUpdatesRow(
                        leftRequester = selectedSectionFocus,
                        entryRequester = paneEntryFocus,
                        firstRequester = paneFirstFocus,
                        checking = checking,
                        onClick = {
                            if (!checking) {
                                checking = true
                                scope.launch {
                                    val result = runCatching { checkForUpdate(versionName) }
                                        .getOrElse { UpdateCheck.Failed(UpdateFailure.Network) }
                                    checking = false
                                    updateDialog = when (result) {
                                        is UpdateCheck.UpToDate -> UpdateDialog.UpToDate
                                        is UpdateCheck.Available ->
                                            UpdateDialog.Available(result.version, result.notes)
                                        is UpdateCheck.Failed -> UpdateDialog.Failed(result.kind)
                                    }
                                }
                            }
                        },
                    )
                }

                SettingsSection.PreferredLanguage -> {
                    PaneTitle(stringResource(R.string.settings_language_title))
                    Language.entries.forEachIndexed { index, option ->
                        LanguageRow(
                            label = languageLabel(option),
                            selected = option == language,
                            onSelect = { state.setLanguage(option) },
                            leftRequester = selectedSectionFocus,
                            firstRequester = if (index == 0) paneFirstFocus else null,
                            entryRequester = if (option == (lastLanguageFocus ?: Language.entries.first())) {
                                paneEntryFocus
                            } else {
                                null
                            },
                            onFocus = { lastLanguageFocus = option },
                            cancelUp = index == 0,
                            cancelDown = index == Language.entries.lastIndex,
                        )
                    }
                }

                SettingsSection.HelpAndSupport -> {
                    PaneTitle(stringResource(R.string.settings_help))
                    Row(
                        modifier = Modifier
                            .testTag("settings-help-contact")
                            .fillMaxSizeWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_help_contact_label),
                            color = HomeSpec.OnSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.settings_help_contact_email),
                            color = HomeSpec.OnSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

        if (showLogOut) {
            ConfirmDialog(
                title = stringResource(R.string.settings_logout_dialog_title),
                text = stringResource(R.string.settings_logout_dialog_body),
                confirmLabel = stringResource(R.string.settings_logout_dialog_confirm),
                cancelLabel = stringResource(R.string.settings_logout_dialog_cancel),
                onConfirm = {
                    showLogOut = false
                    state.logOut()
                },
                onDismiss = { showLogOut = false },
                tagPrefix = "settings-logout",
            )
        }

        updateDialog?.let { dialog ->
            when (dialog) {
                is UpdateDialog.UpToDate -> ConfirmDialog(
                    title = stringResource(R.string.settings_update_title),
                    text = stringResource(R.string.settings_update_latest, versionName),
                    onDismiss = { updateDialog = null },
                    confirmLabel = stringResource(R.string.settings_update_ok),
                    onConfirm = { updateDialog = null },
                    tagPrefix = "settings-update",
                )

                is UpdateDialog.Failed -> ConfirmDialog(
                    title = stringResource(R.string.settings_update_title),
                    text = stringResource(dialog.kind.messageRes()),
                    onDismiss = { updateDialog = null },
                    confirmLabel = stringResource(R.string.settings_update_ok),
                    onConfirm = { updateDialog = null },
                    tagPrefix = "settings-update",
                )

                is UpdateDialog.Available -> {
                    val noNotes = stringResource(R.string.settings_update_no_notes)
                    ConfirmDialog(
                        title = stringResource(R.string.settings_update_available, dialog.version),
                        text = if (dialog.notes.isBlank()) noNotes else dialog.notes,
                        scrollableBody = true,
                        onDismiss = { updateDialog = null },
                        confirmLabel = stringResource(R.string.settings_update_download),
                        cancelLabel = stringResource(R.string.settings_update_later),
                        onConfirm = {
                            if (openDownloadPage != null) {
                                openDownloadPage(DownloadPageUrl)
                            } else {
                                openInBrowser(context, DownloadPageUrl)
                            }
                        },
                        tagPrefix = "settings-update",
                    )
                }

            }
        }
    }
}

private sealed interface UpdateDialog {
    data object UpToDate : UpdateDialog
    data class Available(val version: String, val notes: String) : UpdateDialog
    data class Failed(val kind: UpdateFailure) : UpdateDialog
}

private fun UpdateFailure.messageRes(): Int = when (this) {
    UpdateFailure.Network -> R.string.settings_update_failed_network
    UpdateFailure.Http -> R.string.settings_update_failed_http
    UpdateFailure.Parse -> R.string.settings_update_failed_parse
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        android.util.Log.i("SettingsUpdate", "could not open $url: ${it.javaClass.simpleName}")
    }
}

private enum class SettingsSection(val labelRes: Int, val icon: ImageVector) {
    Accounts(R.string.settings_account, Icons.Outlined.Person),
    About(R.string.settings_about, Icons.Outlined.Info),
    PreferredLanguage(R.string.settings_preferred_language, Icons.Outlined.Translate),
    HelpAndSupport(R.string.settings_help, Icons.Outlined.HelpOutline),
}

@Composable
private fun SectionItem(
    section: SettingsSection,
    onSelect: () -> Unit,
    onConfirmed: () -> Unit,
    requester: FocusRequester? = null,
    sectionFocus: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    cancelDown: Boolean = false,
    upRequester: FocusRequester? = null,
    cancelLeft: Boolean = false,
    cancelRight: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = sectionItemColors(focused)
    Row(
        modifier = Modifier
            .testTag("settings-section-${section.name}")
            .fillMaxWidth()
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .let { if (requester != null) it.focusRequester(requester) else it }
            .let { if (sectionFocus != null) it.focusRequester(sectionFocus) else it }
            .focusProperties {
                if (upRequester != null) up = upRequester
                if (rightRequester != null) right = rightRequester
                if (cancelRight) right = FocusRequester.Cancel
                if (cancelLeft) left = FocusRequester.Cancel
                down = if (cancelDown) FocusRequester.Cancel else FocusRequester.Default
            }
            .onFocusState { focused = it; if (it) onSelect() }
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onConfirmed()
                    true
                } else {
                    false
                }
            }
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
            modifier = Modifier.testTag(
                if (focused) {
                    "settings-section-${section.name}-highlighted"
                } else {
                    "settings-section-${section.name}-plain"
                },
            ),
        )
    }
}

@Composable
private fun DisplayRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxSizeWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = HomeSpec.OnSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = HomeSpec.OnSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VersionRow(versionName: String) =
    DisplayRow(stringResource(R.string.settings_about_row_version), versionName)

@Composable
private fun LicenseRow() =
    DisplayRow(stringResource(R.string.settings_about_row_license), stringResource(R.string.settings_about_license_value))

@Composable
private fun CheckForUpdatesRow(
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
    firstRequester: FocusRequester? = null,
    checking: Boolean = false,
    onClick: () -> Unit = {},
) = ActionRow(
    label = stringResource(
        if (checking) R.string.settings_check_updates_checking
        else R.string.settings_about_row_check_updates,
    ),
    tag = "settings-check-updates",
    onClick = onClick,
    leftRequester = leftRequester,
    entryRequester = entryRequester,
    firstRequester = firstRequester,
)

@Composable
private fun ActionRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
    firstRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = actionableRowColors(focused)
    Row(
        modifier = Modifier
            .testTag(tag)
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .let { if (entryRequester != null) it.focusRequester(entryRequester) else it }
            .let { if (firstRequester != null) it.focusRequester(firstRequester) else it }
            .focusProperties {
                if (leftRequester != null) left = leftRequester
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusState { focused = it }
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxSizeWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = paneChevronColor(focused),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
    firstRequester: FocusRequester? = null,
    cancelUp: Boolean = false,
    cancelDown: Boolean = false,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val colors = actionableRowColors(focused)
    Row(
        modifier = Modifier
            .testTag("settings-language-$label")
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .let { if (entryRequester != null) it.focusRequester(entryRequester) else it }
            .let { if (firstRequester != null) it.focusRequester(firstRequester) else it }
            .focusProperties {
                if (leftRequester != null) left = leftRequester
                up = if (cancelUp) FocusRequester.Cancel else FocusRequester.Default
                down = if (cancelDown) FocusRequester.Cancel else FocusRequester.Default
                right = FocusRequester.Cancel
            }
            .onFocusState { focused = it; if (it) onFocus() }
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
            .fillMaxSizeWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = null,
                tint = trailingIconColor(focused),
                modifier = Modifier
                    .size(24.dp)
                    .testTag("settings-language-check-$label"),
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

private fun Modifier.onFocusState(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { state -> onChange(state.isFocused) }

private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.SettingsPaneWidth))
