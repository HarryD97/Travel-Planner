package com.example.travel.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.travel.data.ItineraryItem
import com.example.travel.data.Location
import com.example.travel.data.LocationType
import com.example.travel.data.database.ItineraryDao
import com.example.travel.data.database.ItineraryEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import java.util.UUID

class ItineraryRepository(private val itineraryDao: ItineraryDao) {
    
    private val gson = Gson()
    
    fun getAllItineraryItems(): LiveData<List<ItineraryItem>> {
        return itineraryDao.getAllItineraryItems().map { entities ->
            entities.map { entity -> entity.toItineraryItem() }
        }
    }
    
    suspend fun insertItineraryItem(item: ItineraryItem) {
        itineraryDao.insertItineraryItem(item.toEntity())
    }
    
    suspend fun updateItineraryItem(item: ItineraryItem) {
        itineraryDao.updateItineraryItem(item.toEntity())
    }
    
    suspend fun deleteItineraryItem(item: ItineraryItem) {
        itineraryDao.deleteItineraryItem(item.toEntity())
    }
    
    suspend fun deleteAllItineraryItems() {
        itineraryDao.deleteAllItineraryItems()
    }
    
    suspend fun getItineraryItemCount(): Int {
        return itineraryDao.getItineraryItemCount()
    }
    
    suspend fun updateVisitedStatus(id: String, visited: Boolean) {
        itineraryDao.updateVisitedStatus(id, visited)
    }
    
    suspend fun updateItemOrder(id: String, order: Int) {
        itineraryDao.updateItemOrder(id, order)
    }
    
    // Extension functions for conversion
    private fun ItineraryItem.toEntity(): ItineraryEntity {
        val photosJson = gson.toJson(photos)
        return ItineraryEntity(
            id = id.ifBlank { UUID.randomUUID().toString() },
            locationId = location.id.ifBlank { UUID.randomUUID().toString() },
            locationName = location.name,
            locationAddress = location.address,
            latitude = location.latitude,
            longitude = location.longitude,
            locationType = location.type.name,
            locationDescription = location.description,
            locationImageUrl = location.imageUrl,
            plannedDate = plannedDate?.time,
            plannedTime = plannedTime,
            notes = notes,
            photos = photosJson,
            visited = visited,
            order = order
        )
    }
    
    private fun ItineraryEntity.toItineraryItem(): ItineraryItem {
        val photosList: MutableList<String> = try {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(photos, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
        
        return ItineraryItem(
            id = id,
            location = Location(
                id = locationId,
                name = locationName,
                address = locationAddress,
                latitude = latitude,
                longitude = longitude,
                type = try { LocationType.valueOf(locationType) } catch (e: Exception) { LocationType.CUSTOM },
                description = locationDescription,
                imageUrl = locationImageUrl,
                addedToItinerary = true
            ),
            plannedDate = plannedDate?.let { Date(it) },
            plannedTime = plannedTime,
            notes = notes,
            photos = photosList,
            visited = visited,
            order = order
        )
    }
} 