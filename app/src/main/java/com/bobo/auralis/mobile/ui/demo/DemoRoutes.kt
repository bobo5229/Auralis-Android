package com.bobo.auralis.mobile.ui.demo

internal fun isMainRoute(route: String): Boolean = mainDestinations.any { it.route == route }

internal fun dockRouteFor(route: String): String = when {
    route.startsWith("album/") || route.startsWith("library/") -> "library"
    isMainRoute(route) -> route
    else -> "home"
}
