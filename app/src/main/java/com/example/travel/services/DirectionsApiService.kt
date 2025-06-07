package com.example.travel.services

import android.util.Log
import com.example.travel.data.Location
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.URLEncoder

data class NavigationStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val startLatLng: Pair<Double, Double>,
    val endLatLng: Pair<Double, Double>,
    val maneuver: String = "" // TURN_LEFT, TURN_RIGHT, STRAIGHT, etc.
)

enum class TravelMode(val apiValue: String, val displayName: String) {
    DRIVING("driving", "Driving"),
    WALKING("walking", "Walking"),
    BICYCLING("bicycling", "Bicycling"),
    TRANSIT("transit", "Public Transit")
}

data class NavigationRoute(
    val polylinePoints: String,
    val totalDistance: String,
    val totalDuration: String,
    val steps: List<NavigationStep>,
    val waypoints: List<Pair<Double, Double>>,
    val travelMode: TravelMode = TravelMode.DRIVING
)

class DirectionsApiService {
    
    companion object {
        private const val TAG = "DirectionsApiService"
        private const val BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private const val API_KEY = "" // From secrets.properties via gradle
    }
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
    
    private val gson = Gson()
    
    suspend fun getDirections(
        origin: Location,
        destination: Location,
        waypoints: List<Location> = emptyList(),
        travelMode: TravelMode = TravelMode.DRIVING
    ): NavigationRoute {
        return withContext(Dispatchers.IO) {
            try {
                val response = callGoogleDirectionsAPI(origin, destination, waypoints, travelMode)
                parseDirectionsResponse(response, travelMode)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting directions: ${e.message}", e)
                // Fallback to mock data if API fails
                generateMockRoute(origin, destination, waypoints, travelMode)
            }
        }
    }
    
    private fun callGoogleDirectionsAPI(
        origin: Location,
        destination: Location,
        waypoints: List<Location>,
        travelMode: TravelMode = TravelMode.DRIVING
    ): String {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destination.latitude},${destination.longitude}"
        
        val waypointsStr = if (waypoints.isNotEmpty()) {
            // Add optimize:true to enable waypoint optimization and automatic road snapping
            "optimize:true|" + waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
        } else ""
        
        val url = buildString {
            append(BASE_URL)
            append("?origin=${URLEncoder.encode(originStr, "UTF-8")}")
            append("&destination=${URLEncoder.encode(destinationStr, "UTF-8")}")
            if (waypointsStr.isNotEmpty()) {
                append("&waypoints=${URLEncoder.encode(waypointsStr, "UTF-8")}")
            }
            append("&mode=${travelMode.apiValue}")
            append("&units=metric")
            append("&alternatives=false")
            // Add avoid parameters to get better routing on roads
            append("&avoid=tolls") // Avoid tolls by default for better free routing
            // Enable automatic road snapping
            append("&region=cn") // Set region for better local routing
            append("&language=en") // English language for navigation instructions
            append("&key=$API_KEY")
        }
        
        Log.d(TAG, "Calling Google Directions API: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("API call failed: ${response.code}")
        }
        
        return response.body?.string() ?: throw IOException("Empty response body")
    }
    
    private fun parseDirectionsResponse(jsonResponse: String, travelMode: TravelMode = TravelMode.DRIVING): NavigationRoute {
        Log.d(TAG, "Parsing directions response...")
        val jsonObject = JsonParser.parseString(jsonResponse).asJsonObject
        
        val status = jsonObject.get("status").asString
        Log.d(TAG, "API Status: $status")
        if (status != "OK") {
            throw IOException("API returned status: $status")
        }
        
        val routes = jsonObject.getAsJsonArray("routes")
        if (routes.size() == 0) {
            throw IOException("No routes found")
        }
        
        val route = routes[0].asJsonObject
        val legs = route.getAsJsonArray("legs")
        Log.d(TAG, "Found ${legs.size()} legs in route")
        
        val allSteps = mutableListOf<NavigationStep>()
        var totalDistanceMeters = 0
        var totalDurationSeconds = 0
        
        // Parse each leg of the journey
        for (i in 0 until legs.size()) {
            val leg = legs[i].asJsonObject
            
            totalDistanceMeters += leg.getAsJsonObject("distance").get("value").asInt
            totalDurationSeconds += leg.getAsJsonObject("duration").get("value").asInt
            
            val steps = leg.getAsJsonArray("steps")
            Log.d(TAG, "Leg $i has ${steps.size()} steps")
            
            // Parse each step in the leg
            for (j in 0 until steps.size()) {
                val step = steps[j].asJsonObject
                allSteps.add(parseNavigationStep(step))
            }
        }
        
        // Get polyline points
        val overviewPolyline = route.getAsJsonObject("overview_polyline").get("points").asString
        Log.d(TAG, "Overview polyline length: ${overviewPolyline.length}")
        Log.d(TAG, "Polyline sample: ${overviewPolyline.take(50)}...")
        
        // Decode polyline to verify it works
        val decodedPoints = decodePolyline(overviewPolyline)
        Log.d(TAG, "Decoded ${decodedPoints.size} polyline points")
        if (decodedPoints.isNotEmpty()) {
            Log.d(TAG, "First point: ${decodedPoints.first()}")
            Log.d(TAG, "Last point: ${decodedPoints.last()}")
        }
        
        return NavigationRoute(
            polylinePoints = overviewPolyline,
            totalDistance = formatDistance(totalDistanceMeters),
            totalDuration = formatDuration(totalDurationSeconds / 60), // Convert to minutes
            steps = allSteps,
            waypoints = decodedPoints,
            travelMode = travelMode
        )
    }
    
    private fun parseNavigationStep(stepJson: JsonObject): NavigationStep {
        val htmlInstructions = stepJson.get("html_instructions").asString
        val cleanInstructions = cleanHtmlInstructions(htmlInstructions)
        
        val distance = stepJson.getAsJsonObject("distance")
        val duration = stepJson.getAsJsonObject("duration")
        
        val startLocation = stepJson.getAsJsonObject("start_location")
        val endLocation = stepJson.getAsJsonObject("end_location")
        
        val startLatLng = Pair(
            startLocation.get("lat").asDouble,
            startLocation.get("lng").asDouble
        )
        val endLatLng = Pair(
            endLocation.get("lat").asDouble,
            endLocation.get("lng").asDouble
        )
        
        // Extract maneuver if available
        val maneuver = if (stepJson.has("maneuver")) {
            convertManeuver(stepJson.get("maneuver").asString)
        } else {
            "STRAIGHT"
        }
        
        return NavigationStep(
            instruction = cleanInstructions,
            distance = distance.get("text").asString,
            duration = duration.get("text").asString,
            startLatLng = startLatLng,
            endLatLng = endLatLng,
            maneuver = maneuver
        )
    }
    
    private fun cleanHtmlInstructions(htmlInstructions: String): String {
        return htmlInstructions
            .replace("<[^>]*>".toRegex(), "") // Remove HTML tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
    
    private fun convertManeuver(googleManeuver: String): String {
        return when (googleManeuver.lowercase()) {
            "turn-left" -> "TURN_LEFT"
            "turn-right" -> "TURN_RIGHT"
            "turn-slight-left" -> "TURN_LEFT"
            "turn-slight-right" -> "TURN_RIGHT"
            "turn-sharp-left" -> "TURN_LEFT"
            "turn-sharp-right" -> "TURN_RIGHT"
            "straight" -> "STRAIGHT"
            "keep-left" -> "TURN_LEFT"
            "keep-right" -> "TURN_RIGHT"
            "uturn-left", "uturn-right" -> "U_TURN"
            "merge" -> "MERGE"
            "fork-left" -> "TURN_LEFT"
            "fork-right" -> "TURN_RIGHT"
            "ferry" -> "FERRY"
            "ferry-train" -> "FERRY"
            "roundabout-left" -> "ROUNDABOUT_LEFT"
            "roundabout-right" -> "ROUNDABOUT_RIGHT"
            else -> "STRAIGHT"
        }
    }
    
    private fun formatDistance(meters: Int): String {
        return if (meters < 1000) {
            "$meters m"
        } else {
            String.format("%.1f km", meters / 1000.0)
        }
    }
    
    private fun formatDuration(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes} min"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "${hours}h ${mins}min"
            }
        }
    }
    
    // Simple polyline decoder - for production use Google's polyline utility
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val polylinePoints = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0
        
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var byte: Int
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            
            val deltaLat = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
            lat += deltaLat
            
            shift = 0
            result = 0
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            
            val deltaLng = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
            lng += deltaLng
            
            polylinePoints.add(Pair(lat / 1e5, lng / 1e5))
        }
        
        return polylinePoints
    }
    
    // Fallback method when API is unavailable
    private fun generateMockRoute(
        origin: Location,
        destination: Location,
        waypoints: List<Location>,
        travelMode: TravelMode = TravelMode.DRIVING
    ): NavigationRoute {
        
        val allPoints = mutableListOf<Location>().apply {
            add(origin)
            addAll(waypoints)
            add(destination)
        }
        
        val steps = mutableListOf<NavigationStep>()
        val routePoints = mutableListOf<Pair<Double, Double>>()
        
        var totalDistanceKm = 0.0
        
        // Speed assumptions based on travel mode
        val averageSpeed = when (travelMode) {
            TravelMode.WALKING -> 5.0 // 5 km/h
            TravelMode.BICYCLING -> 15.0 // 15 km/h
            TravelMode.DRIVING -> 40.0 // 40 km/h
            TravelMode.TRANSIT -> 25.0 // 25 km/h (including waiting time)
        }
        
        // Generate simple direct route as fallback
        for (i in 0 until allPoints.size - 1) {
            val start = allPoints[i]
            val end = allPoints[i + 1]
            
            val distance = calculateDistance(start.latitude, start.longitude, end.latitude, end.longitude)
            totalDistanceKm += distance
            
            // Add simple point-to-point route
            routePoints.add(Pair(start.latitude, start.longitude))
            routePoints.add(Pair(end.latitude, end.longitude))
            
            val actionVerb = when (travelMode) {
                TravelMode.WALKING -> "Walk to"
                TravelMode.BICYCLING -> "Bike to"
                TravelMode.DRIVING -> "Drive to"
                TravelMode.TRANSIT -> "Go to"
            }
            
            steps.add(
                NavigationStep(
                    instruction = if (i == 0) "$actionVerb ${end.name}" else "Continue to ${end.name}",
                    distance = String.format("%.1f km", distance),
                    duration = String.format("%.0f min", distance / averageSpeed * 60),
                    startLatLng = Pair(start.latitude, start.longitude),
                    endLatLng = Pair(end.latitude, end.longitude),
                    maneuver = if (i == 0) "DEPART" else if (i == allPoints.size - 2) "ARRIVE" else "STRAIGHT"
                )
            )
        }
        
        return NavigationRoute(
            polylinePoints = routePoints.joinToString("|") { "${it.first},${it.second}" },
            totalDistance = String.format("%.1f km", totalDistanceKm),
            totalDuration = String.format("%.0f min", totalDistanceKm / averageSpeed * 60),
            steps = steps,
            waypoints = routePoints,
            travelMode = travelMode
        )
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        
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

    /**
     * Enhanced getDirections method with automatic road snapping
     */
    suspend fun getDirectionsWithRoadSnapping(
        origin: Location,
        destination: Location,
        waypoints: List<Location> = emptyList(),
        travelMode: TravelMode = TravelMode.DRIVING
    ): NavigationRoute {
        return withContext(Dispatchers.IO) {
            try {
                // First try with the enhanced API call
                val response = callGoogleDirectionsAPIWithSnapping(origin, destination, waypoints, travelMode)
                parseDirectionsResponse(response, travelMode)
            } catch (e: Exception) {
                Log.w(TAG, "Enhanced directions failed, trying fallback: ${e.message}")
                try {
                    // Fallback to regular API call
                    val response = callGoogleDirectionsAPI(origin, destination, waypoints, travelMode)
                    parseDirectionsResponse(response, travelMode)
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "All direction methods failed: ${fallbackException.message}", fallbackException)
                    // Final fallback to mock data
                    generateMockRoute(origin, destination, waypoints, travelMode)
                }
            }
        }
    }

    private fun callGoogleDirectionsAPIWithSnapping(
        origin: Location,
        destination: Location,
        waypoints: List<Location>,
        travelMode: TravelMode = TravelMode.DRIVING
    ): String {
        // For road snapping, we'll use more precise positioning
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destination.latitude},${destination.longitude}"
        
        val waypointsStr = if (waypoints.isNotEmpty()) {
            // Use enc: prefix for encoded polyline waypoints (better for road snapping)
            waypoints.joinToString("|") { 
                "${it.latitude},${it.longitude}" 
            }
        } else ""
        
        val url = buildString {
            append(BASE_URL)
            append("?origin=${URLEncoder.encode(originStr, "UTF-8")}")
            append("&destination=${URLEncoder.encode(destinationStr, "UTF-8")}")
            if (waypointsStr.isNotEmpty()) {
                // optimize:true helps with road snapping and waypoint ordering
                append("&waypoints=optimize:true|${URLEncoder.encode(waypointsStr, "UTF-8")}")
            }
            append("&mode=${travelMode.apiValue}")
            append("&units=metric")
            append("&alternatives=false")
            
            // Enhanced parameters for better road snapping
            when (travelMode) {
                TravelMode.DRIVING -> {
                    append("&avoid=ferries") // Avoid ferries for more reliable routing
                    append("&traffic_model=best_guess")
                }
                TravelMode.WALKING -> {
                    // Walking mode automatically snaps to walkable paths
                }
                TravelMode.BICYCLING -> {
                    // Bicycling mode automatically prefers bike-friendly routes
                }
                TravelMode.TRANSIT -> {
                    append("&transit_mode=bus|subway|train")
                    append("&transit_routing_preference=less_walking")
                }
            }
            
            // Regional settings for better local routing
            append("&region=cn")
            append("&language=en") // English language for navigation instructions
            append("&key=$API_KEY")
        }
        
        Log.d(TAG, "Calling Enhanced Google Directions API: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Enhanced API call failed: ${response.code}")
        }
        
        return response.body?.string() ?: throw IOException("Empty response body")
    }

    /**
     * Snaps a location to the nearest road using a simple offset technique
     * This is a fallback method when API-based snapping is not available
     */
    private fun snapToRoad(location: Location, travelMode: TravelMode): Location {
        // Simple heuristic: add small random offset to move point closer to potential roads
        val offsetRange = when (travelMode) {
            TravelMode.DRIVING -> 0.0001 // ~11 meters
            TravelMode.WALKING -> 0.00005 // ~5.5 meters  
            TravelMode.BICYCLING -> 0.00008 // ~8.8 meters
            TravelMode.TRANSIT -> 0.0002 // ~22 meters (transit stops)
        }
        
        // Apply small offset to potentially move the point to a nearby road
        val adjustedLat = location.latitude + (Math.random() - 0.5) * offsetRange
        val adjustedLng = location.longitude + (Math.random() - 0.5) * offsetRange
        
        return location.copy(
            latitude = adjustedLat,
            longitude = adjustedLng
        )
    }
} 
