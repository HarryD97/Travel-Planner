package com.example.travel.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.travel.services.AmadeusApiService

import com.example.travel.services.Hotel
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val amadeusService = AmadeusApiService(application)

    private val _hotels = MutableLiveData<List<Hotel>>()
    val hotels: LiveData<List<Hotel>> = _hotels

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun searchHotels(latitude: Double, longitude: Double, checkIn: String? = null, checkOut: String? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val hotelResults = amadeusService.searchHotels(latitude, longitude, checkIn, checkOut)
                _hotels.value = hotelResults
            } catch (e: Exception) {
                _errorMessage.value = "Failed to search for hotels: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Hotel sorting function
    fun sortHotelsByPrice() {
        val currentHotels = _hotels.value ?: return
        _hotels.value = currentHotels.sortedBy { it.priceValue }
    }

    fun sortHotelsByRating() {
        val currentHotels = _hotels.value ?: return
        _hotels.value = currentHotels.sortedByDescending { it.rating }
    }

    fun sortHotelsByDistance() {
        val currentHotels = _hotels.value ?: return
        _hotels.value = currentHotels.sortedBy { it.distanceKm }
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }
}