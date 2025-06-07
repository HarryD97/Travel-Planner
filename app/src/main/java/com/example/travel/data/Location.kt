package com.example.travel.data

data class Location(
    val id: String = "",
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val type: LocationType = LocationType.CUSTOM,
    val description: String = "",
    val imageUrl: String = "",
    val addedToItinerary: Boolean = false
)

enum class LocationType {
    RESTAURANT,
    HOTEL,
    ATTRACTION,
    TRANSPORT,
    CUSTOM
} 