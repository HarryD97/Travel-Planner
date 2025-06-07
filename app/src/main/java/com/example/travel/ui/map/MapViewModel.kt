package com.example.travel.ui.map

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travel.data.Location
import com.example.travel.data.Route
import com.example.travel.services.DirectionsApiService
import com.example.travel.services.LocationCorrectionService
import com.example.travel.services.NavigationRoute
import com.example.travel.services.NavigationStep
import com.example.travel.services.TravelMode
import com.example.travel.services.RouteOptimizer
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    private val _selectedLocations = MutableLiveData<List<Location>>()
    val selectedLocations: LiveData<List<Location>> = _selectedLocations

    private val _route = MutableLiveData<Route?>()
    val route: LiveData<Route?> = _route

    private val _navigationRoute = MutableLiveData<NavigationRoute?>()
    val navigationRoute: LiveData<NavigationRoute?> = _navigationRoute

    private val _currentNavigationStep = MutableLiveData<NavigationStep?>()
    val currentNavigationStep: LiveData<NavigationStep?> = _currentNavigationStep

    private val _isNavigating = MutableLiveData<Boolean>()
    val isNavigating: LiveData<Boolean> = _isNavigating

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation

    private val _destinationLocation = MutableLiveData<Location?>()
    val destinationLocation: LiveData<Location?> = _destinationLocation

    private val _currentTravelMode = MutableLiveData<TravelMode>()
    val currentTravelMode: LiveData<TravelMode> = _currentTravelMode

    init {
        // Set default travel mode
        _currentTravelMode.value = TravelMode.WALKING
    }

    private val _isOptimizing = MutableLiveData<Boolean>()
    val isOptimizing: LiveData<Boolean> = _isOptimizing

    private val locationsList = mutableListOf<Location>()
    private val directionsService = DirectionsApiService()
    private val routeOptimizer = RouteOptimizer()
    private val locationCorrectionService = LocationCorrectionService()
    private var currentStepIndex = 0

    fun addLocation(location: Location) {
        locationsList.add(location)
        _selectedLocations.value = locationsList.toList()
    }

    fun removeLocation(location: Location) {
        locationsList.remove(location)
        _selectedLocations.value = locationsList.toList()
    }

    fun clearLocations() {
        locationsList.clear()
        _selectedLocations.value = locationsList.toList()
        _route.value = null
    }

    fun clearAllData() {
        // Clear all locations and routes
        locationsList.clear()
        _selectedLocations.value = locationsList.toList()
        _route.value = null
        _navigationRoute.value = null
        _destinationLocation.value = null

        // Stop navigation if active
        if (_isNavigating.value == true) {
            stopNavigation()
        }

        // Reset step index
        currentStepIndex = 0
    }

    fun setCurrentLocation(latitude: Double, longitude: Double) {
        val location = Location(
            name = "Current Location",
            address = "Your current location",
            latitude = latitude,
            longitude = longitude
        )
        _currentLocation.value = location
    }

    fun setTravelMode(travelMode: TravelMode) {
        _currentTravelMode.value = travelMode
    }

    fun navigateToLocation(destination: Location) {
        val currentLoc = _currentLocation.value
        if (currentLoc == null) {
            // Try to get current location first
            return
        }

        _destinationLocation.value = destination
        calculateNavigationRoute(currentLoc, destination)
    }

    private fun calculateNavigationRoute(origin: Location, destination: Location) {
        viewModelScope.launch {
            _isLoading.value = true
            val travelMode = _currentTravelMode.value ?: TravelMode.WALKING
            try {

                // Get detailed navigation route from DirectionsApiService
                val navigationRoute = directionsService.getDirections(
                    origin = origin,
                    destination = destination,
                    waypoints = emptyList(),
                    travelMode = travelMode
                )

                _navigationRoute.value = navigationRoute

                // Also create simple route for backwards compatibility
                val route = Route(
                    startLocation = origin,
                    endLocation = destination,
                    distance = navigationRoute.totalDistance,
                    duration = navigationRoute.totalDuration,
                    polylinePoints = navigationRoute.polylinePoints
                )

                _route.value = route

                // Automatically start navigation
                startNavigation()

            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to calculate navigation route: ${e.message}", e)
                // Handle error - create fallback route for long distance navigation
                val distance = calculateDistance(
                    origin.latitude, origin.longitude,
                    destination.latitude, destination.longitude
                )

                Log.w("MapViewModel", "Navigation distance: ${String.format("%.1f km", distance)}")

                if (distance > 100) { // If distance > 100km, use simple route
                    Log.w("MapViewModel", "Creating fallback navigation route for long distance")
                    val averageSpeed = when (travelMode) {
                        TravelMode.WALKING -> 5.0
                        TravelMode.BICYCLING -> 15.0
                        TravelMode.DRIVING -> 60.0
                        TravelMode.TRANSIT -> 40.0
                        else -> 5.0 // Default to walking speed
                    }

                    val actionVerb = when (travelMode) {
                        TravelMode.WALKING -> "Walk to"
                        TravelMode.BICYCLING -> "Cycle to"
                        TravelMode.DRIVING -> "Drive to"
                        TravelMode.TRANSIT -> "Go to"
                        else -> "Go to" // Default action
                    }

                    val fallbackRoute = com.example.travel.services.NavigationRoute(
                        polylinePoints = "${origin.latitude},${origin.longitude}|${destination.latitude},${destination.longitude}",
                        totalDistance = String.format("%.1f km", distance),
                        totalDuration = String.format("%.0f min", distance / averageSpeed * 60),
                        steps = listOf(
                            com.example.travel.services.NavigationStep(
                                instruction = "$actionVerb ${destination.name}",
                                distance = String.format("%.1f km", distance),
                                duration = String.format("%.0f min", distance / averageSpeed * 60),
                                startLatLng = Pair(origin.latitude, origin.longitude),
                                endLatLng = Pair(destination.latitude, destination.longitude),
                                maneuver = "STRAIGHT"
                            )
                        ),
                        waypoints = listOf(
                            Pair(origin.latitude, origin.longitude),
                            Pair(destination.latitude, destination.longitude)
                        ),
                        travelMode = travelMode
                    )

                    _navigationRoute.value = fallbackRoute

                    val route = Route(
                        startLocation = origin,
                        endLocation = destination,
                        distance = fallbackRoute.totalDistance,
                        duration = fallbackRoute.totalDuration,
                        polylinePoints = fallbackRoute.polylinePoints
                    )

                    _route.value = route

                    // Still start navigation with fallback route
                    startNavigation()
                } else {
                    _route.value = null
                    _navigationRoute.value = null
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateRoute(locations: List<Location>) {
        if (locations.size < 2) return

        viewModelScope.launch {
            _isLoading.value = true

            val travelMode = _currentTravelMode.value ?: TravelMode.WALKING
            val currentLoc = _currentLocation.value

            // First, correct all locations for better navigation
            val correctedLocations = locations.map { location ->
                try {
                    locationCorrectionService.correctLocationForNavigation(location, travelMode)
                } catch (e: Exception) {
                    Log.w("MapViewModel", "Failed to correct location ${location.name}: ${e.message}")
                    location // Use original if correction fails
                }
            }

            // Optimize route if there are multiple locations
            val optimizedLocations = if (correctedLocations.size > 2) {
                _isOptimizing.value = true
                val optimized = routeOptimizer.optimizeRoute(
                    locations = correctedLocations,
                    startLocation = currentLoc,
                    travelMode = travelMode
                )
                _isOptimizing.value = false
                optimized.locations
            } else {
                correctedLocations
            }

            try {
                val startLocation = optimizedLocations.first()
                val endLocation = optimizedLocations.last()
                val waypoints = if (optimizedLocations.size > 2) {
                    optimizedLocations.subList(1, optimizedLocations.size - 1)
                } else {
                    emptyList()
                }

                // Get detailed navigation route with road snapping from DirectionsApiService
                val navigationRoute = directionsService.getDirectionsWithRoadSnapping(
                    origin = startLocation,
                    destination = endLocation,
                    waypoints = waypoints,
                    travelMode = travelMode
                )

                _navigationRoute.value = navigationRoute

                // Update selected locations to show optimized order
                _selectedLocations.value = optimizedLocations

                // Also create simple route for backwards compatibility
                val route = Route(
                    startLocation = startLocation,
                    endLocation = endLocation,
                    distance = navigationRoute.totalDistance,
                    duration = navigationRoute.totalDuration,
                    polylinePoints = navigationRoute.polylinePoints
                )

                _route.value = route
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to calculate route: ${e.message}", e)
                // Handle error - show fallback route for long distance
                val startLocation = optimizedLocations.first()
                val endLocation = optimizedLocations.last()
                val distance = calculateDistance(
                    startLocation.latitude, startLocation.longitude,
                    endLocation.latitude, endLocation.longitude
                )

                Log.w("MapViewModel", "Distance: ${String.format("%.1f km", distance)}")

                if (distance > 200) { // If distance > 200km, might be too far for Directions API
                    Log.w("MapViewModel", "Distance too large (${String.format("%.1f km", distance)}), using simple straight-line route")
                    // Create a simple straight-line route for very long distances
                    val steps = mutableListOf<com.example.travel.services.NavigationStep>()
                    val routePoints = mutableListOf<Pair<Double, Double>>()

                    routePoints.add(Pair(startLocation.latitude, startLocation.longitude))
                    routePoints.add(Pair(endLocation.latitude, endLocation.longitude))

                    val averageSpeed = when (travelMode) {
                        TravelMode.WALKING -> 5.0 // 5 km/h
                        TravelMode.BICYCLING -> 15.0 // 15 km/h
                        TravelMode.DRIVING -> 60.0 // 60 km/h for long distances
                        TravelMode.TRANSIT -> 40.0 // 40 km/h
                        else -> 5.0 // Default to walking speed
                    }

                    val actionVerb = when (travelMode) {
                        TravelMode.WALKING -> "Walk to"
                        TravelMode.BICYCLING -> "Cycle to"
                        TravelMode.DRIVING -> "Drive to"
                        TravelMode.TRANSIT -> "Go to"
                        else -> "Go to" // Default action
                    }

                    steps.add(
                        com.example.travel.services.NavigationStep(
                            instruction = "$actionVerb ${endLocation.name}",
                            distance = String.format("%.1f km", distance),
                            duration = String.format("%.0f min", distance / averageSpeed * 60),
                            startLatLng = Pair(startLocation.latitude, startLocation.longitude),
                            endLatLng = Pair(endLocation.latitude, endLocation.longitude),
                            maneuver = "STRAIGHT"
                        )
                    )

                    val fallbackRoute = com.example.travel.services.NavigationRoute(
                        polylinePoints = routePoints.joinToString("|") { "${it.first},${it.second}" },
                        totalDistance = String.format("%.1f km", distance),
                        totalDuration = String.format("%.0f min", distance / averageSpeed * 60),
                        steps = steps,
                        waypoints = routePoints,
                        travelMode = travelMode
                    )

                    _navigationRoute.value = fallbackRoute

                    val route = Route(
                        startLocation = startLocation,
                        endLocation = endLocation,
                        distance = fallbackRoute.totalDistance,
                        duration = fallbackRoute.totalDuration,
                        polylinePoints = fallbackRoute.polylinePoints
                    )

                    _route.value = route
                } else {
                    Log.e("MapViewModel", "Route calculation failed for distance ${String.format("%.1f km", distance)}")
                    _route.value = null
                    _navigationRoute.value = null
                }
            } finally {
                _isLoading.value = false
                _isOptimizing.value = false
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine formula for calculating distance between two points
        val earthRadius = 6371.0 // Earth radius in kilometers

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    fun startNavigation() {
        val navRoute = _navigationRoute.value ?: return
        if (navRoute.steps.isNotEmpty()) {
            _isNavigating.value = true
            currentStepIndex = 0
            _currentNavigationStep.value = navRoute.steps[currentStepIndex]
        }
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _currentNavigationStep.value = null
        _navigationRoute.value = null
        _route.value = null
        _destinationLocation.value = null
        currentStepIndex = 0
    }

    fun nextNavigationStep() {
        val navRoute = _navigationRoute.value ?: return
        if (currentStepIndex < navRoute.steps.size - 1) {
            currentStepIndex++
            _currentNavigationStep.value = navRoute.steps[currentStepIndex]
        } else {
            // Navigation completed
            stopNavigation()
        }
    }

    fun previousNavigationStep() {
        val navRoute = _navigationRoute.value ?: return
        if (currentStepIndex > 0) {
            currentStepIndex--
            _currentNavigationStep.value = navRoute.steps[currentStepIndex]
        }
    }

    fun updateCurrentLocation(latitude: Double, longitude: Double) {
        // Check if user is close to the next step and auto-advance
        val currentStep = _currentNavigationStep.value ?: return
        val distanceToNextStep = calculateDistance(
            latitude, longitude,
            currentStep.endLatLng.first, currentStep.endLatLng.second
        )

        // If within 50 meters of the step end point, advance to next step
        if (distanceToNextStep < 0.05) { // 50 meters in km
            nextNavigationStep()
        }
    }

    private fun estimateTime(distanceKm: Double): String {
        // Simple estimation: assume average speed of 40 km/h
        val timeHours = distanceKm / 40.0
        val timeMinutes = (timeHours * 60).toInt()

        return when {
            timeMinutes < 60 -> "${timeMinutes} min"
            else -> {
                val hours = timeMinutes / 60
                val minutes = timeMinutes % 60
                "${hours}h ${minutes}min"
            }
        }
    }
}