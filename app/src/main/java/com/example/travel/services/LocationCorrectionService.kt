package com.example.travel.services

import android.util.Log
import com.example.travel.data.Location
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.URLEncoder

/**
 * Service for correcting user-marked locations to make them more suitable for navigation
 */
class LocationCorrectionService {
    
    companion object {
        private const val TAG = "LocationCorrectionService"
        private const val PLACES_API_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        private const val ROADS_API_URL = "https://roads.googleapis.com/v1/snapToRoads"
        private const val API_KEY = "AIzaSyB0c_6xBYVsuRwJKnMbbz3tBt_I0iibP8g"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
    
    /**
     * Corrects a location to make it more suitable for navigation
     * This method tries multiple approaches to find the best navigable point
     */
    suspend fun correctLocationForNavigation(
        location: Location,
        travelMode: TravelMode = TravelMode.DRIVING
    ): Location {
        return withContext(Dispatchers.IO) {
            try {
                // Try to snap to roads first for driving mode
                if (travelMode == TravelMode.DRIVING) {
                    val roadSnapped = snapToRoads(location)
                    if (roadSnapped != null) {
                        Log.d(TAG, "Successfully snapped ${location.name} to road")
                        return@withContext roadSnapped
                    }
                }
                
                // Try to find nearby places that are more suitable for navigation
                val nearbyPlace = findBestNearbyPlace(location, travelMode)
                if (nearbyPlace != null) {
                    Log.d(TAG, "Found better nearby place for ${location.name}")
                    return@withContext nearbyPlace
                }
                
                // Fallback: apply smart offset based on travel mode
                val smartCorrected = applySmartCorrection(location, travelMode)
                Log.d(TAG, "Applied smart correction to ${location.name}")
                return@withContext smartCorrected
                
            } catch (e: Exception) {
                Log.w(TAG, "Location correction failed for ${location.name}: ${e.message}")
                // Return original location if all correction methods fail
                return@withContext location
            }
        }
    }
    
    /**
     * Uses Google Roads API to snap the location to the nearest road
     */
    private suspend fun snapToRoads(location: Location): Location? {
        return try {
            val url = buildString {
                append(ROADS_API_URL)
                append("?path=${location.latitude},${location.longitude}")
                append("&interpolate=true")
                append("&key=$API_KEY")
            }
            
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { parseRoadsResponse(it, location) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Roads API failed: ${e.message}")
            null
        }
    }
    
    private fun parseRoadsResponse(jsonResponse: String, originalLocation: Location): Location? {
        return try {
            val jsonObject = JsonParser.parseString(jsonResponse).asJsonObject
            val snappedPoints = jsonObject.getAsJsonArray("snappedPoints")
            
            if (snappedPoints.size() > 0) {
                val snappedPoint = snappedPoints[0].asJsonObject
                val locationObj = snappedPoint.getAsJsonObject("location")
                
                val snappedLat = locationObj.get("latitude").asDouble
                val snappedLng = locationObj.get("longitude").asDouble
                
                originalLocation.copy(
                    latitude = snappedLat,
                    longitude = snappedLng,
                    address = "${originalLocation.address} (Road Adjusted)"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse roads response: ${e.message}")
            null
        }
    }
    
    /**
     * Finds nearby places that might be better for navigation
     */
    private suspend fun findBestNearbyPlace(
        location: Location, 
        travelMode: TravelMode
    ): Location? {
        return try {
            val searchTypes = when (travelMode) {
                TravelMode.DRIVING -> "gas_station|parking|establishment"
                TravelMode.WALKING -> "point_of_interest|establishment"
                TravelMode.BICYCLING -> "park|point_of_interest"
                TravelMode.TRANSIT -> "transit_station|bus_station|subway_station"
            }
            
            val radius = when (travelMode) {
                TravelMode.DRIVING -> 100 // 100 meters for driving
                TravelMode.WALKING -> 50  // 50 meters for walking
                TravelMode.BICYCLING -> 75 // 75 meters for cycling
                TravelMode.TRANSIT -> 200 // 200 meters for transit
            }
            
            val url = buildString {
                append(PLACES_API_URL)
                append("?location=${location.latitude},${location.longitude}")
                append("&radius=$radius")
                append("&type=$searchTypes")
                append("&language=en") // English language for place names
                append("&key=$API_KEY")
            }
            
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { parsePlacesResponse(it, location) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Places API failed: ${e.message}")
            null
        }
    }
    
    private fun parsePlacesResponse(jsonResponse: String, originalLocation: Location): Location? {
        return try {
            val jsonObject = JsonParser.parseString(jsonResponse).asJsonObject
            val results = jsonObject.getAsJsonArray("results")
            
            if (results.size() > 0) {
                // Get the first (closest) result
                val place = results[0].asJsonObject
                val geometry = place.getAsJsonObject("geometry")
                val locationObj = geometry.getAsJsonObject("location")
                
                val placeLat = locationObj.get("lat").asDouble
                val placeLng = locationObj.get("lng").asDouble
                val placeName = place.get("name").asString
                
                // Only use this place if it's reasonably close to the original
                val distance = calculateDistance(
                    originalLocation.latitude, originalLocation.longitude,
                    placeLat, placeLng
                )
                
                if (distance < 0.1) { // Within 100 meters
                    originalLocation.copy(
                        latitude = placeLat,
                        longitude = placeLng,
                        address = "Near ${placeName}"
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse places response: ${e.message}")
            null
        }
    }
    
    /**
     * Applies smart correction based on travel mode and location characteristics
     */
    private fun applySmartCorrection(location: Location, travelMode: TravelMode): Location {
        val offsetDistance = when (travelMode) {
            TravelMode.DRIVING -> 0.0001  // ~11 meters - move towards likely road
            TravelMode.WALKING -> 0.00003 // ~3 meters - small adjustment for walkways
            TravelMode.BICYCLING -> 0.00005 // ~5.5 meters - towards bike-friendly paths
            TravelMode.TRANSIT -> 0.00015  // ~16 meters - towards public areas
        }
        
        // Apply intelligent offset based on location context
        val correctedLocation = when {
            // If location looks like it's inside a building (very precise coordinates)
            hasHighPrecisionCoordinates(location) -> {
                Log.d(TAG, "Detected indoor location, moving towards street")
                moveTowardsStreet(location, offsetDistance)
            }
            // If location is in a large area (park, mall, etc.)
            isInLargeArea(location) -> {
                Log.d(TAG, "Detected large area location, moving towards access point")
                moveTowardsAccess(location, offsetDistance)
            }
            // Default case
            else -> {
                Log.d(TAG, "Applying standard correction")
                applyStandardOffset(location, offsetDistance)
            }
        }
        
        return correctedLocation.copy(
            address = "${location.address} (Auto Corrected)"
        )
    }
    
    private fun hasHighPrecisionCoordinates(location: Location): Boolean {
        // Check if coordinates have many decimal places (indicating GPS precision)
        val latString = location.latitude.toString()
        val lngString = location.longitude.toString()
        
        val latDecimals = latString.substringAfter(".").length
        val lngDecimals = lngString.substringAfter(".").length
        
        return latDecimals > 6 || lngDecimals > 6
    }
    
    private fun isInLargeArea(location: Location): Boolean {
        // Simple heuristic: check if location name suggests a large area
        val largePlaceKeywords = listOf("park", "plaza", "mall", "center", "building", "campus", "university")
        return largePlaceKeywords.any { keyword -> 
            location.name.contains(keyword) || location.address.contains(keyword)
        }
    }
    
    private fun moveTowardsStreet(location: Location, offset: Double): Location {
        // Move slightly towards what might be a street (usually east-west or north-south)
        return location.copy(
            latitude = location.latitude + offset * 0.7, // Slightly north
            longitude = location.longitude + offset * 0.3  // Slightly east
        )
    }
    
    private fun moveTowardsAccess(location: Location, offset: Double): Location {
        // Move towards what might be an access road or entrance
        return location.copy(
            latitude = location.latitude - offset * 0.5, // Slightly south (towards street)
            longitude = location.longitude + offset * 0.5  // Slightly east
        )
    }
    
    private fun applyStandardOffset(location: Location, offset: Double): Location {
        // Apply small random offset to potentially hit a nearby road
        val randomFactorLat = (Math.random() - 0.5) * 2 // -1 to 1
        val randomFactorLng = (Math.random() - 0.5) * 2 // -1 to 1
        
        return location.copy(
            latitude = location.latitude + offset * randomFactorLat,
            longitude = location.longitude + offset * randomFactorLng
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
} 