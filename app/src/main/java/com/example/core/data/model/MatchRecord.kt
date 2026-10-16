package com.example.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val gameTitle: String,
    val mode: String,
    val player1Score: Int,
    val player2Score: Int,
    val winner: String?,
    val durationSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
