package com.example.travel.ui.fitness

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.travel.data.repository.StepCountRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

data class FitnessStats(
    val steps: Int = 0,
    val distanceKm: Double = 0.0,
    val activeMinutes: Int = 0
)

class FitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val stepCountRepository = StepCountRepository(application)

    private val _dailyStats = MutableLiveData<FitnessStats>()
    val dailyStats: LiveData<FitnessStats> = _dailyStats

    private val _isTracking = MutableLiveData<Boolean>()
    val isTracking: LiveData<Boolean> = _isTracking

    private var sessionStartTime: Long = 0
    private var sessionSteps = 0
    private var dailySteps = 0

    // Constants for calculations
    private companion object {
        const val STEPS_PER_KM = 1250.0 // Average steps per kilometer
        const val STEPS_PER_MINUTE_ACTIVE = 80 // Steps per minute to consider as active
    }

    init {
        _dailyStats.value = FitnessStats()
        _isTracking.value = false
        
        loadTodaySteps()
        
        // Start background step counting service
        stepCountRepository.startStepCounting()
        
        // Start periodic refresh
        startPeriodicRefresh()
    }

    fun startTracking() {
        _isTracking.value = true
        sessionStartTime = System.currentTimeMillis()
        sessionSteps = 0
        
        // 开始追踪时立即刷新数据
        refreshData()
        
        // 启动高频率刷新（追踪模式下）
        startHighFrequencyRefresh()
    }

    fun stopTracking() {
        _isTracking.value = false
        // Keep session data for display
    }

    fun addSteps(steps: Int) {
        // 这个方法现在主要用于手动调整，大部分时候步数由后台服务处理
        sessionSteps += steps
        
        // 立即刷新数据以获取最新的数据库数据
        refreshData()
    }

    fun resetDailyStats() {
        viewModelScope.launch {
            try {
                // 重置数据库中的今日步数
                stepCountRepository.resetTodaySteps()
                
                // 重置内存中的变量
                dailySteps = 0
                updateDailyStats()
                
                // 通知后台服务重置
                stepCountRepository.stopStepCounting()
                stepCountRepository.startStepCounting()
                
            } catch (e: Exception) {
                // 处理错误
                Log.e("FitnessViewModel", "Failed to reset daily stats", e)
            }
        }
    }

    private fun updateDailyStats() {
        val distance = calculateDistance(dailySteps)
        val activeMinutes = calculateActiveMinutes(dailySteps)

        _dailyStats.value = FitnessStats(
            steps = dailySteps,
            distanceKm = distance,
            activeMinutes = activeMinutes
        )
    }

    private fun calculateDistance(steps: Int): Double {
        return steps / STEPS_PER_KM
    }

    private fun calculateActiveMinutes(steps: Int): Int {
        // Estimate active minutes based on steps
        // Assume active if taking more than 80 steps per minute
        return (steps / STEPS_PER_MINUTE_ACTIVE).toInt()
    }

    fun getTodaysGoalProgress(): Float {
        val dailyGoal = 10000 // Standard daily step goal
        val current = _dailyStats.value?.steps ?: 0
        return (current.toFloat() / dailyGoal).coerceAtMost(1.0f)
    }

    fun getMotivationalMessage(): String {
        val steps = _dailyStats.value?.steps ?: 0
        return when {
            steps < 1000 -> "Start your journey! Every step counts."
            steps < 3000 -> "Great start! Keep moving forward."
            steps < 5000 -> "You're doing well! Halfway to your goal."
            steps < 8000 -> "Excellent progress! You're almost there."
            steps < 10000 -> "So close to your goal! Push a little more."
            else -> "Outstanding! You've exceeded your daily goal!"
        }
    }
    
    private fun loadTodaySteps() {
        viewModelScope.launch {
            try {
                val todayStepCount = stepCountRepository.getTodayStepCount()
                dailySteps = todayStepCount?.stepCount ?: 0
                updateDailyStats()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun refreshData() {
        loadTodaySteps()
    }
    
    // 标准频率刷新（正常模式）
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(10_000) // 改为每10秒刷新一次（比原来的30秒快3倍）
                if (_isTracking.value != true) { // 只在非追踪模式下使用标准频率
                    loadTodaySteps()
                }
            }
        }
    }
    
    // 高频率刷新（追踪模式下）
    private fun startHighFrequencyRefresh() {
        viewModelScope.launch {
            while (isActive && _isTracking.value == true) {
                delay(3_000) // 追踪模式下每3秒刷新一次
                loadTodaySteps()
            }
        }
    }
    
    // 添加立即刷新方法供外部调用
    fun forceRefresh() {
        viewModelScope.launch {
            loadTodaySteps()
        }
    }
} 