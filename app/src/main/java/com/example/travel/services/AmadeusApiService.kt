package com.example.travel.services

import android.content.Context
import kotlinx.coroutines.delay
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.*

// Data classes for API responses
data class Hotel(
    val id: String,
    val name: String,
    val address: String,
    val rating: Float,
    val price: String,
    val priceValue: Double, // For sorting
    val imageUrl: String = "",
    val latitude: Double,
    val longitude: Double,
    val amenities: List<String> = emptyList(),
    val description: String = "",
    val distanceKm: Double = 0.0, // Distance from search location
    val distanceText: String = "" // Human readable distance text
)



class AmadeusApiService(private val context: Context) {
    
    private val googlePlacesService = GooglePlacesService(context)
    

    
    suspend fun searchHotels(latitude: Double, longitude: Double, checkIn: String? = null, checkOut: String? = null): List<Hotel> {
        return try {
            val hotels = googlePlacesService.searchHotels(latitude, longitude)
            // Sort by price value for consistent ordering
            hotels.sortedBy { it.priceValue }
        } catch (e: Exception) {
            // Fallback to mock data if Places API fails
            generateMockHotels(latitude, longitude)
        }
    }
    
    private suspend fun generateMockHotels(latitude: Double, longitude: Double): List<Hotel> {
        delay(500) // Simulate API delay
        
        val mockHotels = mutableListOf<Hotel>()
        
        val hotelData = listOf(
            Triple("Central Plaza Hotel", "123 Main Street", Pair(latitude + 0.001, longitude + 0.001)),
            Triple("Budget Inn", "456 Side Street", Pair(latitude - 0.002, longitude + 0.002)),
            Triple("Luxury Resort & Spa", "789 Premium Avenue", Pair(latitude + 0.003, longitude - 0.001))
        )
        
        hotelData.forEachIndexed { index, (name, address, coords) ->
            val distance = calculateDistance(latitude, longitude, coords.first, coords.second)
            val distanceText = when {
                distance < 1.0 -> "${(distance * 1000).toInt()}m"
                distance < 10.0 -> String.format("%.1fkm", distance)
                else -> "${distance.toInt()}km"
            }
            
            val hotel = when (index) {
                0 -> Hotel(
                    id = "mock_1",
                    name = name,
                    address = address,
                    rating = 4.2f,
                    price = "$120/night",
                    priceValue = 120.0,
                    latitude = coords.first,
                    longitude = coords.second,
                    amenities = listOf("Free WiFi", "Pool", "Gym", "Restaurant"),
                    description = "A comfortable hotel in the heart of the city.",
                    distanceKm = distance,
                    distanceText = distanceText
                )
                1 -> Hotel(
                    id = "mock_2",
                    name = name,
                    address = address,
                    rating = 3.5f,
                    price = "$80/night",
                    priceValue = 80.0,
                    latitude = coords.first,
                    longitude = coords.second,
                    amenities = listOf("Free WiFi", "Parking"),
                    description = "Affordable accommodation with basic amenities.",
                    distanceKm = distance,
                    distanceText = distanceText
                )
                else -> Hotel(
                    id = "mock_3",
                    name = name,
                    address = address,
                    rating = 4.8f,
                    price = "$350/night",
                    priceValue = 350.0,
                    latitude = coords.first,
                    longitude = coords.second,
                    amenities = listOf("Free WiFi", "Pool", "Spa", "Gym", "Restaurant", "Bar", "Concierge"),
                    description = "Experience luxury at its finest with world-class amenities.",
                    distanceKm = distance,
                    distanceText = distanceText
                )
            }
            mockHotels.add(hotel)
        }
        
        return mockHotels.sortedBy { it.priceValue }
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
    
    // Get hotel details
    suspend fun getHotelDetails(hotelId: String): Hotel? {
        return try {
            googlePlacesService.getHotelDetails(hotelId)
        } catch (e: Exception) {
            // Fallback or return null if API fails
            null
        }
    }
    

    
    // In a real implementation, you would also need:
    // - Authentication methods for Amadeus API
    // - Error handling for API failures
    // - Proper request/response models matching Amadeus API structure
    // - Rate limiting and caching
    
    private fun getAuthToken(): String {
        // Mock authentication - in real implementation, this would call Amadeus auth endpoint
        return "mock_access_token"
    }
} 