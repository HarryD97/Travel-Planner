package com.example.travel.data.repository

import android.content.Context
import com.example.travel.data.database.StepCountDao
import com.example.travel.data.database.StepCountEntity
import com.example.travel.data.database.TravelDatabase
import com.example.travel.services.StepCountService
import kotlinx.coroutines.flow.Flow
import java.util.*

class StepCountRepository(private val context: Context) {
    
    private val database = TravelDatabase.getDatabase(context)
    private val stepCountDao: StepCountDao = database.stepCountDao()
    
    fun getAllStepCounts(): Flow<List<StepCountEntity>> {
        return stepCountDao.getAllStepCounts()
    }
    
    suspend fun getStepCountByDate(date: Date): StepCountEntity? {
        return stepCountDao.getStepCountByDate(date)
    }
    
    suspend fun getTodayStepCount(): StepCountEntity? {
        val today = getTodayDate()
        return stepCountDao.getStepCountByDate(today)
    }
    
    suspend fun getStepCountsBetweenDates(startDate: Date, endDate: Date): List<StepCountEntity> {
        return stepCountDao.getStepCountsBetweenDates(startDate, endDate)
    }
    
    suspend fun getTotalStepsBetweenDates(startDate: Date, endDate: Date): Int {
        return stepCountDao.getTotalStepsBetweenDates(startDate, endDate) ?: 0
    }
    
    suspend fun getWeeklySteps(): Int {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = calendar.time
        return getTotalStepsBetweenDates(startDate, endDate)
    }
    
    suspend fun getMonthlySteps(): Int {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = calendar.time
        return getTotalStepsBetweenDates(startDate, endDate)
    }
    
    suspend fun insertStepCount(stepCount: StepCountEntity) {
        stepCountDao.insertStepCount(stepCount)
    }
    
    suspend fun updateStepCount(stepCount: StepCountEntity) {
        stepCountDao.updateStepCount(stepCount)
    }
    
    suspend fun deleteStepCount(stepCount: StepCountEntity) {
        stepCountDao.deleteStepCount(stepCount)
    }
    
    suspend fun resetTodaySteps() {
        val today = getTodayDate()
        val existingRecord = stepCountDao.getStepCountByDate(today)
        if (existingRecord != null) {
            // Delete today's record
            stepCountDao.deleteStepCount(existingRecord)
        }
    }
    
    suspend fun cleanupOldRecords(daysToKeep: Int = 90) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysToKeep)
        stepCountDao.deleteOldRecords(calendar.time)
    }
    
    fun startStepCounting() {
        StepCountService.startService(context)
    }
    
    fun stopStepCounting() {
        StepCountService.stopService(context)
    }
    
    private fun getTodayDate(): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
} 