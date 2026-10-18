@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package tv.telegram.ui.settings

import android.content.ActivityNotFoundException
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

/**
 * Settings as a page, rebuilt from the design frames `3239:2418` (default) and
 * `3240:2375` (the "Check for updates" row focused).
 *
 * Geometry is the frames': the list is 268 wide at x=58 with 4dp between items and
 * 14/16 item padding, the pane is 452 wide at x=398, and 72dp separates them. The
 * shell supplies the 58dp side margin, so this screen only adds the 10dp that lifts
 * its content from the shell's 96dp origin to the frame's 106dp.
 *
 * Row rules, straight from the frames: the section holding focus is a white pill with the
 * dark inverse label, and taking focus selects it (2026-10-15: focus IS the selection, as in
 * the top bar); display-only rows (Version, License) are unfilled; the actionable row
 * ("Check for updates") sits on a surface-container panel and turns white while focused.
 * Everything is 4dp rounded.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    /**
     * Shown as the Version row's value. Defaults to the build's own versionName so the
     * page reports what is actually installed; tests inject a fixed value.
     */
    versionName: String = BuildConfig.VERSION_NAME,
    /**
     * The shell hands the top bar's Down here: it must point at something *this* page
     * actually composes, otherwise the bar's focus search targets an unattached requester
     * and throws (see the settings crasher). It is only the Down target — arriving on the
     * page deliberately does NOT take focus (2026-10-15), so switching to the Setting tab
     * leaves focus on the bar.
     */
    contentEntryFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    /**
     * How the About pane's "Check for updates" asks GitHub. Injectable so the UI tests can
     * answer without a network call (2026-10-18).
     */
    checkForUpdate: suspend (String) -> UpdateCheck = ::fetchLatestRelease,
    /** Opens the download page; null uses the system browser. Returns false when there is none. */
    openDownloadPage: ((String) -> Boolean)? = null,
) {
    var selected by remember { mutableStateOf(SettingsSection.Accounts) }
    var showLogOut by remember { mutableStateOf(false) }
    // Update check: one request at a time, and one dialog for its outcome (2026-10-18).
    var checking by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<UpdateDialog?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Left/Right between the section list and the pane: Right from the list always reaches
    // the pane's entry stop, and a pane row going Left returns to the CHOSEN section (the
    // default geometric search drifted to whichever item happened to sit leftwards).
    // Where a pane row's Left lands. Taking focus selects a section (2026-10-15), so the
    // chosen section is always the one that last held focus: one requester covers both the
    // "return to the chosen section" and the "return where I came from" cases.
    val selectedSectionFocus = remember { FocusRequester() }
    val paneEntryFocus = remember { FocusRequester() }
    // Where OK on a section row lands: the pane's FIRST focusable row (2026-10-15). In
    // Accounts and About that is the single actionable row; in the language pane it is the
    // first language. Deliberately separate from paneEntryFocus, which the Right key uses and
    // which remembers the row the user last visited.
    val paneFirstFocus = remember { FocusRequester() }
    var lastLanguageFocus by remember { mutableStateOf<Language?>(null) }

    // Only panes that compose a focusable row may be the list's Right target: Help has none,
    // and pointing the search at an unattached requester throws.
    val paneEntryOrNull = if (selected == SettingsSection.HelpAndSupport) null else paneEntryFocus

    val authState by state.authState.collectAsStateWithLifecycle()
    val user by state.currentUser.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()

    // Opaque like the home screen's root (2026-10-15): the window has a backdrop, but the
    // NavHost keeps the outgoing page composed while a tab switch settles, and a transparent
    // root let the two content areas draw into the same box.
    Box(modifier = modifier.fillMaxSize().background(HomeSpec.Background)) {
    Row(
        // The frame's 10dp: shell content starts at 96, the frame draws from 106.
        modifier = Modifier
            .fillMaxSize()
            // Only the dialog's own scrim dims the page — no blur: the design's backdrop
            // blur needs API 31+ and cannot reach outside the page from a Dialog window.
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
                    // Focus IS the selection: taking focus switches the pane (2026-10-15).
                    onSelect = { selected = section },
                    // OK enters the pane this section shows. Help composes no focusable row, so
                    // there is nowhere to go and the press stays on the row.
                    onConfirmed = {
                        if (section != SettingsSection.HelpAndSupport) {
                            runCatching { paneFirstFocus.requestFocus() }
                        }
                    },
                    // Only the first entry is the bar's Down target.
                    requester = if (index == 0) contentEntryFocus else null,
                    // A pane row going Left comes back to the chosen section.
                    sectionFocus = if (section == selected) selectedSectionFocus else null,
                    rightRequester = paneEntryOrNull,
                    // The last item ends the list: Down must not escape below it.
                    cancelDown = section == SettingsSection.entries.last(),
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
                    // Four display rows (unfilled, like the About pane's) and the one
                    // actionable row the frames draw.
                    DisplayRow(stringResource(R.string.settings_account_row_id), user?.id?.toString() ?: "—")
                    DisplayRow(stringResource(R.string.settings_account_row_username), orDash(user?.username ?: ""))
                    DisplayRow(
                        stringResource(R.string.settings_account_row_phone),
                        // Shown in full, per the design (no masking).
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
                            // OK on the section row lands on the FIRST language; Right keeps
                            // its remembered-row behaviour through entryRequester.
                            firstRequester = if (index == 0) paneFirstFocus else null,
                            // Right from the list lands on the remembered row, else the first.
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
                    // Display-only, per the product decision: not focusable, and confirming
                    // it does nothing. The frame stacks label over address, but a 48dp row
                    // with 12dp padding only leaves 24dp — so they share one line here.
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
            // The shared component: a real Dialog, so the scrim covers the whole window
            // (top bar included) and the panel centres in the frame, like the design.
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
                        // Release notes can run long; the panel scrolls them.
                        scrollableBody = true,
                        onDismiss = { updateDialog = null },
                        confirmLabel = stringResource(R.string.settings_update_download),
                        cancelLabel = stringResource(R.string.settings_update_later),
                        onConfirm = {
                            // A TV box often has no browser: fall back to showing the address
                            // (it cannot be typed on a remote).
                            val opened = openDownloadPage?.invoke(DownloadPageUrl)
                                ?: openInBrowser(context, DownloadPageUrl)
                            updateDialog = if (opened) null else UpdateDialog.NoBrowser(DownloadPageUrl)
                        },
                        tagPrefix = "settings-update",
                    )
                }

                is UpdateDialog.NoBrowser -> ConfirmDialog(
                    title = stringResource(R.string.settings_update_title),
                    text = stringResource(R.string.settings_update_no_browser, dialog.url),
                    onDismiss = { updateDialog = null },
                    confirmLabel = stringResource(R.string.settings_update_ok),
                    onConfirm = { updateDialog = null },
                    tagPrefix = "settings-update",
                )
            }
        }
    }
}

/** The update flow's dialog states: nothing at all while a check is in flight. */
private sealed interface UpdateDialog {
    data object UpToDate : UpdateDialog
    data class Available(val version: String, val notes: String) : UpdateDialog
    data class Failed(val kind: UpdateFailure) : UpdateDialog
    data class NoBrowser(val url: String) : UpdateDialog
}

private fun UpdateFailure.messageRes(): Int = when (this) {
    UpdateFailure.Network -> R.string.settings_update_failed_network
    UpdateFailure.Http -> R.string.settings_update_failed_http
    UpdateFailure.Parse -> R.string.settings_update_failed_parse
}

/** Opens the download page in whatever browser the box has; false when there is none. */
private fun openInBrowser(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    false
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
    /** Called when this row takes focus: focus IS the selection. */
    onSelect: () -> Unit,
    /** Called when OK lands on it: hand focus to the pane this section shows. */
    onConfirmed: () -> Unit,
    requester: FocusRequester? = null,
    /** The chosen row's own requester, so a pane row's Left can return to it. */
    sectionFocus: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    cancelDown: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = sectionItemColors(focused)
    Row(
        modifier = Modifier
            .testTag("settings-section-${section.name}")
            // The design's list column stretches (alignItems: stretch), so the pill must
            // span the whole 268dp row — without this the Row hugs icon+label and the
            // highlight only covers ~half the row.
            .fillMaxWidth()
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            // The requester must sit before focusable() to be the focus target.
            .let { if (requester != null) it.focusRequester(requester) else it }
            .let { if (sectionFocus != null) it.focusRequester(sectionFocus) else it }
            .focusProperties {
                if (rightRequester != null) right = rightRequester
                down = if (cancelDown) FocusRequester.Cancel else FocusRequester.Default
            }
            // Focus selects, exactly like the top bar: no separate chosen-only look.
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

/** A display-only row: label on the left, value on the right, no fill (per the frame). */
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
        // The value takes the leftover width rather than its natural one, so a long bio
        // ellipsizes there instead of pushing the row past its 48dp line.
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

/**
 * The frame's actionable row: asks GitHub whether a newer release exists (2026-10-18). The label
 * reports the request while it is in flight, so a slow network does not read as a dead button.
 */
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

/** An actionable row: surface-container at rest, white with the dark label once focused. */
@Composable
private fun ActionRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
    /** OK on the section row above lands here: this is the pane's first focusable row. */
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
                // A pane with a single actionable row is a closed list: Up, Down and Right
                // all stop here instead of letting the default search wander off.
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
            // On-surface at rest (matching its label), dark once the row turns white.
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
    /** Carried by the pane's first row, where OK on the section row above lands. */
    firstRequester: FocusRequester? = null,
    // The list is a closed column: Up at the first row, Down at the last one and Right
    // anywhere stop there instead of letting the default search escape to the section list.
    cancelUp: Boolean = false,
    cancelDown: Boolean = false,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // The design puts the white fill on FOCUS only; being the chosen language shows up as
    // the trailing check instead (the top bar keeps a pill for its selection).
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
            // Fixed height, like the frame (452x48): a hug height made the selected row grow
            // by the 24dp check (12+24+12) while the others stayed at 12+20+12.
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
                // White on the dark row, dark on the focused white row.
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

/** Focus tracking helper: keeps every row's focus bookkeeping identical. */
private fun Modifier.onFocusState(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { state -> onChange(state.isFocused) }

/** Rows span the pane's 452dp column. */
private fun Modifier.fillMaxSizeWidth(): Modifier = this.then(Modifier.width(HomeSpec.SettingsPaneWidth))
