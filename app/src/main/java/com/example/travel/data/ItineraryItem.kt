package com.example.travel.data

import java.util.Date

data class ItineraryItem(
    val id: String = "",
    val location: Location,
    val plannedDate: Date? = null,
    val plannedTime: String = "",
    val notes: String = "",
    val photos: MutableList<String> = mutableListOf(),
    val visited: Boolean = false,
    val order: Int = 0
)

data class Route(
    val startLocation: Location,
    val endLocation: Location,
    val distance: String,
    val duration: String,
    val polylinePoints: String = ""
)

data class TravelStats(
    val totalDistance: String = "0 km",
    val totalDuration: String = "0 min",
    val totalLocations: Int = 0
) 