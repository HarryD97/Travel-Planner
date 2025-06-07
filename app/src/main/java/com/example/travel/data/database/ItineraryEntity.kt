package com.example.travel.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "itinerary_items")
data class ItineraryEntity(
    @PrimaryKey
    val id: String,
    val locationId: String,
    val locationName: String,
    val locationAddress: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: String,
    val locationDescription: String,
    val locationImageUrl: String,
    val plannedDate: Long? = null,
    val plannedTime: String = "",
    val notes: String = "",
    val photos: String = "", // JSON string of photo URLs
    val visited: Boolean = false,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) 