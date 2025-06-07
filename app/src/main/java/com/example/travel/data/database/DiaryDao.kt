package com.example.travel.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DiaryDao {
    
    // Diary operations
    @Query("SELECT * FROM travel_diaries ORDER BY date DESC")
    fun getAllDiaries(): LiveData<List<DiaryEntity>>
    
    @Query("SELECT * FROM travel_diaries ORDER BY date DESC")
    suspend fun getAllDiariesSync(): List<DiaryEntity>
    
    @Query("SELECT * FROM travel_diaries WHERE id = :diaryId")
    suspend fun getDiaryById(diaryId: String): DiaryEntity?
    
    @Query("SELECT * FROM travel_diaries WHERE isCompleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun getIncompleteDiary(): DiaryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: DiaryEntity)
    
    @Update
    suspend fun updateDiary(diary: DiaryEntity)
    
    @Delete
    suspend fun deleteDiary(diary: DiaryEntity)
    
    // Waypoint operations
    @Query("SELECT * FROM diary_waypoints WHERE diaryId = :diaryId ORDER BY timestamp ASC")
    suspend fun getWaypointsByDiaryId(diaryId: String): List<WaypointEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity)
    
    @Update
    suspend fun updateWaypoint(waypoint: WaypointEntity)
    
    @Delete
    suspend fun deleteWaypoint(waypoint: WaypointEntity)
    
    // Photo operations
    @Query("SELECT * FROM diary_photos WHERE diaryId = :diaryId ORDER BY timestamp ASC")
    suspend fun getPhotosByDiaryId(diaryId: String): List<PhotoEntity>
    
    @Query("SELECT * FROM diary_photos WHERE waypointId = :waypointId ORDER BY timestamp ASC")
    suspend fun getPhotosByWaypointId(waypointId: String): List<PhotoEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)
    
    @Update
    suspend fun updatePhoto(photo: PhotoEntity)
    
    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)
    
    // Statistics
    @Query("SELECT COUNT(*) FROM travel_diaries")
    suspend fun getTotalDiariesCount(): Int
    
    @Query("SELECT COUNT(*) FROM diary_waypoints")
    suspend fun getTotalWaypointsCount(): Int
    
    @Query("SELECT COUNT(*) FROM diary_photos")
    suspend fun getTotalPhotosCount(): Int
    
    @Query("SELECT SUM(totalSteps) FROM travel_diaries")
    suspend fun getTotalSteps(): Int?
    
    @Query("SELECT SUM(totalDistance) FROM travel_diaries")
    suspend fun getTotalDistance(): Double?
} 