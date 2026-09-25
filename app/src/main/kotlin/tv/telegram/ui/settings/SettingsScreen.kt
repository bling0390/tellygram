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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.blur
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
    /**
     * The shell hands the top bar's Down here: it must point at something *this* page
     * actually composes, otherwise the bar's focus search targets an unattached requester
     * and throws (see the settings crasher).
     */
    contentEntryFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(SettingsSection.Accounts) }
    var showLogOut by remember { mutableStateOf(false) }

    // Left/Right between the section list and the pane: Right from the list always reaches
    // the pane's entry stop, and a pane row going Left returns to the CHOSEN section (the
    // default geometric search drifted to whichever item happened to sit leftwards).
    val selectedSectionFocus = remember { FocusRequester() }
    val paneEntryFocus = remember { FocusRequester() }
    // Which section item last held focus. A pane row going Left returns there — not to the
    // chosen section, which may be a different item entirely — and to the first item when
    // the list has never been visited.
    val rememberedSectionFocus = remember { FocusRequester() }
    var lastSectionFocus by remember { mutableStateOf<SettingsSection?>(null) }
    var lastLanguageFocus by remember { mutableStateOf<Language?>(null) }

    // Only panes that compose a focusable row may be the list's Right target: Help has none,
    // and pointing the search at an unattached requester throws.
    val paneEntryOrNull = if (selected == SettingsSection.HelpAndSupport) null else paneEntryFocus

    // Arriving on the page takes focus: the shell hands the bar's Down target here, and
    // without this the top bar keeps focus and the pane looks inert. Waits a frame — a
    // request during the first composition has nothing to land on — and retries once.
    LaunchedEffect(contentEntryFocus) {
        val target = contentEntryFocus ?: return@LaunchedEffect
        withFrameNanos { }
        if (!runCatching { target.requestFocus() }.isSuccess) {
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }
    val authState by state.authState.collectAsStateWithLifecycle()
    val user by state.currentUser.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
    Row(
        // The frame's 10dp: shell content starts at 96, the frame draws from 106.
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp)
            // The design dims and blurs the page behind the dialog. blur() needs API 31+
            // and is a no-op below that.
            .blur(if (showLogOut) 4.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(HomeSpec.SettingsPaneGap),
    ) {
        Column(
            modifier = Modifier.width(HomeSpec.SettingsListWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsSection.entries.forEachIndexed { index, section ->
                SectionItem(
                    section = section,
                    selected = section == selected,
                    onSelect = { selected = section },
                    // Only the first entry is the bar's Down target.
                    requester = if (index == 0) contentEntryFocus else null,
                    selectedRequester = if (section == selected) selectedSectionFocus else null,
                    rememberedRequester = if (section == (lastSectionFocus ?: SettingsSection.entries.first())) {
                        rememberedSectionFocus
                    } else {
                        null
                    },
                    rightRequester = paneEntryOrNull,
                    // The last item ends the list: Down must not escape below it.
                    cancelDown = section == SettingsSection.entries.last(),
                    onFocus = { lastSectionFocus = section },
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
                        leftRequester = rememberedSectionFocus,
                        entryRequester = paneEntryFocus,
                    )
                }

                SettingsSection.About -> {
                    PaneTitle(stringResource(R.string.settings_about))
                    VersionRow(versionName)
                    LicenseRow()
                    CheckForUpdatesRow(
                        leftRequester = rememberedSectionFocus,
                        entryRequester = paneEntryFocus,
                    )
                }

                SettingsSection.PreferredLanguage -> {
                    PaneTitle(stringResource(R.string.settings_language_title))
                    Language.entries.forEachIndexed { index, option ->
                        LanguageRow(
                            label = languageLabel(option),
                            selected = option == language,
                            onSelect = { state.setLanguage(option) },
                            leftRequester = rememberedSectionFocus,
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
    selected: Boolean,
    onSelect: () -> Unit,
    requester: FocusRequester? = null,
    selectedRequester: FocusRequester? = null,
    rememberedRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    cancelDown: Boolean = false,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // The design's List item has a Focused variant that is the same white pill, so a
    // focused section lights up even before it is chosen.
    val colors = sectionItemColors(selected || focused)
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
            .let { if (selectedRequester != null) it.focusRequester(selectedRequester) else it }
            .let { if (rememberedRequester != null) it.focusRequester(rememberedRequester) else it }
            .focusProperties {
                if (rightRequester != null) right = rightRequester
                down = if (cancelDown) FocusRequester.Cancel else FocusRequester.Default
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
                if (selected || focused) {
                    "settings-section-${section.name}-highlighted"
                } else {
                    "settings-section-${section.name}-plain"
                },
            ),
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
 * The frame's actionable row. Focusable, and deliberately inert: pressing OK does
 * nothing yet (product decision, 2026-09-23).
 */
@Composable
private fun CheckForUpdatesRow(
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
) = ActionRow(
    label = stringResource(R.string.settings_about_row_check_updates),
    tag = "settings-check-updates",
    // Deliberately inert (product decision, 2026-09-23).
    onClick = { },
    leftRequester = leftRequester,
    entryRequester = entryRequester,
)

/** An actionable row: surface-container at rest, white with the dark label once focused. */
@Composable
private fun ActionRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
    leftRequester: FocusRequester? = null,
    entryRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = actionableRowColors(focused)
    Row(
        modifier = Modifier
            .testTag(tag)
            .background(colors.fill, RoundedCornerShape(HomeSpec.Corner))
            .let { if (entryRequester != null) it.focusRequester(entryRequester) else it }
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
