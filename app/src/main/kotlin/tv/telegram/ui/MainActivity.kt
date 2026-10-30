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
import tv.telegram.ui.audio.AudioPlayerScreen
import tv.telegram.ui.components.TopNavTab
import tv.telegram.ui.focus.BackController
import tv.telegram.ui.focus.BackPriority
import tv.telegram.ui.focus.BackRegistration
import tv.telegram.ui.focus.EntryFocus
import tv.telegram.ui.focus.LocalBackController
import tv.telegram.ui.focus.dpadNavigationSounds
import tv.telegram.ui.theme.TellygramTheme
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.telegram.td.MediaType

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
    LaunchedEffect(navController) {
        navController.enableOnBackPressed(false)
        android.util.Log.i("HomeBack", "NavHost back disabled; the controller owns Back")
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inHome = showsRail(currentRoute)

    val settingsRailFocus = remember { FocusRequester() }
    val chatsRailFocus = remember { FocusRequester() }
    val chatsListRightFocus = remember { FocusRequester() }
    val searchRailFocus = remember { FocusRequester() }
    var railEnabled by remember { mutableStateOf(true) }

    var railFocused by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var exitDialogWasShown by remember { mutableStateOf(false) }
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

    LaunchedEffect(backController.hasActive) {
        android.util.Log.i("HomeBack", "backController.hasActive -> ${backController.hasActive}")
    }
    BackHandler(enabled = backController.hasActive) {
        android.util.Log.i("HomeBack", "BackHandler fired; hasActive=${backController.hasActive}")
        backController.dispatch()
    }

    BackRegistration(BackPriority.RAIL_EXIT, enabled = inHome && railFocused) {
        exitDialogWasShown = true
        showExitConfirm = true
    }
    BackRegistration(BackPriority.SEARCH_NAV, enabled = currentRoute == Routes.HOME_SEARCH) {
        navController.navigate(Routes.HOME_CHATS) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val startDestination = remember { authStartRoute(authState) }

    LaunchedEffect(authState) {
        if (authState is tv.telegram.td.AuthState.Ready) return@LaunchedEffect
        val target = authStartRoute(authState)
        if (currentRoute != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val railWidth = 84.dp

    val isShellPage = currentRoute == Routes.HOME_SCREEN || currentRoute == Routes.HOME_SETTINGS

    val homeContentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val settingsContentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val homeTopBarFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val settingTabFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().dpadNavigationSounds()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isShellPage) {
                Spacer(Modifier.height(TopNavBarTopMargin))
                val me by viewModel.currentUser.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    if (me?.photoFileId == null) viewModel.refreshMe()
                }
                TopNavBar(
                    onTabSelected = { tab ->
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
                    onTabConfirmed = { tab ->
                        runCatching {
                            contentEntryFor(tab, homeContentFocus, settingsContentFocus).requestFocus()
                        }
                    },
                    onSearchClick = { },
                    modifier = Modifier.padding(horizontal = TopNavBarSideMargin),
                    selectedTabFocus = homeTopBarFocus,
                    settingTabFocus = settingTabFocus,
                    contentFocus = if (currentRoute == Routes.HOME_SETTINGS) {
                        settingsContentFocus
                    } else {
                        homeContentFocus
                    },
                    avatar = {
                        Avatar(
                            photoFileId = me?.photoFileId,
                            name = me?.displayName ?: "",
                            id = me?.id ?: 0L,
                            state = viewModel,
                        )
                    },
                )
                Spacer(Modifier.height(TopNavBarBottomGap))
            }

            EntryFocus(active = isShellPage, target = homeTopBarFocus)

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
                    onOpenMediaItem = { item ->
                        navController.navigate(
                            if (item.type == MediaType.Photo) Routes.photoAt(item.messageId)
                            else Routes.playerAt(item.messageId)
                        )
                    },
                    onOpenPhoto = { index -> navController.navigate(Routes.photo(index)) },
                    onOpenAudio = { index -> navController.navigate(Routes.audioPlayer(index)) },
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
                    SettingsScreen(
                        state = viewModel,
                        contentEntryFocus = settingsContentFocus,
                        topBarFocus = settingTabFocus,
                    )
                }
            }

            composable(
                route = Routes.PHOTO_AT,
                arguments = listOf(navArgument("messageId") { type = NavType.LongType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val messageId = entry.arguments?.getLong("messageId") ?: 0L
                val context by viewModel.viewerContext.collectAsStateWithLifecycle()
                val photos = remember(context, messageId) {
                    context?.items.orEmpty().filter { it.type == MediaType.Photo }
                }
                val start = photos.indexOfFirst { it.messageId == messageId }.coerceAtLeast(0)
                if (photos.isEmpty()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    PhotoPreviewScreen(
                        state = viewModel,
                        index = start,
                        overrideItems = photos,
                        onClose = {
                            viewModel.clearViewerContext()
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable(
                route = Routes.PHOTO,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                PhotoPreviewScreen(
                    state = viewModel,
                    index = entry.arguments?.getInt("index") ?: 0,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PLAYER_AT,
                arguments = listOf(navArgument("messageId") { type = NavType.LongType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val messageId = entry.arguments?.getLong("messageId") ?: 0L
                val context by viewModel.viewerContext.collectAsStateWithLifecycle()
                val list = remember(context, messageId) {
                    context?.items.orEmpty().filter { it.type != MediaType.Photo }
                }
                val start = list.indexOfFirst { it.messageId == messageId }.coerceAtLeast(0)
                if (list.isEmpty()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    PlayerScreen(
                        viewModel = viewModel,
                        index = start,
                        items = list,
                        onClose = {
                            viewModel.clearViewerContext()
                            navController.popBackStack()
                        },
                        onNavigateTo = { newIndex ->
                            list.getOrNull(newIndex)?.let { next ->
                                navController.navigate(Routes.playerAt(next.messageId)) {
                                    popUpTo(Routes.PLAYER_AT) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
            }

            composable(
                route = Routes.AUDIO_PLAYER,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                AudioPlayerScreen(
                    viewModel = viewModel,
                    index = index,
                    onClose = { navController.popBackStack() },
                    onNavigateTo = { newIndex ->
                        navController.navigate(Routes.audioPlayer(newIndex)) {
                            popUpTo(Routes.AUDIO_PLAYER) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
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
                enabled = railEnabled,
                onFocusChange = { railFocused = it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(railWidth)
                    .fillMaxHeight(),
            )
        }

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
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val entries = listOf(
        NavEntry(Routes.HOME_SEARCH, stringResource(R.string.nav_search), Icons.Default.Search),
        NavEntry(Routes.HOME_CHATS, stringResource(R.string.nav_chats), Icons.Default.Chat),
        NavEntry(Routes.HOME_SETTINGS, stringResource(R.string.nav_settings), Icons.Default.Settings),
    )

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
                onClick = { if (enabled) onSelect(entry.route) },
                fr = when (idx) {
                    0 -> searchFocus
                    1 -> chatsFocus
                    2 -> settingsFocus
                    else -> null
                },
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

internal fun showsRail(route: String?): Boolean =
    route == Routes.HOME_CHATS || route == Routes.HOME_SEARCH

internal fun authStartRoute(state: tv.telegram.td.AuthState): String = when {
    state is AuthState.Ready -> Routes.HOME_SCREEN
    state is AuthState.WaitTdlibParams ||
        state is AuthState.WaitEncryptionKey ||
        state is AuthState.Idle -> Routes.COLD_START
    else -> Routes.QR_LOGIN
}

internal fun contentEntryFor(
    tab: TopNavTab,
    home: FocusRequester,
    settings: FocusRequester,
): FocusRequester = when (tab) {
    TopNavTab.Chat -> home
    TopNavTab.Setting -> settings
}
