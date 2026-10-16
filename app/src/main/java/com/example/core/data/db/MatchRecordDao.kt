package com.example.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.model.MatchRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchRecordDao {
    @Query("SELECT * FROM match_records ORDER BY timestamp DESC")
    fun getAllMatches(): Flow<List<MatchRecord>>

    @Query("SELECT * FROM match_records WHERE gameId = :gameId ORDER BY timestamp DESC")
    fun getMatchesByGame(gameId: String): Flow<List<MatchRecord>>

    @Query("SELECT COUNT(*) FROM match_records")
    fun getTotalMatchesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchRecord): Long

    @Query("DELETE FROM match_records")
    suspend fun clearAllMatches()
}
