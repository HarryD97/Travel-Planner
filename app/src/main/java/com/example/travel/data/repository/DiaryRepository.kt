package com.example.travel.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.travel.data.TravelDiary
import com.example.travel.data.DiaryWaypoint
import com.example.travel.data.DiaryPhoto
import com.example.travel.data.Location
import com.example.travel.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class DiaryRepository(context: Context) {
    
    private val database = TravelDatabase.getDatabase(context)
    private val diaryDao = database.diaryDao()
    
    val allDiaries: LiveData<List<TravelDiary>> = diaryDao.getAllDiaries().map { entities ->
        entities.map { entity -> 
            entity.toTravelDiary()
        }
    }
    
    suspend fun getAllDiariesWithDetails(): List<TravelDiary> {
        return withContext(Dispatchers.IO) {
            val diaryEntities = diaryDao.getAllDiariesSync()
            diaryEntities.map { entity ->
                val waypoints = getWaypointsByDiaryId(entity.id)
                val photos = getPhotosByDiaryId(entity.id)
                
                entity.toTravelDiary().copy(
                    waypoints = waypoints.toMutableList(),
                    photos = photos.toMutableList()
                )
            }
        }
    }
    
    suspend fun getDiaryById(diaryId: String): TravelDiary? {
        return withContext(Dispatchers.IO) {
            val diaryEntity = diaryDao.getDiaryById(diaryId)
            diaryEntity?.let {
                val waypoints = getWaypointsByDiaryId(diaryId)
                val photos = getPhotosByDiaryId(diaryId)
                
                it.toTravelDiary().copy(
                    waypoints = waypoints.toMutableList(),
                    photos = photos.toMutableList()
                )
            }
        }
    }
    
    suspend fun getIncompleteDiary(): TravelDiary? {
        return withContext(Dispatchers.IO) {
            val diaryEntity = diaryDao.getIncompleteDiary()
            diaryEntity?.let {
                val waypoints = getWaypointsByDiaryId(it.id)
                val photos = getPhotosByDiaryId(it.id)
                
                it.toTravelDiary().copy(
                    waypoints = waypoints.toMutableList(),
                    photos = photos.toMutableList()
                )
            }
        }
    }
    
    suspend fun insertDiary(diary: TravelDiary) {
        withContext(Dispatchers.IO) {
            diaryDao.insertDiary(diary.toEntity())
        }
    }
    
    suspend fun updateDiary(diary: TravelDiary) {
        withContext(Dispatchers.IO) {
            diaryDao.updateDiary(diary.toEntity())
        }
    }
    
    suspend fun deleteDiary(diary: TravelDiary) {
        withContext(Dispatchers.IO) {
            diaryDao.deleteDiary(diary.toEntity())
        }
    }
    
    suspend fun getWaypointsByDiaryId(diaryId: String): List<DiaryWaypoint> {
        return withContext(Dispatchers.IO) {
            diaryDao.getWaypointsByDiaryId(diaryId).map { it.toDiaryWaypoint() }
        }
    }
    
    suspend fun insertWaypoint(diaryId: String, waypoint: DiaryWaypoint) {
        withContext(Dispatchers.IO) {
            diaryDao.insertWaypoint(waypoint.toEntity(diaryId))
        }
    }
    
    suspend fun getPhotosByDiaryId(diaryId: String): List<DiaryPhoto> {
        return withContext(Dispatchers.IO) {
            diaryDao.getPhotosByDiaryId(diaryId).map { it.toDiaryPhoto() }
        }
    }
    
    suspend fun insertPhoto(diaryId: String, photo: DiaryPhoto) {
        withContext(Dispatchers.IO) {
            diaryDao.insertPhoto(photo.toEntity(diaryId))
        }
    }
    
    suspend fun deleteWaypoint(waypoint: DiaryWaypoint) {
        withContext(Dispatchers.IO) {
            diaryDao.deleteWaypoint(waypoint.toEntity(""))
        }
    }
    
    suspend fun deletePhoto(photo: DiaryPhoto) {
        withContext(Dispatchers.IO) {
            diaryDao.deletePhoto(photo.toEntity(""))
        }
    }
    
    // Extension functions for conversion
    private fun DiaryEntity.toTravelDiary(): TravelDiary {
        return TravelDiary(
            id = id,
            title = title,
            description = description,
            date = date,
            totalSteps = totalSteps,
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            isCompleted = isCompleted
        )
    }
    
    private fun TravelDiary.toEntity(): DiaryEntity {
        return DiaryEntity(
            id = id,
            title = title,
            description = description,
            date = date,
            totalSteps = totalSteps,
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            isCompleted = isCompleted
        )
    }
    
    private fun WaypointEntity.toDiaryWaypoint(): DiaryWaypoint {
        return DiaryWaypoint(
            id = id,
            location = Location(
                name = locationName,
                address = locationAddress,
                latitude = latitude,
                longitude = longitude
            ),
            timestamp = timestamp,
            notes = notes,
            stepsAtPoint = stepsAtPoint
        )
    }
    
    private fun DiaryWaypoint.toEntity(diaryId: String): WaypointEntity {
        return WaypointEntity(
            id = id,
            diaryId = diaryId,
            locationName = location.name,
            locationAddress = location.address,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = timestamp,
            notes = notes,
            stepsAtPoint = stepsAtPoint
        )
    }
    
    private fun PhotoEntity.toDiaryPhoto(): DiaryPhoto {
        return DiaryPhoto(
            id = id,
            filePath = filePath,
            timestamp = timestamp,
            location = if (latitude != null && longitude != null) {
                Location(
                    name = "Photo Location",
                    latitude = latitude,
                    longitude = longitude
                )
            } else null,
            caption = caption,
            waypointId = waypointId
        )
    }
    
    private fun DiaryPhoto.toEntity(diaryId: String): PhotoEntity {
        return PhotoEntity(
            id = id,
            diaryId = diaryId,
            filePath = filePath,
            timestamp = timestamp,
            caption = caption,
            waypointId = waypointId,
            latitude = location?.latitude,
            longitude = location?.longitude
        )
    }
} 