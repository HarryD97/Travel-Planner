package com.example.travel.ui.diary

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.example.travel.adapters.WaypointAdapter
import com.example.travel.adapters.DiaryPhotosAdapter
import com.example.travel.databinding.FragmentDiaryRecordBinding
import com.example.travel.data.Location as DataLocation
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DiaryRecordFragment : Fragment(), SensorEventListener, LocationListener {

    private var _binding: FragmentDiaryRecordBinding? = null
    private val binding get() = _binding!!

    private lateinit var diaryViewModel: DiaryViewModel
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var waypointAdapter: WaypointAdapter
    private lateinit var photosAdapter: DiaryPhotosAdapter

    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null

    // Step counter
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var stepCount = 0
    private var initialStepCount = 0
    private var isStepCounterInitialized = false
    private var sessionStartSteps = 0
    private var isUsingStepDetector = false

    // Location tracking
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isLocationTracking = false

    // Recording state - removed local isRecording, use ViewModel state

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            addPhotoToDiary(selectedUri)
        }
    }

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeCamera()
            startLocationTracking()
        } else {
            Toast.makeText(context, "Some permissions were denied, functionality may be limited", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use requireActivity() to ensure ViewModel is shared at the Activity level
        diaryViewModel = ViewModelProvider(requireActivity())[DiaryViewModel::class.java]
        _binding = FragmentDiaryRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupSensors()
        setupLocationTracking()
        setupUI()
        setupRecyclerViews()
        observeViewModel()

        // Request permissions
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // Check if there is an ongoing recording when resuming state
        updateUIBasedOnCurrentDiary()
        if (isLocationTracking && allPermissionsGranted()) {
            startLocationTracking()
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause location listener to save power, but don't clear recording state
        stopLocationTracking()
    }

    private fun setupSensors() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Prioritize TYPE_STEP_COUNTER (cumulative step sensor)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Fallback TYPE_STEP_DETECTOR (single step detection sensor)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        when {
            stepCounterSensor != null -> {
                Log.d("DiaryRecord", "Using STEP_COUNTER sensor")
                isUsingStepDetector = false
            }
            stepDetectorSensor != null -> {
                Log.d("DiaryRecord", "Using STEP_DETECTOR sensor as fallback")
                isUsingStepDetector = true
            }
            else -> {
                Toast.makeText(context, "Device does not support step counter", Toast.LENGTH_LONG).show()
                Log.w("DiaryRecord", "No step sensors available")
            }
        }
    }

    private fun setupLocationTracking() {
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun setupUI() {
        binding.btnStartDiary.setOnClickListener {
            startNewDiary()
        }

        binding.btnStopDiary.setOnClickListener {
            stopCurrentDiary()
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }

        binding.btnSelectFromGallery.setOnClickListener {
            selectFromGallery()
        }

        binding.btnAddWaypoint.setOnClickListener {
            addCurrentLocationAsWaypoint()
        }

        binding.btnToggleCamera.setOnClickListener {
            toggleCamera()
        }

        // Initially hide camera preview
        binding.cameraContainer.visibility = View.GONE
        updateUI()
    }

    private fun setupRecyclerViews() {
        // Setup waypoint list for current diary
        waypointAdapter = WaypointAdapter { waypoint ->
            Toast.makeText(context, "Waypoint: ${waypoint.location.name}", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewWaypoints.adapter = waypointAdapter
        binding.recyclerViewWaypoints.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // Setup photos adapter for real-time photo display
        photosAdapter = DiaryPhotosAdapter { photoPath ->
            // Photo viewing functionality can be added here
            Toast.makeText(context, "View photo: $photoPath", Toast.LENGTH_SHORT).show()
        }

        // Set up photos RecyclerView
        binding.recyclerViewCurrentPhotos.adapter = photosAdapter
        binding.recyclerViewCurrentPhotos.layoutManager = GridLayoutManager(context, 2)
    }

    private fun observeViewModel() {
        diaryViewModel.currentDiary.observe(viewLifecycleOwner) { diary ->
            updateCurrentDiaryUI(diary)
            updateUIBasedOnCurrentDiary()
        }

        diaryViewModel.currentWaypoints.observe(viewLifecycleOwner) { waypoints ->
            waypointAdapter.submitList(waypoints)
        }

        diaryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        diaryViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                diaryViewModel.clearErrorMessage()
            }
        }
    }

    private fun startNewDiary() {
        val title = binding.etDiaryTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(context, "Please enter a diary title", Toast.LENGTH_SHORT).show()
            return
        }

        val description = binding.etDiaryDescription.text.toString().trim()

        stepCount = 0
        initialStepCount = 0
        sessionStartSteps = 0
        isStepCounterInitialized = false

        // Start step counting with improved logic
        startStepCounting()

        // Start location tracking
        startLocationTracking()

        diaryViewModel.startNewDiary(title, description)
        updateUI()

        Toast.makeText(context, "Starting travel diary recording", Toast.LENGTH_SHORT).show()
    }

    private fun stopCurrentDiary() {
        // Stop step counting
        stopStepCounting()

        // Stop location tracking
        stopLocationTracking()

        diaryViewModel.currentDiary.value?.let { diary ->
            diaryViewModel.completeDiary(diary.id, stepCount)
        }

        // Clear form
        binding.etDiaryTitle.text?.clear()
        binding.etDiaryDescription.text?.clear()

        // Clear photo display
        photosAdapter.submitList(emptyList())

        // Hide current diary container
        binding.currentDiaryContainer.visibility = View.GONE

        updateUI()
        Toast.makeText(context, "Travel diary recording completed", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionsIfNeeded() {
        if (!allPermissionsGranted()) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            initializeCamera()
            if (isRecording()) {
                startLocationTracking()
            }
        }
    }

    private fun initializeCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                Log.d("DiaryRecord", "Camera initialized successfully")

            } catch (exc: Exception) {
                Log.e("DiaryRecord", "Camera initialization failed", exc)
                Toast.makeText(context, "Camera initialization failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startLocationTracking() {
        if (!allPermissionsGranted()) {
            Log.w("DiaryRecord", "Location permissions not granted")
            return
        }

        try {
            isLocationTracking = true

            // Check if GPS is enabled
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(context, "Please enable GPS location service", Toast.LENGTH_LONG).show()
                updateLocationStatus("GPS not enabled")
                return
            }

            updateLocationStatus("Getting location...")

            // First, try to get the last known location
            try {
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                lastKnownLocation?.let { location ->
                    onLocationChanged(location)
                }
            } catch (e: SecurityException) {
                Log.e("DiaryRecord", "Security exception getting last known location", e)
            }

            // Start location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000L, // 3 seconds - more frequent updates
                5f,    // 5 meters
                this
            )

            // Also use network provider as a fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L, // 5 seconds
                    10f,   // 10 meters
                    this
                )
            }

            Log.d("DiaryRecord", "Location tracking started")

        } catch (e: SecurityException) {
            Log.e("DiaryRecord", "Location permission denied", e)
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            updateLocationStatus("Permission denied")
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error starting location tracking", e)
            Toast.makeText(context, "Failed to start location service", Toast.LENGTH_SHORT).show()
            updateLocationStatus("Failed to start")
        }
    }

    private fun stopLocationTracking() {
        try {
            isLocationTracking = false
            locationManager.removeUpdates(this)
            Log.d("DiaryRecord", "Location tracking stopped")
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error stopping location tracking", e)
        }
    }

    private fun updateLocationStatus(status: String) {
        activity?.runOnUiThread {
            binding.tvCurrentLocation.text = "Location: $status"
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        if (!isRecording()) {
            Toast.makeText(context, "Please start recording a diary first", Toast.LENGTH_SHORT).show()
            return
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
            .format(System.currentTimeMillis())

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File(requireContext().externalMediaDirs.firstOrNull(), "$name.jpg")
        ).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("DiaryRecord", "Photo capture failed", exception)
                    Toast.makeText(context, "Photo capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        addPhotoToDiary(uri)
                        Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun selectFromGallery() {
        if (!isRecording()) {
            Toast.makeText(context, "Please start recording a diary first", Toast.LENGTH_SHORT).show()
            return
        }
        activityResultLauncher.launch("image/*")
    }

    private fun addPhotoToDiary(uri: Uri) {
        diaryViewModel.currentDiary.value?.let { diary ->
            val location = currentLocation?.let { loc ->
                DataLocation(
                    name = "Current location",
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )
            }

            diaryViewModel.addPhotoToDiary(diary.id, uri.toString(), location)
            Toast.makeText(context, "Photo added to diary", Toast.LENGTH_SHORT).show()

            // Update photo display in real-time
            updateCurrentDiaryUI(diary)
        }
    }

    private fun addCurrentLocationAsWaypoint() {
        if (!isRecording()) {
            Toast.makeText(context, "Please start recording a diary first", Toast.LENGTH_SHORT).show()
            return
        }

        currentLocation?.let { location ->
            diaryViewModel.currentDiary.value?.let { diary ->
                val dataLocation = DataLocation(
                    name = "Waypoint ${diary.waypoints.size + 1}",
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                diaryViewModel.addWaypointToDiary(diary.id, dataLocation, stepCount)
                Toast.makeText(context, "Waypoint added", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            if (isLocationTracking) {
                Toast.makeText(context, "Getting location information, please try again later", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Location service not started", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleCamera() {
        if (binding.cameraContainer.visibility == View.VISIBLE) {
            binding.cameraContainer.visibility = View.GONE
            binding.btnToggleCamera.text = "Show Camera"
        } else {
            binding.cameraContainer.visibility = View.VISIBLE
            binding.btnToggleCamera.text = "Hide Camera"

            // Ensure camera is initialized
            if (camera == null && allPermissionsGranted()) {
                initializeCamera()
            }
        }
    }

    private fun isRecording(): Boolean {
        return diaryViewModel.currentDiary.value != null
    }

    private fun updateUI() {
        val recording = isRecording()

        binding.btnStartDiary.visibility = if (recording) View.GONE else View.VISIBLE
        binding.btnStopDiary.visibility = if (recording) View.VISIBLE else View.GONE
        binding.btnTakePhoto.isEnabled = recording
        binding.btnSelectFromGallery.isEnabled = recording
        binding.btnAddWaypoint.isEnabled = recording

        binding.etDiaryTitle.isEnabled = !recording
        binding.etDiaryDescription.isEnabled = !recording
    }

    private fun updateUIBasedOnCurrentDiary() {
        val diary = diaryViewModel.currentDiary.value
        if (diary != null && !diary.isCompleted) {
            // Restore recording state
            if (!isLocationTracking && allPermissionsGranted()) {
                startLocationTracking()
                startStepCounting()
            }
        }
        updateUI()
    }

    private fun updateCurrentDiaryUI(diary: com.example.travel.data.TravelDiary?) {
        diary?.let {
            binding.tvCurrentDiaryTitle.text = it.title
            binding.tvStepCount.text = "Steps: ${stepCount}"
            binding.tvWaypointCount.text = "Waypoints: ${it.waypoints.size}"
            binding.tvPhotoCount.text = "Photos: ${it.photos.size}"

            binding.currentDiaryContainer.visibility = View.VISIBLE

            // Update photo display
            photosAdapter.submitList(it.photos)
        } ?: run {
            binding.currentDiaryContainer.visibility = View.GONE
            // Clear photo display
            photosAdapter.submitList(emptyList())
        }
    }

    private fun startStepCounting() {
        try {
            // Ensure previous listener is stopped first
            sensorManager.unregisterListener(this)

            when {
                stepCounterSensor != null -> {
                    val success = sensorManager.registerListener(
                        this,
                        stepCounterSensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                    if (success) {
                        Log.d("DiaryRecord", "Step counter sensor registered successfully")
                    } else {
                        Log.w("DiaryRecord", "Failed to register step counter sensor")
                        // Try fallback sensor
                        tryStepDetectorSensor()
                    }
                }
                stepDetectorSensor != null -> {
                    tryStepDetectorSensor()
                }
                else -> {
                    Toast.makeText(context, "Cannot start step recording", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error starting step counting", e)
            Toast.makeText(context, "Failed to start step recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStepDetectorSensor() {
        stepDetectorSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (success) {
                isUsingStepDetector = true
                Log.d("DiaryRecord", "Step detector sensor registered successfully")
            } else {
                Log.w("DiaryRecord", "Failed to register step detector sensor")
                Toast.makeText(context, "Failed to register step sensor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopStepCounting() {
        try {
            sensorManager.unregisterListener(this)
            Log.d("DiaryRecord", "Step sensors unregistered")
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error stopping step counting", e)
        }
    }

    // SensorEventListener implementation
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (!isRecording()) return

            when (sensorEvent.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    handleStepCounterEvent(sensorEvent.values[0].toInt())
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    handleStepDetectorEvent()
                }
            }
        }
    }

    private fun handleStepCounterEvent(totalSteps: Int) {
        try {
            if (!isStepCounterInitialized) {
                // First reading, record initial value
                initialStepCount = totalSteps
                sessionStartSteps = totalSteps
                isStepCounterInitialized = true
                stepCount = 0
                Log.d("DiaryRecord", "Step counter initialized: $totalSteps")
            } else {
                // Calculate session steps
                val newStepCount = totalSteps - sessionStartSteps
                if (newStepCount >= 0 && newStepCount < 100000) { // Prevent abnormal values
                    stepCount = newStepCount
                    updateStepCountDisplay()
                    Log.d("DiaryRecord", "Steps updated: $stepCount (total: $totalSteps)")
                }
            }
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error handling step counter event", e)
        }
    }

    private fun handleStepDetectorEvent() {
        try {
            stepCount++
            updateStepCountDisplay()
            Log.d("DiaryRecord", "Step detected, total: $stepCount")
        } catch (e: Exception) {
            Log.e("DiaryRecord", "Error handling step detector event", e)
        }
    }

    private fun updateStepCountDisplay() {
        activity?.runOnUiThread {
            binding.tvStepCount.text = "Steps: $stepCount"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("DiaryRecord", "Sensor accuracy changed: ${sensor?.name}, accuracy: $accuracy")
    }

    // LocationListener implementation
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        updateLocationStatus("${location.latitude.format(4)}, ${location.longitude.format(4)}")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("DiaryRecord", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("DiaryRecord", "Location provider disabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            updateLocationStatus("GPS disabled")
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStepCounting()
        stopLocationTracking()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
    }
}