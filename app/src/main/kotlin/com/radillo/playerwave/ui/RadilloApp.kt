package com.radillo.playerwave.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.core.ui.theme.RadilloTheme
import com.radillo.playerwave.ui.adaptive.isWideScreen
import com.radillo.playerwave.ui.effects.EqualizerScreen
import com.radillo.playerwave.ui.library.CollectionDetailScreen
import com.radillo.playerwave.ui.library.CollectionDetailViewModel
import com.radillo.playerwave.ui.library.CollectionKind
import com.radillo.playerwave.ui.library.LibraryScreen
import com.radillo.playerwave.ui.library.LibraryViewModel
import com.radillo.playerwave.ui.library.PlaylistDetailScreen
import com.radillo.playerwave.ui.library.PlaylistDetailViewModel
import com.radillo.playerwave.ui.library.SearchScreen
import com.radillo.playerwave.ui.navigation.TopLevelDestination
import com.radillo.playerwave.ui.permissions.AudioPermissionGate
import com.radillo.playerwave.ui.permissions.RequestNotificationPermissionOnce
import com.radillo.playerwave.ui.player.MiniPlayer
import com.radillo.playerwave.ui.player.NowPlayingScreen
import com.radillo.playerwave.ui.player.PlayerViewModel
import com.radillo.playerwave.ui.player.QueueScreen
import com.radillo.playerwave.ui.settings.SettingsScreen
import com.radillo.playerwave.ui.settings.SettingsViewModel
import com.radillo.playerwave.ui.visualizer.VisualizerScreen

object Routes {
    const val COLLECTION = "collection/{${CollectionDetailViewModel.ARG_KIND}}/{${CollectionDetailViewModel.ARG_KEY}}?title={${CollectionDetailViewModel.ARG_TITLE}}"
    const val PLAYLIST = "playlist/{${PlaylistDetailViewModel.ARG_ID}}"
    const val SEARCH = "search"
    const val QUEUE = "queue"
    const val EQUALIZER = "equalizer"
    const val SETTINGS = "settings"
    const val IMMERSIVE = "visualizer/immersive"

    fun collection(kind: CollectionKind, key: String, title: String): String =
        "collection/${kind.name}/${Uri.encode(key)}?title=${Uri.encode(title)}"

    fun playlist(id: Long): String = "playlist/$id"
}

/**
 * Cáscara de la aplicación: tema (modo claro/oscuro y acento de la carátula), navegación de
 * primer nivel (barra inferior o raíl lateral según el ancho), mini reproductor y destinos
 * anidados (colección, lista, búsqueda, cola, ecualizador, ajustes).
 */
@Composable
fun RadilloApp() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val accent by playerViewModel.accent.collectAsStateWithLifecycle()
    val current = settings ?: return

    val darkTheme = when (current.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val animatedAccent = accent?.let { target ->
        animateColorAsState(target, tween(durationMillis = 700), label = "accent").value
    }

    SystemBarsAppearance(darkTheme)

    RadilloTheme(darkTheme = darkTheme, accent = animatedAccent) {
        AppScaffold(playerViewModel)
    }
}

@Composable
private fun AppScaffold(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()

    val wide = isWideScreen()
    val immersive = currentRoute == Routes.IMMERSIVE
    val showMiniPlayer = playbackState.currentTrack != null && !immersive &&
        currentRoute != TopLevelDestination.NOW_PLAYING.route && currentRoute != Routes.QUEUE

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wide && !immersive) {
            AppNavigationRail(navController = navController, currentRoute = currentRoute)
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (immersive) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (!immersive) {
                    Column {
                        if (showMiniPlayer) {
                            MiniPlayer(
                                state = playbackState,
                                onTogglePlay = playerViewModel::togglePlayPause,
                                onNext = playerViewModel::next,
                                onOpen = { navController.navigateTopLevel(TopLevelDestination.NOW_PLAYING) },
                            )
                        }
                        if (!wide) AppNavigationBar(navController = navController, currentRoute = currentRoute)
                    }
                }
            },
        ) { innerPadding ->
            AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.LIBRARY.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.LIBRARY.route) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            AudioPermissionGate(onGranted = libraryViewModel::rescan) {
                RequestNotificationPermissionOnce()
                LibraryScreen(
                    onOpenAlbum = { navController.navigate(Routes.collection(CollectionKind.ALBUM, it.id.toString(), it.title)) },
                    onOpenArtist = { navController.navigate(Routes.collection(CollectionKind.ARTIST, it.id.toString(), it.name)) },
                    onOpenFolder = { navController.navigate(Routes.collection(CollectionKind.FOLDER, it.path, it.name)) },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    viewModel = libraryViewModel,
                )
            }
        }
        composable(
            route = Routes.COLLECTION,
            arguments = listOf(
                navArgument(CollectionDetailViewModel.ARG_KIND) { type = NavType.StringType },
                navArgument(CollectionDetailViewModel.ARG_KEY) { type = NavType.StringType },
                navArgument(CollectionDetailViewModel.ARG_TITLE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            CollectionDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument(PlaylistDetailViewModel.ARG_ID) { type = NavType.StringType }),
        ) {
            PlaylistDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = { navController.popBackStack() })
        }
        composable(TopLevelDestination.NOW_PLAYING.route) {
            NowPlayingScreen(
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            )
        }
        composable(Routes.QUEUE) {
            QueueScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            )
        }
        composable(TopLevelDestination.VISUALIZER.route) {
            VisualizerScreen(
                immersive = false,
                onEnterImmersive = { navController.navigate(Routes.IMMERSIVE) },
                onExitImmersive = {},
            )
        }
        composable(Routes.IMMERSIVE) {
            VisualizerScreen(
                immersive = true,
                onEnterImmersive = {},
                onExitImmersive = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AppNavigationBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(navController: NavHostController, currentRoute: String?) {
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

/** Iconos de las barras del sistema claros u oscuros según el tema elegido (no según el sistema). */
@Composable
private fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
