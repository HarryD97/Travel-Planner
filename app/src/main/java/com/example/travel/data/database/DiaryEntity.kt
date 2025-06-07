package com.example.travel.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.travel.data.Converters
import java.util.Date

@Entity(tableName = "travel_diaries")
@TypeConverters(Converters::class)
data class DiaryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val date: Date,
    val totalSteps: Int,
    val totalDistance: Double,
    val totalDuration: Long,
    val isCompleted: Boolean
)

@Entity(tableName = "diary_waypoints")
@TypeConverters(Converters::class)
data class WaypointEntity(
    @PrimaryKey val id: String,
    val diaryId: String,
    val locationName: String,
    val locationAddress: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date,
    val notes: String,
    val stepsAtPoint: Int
)

@Entity(tableName = "diary_photos")
@TypeConverters(Converters::class)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val diaryId: String,
    val filePath: String,
    val timestamp: Date,
    val caption: String,
    val waypointId: String?,
    val latitude: Double?,
    val longitude: Double?
) 