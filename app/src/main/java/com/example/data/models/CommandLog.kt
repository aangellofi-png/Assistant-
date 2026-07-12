package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val prompt: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoice: Boolean = false,
    val wasSuccessful: Boolean = true
)
