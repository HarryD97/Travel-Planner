package com.example.travel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.travel.databinding.ActivityMainBinding
import com.example.travel.data.repository.StepCountRepository
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var stepCountRepository: StepCountRepository
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        ).apply {
            // Android 13+ requires notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        stepCountRepository = StepCountRepository(this)
        
        setupNavigation()
        requestPermissions()
        checkBatteryOptimization()
        
        // Start step counting service after permission check is complete
        if (hasStepCountingPermissions()) {
            startStepCountingService()
        }
    }
    
    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        
        // Setup bottom navigation with nav controller
        navView.setupWithNavController(navController)
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d("MainActivity", "Permission results received")
            
            // Check if step counting related permissions are granted
            if (hasStepCountingPermissions()) {
                Log.d("MainActivity", "Step counting permissions granted, starting service")
                startStepCountingService()
            } else {
                Log.w("MainActivity", "Step counting permissions not granted")
                // Can show explanation for why these permissions are needed
                showPermissionExplanation()
            }
        }
    }
    
    private fun showPermissionExplanation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder(this)
                .setTitle("Permission Explanation")
                .setMessage("The app needs physical activity recognition permission to record your steps. Without this permission, the step counting feature will not work properly.")
                .setPositiveButton("Request Again") { _, _ ->
                    requestPermissions()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization Settings")
            .setMessage("To ensure the step counting service runs properly in the background, it's recommended to disable battery optimization for this app. This won't significantly affect battery life.")
            .setPositiveButton("Go to Settings") { _, _ ->
                requestIgnoreBatteryOptimization()
            }
            .setNegativeButton("Skip", null)
            .show()
    }
    
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // If can't navigate directly to app settings, go to battery optimization settings list
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }
    
    private fun hasStepCountingPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyArray()
        }
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun startStepCountingService() {
        try {
            stepCountRepository.startStepCounting()
            Log.d("MainActivity", "Step counting service started")
            
            // Check if device supports step counting sensors
            val sensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
            val stepCounter = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
            val stepDetector = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR)
            
            val debugInfo = buildString {
                append("Step sensor support status: ")
                when {
                    stepCounter != null -> append("Supports STEP_COUNTER")
                    stepDetector != null -> append("Supports STEP_DETECTOR")
                    else -> append("Does not support step sensors")
                }
            }
            Log.d("MainActivity", debugInfo)
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start step counting service", e)
        }
    }
} 