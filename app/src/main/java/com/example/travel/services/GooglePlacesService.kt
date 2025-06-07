package com.example.travel.services

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GooglePlacesService(private val context: Context) {
    
    private val placesClient: PlacesClient by lazy {
        if (!Places.isInitialized()) {
            Places.initialize(context, getApiKey())
        }
        Places.createClient(context)
    }
    
    private fun getApiKey(): String {
        // Read API key from AndroidManifest meta-data
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(
                context.packageName, 
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    suspend fun searchHotels(
        latitude: Double, 
        longitude: Double, 
        radiusMeters: Int = 5000
    ): List<Hotel> = suspendCancellableCoroutine { continuation ->
        
        val searchLocation = LatLng(latitude, longitude)
        
        // Create a circular location bias
        val location = LatLng(latitude, longitude)
        val locationBias = CircularBounds.newInstance(location, radiusMeters.toDouble())
        
        // Define place fields to retrieve
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.PRICE_LEVEL,
            Place.Field.PHOTO_METADATAS,
            Place.Field.TYPES,
            Place.Field.OPENING_HOURS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI
        )
        
        // Build the search request
        val searchByTextRequest = SearchByTextRequest.builder("hotels near me", placeFields)
            .setLocationBias(locationBias)
            .setMaxResultCount(20)
            .build()
        
        placesClient.searchByText(searchByTextRequest)
            .addOnSuccessListener { response ->
                val hotels = response.places.mapNotNull { place ->
                    convertPlaceToHotel(place, searchLocation)
                }.filter { hotel ->
                    // Filter to only include actual hotels
                    hotel.name.contains("hotel", ignoreCase = true) ||
                    hotel.name.contains("inn", ignoreCase = true) ||
                    hotel.name.contains("resort", ignoreCase = true) ||
                    hotel.name.contains("lodge", ignoreCase = true) ||
                    hotel.name.contains("suites", ignoreCase = true)
                }
                continuation.resume(hotels)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
    
    suspend fun getHotelDetails(placeId: String): Hotel? = suspendCancellableCoroutine { continuation ->
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.PRICE_LEVEL,
            Place.Field.PHOTO_METADATAS,
            Place.Field.TYPES,
            Place.Field.OPENING_HOURS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.USER_RATINGS_TOTAL
        )
        
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)
        
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val hotel = convertPlaceToHotel(response.place)
                continuation.resume(hotel)
            }
            .addOnFailureListener { exception ->
                if (exception is ApiException) {
                    continuation.resumeWithException(exception)
                } else {
                    continuation.resume(null)
                }
            }
    }
    
    private fun convertPlaceToHotel(place: Place, searchLocation: LatLng? = null): Hotel? {
        val latLng = place.latLng ?: return null
        val name = place.name ?: return null
        val address = place.address ?: "Address not available"
        
        // Convert Google Places price level to actual price estimate
        val priceLevel = place.priceLevel ?: 0
        val (priceDisplay, priceValue) = when (priceLevel) {
            1 -> "$50-80/night" to 65.0
            2 -> "$80-150/night" to 115.0
            3 -> "$150-250/night" to 200.0
            4 -> "$250-400/night" to 325.0
            else -> "$100-200/night" to 150.0
        }
        
        // Get rating or default to 4.0
        val rating = place.rating?.toFloat() ?: 4.0f
        
        // Generate amenities based on place types and price level
        val amenities = generateAmenitiesFromPlace(place, priceLevel)
        
        // Get photo URL if available
        val photoUrl = place.photoMetadatas?.firstOrNull()?.let { photoMetadata ->
            getPhotoUrl(photoMetadata)
        } ?: ""
        
        // Calculate distance from search location
        val (distanceKm, distanceText) = searchLocation?.let { searchLoc ->
            val distance = calculateDistance(
                searchLoc.latitude, searchLoc.longitude,
                latLng.latitude, latLng.longitude
            )
            val distanceText = when {
                distance < 1.0 -> "${(distance * 1000).toInt()}m"
                distance < 10.0 -> String.format("%.1fkm", distance)
                else -> "${distance.toInt()}km"
            }
            distance to distanceText
        } ?: (0.0 to "")
        
        return Hotel(
            id = place.id ?: "",
            name = name,
            address = address,
            rating = rating,
            price = priceDisplay,
            priceValue = priceValue,
            imageUrl = photoUrl,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            amenities = amenities,
            description = generateHotelDescription(name, amenities, rating, address),
            distanceKm = distanceKm,
            distanceText = distanceText
        )
    }
    
    private fun generateAmenitiesFromPlace(place: Place, priceLevel: Int): List<String> {
        val amenities = mutableListOf<String>()
        
        // Basic amenities for all hotels
        amenities.add("Free WiFi")
        
        // Add amenities based on price level
        when (priceLevel) {
            1 -> {
                amenities.addAll(listOf("Parking", "24-hour Front Desk"))
            }
            2 -> {
                amenities.addAll(listOf("Parking", "24-hour Front Desk", "Business Center"))
            }
            3 -> {
                amenities.addAll(listOf(
                    "Pool", "Gym", "Restaurant", "Bar", "Business Center", 
                    "Room Service", "Parking", "24-hour Front Desk"
                ))
            }
            4 -> {
                amenities.addAll(listOf(
                    "Pool", "Spa", "Gym", "Restaurant", "Bar", "Business Center",
                    "Room Service", "Concierge", "Valet Parking", "Airport Shuttle",
                    "24-hour Front Desk"
                ))
            }
            else -> {
                amenities.addAll(listOf("Parking", "Business Center"))
            }
        }
        
        // Add phone number if available
        if (place.phoneNumber != null) {
            amenities.add("Phone Service")
        }
        
        // Add website if available
        if (place.websiteUri != null) {
            amenities.add("Online Booking")
        }
        
        return amenities.distinct()
    }
    
    private fun generateHotelDescription(
        name: String, 
        amenities: List<String>, 
        rating: Float,
        address: String
    ): String {
        val ratingText = when {
            rating >= 4.5f -> "exceptional"
            rating >= 4.0f -> "excellent"
            rating >= 3.5f -> "very good"
            rating >= 3.0f -> "good"
            else -> "comfortable"
        }
        
        return "Experience $ratingText hospitality at $name located at $address. " +
               "This hotel features: ${amenities.take(5).joinToString(", ")}."
    }
    
    private fun getPhotoUrl(photoMetadata: PhotoMetadata): String {
        return try {
            // Create a photo request with desired dimensions
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(800)
                .setMaxHeight(600)
                .build()
            
            // Note: This is a simplified approach. In a real app, you would
            // use the fetchPhoto method with a callback to get the actual photo
            // For now, we'll return a placeholder URL or empty string
            ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
} 