package com.example.travel.ui.itinerary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.switchMap
import com.example.travel.data.ItineraryItem
import com.example.travel.data.Location
import com.example.travel.data.LocationType
import com.example.travel.data.TravelStats
import com.example.travel.data.database.TravelDatabase
import com.example.travel.data.repository.ItineraryRepository
import com.example.travel.services.AmadeusApiService
import com.example.travel.services.DirectionsApiService
import com.example.travel.services.TravelMode
import kotlinx.coroutines.launch
import java.util.UUID

class ItineraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ItineraryRepository
    val itineraryItems: LiveData<List<ItineraryItem>>

    private val _travelStats = MutableLiveData<TravelStats>()
    val travelStats: LiveData<TravelStats> = _travelStats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val amadeusService = AmadeusApiService(application)
    private val directionsService = DirectionsApiService()

    init {
        val dao = TravelDatabase.getDatabase(application).itineraryDao()
        repository = ItineraryRepository(dao)
        itineraryItems = repository.getAllItineraryItems()

        // Initialize with empty stats
        _travelStats.value = TravelStats()

        // Observe itinerary items changes to update travel stats
        itineraryItems.observeForever(::calculateTravelStats)
    }

    override fun onCleared() {
        super.onCleared()
        itineraryItems.removeObserver(::calculateTravelStats)
    }

    fun addItineraryItem(location: Location) {
        viewModelScope.launch {
            val currentCount = repository.getItineraryItemCount()
            val newItem = ItineraryItem(
                id = UUID.randomUUID().toString(),
                location = location,
                order = currentCount
            )
            repository.insertItineraryItem(newItem)
        }
    }

    fun removeItineraryItem(item: ItineraryItem) {
        viewModelScope.launch {
            repository.deleteItineraryItem(item)
            // Reorder remaining items
            val remainingItems = itineraryItems.value ?: return@launch
            remainingItems.filter { it.id != item.id }
                .sortedBy { it.order }
                .forEachIndexed { index, itineraryItem ->
                    repository.updateItemOrder(itineraryItem.id, index)
                }
        }
    }

    fun toggleVisited(item: ItineraryItem) {
        viewModelScope.launch {
            repository.updateVisitedStatus(item.id, !item.visited)
        }
    }

    fun updateItineraryOrder(newItems: List<ItineraryItem>) {
        viewModelScope.launch {
            try {
                // Update each item's order in the database
                newItems.forEachIndexed { index, item ->
                    repository.updateItemOrder(item.id, index)
                }
            } catch (e: Exception) {
                _errorMessage.value = "更新行程顺序失败: ${e.message}"
            }
        }
    }

    fun clearAllItineraryItems() {
        viewModelScope.launch {
            try {
                repository.deleteAllItineraryItems()
                _errorMessage.value = ""
            } catch (e: Exception) {
                _errorMessage.value = "清空行程失败: ${e.message}"
            }
        }
    }

    private val _navigateToHotelSearch = MutableLiveData<HotelSearchParams?>()
    val navigateToHotelSearch: LiveData<HotelSearchParams?> = _navigateToHotelSearch



    fun searchHotels(latitude: Double, longitude: Double, locationName: String = "当前位置") {
        _navigateToHotelSearch.value = HotelSearchParams(latitude, longitude, locationName)
    }



    fun clearNavigationEvents() {
        _navigateToHotelSearch.value = null
    }

    data class HotelSearchParams(
        val latitude: Double,
        val longitude: Double,
        val locationName: String
    )



    private fun calculateTravelStats(items: List<ItineraryItem>?) {
        if (items.isNullOrEmpty()) {
            _travelStats.value = TravelStats()
            return
        }

        // Use real routing data for more accurate calculations
        viewModelScope.launch {
            try {
                var totalDistanceKm = 0.0
                var totalDurationMin = 0.0

                // Calculate real route data between consecutive points
                for (i in 0 until items.size - 1) {
                    val start = items[i].location
                    val end = items[i + 1].location

                    try {
                        // Use walking as default for itinerary calculations (most common for tourism)
                        val route = directionsService.getDirections(start, end, emptyList(), TravelMode.WALKING)
                        // Parse distance from route (e.g., "15.2 km" -> 15.2)
                        totalDistanceKm += parseDistance(route.totalDistance)
                        // Parse duration from route (e.g., "25 min" -> 25)
                        totalDurationMin += parseDuration(route.totalDuration)
                    } catch (e: Exception) {
                        // Fallback to straight-line distance if route fails
                        val distance = calculateDistance(start.latitude, start.longitude, end.latitude, end.longitude)
                        totalDistanceKm += distance
                        totalDurationMin += distance / 5.0 * 60 // Assume 5 km/h walking speed
                    }
                }

                val stats = TravelStats(
                    totalDistance = String.format("%.1f km", totalDistanceKm),
                    totalDuration = formatDuration(totalDurationMin / 60.0), // Convert minutes to hours
                    totalLocations = items.size
                )

                _travelStats.value = stats
            } catch (e: Exception) {
                // Fallback to simple calculation
                calculateSimpleTravelStats(items)
            }
        }
    }

    private fun calculateSimpleTravelStats(items: List<ItineraryItem>) {
        var totalDistance = 0.0
        var totalDuration = 0.0

        // Calculate total distance and estimated duration using straight lines
        for (i in 0 until items.size - 1) {
            val start = items[i].location
            val end = items[i + 1].location

            val distance = calculateDistance(start.latitude, start.longitude, end.latitude, end.longitude)
            totalDistance += distance

            // Estimate time (assuming 40 km/h average speed)
            totalDuration += distance / 40.0
        }

        val stats = TravelStats(
            totalDistance = String.format("%.1f km", totalDistance),
            totalDuration = formatDuration(totalDuration), // totalDuration is already in hours
            totalLocations = items.size
        )

        _travelStats.value = stats
    }

    private fun parseDistance(distanceStr: String): Double {
        return try {
            distanceStr.replace(" km", "").replace(" m", "").toDouble().let { value ->
                if (distanceStr.contains(" m")) value / 1000.0 else value
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseDuration(durationStr: String): Double {
        return try {
            when {
                durationStr.contains("h") && durationStr.contains("min") -> {
                    val parts = durationStr.split("h")
                    val hours = parts[0].trim().toDouble()
                    val minutes = parts[1].replace("min", "").trim().toDouble()
                    hours * 60 + minutes
                }
                durationStr.contains("h") -> {
                    val hours = durationStr.replace("h", "").trim().toDouble()
                    hours * 60
                }
                durationStr.contains("min") -> {
                    durationStr.replace("min", "").trim().toDouble()
                }
                else -> 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers

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

    private fun formatDuration(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        return when {
            totalMinutes < 60 -> "${totalMinutes} min"
            else -> {
                val h = totalMinutes / 60
                val min = totalMinutes % 60
                "${h}h ${min}min"
            }
        }
    }


} 