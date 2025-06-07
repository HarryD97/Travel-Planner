package com.example.travel.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.travel.data.Converters

@Database(
    entities = [
        ItineraryEntity::class,
        DiaryEntity::class,
        WaypointEntity::class,
        PhotoEntity::class,
        StepCountEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TravelDatabase : RoomDatabase() {
    
    abstract fun itineraryDao(): ItineraryDao
    abstract fun diaryDao(): DiaryDao
    abstract fun stepCountDao(): StepCountDao
    
    companion object {
        @Volatile
        private var INSTANCE: TravelDatabase? = null
        
        fun getDatabase(context: Context): TravelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TravelDatabase::class.java,
                    "travel_database"
                ).fallbackToDestructiveMigration() // Allow database rebuild
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 