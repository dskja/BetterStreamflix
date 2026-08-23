package com.betterstreamflix.cast

/**
 * Cast route selector — manages media route selection for
 * casting to external devices.
 */
object CastRouteSelector {

    private val routes = mutableListOf<CastRoute>()
    private var selectedRoute: CastRoute? = null

    data class CastRoute(
        val id: String,
        val name: String,
        val description: String,
        val deviceType: CastManager.DeviceType,
        val isAvailable: Boolean,
    )

    /**
     * Add a route.
     */
    fun addRoute(route: CastRoute) {
        if (routes.none { it.id == route.id }) {
            routes.add(route)
        }
    }

    /**
     * Remove a route.
     */
    fun removeRoute(routeId: String) {
        routes.removeAll { it.id == routeId }
        if (selectedRoute?.id == routeId) {
            selectedRoute = null
        }
    }

    /**
     * Select a route.
     */
    fun selectRoute(routeId: String): Boolean {
        val route = routes.find { it.id == routeId && it.isAvailable } ?: return false
        selectedRoute = route
        return true
    }

    /**
     * Get the selected route.
     */
    fun getSelectedRoute(): CastRoute? = selectedRoute

    /**
     * Get all available routes.
     */
    fun getAvailableRoutes(): List<CastRoute> = routes.filter { it.isAvailable }

    /**
     * Get all routes.
     */
    fun getAllRoutes(): List<CastRoute> = routes.toList()

    /**
     * Clear all routes.
     */
    fun clearRoutes() {
        routes.clear()
        selectedRoute = null
    }

    /**
     * Check if a route is selected.
     */
    fun hasSelectedRoute(): Boolean = selectedRoute != null

    /**
     * Unselect the current route.
     */
    fun unselectRoute() {
        selectedRoute = null
    }
}
