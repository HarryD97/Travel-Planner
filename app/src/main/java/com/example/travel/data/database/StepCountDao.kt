package com.example.travel.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface StepCountDao {
    
    @Query("SELECT * FROM step_counts ORDER BY date DESC")
    fun getAllStepCounts(): Flow<List<StepCountEntity>>
    
    @Query("SELECT * FROM step_counts WHERE date = :date LIMIT 1")
    suspend fun getStepCountByDate(date: Date): StepCountEntity?
    
    @Query("SELECT * FROM step_counts WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getStepCountsBetweenDates(startDate: Date, endDate: Date): List<StepCountEntity>
    
    @Query("SELECT SUM(stepCount) FROM step_counts WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalStepsBetweenDates(startDate: Date, endDate: Date): Int?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStepCount(stepCount: StepCountEntity)
    
    @Update
    suspend fun updateStepCount(stepCount: StepCountEntity)
    
    @Delete
    suspend fun deleteStepCount(stepCount: StepCountEntity)
    
    @Query("DELETE FROM step_counts WHERE date < :date")
    suspend fun deleteOldRecords(date: Date)
    
    @Query("SELECT COUNT(*) FROM step_counts WHERE date = :date")
    suspend fun hasRecordForDate(date: Date): Int
} 