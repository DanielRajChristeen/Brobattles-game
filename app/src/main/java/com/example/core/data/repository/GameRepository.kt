package com.example.core.data.repository

import com.example.core.data.db.MatchRecordDao
import com.example.core.data.db.UserSettingsDao
import com.example.core.data.model.MatchRecord
import com.example.core.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class GameRepository(
    private val matchRecordDao: MatchRecordDao,
    private val userSettingsDao: UserSettingsDao
) {
    val userSettings: Flow<UserSettings> = userSettingsDao.getSettings().map {
        it ?: UserSettings()
    }

    val matchHistory: Flow<List<MatchRecord>> = matchRecordDao.getAllMatches()

    val totalMatchesCount: Flow<Int> = matchRecordDao.getTotalMatchesCount()

    fun getMatchesForGame(gameId: String): Flow<List<MatchRecord>> =
        matchRecordDao.getMatchesByGame(gameId)

    suspend fun recordMatch(
        gameId: String,
        gameTitle: String,
        mode: String,
        player1Score: Int,
        player2Score: Int,
        winner: String?,
        durationSeconds: Long
    ): Long {
        val record = MatchRecord(
            gameId = gameId,
            gameTitle = gameTitle,
            mode = mode,
            player1Score = player1Score,
            player2Score = player2Score,
            winner = winner,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        return matchRecordDao.insertMatch(record)
    }

    suspend fun updateSettings(settings: UserSettings) {
        userSettingsDao.saveSettings(settings)
    }

    suspend fun updateUsername(username: String) {
        val trimmed = username.trim()
        if (trimmed.isNotEmpty()) {
            val current = userSettingsDao.getSettings().firstOrNull() ?: UserSettings()
            userSettingsDao.saveSettings(current.copy(username = trimmed))
        }
    }

    suspend fun clearAllStats() {
        matchRecordDao.clearAllMatches()
    }
}
