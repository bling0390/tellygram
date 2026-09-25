@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package tv.telegram.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import tv.telegram.R
import tv.telegram.td.AuthState
import tv.telegram.ui.chats.ChatsScreen
import tv.telegram.ui.login.ColdStartScreen
import tv.telegram.ui.login.QrCodeScreen
import tv.telegram.ui.photo.PhotoPreviewScreen
import tv.telegram.ui.player.PlayerScreen
import tv.telegram.ui.search.SearchScreen
import tv.telegram.ui.settings.SettingsScreen
import tv.telegram.ui.home.HomeScreen
import tv.telegram.ui.nav.Routes
import tv.telegram.ui.components.Avatar
import tv.telegram.ui.components.ConfirmDialog
import tv.telegram.ui.components.TopNavBar
import tv.telegram.ui.components.TopNavBarBottomGap
import tv.telegram.ui.components.TopNavBarSideMargin
import tv.telegram.ui.components.TopNavBarTopMargin
import tv.telegram.ui.components.TopNavTab
import tv.telegram.ui.focus.BackController
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.focus.LocalBackController
import tv.telegram.ui.focus.dpadNavigationSounds
import tv.telegram.ui.theme.TellygramTheme
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// AppCompatActivity, not ComponentActivity: the per-app language API
// (AppCompatDelegate.setApplicationLocales) only applies through AppCompat activities.
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            TellygramTheme(themeMode = themeMode) {
                val backController = remember { BackController() }
                CompositionLocalProvider(LocalBackController provides backController) {
                    AppNavHost(viewModel = viewModel, backController = backController)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: MainViewModel, backController: BackController) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Legacy rail destinations only. Home and Settings are SHELL pages — they bring the
    // global top bar and the shell's own margins, so the rail (and its reserved width)
    // must not appear on them.
    val inHome = showsRail(currentRoute)

    // Settings is a NavHost page now (Figma 623:1208 lays the section list and
    // the detail pane out side by side), not a drawer overlay: the rail
    // navigates to it like any other destination and Back pops it.
    // Focus target for the rail's settings item — the drawer returns focus
    // here when it closes.
    val settingsRailFocus = remember { FocusRequester() }
    // Focus target for the rail's chats item. The chat list routes Left
    // here explicitly (see ChatSidebar): directional focus search from a
    // chat row would otherwise land on whichever rail icon is vertically
    // nearest — Search near the top, Settings near the bottom — which
    // reads as random.
    val chatsRailFocus = remember { FocusRequester() }
    // Focus target the rail's Chats item hands Right to: the chat list's
    // selected chat (or the first chat when nothing is selected). Right from
    // the rail would otherwise land on a random row via spatial focus search.
    val chatsListRightFocus = remember { FocusRequester() }
    // Focus target for the rail's search item. The search results grid
    // routes Left here explicitly (see ResultsGrid) for the same reason.
    val searchRailFocus = remember { FocusRequester() }
    // The rail stays focusable everywhere: settings is a page now, so nothing
    // needs the old "drop the rail while the drawer slides" dance.
    var railEnabled by remember { mutableStateOf(true) }

    // Exit-confirmation dialog: Back on the rail (outermost layer) asks
    // first instead of quitting immediately. railFocused comes from
    // NavRail's onFocusChange (hasFocus bubbles up from the focused icon).
    var railFocused by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // True once the dialog has been opened; the close handler restores
    // focus to the current route's rail item (the dialog is a separate
    // window — focus would otherwise be lost when it leaves).
    var exitDialogWasShown by remember { mutableStateOf(false) }
    // Hoisted out of the dialog's onClick lambdas: LocalContext.current is
    // a @Composable read and can't be called from a plain onClick.
    val context = LocalContext.current
    LaunchedEffect(showExitConfirm) {
        if (!showExitConfirm && exitDialogWasShown) {
            delay(300L)
            try {
                when (currentRoute) {
                    Routes.HOME_SEARCH -> searchRailFocus.requestFocus()
                    Routes.HOME_CHATS -> chatsRailFocus.requestFocus()
                    else -> settingsRailFocus.requestFocus()
                }
            } catch (_: IllegalStateException) {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            val target = when (event) {
                NavEvent.GoToQrCode -> Routes.QR_LOGIN
                NavEvent.GoToHome -> Routes.HOME_SCREEN
            }
            navController.navigate(target) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    // Root Back dispatcher: exactly one BackHandler, priority resolved by
    // the controller. Layers register their own action (BackRegistration)
    // instead of relying on "deepest composed BackHandler wins".
    BackHandler(enabled = backController.hasActive) {
        backController.dispatch()
    }

    // Rail focus (outermost layer): Back asks for exit confirmation.
    BackRegistration(BackPriority.RAIL_EXIT, enabled = inHome && railFocused) {
        exitDialogWasShown = true
        showExitConfirm = true
    }
    // Search route: Back returns to the chat list.
    BackRegistration(BackPriority.SEARCH_NAV, enabled = currentRoute == Routes.HOME_SEARCH) {
        navController.navigate(Routes.HOME_CHATS) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Decide the initial destination once, at first composition. Making this
    // reactive to authState would rebuild the whole NavGraph whenever auth
    // leaves Ready (e.g. mid sign-out), resetting the UI to COLD_START and
    // racing the GoToQrCode/GoToHome events. Navigation is event-driven only.
    val startDestination = remember {
        when {
            authState is AuthState.Ready -> Routes.HOME_SCREEN
            authState is AuthState.WaitTdlibParams ||
                authState is AuthState.WaitEncryptionKey ||
                authState is AuthState.Idle -> Routes.COLD_START
            else -> Routes.QR_LOGIN
        }
    }

    // Sidebar is always icon-only: a fixed 96dp rail. No expand/collapse
    // animation, so the NavHost padding never changes and the page never
    // jumps when focus moves between the rail and the content area.
    val railWidth = 84.dp

    // Pages that render inside the app shell: they get the shared top bar and
    // the design's content box (y=96, x=58). Everything else (login, player)
    // keeps the whole window.
    // Both shell pages share the global top bar; each one says which tab is active.
    val isShellPage = currentRoute == Routes.HOME_SCREEN || currentRoute == Routes.HOME_SETTINGS
    val shellTab = if (currentRoute == Routes.HOME_SETTINGS) TopNavTab.Setting else TopNavTab.Chat

    // Focus bridge between the shell's top bar and the home screen's chat list: Down
    // from the bar lands on the selected chat row, and Back from that list returns to
    // the bar. Both live outside this function's branches so the bar and the NavHost
    // destination share the same objects.
    val homeContentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // The settings page's own Down target. The bar must never point at the home
    // screen's requester while that screen is not composed: an unattached
    // FocusRequester throws as soon as the focus search touches it.
    val settingsContentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val homeTopBarFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    // D-pad presses play the platform's navigation sounds for the whole
    // activity window (rail, pages, in-tree overlays). Dialog / Popup windows
    // own separate input pipelines and carry their own copy.
    Box(modifier = Modifier.fillMaxSize().dpadNavigationSounds()) {
        // The shell keeps the top bar outside the NavHost, so it survives
        // navigation: pages swap below it while the bar keeps its focus and
        // state. It also owns the design's content box for those pages.
        Column(modifier = Modifier.fillMaxSize()) {
            if (isShellPage) {
                Spacer(Modifier.height(TopNavBarTopMargin))
                val me by viewModel.currentUser.collectAsStateWithLifecycle()
                // Focus bridge between the shell's top bar and the home screen's chat
                // list: Down from the bar lands on the selected chat row, and Back from
                // that list returns to the bar.
                // Telegram can deliver the profile photo a beat after login; one
                // retry covers that without polling. Until it lands (or if the
                // account has no photo) Avatar draws the initial instead.
                LaunchedEffect(Unit) {
                    if (me?.photoFileId == null) viewModel.refreshMe()
                }
                TopNavBar(
                    selectedTab = shellTab,
                    onTabSelected = { tab ->
                        // Chat and Setting are the two shell pages; search stays inert.
                        val target = when (tab) {
                            TopNavTab.Chat -> Routes.HOME_SCREEN
                            TopNavTab.Setting -> Routes.HOME_SETTINGS
                        }
                        if (currentRoute != target) {
                            navController.navigate(target) {
                                popUpTo(Routes.HOME_SCREEN)
                                launchSingleTop = true
                            }
                        }
                    },
                    onSearchClick = { },
                    modifier = Modifier.padding(horizontal = TopNavBarSideMargin),
                    selectedTabFocus = homeTopBarFocus,
                    contentFocus = if (currentRoute == Routes.HOME_SETTINGS) {
                        settingsContentFocus
                    } else {
                        homeContentFocus
                    },
                    avatar = {
                        Avatar(
                            // Real photo when available, initial otherwise.
                            photoFileId = me?.photoFileId,
                            name = me?.displayName ?: "",
                            id = me?.id ?: 0L,
                            state = viewModel,
                        )
                    },
                )
                Spacer(Modifier.height(TopNavBarBottomGap))
            }

            NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (isShellPage) {
                        Modifier.padding(horizontal = TopNavBarSideMargin)
                    } else {
                        Modifier
                    },
                )
                .padding(start = if (inHome) railWidth else 0.dp),
        ) {
            composable(Routes.COLD_START) { ColdStartScreen(viewModel = viewModel) }

            composable(Routes.QR_LOGIN) { QrCodeScreen(viewModel = viewModel) }

            composable(
                route = Routes.HOME_SCREEN,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                HomeScreen(
                    state = viewModel,
                    onOpenPlayer = { index -> navController.navigate(Routes.player(index)) },
                    onOpenPhoto = { index -> navController.navigate(Routes.photo(index)) },
                    contentEntryFocus = homeContentFocus,
                    topBarFocus = homeTopBarFocus,
                )
            }

            navigation(startDestination = Routes.HOME_CHATS, route = Routes.HOME) {
                composable(
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                    route = Routes.HOME_SEARCH,
                ) {
                    SearchScreen(
                        viewModel = viewModel,
                        railSearchFocus = searchRailFocus,
                        onOpenChats = {
                            navController.navigate(Routes.HOME_CHATS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(
                    route = Routes.HOME_CHATS,
                    enterTransition = { slideInHorizontally(tween(300)) { it } },
                    exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                    popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } },
                ) {
                    ChatsScreen(
                        viewModel = viewModel,
                        railChatsFocus = chatsRailFocus,
                        chatsListRightFocus = chatsListRightFocus,
                        onOpenPlayer = { index -> navController.navigate(Routes.player(index)) },
                    )
                }
                composable(
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                    route = Routes.HOME_SETTINGS,
                ) {
                    SettingsScreen(state = viewModel, contentEntryFocus = settingsContentFocus)
                }
            }

            composable(
                route = Routes.PHOTO,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                PhotoPreviewScreen(
                    state = viewModel,
                    index = entry.arguments?.getInt("index") ?: 0,
                    onClose = { navController.popBackStack() },
                    onNavigateTo = { newIndex ->
                        navController.navigate(Routes.photo(newIndex)) {
                            popUpTo(Routes.PHOTO) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
                // A full-screen video player should cut in, not animate. The
                // NavHost default is a long cross-fade, and PlayerScreen has to
                // build an ExoPlayer (plus inflate a PlayerView) as it enters;
                // running both at once dropped frames on TV hardware.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                PlayerScreen(
                    viewModel = viewModel,
                    index = index,
                    onClose = { navController.popBackStack() },
                    onNavigateTo = { newIndex ->
                        navController.navigate(Routes.player(newIndex)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            }
        }

        if (inHome) {
            NavRail(
                current = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                settingsFocus = settingsRailFocus,
                chatsFocus = chatsRailFocus,
                searchFocus = searchRailFocus,
                chatsRightFocus = chatsListRightFocus,
                // While the settings drawer is open, the rail must not
                // participate in focus search — otherwise pressing Up from
                // the drawer's first row escapes to the rail's chats item.
                // Delayed (railEnabled) so the drawer's first row can claim
                // focus directly instead of the focus system flashing the
                // chat list's "Archived Chats" for a frame.
                enabled = railEnabled,
                onFocusChange = { railFocused = it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(railWidth)
                    .fillMaxHeight(),
            )
        }

        // Exit confirmation (Back on the rail). Confirm quits the app;
        // cancel / Back closes it and focus returns to the rail item.
        if (showExitConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.exit_confirm_title),
                text = stringResource(R.string.exit_confirm_text),
                confirmLabel = stringResource(R.string.exit_confirm_confirm),
                onConfirm = { (context as? Activity)?.finish() },
                cancelLabel = stringResource(R.string.exit_confirm_cancel),
                onDismiss = { showExitConfirm = false },
            )
        }

    }
}

private data class NavEntry(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun NavRail(
    current: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    settingsFocus: FocusRequester? = null,
    chatsFocus: FocusRequester? = null,
    searchFocus: FocusRequester? = null,
    chatsRightFocus: FocusRequester? = null,
    enabled: Boolean = true,
    // Reports whether any rail item holds focus (hasFocus bubbles up from
    // the focused icon). AppNavHost uses it so Back on the rail — the
    // outermost layer — opens an exit-confirmation dialog instead of
    // quitting immediately.
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val entries = listOf(
        NavEntry(Routes.HOME_SEARCH, stringResource(R.string.nav_search), Icons.Default.Search),
        NavEntry(Routes.HOME_CHATS, stringResource(R.string.nav_chats), Icons.Default.Chat),
        NavEntry(Routes.HOME_SETTINGS, stringResource(R.string.nav_settings), Icons.Default.Settings),
    )

    // No unconditional focus grab here: the content area (chat sidebar /
    // search bar) owns initial focus per route. The rail gains focus only
    // when the user deliberately moves Left into it, or when the settings
    // drawer closes (settingsFocus restore). The old Unit-keyed request
    // raced the content screens' own initial-focus requests and usually won
    // (rail composes last in the Box), so returning from the player landed
    // focus on the Search icon instead of the chat list / media grid.

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 24.dp, horizontal = 8.dp)
            .onFocusChanged { onFocusChange?.invoke(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEachIndexed { idx, entry ->
            RailItem(
                entry = entry,
                selected = current == entry.route,
                // enabled gates BOTH focusability (canFocus below) and
                // activation — while the settings drawer is open the rail
                // must not be clickable either (mouse / accessibility).
                onClick = { if (enabled) onSelect(entry.route) },
                fr = when (idx) {
                    0 -> searchFocus // Search — results grid Left lands here
                    1 -> chatsFocus // Chats — chat list Left lands here
                    2 -> settingsFocus
                    else -> null
                },
                // Chats item → Right enters the chat list at the selected /
                // first chat instead of a random row.
                rightTarget = if (idx == 1) chatsRightFocus else null,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun RailItem(
    entry: NavEntry,
    selected: Boolean,
    onClick: () -> Unit,
    fr: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    // Selected and focused items get a true CIRCLE background (CircleShape,
    // not RoundedCornerShape percent — the old 26 was 26% which renders a
    // rounded-rect on a 52dp card). Focus feedback = circle chip + brighter icon.
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1f),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = CardDefaults.shape(
            CircleShape,
            CircleShape,
            CircleShape,
        ),
        border = CardDefaults.border(
            Border.None,
            Border.None,
            Border.None,
        ),
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            // While the settings drawer is open (enabled = false) the rail
            // item drops out of focus search — D-pad can't escape the drawer
            // into the rail. Visuals (selected chip) stay unchanged.
            .focusProperties {
                canFocus = enabled
                rightTarget?.let { right = it }
            }
            .onFocusChanged { focused = it.hasFocus }
            .let { if (fr != null) it.focusRequester(fr) else it },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = when {
                    selected -> MaterialTheme.colorScheme.primary
                    focused -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * True only for the routes that still live behind the legacy navigation rail. The shell
 * pages (HomeScreen, SettingsScreen) draw the global top bar instead — showing both was
 * the bug: the settings page rendered the rail *and* the bar, with the rail's width
 * reserved on top.
 */
internal fun showsRail(route: String?): Boolean =
    route == Routes.HOME_CHATS || route == Routes.HOME_SEARCH
