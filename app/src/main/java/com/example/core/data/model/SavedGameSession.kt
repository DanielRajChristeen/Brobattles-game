package com.example.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent save slot for an unfinished Vs Bot (SOLO) game session.
 * Each of the 8 games uses its unique lowercase gameId as the primary key,
 * guaranteeing completely independent save slots that never overwrite each other.
 */
@Entity(tableName = "saved_bot_sessions")
data class SavedGameSession(
    @PrimaryKey
    val gameId: String,
    val schemaVersion: Int,
    val payloadJson: String,
    val checksum: String,
    val summary: String,
    val updatedAt: Long
)
