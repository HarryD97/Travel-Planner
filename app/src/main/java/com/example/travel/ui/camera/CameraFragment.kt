package com.example.travel.ui.diary

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.travel.databinding.FragmentDiaryBinding
import com.example.travel.data.TravelDiary
import com.example.travel.data.DiaryWaypoint
import com.example.travel.data.DiaryPhoto
import com.example.travel.data.Location
import com.example.travel.databinding.FragmentCameraBinding
import com.example.travel.ui.camera.CameraViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var cameraViewModel: CameraViewModel
    private lateinit var cameraExecutor: ExecutorService
    
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            cameraViewModel.processImageFromGallery(selectedUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        cameraViewModel = ViewModelProvider(this)[CameraViewModel::class.java]
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        observeViewModel()
        
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }
    
    private fun setupUI() {
        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }
        
        binding.btnSelectFromGallery.setOnClickListener {
            selectFromGallery()
        }
        
        binding.btnIdentifyLandmark.setOnClickListener {
            identifyLandmark()
        }
        
        binding.btnToggleCamera.setOnClickListener {
            toggleCamera()
        }
    }
    
    private fun observeViewModel() {
        cameraViewModel.capturedImage.observe(viewLifecycleOwner) { bitmap ->
            bitmap?.let {
                binding.imagePreview.setImageBitmap(it)
                binding.imagePreview.visibility = View.VISIBLE
                binding.btnIdentifyLandmark.visibility = View.VISIBLE
            }
        }
        
        cameraViewModel.landmarkResult.observe(viewLifecycleOwner) { result ->
            binding.tvLandmarkResult.text = result
            binding.tvLandmarkResult.visibility = if (result.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        cameraViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnIdentifyLandmark.isEnabled = !isLoading
        }
        
        cameraViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            // Preview
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            // ImageCapture
            imageCapture = ImageCapture.Builder().build()
            
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
            } catch (exc: Exception) {
                Toast.makeText(context, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
            
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        // Create time stamped name and MediaStore entry
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
            .format(System.currentTimeMillis())
        
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File(requireContext().externalMediaDirs.firstOrNull(), "$name.jpg")
        ).build()
        
        // Set up image capture listener
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "Photo capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        loadImageFromUri(uri)
                    }
                }
            }
        )
    }
    
    private fun selectFromGallery() {
        activityResultLauncher.launch("image/*")
    }
    
    private fun identifyLandmark() {
        cameraViewModel.identifyLandmark()
    }
    
    private fun toggleCamera() {
        // Toggle between front and back camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val cameraSelector = if (camera?.cameraInfo?.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(context, "Failed to switch camera", Toast.LENGTH_SHORT).show()
            }
            
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            cameraViewModel.setCapturedImage(bitmap)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun allPermissionsGranted() = arrayOf(Manifest.permission.CAMERA).all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    context,
                    "Camera permission is required for this feature",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1002
    }
} 