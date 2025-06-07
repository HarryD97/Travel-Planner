package com.example.travel.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travel.services.VisionApiService
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {

    private val _capturedImage = MutableLiveData<Bitmap?>()
    val capturedImage: LiveData<Bitmap?> = _capturedImage

    private val _landmarkResult = MutableLiveData<String>()
    val landmarkResult: LiveData<String> = _landmarkResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val visionService = VisionApiService()

    fun setCapturedImage(bitmap: Bitmap) {
        _capturedImage.value = bitmap
        _landmarkResult.value = "" // Clear previous results
    }

    fun processImageFromGallery(uri: Uri) {
        // In a real implementation, you would convert URI to Bitmap
        // For now, we'll simulate this
        _landmarkResult.value = "" // Clear previous results
    }

    fun identifyLandmark() {
        val bitmap = _capturedImage.value ?: return
        
        _isLoading.value = true
        _errorMessage.value = ""
        
        viewModelScope.launch {
            try {
                val result = visionService.identifyLandmark(bitmap)
                _landmarkResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Failed to identify landmark: ${e.message}"
                _landmarkResult.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun analyzeImageText() {
        val bitmap = _capturedImage.value ?: return
        
        _isLoading.value = true
        _errorMessage.value = ""
        
        viewModelScope.launch {
            try {
                val result = visionService.detectText(bitmap)
                _landmarkResult.value = "Detected text: $result"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to analyze text: ${e.message}"
                _landmarkResult.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun detectObjects() {
        val bitmap = _capturedImage.value ?: return
        
        _isLoading.value = true
        _errorMessage.value = ""
        
        viewModelScope.launch {
            try {
                val result = visionService.detectObjects(bitmap)
                _landmarkResult.value = "Detected objects: $result"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to detect objects: ${e.message}"
                _landmarkResult.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }
} 