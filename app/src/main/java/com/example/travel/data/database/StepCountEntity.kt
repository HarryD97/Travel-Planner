package com.example.travel.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "step_counts")
data class StepCountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,
    val stepCount: Int,
    val timestamp: Long,
    val isCompleted: Boolean = false
) 