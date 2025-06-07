package com.example.travel.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ItineraryDao {
    
    @Query("SELECT * FROM itinerary_items ORDER BY `order` ASC")
    fun getAllItineraryItems(): LiveData<List<ItineraryEntity>>
    
    @Query("SELECT * FROM itinerary_items WHERE id = :id")
    suspend fun getItineraryItemById(id: String): ItineraryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraryItem(item: ItineraryEntity)
    
    @Update
    suspend fun updateItineraryItem(item: ItineraryEntity)
    
    @Delete
    suspend fun deleteItineraryItem(item: ItineraryEntity)
    
    @Query("DELETE FROM itinerary_items")
    suspend fun deleteAllItineraryItems()
    
    @Query("SELECT COUNT(*) FROM itinerary_items")
    suspend fun getItineraryItemCount(): Int
    
    @Query("UPDATE itinerary_items SET visited = :visited WHERE id = :id")
    suspend fun updateVisitedStatus(id: String, visited: Boolean)
    
    @Query("UPDATE itinerary_items SET `order` = :order WHERE id = :id")
    suspend fun updateItemOrder(id: String, order: Int)
} 