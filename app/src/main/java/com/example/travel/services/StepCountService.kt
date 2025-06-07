package com.example.travel.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.travel.MainActivity
import com.example.travel.R
import com.example.travel.data.database.StepCountEntity
import com.example.travel.data.database.TravelDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class StepCountService : Service(), SensorEventListener {
    
    companion object {
        const val CHANNEL_ID = "step_count_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "StepCountService"
        
        fun startService(context: Context) {
            val intent = Intent(context, StepCountService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, StepCountService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: TravelDatabase
    
    private var totalStepsToday = 0
    private var lastStepCount = 0
    private var isFirstReading = true
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    override fun onCreate() {
        super.onCreate()
        
        database = TravelDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Create WakeLock to keep CPU running, ensuring sensors work during screen lock
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TravelApp:StepCountWakeLock"
        )
        
        // Prefer TYPE_STEP_COUNTER (cumulative step counting)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        // If no step counter sensor, use step detector sensor
        if (stepCounterSensor == null) {
            stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        }
        
        createNotificationChannel()
        loadTodayStepCount()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Acquire WakeLock to ensure sensors work during screen lock
        wakeLock?.acquire(60*60*1000L /*60 minutes*/)
        
        // Start WakeLock renewal timer
        startWakeLockRenewal()
        
        // 重新加载今日步数（可能已被重置）
        loadTodayStepCount()
        
        // 重置传感器状态
        isFirstReading = true
        lastStepCount = 0
        
        // Register sensor listeners with lower sampling rate to save battery
        stepCounterSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Registered step counter sensor")
        } ?: stepDetectorSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Registered step detector sensor")
        } ?: run {
            Log.e(TAG, "Device does not support step sensors")
            wakeLock?.release()
            stopSelf()
        }
        
        return START_STICKY // Service will be restarted if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        
        // Release WakeLock
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        
        serviceScope.cancel()
        Log.d(TAG, "Step counting service stopped")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    handleStepCounterSensor(sensorEvent.values[0].toInt())
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    handleStepDetectorSensor()
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle sensor accuracy changes
    }
    
    private fun handleStepCounterSensor(totalSteps: Int) {
        if (isFirstReading) {
            // 第一次读取，设置基准值
            isFirstReading = false
            lastStepCount = totalSteps
            Log.d(TAG, "Step counter initialized: $totalSteps, today's existing steps: $totalStepsToday")
        } else {
            // 计算新增步数
            val newSteps = totalSteps - lastStepCount
            if (newSteps > 0 && newSteps < 1000) { // 防止异常的大幅跳跃
                totalStepsToday += newSteps
                lastStepCount = totalSteps
                saveStepCount()
                updateNotification()
                Log.d(TAG, "New steps detected: $newSteps, total today: $totalStepsToday")
            } else if (newSteps < 0) {
                // 处理设备重启或传感器重置的情况
                Log.w(TAG, "Step counter reset detected, reinitializing...")
                lastStepCount = totalSteps
                isFirstReading = true
            }
        }
    }
    
    private fun handleStepDetectorSensor() {
        // 每检测到一步就增加1
        totalStepsToday++
        saveStepCount()
        updateNotification()
        Log.d(TAG, "Step detected, total today: $totalStepsToday")
    }
    
    private fun loadTodayStepCount() {
        serviceScope.launch {
            try {
                val today = getTodayDate()
                val existingRecord = database.stepCountDao().getStepCountByDate(today)
                totalStepsToday = existingRecord?.stepCount ?: 0
                
                // 重置传感器相关变量
                isFirstReading = true
                lastStepCount = 0
                
                Log.d(TAG, "Loaded today's steps: $totalStepsToday")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load today's steps", e)
            }
        }
    }
    
    private fun saveStepCount() {
        serviceScope.launch {
            try {
                val today = getTodayDate()
                val stepCountEntity = StepCountEntity(
                    date = today,
                    stepCount = totalStepsToday,
                    timestamp = System.currentTimeMillis(),
                    isCompleted = false
                )
                
                // 使用 upsert 模式，如果存在则更新，不存在则插入
                val existingRecord = database.stepCountDao().getStepCountByDate(today)
                if (existingRecord != null) {
                    database.stepCountDao().updateStepCount(
                        stepCountEntity.copy(id = existingRecord.id)
                    )
                } else {
                    database.stepCountDao().insertStepCount(stepCountEntity)
                }
                
                Log.d(TAG, "Saved steps: $totalStepsToday")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save steps", e)
            }
        }
    }
    
    private fun getTodayDate(): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Display step counting status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Step Counting Active")
        .setContentText("Today's steps: $totalStepsToday")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(createPendingIntent())
        .setOngoing(true)
        .setSilent(true)
        .build()
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun startWakeLockRenewal() {
        serviceScope.launch {
            while (isActive) {
                delay(50 * 60 * 1000L) // 每50分钟续约一次
                try {
                    wakeLock?.let { wl ->
                        if (wl.isHeld) {
                            wl.release()
                        }
                        wl.acquire(60*60*1000L /*60 minutes*/)
                        Log.d(TAG, "WakeLock renewed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to renew WakeLock", e)
                }
            }
        }
    }
} 