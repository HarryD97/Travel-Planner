package com.example.travel.ui.fitness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.travel.databinding.FragmentFitnessBinding
import com.example.travel.data.repository.StepCountRepository
import kotlinx.coroutines.*

class FitnessFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentFitnessBinding? = null
    private val binding get() = _binding!!

    private lateinit var fitnessViewModel: FitnessViewModel
    private lateinit var stepCountRepository: StepCountRepository
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null

    private var initialStepCount = 0
    private var sessionSteps = 0
    private var isFirstReading = true

    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fitnessViewModel = ViewModelProvider(this)[FitnessViewModel::class.java]
        stepCountRepository = StepCountRepository(requireContext())
        _binding = FragmentFitnessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSensors()
        setupUI()
        observeViewModel()
        checkPermissions()

        // Refresh data immediately
        fitnessViewModel.forceRefresh()

        // Do not start step service here, as background service should be started by MainActivity
        // stepCountRepository.startStepCounting()
    }

    private fun setupSensors() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Try to get step counter sensor (more accurate but requires API 19+)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Fallback to step detector sensor
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // Display sensor availability information
        val sensorInfo = buildString {
            append("Sensor Status:\n")
            if (stepCounterSensor != null) {
                append("✓ Step Counter Available (${stepCounterSensor!!.name})\n")
            } else {
                append("✗ Step Counter Unavailable\n")
            }

            if (stepDetectorSensor != null) {
                append("✓ Step Detector Available (${stepDetectorSensor!!.name})\n")
            } else {
                append("✗ Step Detector Unavailable\n")
            }
        }

        Log.d("FitnessFragment", sensorInfo)

        if (stepCounterSensor == null && stepDetectorSensor == null) {
            binding.tvStepCounterUnavailable.visibility = View.VISIBLE
            binding.cardStepsToday.visibility = View.GONE
        } else {
            binding.tvStepCounterUnavailable.visibility = View.GONE
            binding.cardStepsToday.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        binding.btnResetSteps.setOnClickListener {
            resetDailySteps()
        }

        binding.btnStartTracking.setOnClickListener {
            startStepTracking()
            // Do not start the service repeatedly, the background service is already running
            // stepCountRepository.startStepCounting()
        }

        binding.btnStopTracking.setOnClickListener {
            stopStepTracking()
            // Do not stop the background service, only stop foreground tracking
            // stepCountRepository.stopStepCounting()
        }

        binding.btnResetSteps.setOnClickListener {
            resetDailySteps()
        }

        // Add functionality to refresh data when clicking the step card
        binding.cardStepsToday.setOnClickListener {
            fitnessViewModel.forceRefresh()
            Toast.makeText(context, "Data Refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        fitnessViewModel.dailyStats.observe(viewLifecycleOwner) { stats ->
            binding.tvStepsCount.text = stats.steps.toString()
            binding.tvDistanceValue.text = String.format("%.2f km", stats.distanceKm)
            binding.tvActiveTimeValue.text = formatActiveTime(stats.activeMinutes)
        }

        fitnessViewModel.isTracking.observe(viewLifecycleOwner) { isTracking ->
            binding.btnStartTracking.visibility = if (isTracking) View.GONE else View.VISIBLE
            binding.btnStopTracking.visibility = if (isTracking) View.VISIBLE else View.GONE
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    PERMISSION_REQUEST_ACTIVITY_RECOGNITION
                )
                return
            }
        }
        startStepTracking()
    }

    private fun startStepTracking() {
        // First ensure to clear previous listeners
        sensorManager.unregisterListener(this)

        var listenerRegistered = false

        // Prioritize trying the step counter sensor
        stepCounterSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (success) {
                listenerRegistered = true
                Log.d("FitnessFragment", "Step counter sensor registered successfully")
            } else {
                Log.w("FitnessFragment", "Failed to register step counter sensor")
            }
        }

        // If step counter is unavailable, try the step detector
        if (!listenerRegistered) {
            stepDetectorSensor?.let { sensor ->
                val success = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (success) {
                    listenerRegistered = true
                    Log.d("FitnessFragment", "Step detector sensor registered successfully")
                } else {
                    Log.w("FitnessFragment", "Failed to register step detector sensor")
                }
            }
        }

        if (listenerRegistered) {
            fitnessViewModel.startTracking()
            Toast.makeText(context, "Foreground step tracking started", Toast.LENGTH_SHORT).show()
            Log.d("FitnessFragment", "Frontend step tracking started")
        } else {
            Toast.makeText(context, "Unable to start foreground step tracking", Toast.LENGTH_LONG).show()
            Log.w("FitnessFragment", "Failed to start frontend step tracking")
        }
    }

    private fun stopStepTracking() {
        sensorManager.unregisterListener(this)
        fitnessViewModel.stopTracking()
        Toast.makeText(context, "Foreground step tracking stopped", Toast.LENGTH_SHORT).show()
        Log.d("FitnessFragment", "Frontend step tracking stopped")
    }

    private fun resetDailySteps() {
        // Stop foreground sensor listener
        sensorManager.unregisterListener(this)

        // Reset ViewModel
        fitnessViewModel.resetDailyStats()

        // Reset local variables
        sessionSteps = 0
        initialStepCount = 0
        isFirstReading = true

        Toast.makeText(context, "Daily steps reset", Toast.LENGTH_SHORT).show()

        // If currently tracking, restart
        if (fitnessViewModel.isTracking.value == true) {
            startStepTracking()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            // Only process sensor events if tracking is active
            if (fitnessViewModel.isTracking.value != true) return

            when (sensorEvent.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    // Step counter gives total steps since device boot
                    val totalSteps = sensorEvent.values[0].toInt()

                    if (isFirstReading) {
                        initialStepCount = totalSteps
                        isFirstReading = false
                        Log.d("FitnessFragment", "Step counter initialized: $totalSteps")
                    } else {
                        val newSteps = totalSteps - initialStepCount
                        if (newSteps > sessionSteps && newSteps < sessionSteps + 100) { // Prevent abnormal jumps
                            val stepDiff = newSteps - sessionSteps
                            sessionSteps = newSteps
                            Log.d("FitnessFragment", "Session steps updated: +$stepDiff, total session: $sessionSteps")

                            // Refresh data immediately to get the latest database data
                            fitnessViewModel.forceRefresh()
                        }
                    }
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    // Step detector triggers for each step
                    sessionSteps++
                    Log.d("FitnessFragment", "Step detected, session total: $sessionSteps")

                    // Refresh data immediately to get the latest database data
                    fitnessViewModel.forceRefresh()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_ACTIVITY_RECOGNITION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startStepTracking()
                } else {
                    Toast.makeText(
                        context,
                        "Activity recognition permission is required for step counting",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun formatActiveTime(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                "${hours}h ${remainingMinutes}min"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (fitnessViewModel.isTracking.value == true) {
            startStepTracking()
        }
        // Refresh data immediately when Fragment becomes visible
        fitnessViewModel.forceRefresh()

        // Start high-frequency refresh
        startForegroundRefresh()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Stop high-frequency refresh
        stopForegroundRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager.unregisterListener(this)
        stopForegroundRefresh()
        _binding = null
    }

    private fun startForegroundRefresh() {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(2_000) // Refresh every 2 seconds
                fitnessViewModel.forceRefresh()
            }
        }
    }

    private fun stopForegroundRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    companion object {
        private const val PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 1003
    }
}