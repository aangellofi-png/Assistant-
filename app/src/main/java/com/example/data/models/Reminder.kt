package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dateTime: String, // format e.g. "Today, 5:00 PM" or "12 July, 9:00 AM"
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
