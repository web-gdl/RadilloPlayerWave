package com.radillo.playerwave.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.radillo.playerwave.R
import com.radillo.playerwave.ui.library.CollectionDetailScreen
import com.radillo.playerwave.ui.library.CollectionDetailViewModel
import com.radillo.playerwave.ui.library.CollectionKind
import com.radillo.playerwave.ui.library.EmptyState
import com.radillo.playerwave.ui.library.LibraryScreen
import com.radillo.playerwave.ui.library.LibraryViewModel
import com.radillo.playerwave.ui.navigation.TopLevelDestination
import com.radillo.playerwave.ui.permissions.AudioPermissionGate
import com.radillo.playerwave.ui.permissions.RequestNotificationPermissionOnce
import com.radillo.playerwave.ui.player.MiniPlayer
import com.radillo.playerwave.ui.player.NowPlayingScreen
import com.radillo.playerwave.ui.player.PlayerViewModel
import com.radillo.playerwave.ui.player.QueueScreen

object Routes {
    const val COLLECTION = "collection/{${CollectionDetailViewModel.ARG_KIND}}/{${CollectionDetailViewModel.ARG_KEY}}?title={${CollectionDetailViewModel.ARG_TITLE}}"
    const val QUEUE = "queue"

    fun collection(kind: CollectionKind, key: String, title: String): String =
        "collection/${kind.name}/${Uri.encode(key)}?title=${Uri.encode(title)}"
}

/**
 * Cáscara de navegación: barra inferior con tres destinos de primer nivel, mini reproductor
 * y destinos anidados (detalle de colección, cola).
 */
@Composable
fun RadilloApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val showMiniPlayer = playbackState.currentTrack != null &&
        currentRoute != TopLevelDestination.NOW_PLAYING.route && currentRoute != Routes.QUEUE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        state = playbackState,
                        onTogglePlay = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                        onOpen = { navController.navigateTopLevel(TopLevelDestination.NOW_PLAYING) },
                    )
                }
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.LIBRARY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.LIBRARY.route) {
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                AudioPermissionGate(onGranted = libraryViewModel::rescan) {
                    RequestNotificationPermissionOnce()
                    LibraryScreen(
                        onOpenAlbum = { navController.navigate(Routes.collection(CollectionKind.ALBUM, it.id.toString(), it.title)) },
                        onOpenArtist = { navController.navigate(Routes.collection(CollectionKind.ARTIST, it.id.toString(), it.name)) },
                        onOpenFolder = { navController.navigate(Routes.collection(CollectionKind.FOLDER, it.path, it.name)) },
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
            composable(TopLevelDestination.NOW_PLAYING.route) {
                NowPlayingScreen(onOpenQueue = { navController.navigate(Routes.QUEUE) })
            }
            composable(Routes.QUEUE) {
                QueueScreen(onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.VISUALIZER.route) {
                EmptyState(stringResource(R.string.placeholder_visualizer))
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
