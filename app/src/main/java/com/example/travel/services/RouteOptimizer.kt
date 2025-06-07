package com.example.travel.services

import android.util.Log
import com.example.travel.data.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class RouteOptimizer {
    
    companion object {
        private const val TAG = "RouteOptimizer"
    }
    
    data class OptimizedRoute(
        val locations: List<Location>,
        val totalDistance: Double,
        val totalDuration: Double,
        val optimizationImprovement: Double // Percentage improvement
    )
    
    /**
     * Optimize route using advanced algorithms
     * Combines nearest neighbor with 2-opt improvement
     */
    suspend fun optimizeRoute(
        locations: List<Location>,
        startLocation: Location? = null,
        travelMode: TravelMode = TravelMode.DRIVING
    ): OptimizedRoute = withContext(Dispatchers.Default) {
        
        if (locations.size <= 2) {
            return@withContext OptimizedRoute(
                locations = locations,
                totalDistance = 0.0,
                totalDuration = 0.0,
                optimizationImprovement = 0.0
            )
        }
        
        Log.d(TAG, "Optimizing route for ${locations.size} locations")
        
        val allLocations = if (startLocation != null) {
            listOf(startLocation) + locations
        } else {
            locations
        }
        
        // Calculate original route distance
        val originalDistance = calculateTotalDistance(allLocations)
        
        // Apply optimization algorithms
        val optimized = if (allLocations.size <= 10) {
            // For small routes, use more precise algorithm
            optimizeWithNearestNeighborAnd2Opt(allLocations, startLocation)
        } else {
            // For larger routes, use faster heuristic
            optimizeWithNearestNeighbor(allLocations, startLocation)
        }
        
        val optimizedDistance = calculateTotalDistance(optimized)
        val improvement = if (originalDistance > 0) {
            ((originalDistance - optimizedDistance) / originalDistance) * 100
        } else 0.0
        
        val duration = estimateTotalDuration(optimized, travelMode)
        
        Log.d(TAG, "Route optimization complete. Improvement: ${String.format("%.1f", improvement)}%")
        
        OptimizedRoute(
            locations = optimized,
            totalDistance = optimizedDistance,
            totalDuration = duration,
            optimizationImprovement = improvement
        )
    }
    
    /**
     * Nearest Neighbor algorithm with 2-opt improvement
     */
    private fun optimizeWithNearestNeighborAnd2Opt(
        locations: List<Location>,
        startLocation: Location?
    ): List<Location> {
        // Start with nearest neighbor
        var route = nearestNeighborTSP(locations, startLocation)
        
        // Apply 2-opt improvements
        route = twoOptImprovement(route, startLocation)
        
        return route
    }
    
    /**
     * Simple nearest neighbor algorithm for larger datasets
     */
    private fun optimizeWithNearestNeighbor(
        locations: List<Location>,
        startLocation: Location?
    ): List<Location> {
        return nearestNeighborTSP(locations, startLocation)
    }
    
    /**
     * Nearest Neighbor TSP algorithm
     */
    private fun nearestNeighborTSP(
        locations: List<Location>,
        startLocation: Location?
    ): List<Location> {
        if (locations.isEmpty()) return emptyList()
        
        val unvisited = locations.toMutableList()
        val route = mutableListOf<Location>()
        
        // Start from specified location or first location
        val start = startLocation ?: locations.first()
        var current = start
        
        if (startLocation != null) {
            unvisited.remove(current)
        }
        route.add(current)
        
        // Visit nearest unvisited location each time
        while (unvisited.isNotEmpty()) {
            val nearest = unvisited.minByOrNull { location ->
                calculateDistance(
                    current.latitude, current.longitude,
                    location.latitude, location.longitude
                )
            }
            
            if (nearest != null) {
                unvisited.remove(nearest)
                route.add(nearest)
                current = nearest
            }
        }
        
        return route
    }
    
    /**
     * 2-opt improvement algorithm
     */
    private fun twoOptImprovement(
        route: List<Location>,
        startLocation: Location?
    ): List<Location> {
        var improved = route.toList()
        var bestDistance = calculateTotalDistance(improved)
        var improvement = true
        
        val startIndex = if (startLocation != null) 1 else 0 // Don't move start location
        
        while (improvement) {
            improvement = false
            
            for (i in startIndex until improved.size - 1) {
                for (j in i + 1 until improved.size) {
                    // Try swapping edges
                    val newRoute = improved.toMutableList()
                    
                    // Reverse the segment between i and j
                    val segment = newRoute.subList(i, j + 1)
                    segment.reverse()
                    
                    val newDistance = calculateTotalDistance(newRoute)
                    if (newDistance < bestDistance) {
                        improved = newRoute
                        bestDistance = newDistance
                        improvement = true
                    }
                }
            }
        }
        
        return improved
    }
    
    /**
     * Calculate total distance for a route
     */
    private fun calculateTotalDistance(locations: List<Location>): Double {
        if (locations.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 0 until locations.size - 1) {
            totalDistance += calculateDistance(
                locations[i].latitude, locations[i].longitude,
                locations[i + 1].latitude, locations[i + 1].longitude
            )
        }
        return totalDistance
    }
    
    /**
     * Estimate total duration based on travel mode
     */
    private fun estimateTotalDuration(locations: List<Location>, travelMode: TravelMode): Double {
        val distance = calculateTotalDistance(locations)
        val speed = when (travelMode) {
            TravelMode.WALKING -> 5.0 // 5 km/h
            TravelMode.BICYCLING -> 15.0 // 15 km/h
            TravelMode.DRIVING -> 40.0 // 40 km/h
            TravelMode.TRANSIT -> 25.0 // 25 km/h
        }
        return distance / speed * 60 // Return minutes
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
} 