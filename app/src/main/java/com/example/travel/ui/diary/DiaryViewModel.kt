package com.example.travel.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.travel.data.TravelDiary
import com.example.travel.data.DiaryWaypoint
import com.example.travel.data.DiaryPhoto
import com.example.travel.data.Location
import com.example.travel.data.repository.DiaryRepository
import kotlinx.coroutines.launch
import java.util.*

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DiaryRepository(application)

    val allDiaries: LiveData<List<TravelDiary>> = repository.allDiaries

    private val _allDiariesWithDetails = MutableLiveData<List<TravelDiary>>()
    val allDiariesWithDetails: LiveData<List<TravelDiary>> = _allDiariesWithDetails

    private val _currentDiary = MutableLiveData<TravelDiary?>()
    val currentDiary: LiveData<TravelDiary?> = _currentDiary

    private val _currentWaypoints = MutableLiveData<List<DiaryWaypoint>>()
    val currentWaypoints: LiveData<List<DiaryWaypoint>> = _currentWaypoints

    init {
        // Check for incomplete diaries on initialization
        loadIncompleteDiary()
        // Load details for all diaries
        loadAllDiariesWithDetails()
    }

    // For diary viewing
    private val _selectedDiary = MutableLiveData<TravelDiary?>()
    val selectedDiary: LiveData<TravelDiary?> = _selectedDiary

    private val _selectedWaypoints = MutableLiveData<List<DiaryWaypoint>>()
    val selectedWaypoints: LiveData<List<DiaryWaypoint>> = _selectedWaypoints

    private val _selectedPhotos = MutableLiveData<List<DiaryPhoto>>()
    val selectedPhotos: LiveData<List<DiaryPhoto>> = _selectedPhotos

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun startNewDiary(title: String, description: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val diary = TravelDiary(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    date = Date()
                )

                repository.insertDiary(diary)
                _currentDiary.value = diary
                _currentWaypoints.value = emptyList()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to create diary: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeDiary(diaryId: String, totalSteps: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val diary = _currentDiary.value
                diary?.let {
                    // Calculate total distance
                    val totalDistance = calculateTotalDistance(it.waypoints)

                    val updatedDiary = it.copy(
                        totalSteps = totalSteps,
                        totalDistance = totalDistance,
                        isCompleted = true
                    )
                    repository.updateDiary(updatedDiary)
                    _currentDiary.value = null
                    _currentWaypoints.value = emptyList()

                    // Refresh diary list to display latest data
                    loadAllDiariesWithDetails()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to complete diary: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDiary(diaryId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val diary = repository.getDiaryById(diaryId)
                _currentDiary.value = diary

                diary?.let {
                    val waypoints = repository.getWaypointsByDiaryId(diaryId)
                    _currentWaypoints.value = waypoints
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load diary: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addWaypointToDiary(diaryId: String, location: Location, stepsAtPoint: Int) {
        viewModelScope.launch {
            try {
                val waypoint = DiaryWaypoint(
                    id = UUID.randomUUID().toString(),
                    location = location,
                    timestamp = Date(),
                    stepsAtPoint = stepsAtPoint
                )

                repository.insertWaypoint(diaryId, waypoint)

                // Update current diary waypoints
                val currentWaypoints = _currentWaypoints.value?.toMutableList() ?: mutableListOf()
                currentWaypoints.add(waypoint)
                _currentWaypoints.value = currentWaypoints

                // Update current diary with new waypoint and recalculated distance
                _currentDiary.value?.let { diary ->
                    val updatedWaypoints = diary.waypoints.toMutableList()
                    updatedWaypoints.add(waypoint)
                    val totalDistance = calculateTotalDistance(updatedWaypoints)

                    val updatedDiary = diary.copy(
                        waypoints = updatedWaypoints,
                        totalDistance = totalDistance
                    )
                    _currentDiary.value = updatedDiary

                    // If it's not a completed diary, update the distance in the database
                    if (!diary.isCompleted) {
                        repository.updateDiary(updatedDiary)
                    }
                }

            } catch (e: Exception) {
                _errorMessage.value = "Failed to add waypoint: ${e.message}"
            }
        }
    }

    fun addPhotoToDiary(diaryId: String, photoPath: String, location: Location?) {
        viewModelScope.launch {
            try {
                val photo = DiaryPhoto(
                    id = UUID.randomUUID().toString(),
                    filePath = photoPath,
                    timestamp = Date(),
                    location = location
                )

                repository.insertPhoto(diaryId, photo)

                // Update current diary photos
                _currentDiary.value?.let { diary ->
                    val updatedPhotos = diary.photos.toMutableList()
                    updatedPhotos.add(photo)
                    _currentDiary.value = diary.copy(photos = updatedPhotos)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Failed to add photo: ${e.message}"
            }
        }
    }

    fun deleteDiary(diary: TravelDiary) {
        viewModelScope.launch {
            try {
                repository.deleteDiary(diary)
                if (_currentDiary.value?.id == diary.id) {
                    _currentDiary.value = null
                    _currentWaypoints.value = emptyList()
                }

                // Refresh diary list
                loadAllDiariesWithDetails()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete diary: ${e.message}"
            }
        }
    }

    fun updateDiaryInfo(diary: TravelDiary, newTitle: String, newDescription: String) {
        viewModelScope.launch {
            try {
                val updatedDiary = diary.copy(
                    title = newTitle,
                    description = newDescription
                )
                repository.updateDiary(updatedDiary)

                // If the updated diary is the current diary, update the current diary state as well
                if (_currentDiary.value?.id == diary.id) {
                    _currentDiary.value = updatedDiary
                }

                // Refresh diary list
                loadAllDiariesWithDetails()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update diary: ${e.message}"
            }
        }
    }

    fun selectDiaryForView(diaryId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                var diary = repository.getDiaryById(diaryId)

                diary?.let {
                    val waypoints = repository.getWaypointsByDiaryId(diaryId)
                    val photos = repository.getPhotosByDiaryId(diaryId)

                    // If distance is 0 but there are multiple waypoints, recalculate distance
                    if (it.totalDistance == 0.0 && waypoints.size >= 2) {
                        val calculatedDistance = calculateTotalDistance(waypoints)
                        diary = it.copy(
                            totalDistance = calculatedDistance,
                            waypoints = waypoints.toMutableList(),
                            photos = photos.toMutableList()
                        )
                        // Update database
                        repository.updateDiary(diary)
                    } else {
                        diary = it.copy(
                            waypoints = waypoints.toMutableList(),
                            photos = photos.toMutableList()
                        )
                    }

                    _selectedDiary.value = diary
                    _selectedWaypoints.value = waypoints
                    _selectedPhotos.value = photos
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load diary details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    /**
     * Load an incomplete diary for state restoration
     */
    private fun loadIncompleteDiary() {
        viewModelScope.launch {
            try {
                val incompleteDiary = repository.getIncompleteDiary()
                incompleteDiary?.let { diary ->
                    _currentDiary.value = diary

                    // Load associated waypoints
                    val waypoints = repository.getWaypointsByDiaryId(diary.id)
                    _currentWaypoints.value = waypoints
                }
            } catch (e: Exception) {
                // Handle silently, don't show error message
                // Because this is a background recovery operation
                android.util.Log.e("DiaryViewModel", "Error loading incomplete diary", e)
            }
        }
    }

    /**
     * Manually reload current diary state for state synchronization after Fragment switching
     */
    fun reloadCurrentDiary() {
        _currentDiary.value?.let { diary ->
            viewModelScope.launch {
                try {
                    val updatedDiary = repository.getDiaryById(diary.id)
                    updatedDiary?.let {
                        _currentDiary.value = it
                        val waypoints = repository.getWaypointsByDiaryId(diary.id)
                        _currentWaypoints.value = waypoints
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to refresh diary status: ${e.message}"
                }
            }
        }
    }

    /**
     * Load all diaries with complete details
     */
    fun loadAllDiariesWithDetails() {
        viewModelScope.launch {
            try {
                val diariesWithDetails = repository.getAllDiariesWithDetails()

                // Check and update diaries with missing distance data
                val updatedDiaries = diariesWithDetails.map { diary ->
                    if (diary.totalDistance == 0.0 && diary.waypoints.size >= 2) {
                        val calculatedDistance = calculateTotalDistance(diary.waypoints)
                        val updatedDiary = diary.copy(totalDistance = calculatedDistance)
                        // Update database in the background
                        viewModelScope.launch {
                            repository.updateDiary(updatedDiary)
                        }
                        updatedDiary
                    } else {
                        diary
                    }
                }

                _allDiariesWithDetails.value = updatedDiaries
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load diary list: ${e.message}"
            }
        }
    }

    fun deleteWaypoint(diaryId: String, waypoint: DiaryWaypoint) {
        viewModelScope.launch {
            try {
                repository.deleteWaypoint(waypoint)

                // Update current diary's waypoint list
                val currentWaypoints = _currentWaypoints.value?.toMutableList() ?: mutableListOf()
                currentWaypoints.removeAll { it.id == waypoint.id }
                _currentWaypoints.value = currentWaypoints

                // Update current diary and recalculate distance
                _currentDiary.value?.let { diary ->
                    val updatedWaypoints = diary.waypoints.toMutableList()
                    updatedWaypoints.removeAll { it.id == waypoint.id }
                    val totalDistance = calculateTotalDistance(updatedWaypoints)

                    val updatedDiary = diary.copy(
                        waypoints = updatedWaypoints,
                        totalDistance = totalDistance
                    )
                    _currentDiary.value = updatedDiary

                    // Update distance in the database
                    repository.updateDiary(updatedDiary)
                }

                // Refresh diary list
                loadAllDiariesWithDetails()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete waypoint: ${e.message}"
            }
        }
    }

    fun deletePhoto(diaryId: String, photo: DiaryPhoto) {
        viewModelScope.launch {
            try {
                repository.deletePhoto(photo)

                // Update current diary's photo list
                _currentDiary.value?.let { diary ->
                    val updatedPhotos = diary.photos.toMutableList()
                    updatedPhotos.removeAll { it.id == photo.id }
                    _currentDiary.value = diary.copy(photos = updatedPhotos)
                }

                // Refresh diary list
                loadAllDiariesWithDetails()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete photo: ${e.message}"
            }
        }
    }

    /**
     * Calculates the total distance between waypoints
     */
    private fun calculateTotalDistance(waypoints: List<DiaryWaypoint>): Double {
        if (waypoints.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until waypoints.size - 1) {
            val start = waypoints[i].location
            val end = waypoints[i + 1].location
            totalDistance += calculateDistance(
                start.latitude, start.longitude,
                end.latitude, end.longitude
            )
        }
        return totalDistance
    }

    /**
     * Calculates the distance between two points using the Haversine formula (in kilometers)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius (kilometers)

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}