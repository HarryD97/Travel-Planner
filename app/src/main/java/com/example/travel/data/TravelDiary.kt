package com.example.travel.data

import java.util.Date

data class TravelDiary(
    val id: String = "",
    val title: String,
    val description: String = "",
    val date: Date = Date(),
    val waypoints: MutableList<DiaryWaypoint> = mutableListOf(),
    val photos: MutableList<DiaryPhoto> = mutableListOf(),
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0, // in kilometers
    val totalDuration: Long = 0L, // in milliseconds
    val isCompleted: Boolean = false
)

data class DiaryWaypoint(
    val id: String = "",
    val location: Location,
    val timestamp: Date = Date(),
    val notes: String = "",
    val stepsAtPoint: Int = 0,
    val photos: MutableList<String> = mutableListOf() // photo file paths
)

data class DiaryPhoto(
    val id: String = "",
    val filePath: String,
    val timestamp: Date = Date(),
    val location: Location? = null,
    val caption: String = "",
    val waypointId: String? = null // reference to associated waypoint if any
)

data class StepCounter(
    val timestamp: Date = Date(),
    val stepCount: Int = 0,
    val distanceTraveled: Double = 0.0 // in meters
)

data class TravelSummary(
    val totalDiaries: Int = 0,
    val totalWaypoints: Int = 0,
    val totalPhotos: Int = 0,
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0,
    val averageStepsPerDiary: Int = 0
) 