package com.bobo.auralis.mobile.ui.app

import com.bobo.auralis.mobile.ui.demo.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

internal val mainDestinations = listOf(
    DemoDestination("home", "首页", Icons.Rounded.Home),
    DemoDestination("library", "资料库", Icons.Rounded.LibraryMusic),
    DemoDestination("roam", "漫游", Icons.Rounded.Explore),
    DemoDestination("search", "搜索", Icons.Rounded.Search),
)

/** The back stack remains the only source of route and argument state. */
internal class DemoNavigationState(
    val controller: androidx.navigation.NavHostController,
    private val entry: androidx.compose.runtime.State<androidx.navigation.NavBackStackEntry?>,
) {
    val currentRoute: String get() = entry.value?.destination?.route ?: "home"
    val genreId: String? get() = entry.value?.arguments?.getString("genreId")
    val isMainDestination: Boolean get() = isMainRoute(currentRoute)
    val isPlayer: Boolean get() = currentRoute == "player"
    val selectedDockRoute: String get() = dockRouteFor(currentRoute)

    fun back() { controller.popBackStack() }

    fun selectMainDestination(route: String) {
        controller.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openLibrarySection(route: String) { controller.navigate(route) }
    fun openAlbum(albumId: String) { controller.navigate("album/$albumId") }
    fun openGenre(genreId: String) { controller.navigate("library/genres/$genreId") }
    fun openPlayer(hasCurrentTrack: Boolean) {
        if (hasCurrentTrack) controller.navigate("player")
    }
}

@Composable
internal fun rememberDemoNavigationState(): DemoNavigationState {
    val controller = rememberNavController()
    val entry = controller.currentBackStackEntryAsState()
    return remember(controller, entry) { DemoNavigationState(controller, entry) }
}
