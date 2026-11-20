package com.example.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.model.SavedGameSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameSessionDao {
    @Query("SELECT * FROM saved_bot_sessions")
    fun observeAllSessions(): Flow<List<SavedGameSession>>

    @Query("SELECT * FROM saved_bot_sessions WHERE gameId = :gameId LIMIT 1")
    fun observeSession(gameId: String): Flow<SavedGameSession?>

    @Query("SELECT * FROM saved_bot_sessions WHERE gameId = :gameId LIMIT 1")
    suspend fun getSession(gameId: String): SavedGameSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SavedGameSession)

    @Query("DELETE FROM saved_bot_sessions WHERE gameId = :gameId")
    suspend fun deleteSession(gameId: String)

    @Query("DELETE FROM saved_bot_sessions")
    suspend fun deleteAllSessions()
}
