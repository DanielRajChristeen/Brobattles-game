package com.example.core.data.save

import com.example.core.data.db.SavedGameSessionDao
import com.example.core.data.model.SavedGameSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, ordered, corruption-resilient manager for persistent Vs Bot game saves.
 *
 * Guarantees:
 * - Non-blocking writes executed off the main thread on [ioDispatcher].
 * - Strict per-game ordering via per-game mutex + sequence numbers so a delayed save
 *   can never overwrite a subsequent save or a match-completion clear.
 * - Automatic validation & safe cleanup of corrupted or incompatible records.
 */
class BotGameSaveManager(
    private val dao: SavedGameSessionDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gameMutexes = ConcurrentHashMap<String, Mutex>()
    private val gameSequences = ConcurrentHashMap<String, AtomicLong>()

    private fun mutexFor(gameId: String): Mutex =
        gameMutexes.computeIfAbsent(gameId.lowercase()) { Mutex() }

    private fun nextSeq(gameId: String): Long =
        gameSequences.computeIfAbsent(gameId.lowercase()) { AtomicLong(0L) }.incrementAndGet()

    private fun currentSeq(gameId: String): Long =
        gameSequences[gameId.lowercase()]?.get() ?: 0L

    /**
     * Observes all valid unfinished Vs Bot sessions keyed by lowercase gameId.
     * Any corrupted/invalid row found in SQLite is safely ignored and pruned asynchronously.
     */
    fun observeAllValidSessions(): Flow<Map<String, SavedGameSession>> {
        return dao.observeAllSessions().map { sessions ->
            val validMap = LinkedHashMap<String, SavedGameSession>()
            val invalidIds = mutableListOf<String>()
            for (session in sessions) {
                if (BotGameStateSerializer.isSessionValid(session)) {
                    validMap[session.gameId.lowercase()] = session
                } else {
                    invalidIds.add(session.gameId)
                }
            }
            if (invalidIds.isNotEmpty()) {
                scope.launch(ioDispatcher) {
                    invalidIds.forEach { badId ->
                        runCatching { dao.deleteSession(badId) }
                    }
                }
            }
            validMap
        }
    }

    /**
     * Retrieves and validates a saved session for [gameId].
     * If corrupted or invalid, safely removes it and returns null.
     */
    suspend fun getValidSession(gameId: String): SavedGameSession? {
        val key = gameId.lowercase()
        val session = runCatching { dao.getSession(key) }.getOrNull() ?: return null
        return if (BotGameStateSerializer.isSessionValid(session)) {
            session
        } else {
            runCatching { dao.deleteSession(key) }
            null
        }
    }

    /**
     * Persists a validated Vs Bot session asynchronously without blocking UI.
     */
    fun saveSessionAsync(session: SavedGameSession) {
        val key = session.gameId.lowercase()
        val seq = nextSeq(key)
        scope.launch(ioDispatcher) {
            mutexFor(key).withLock {
                if (currentSeq(key) != seq) return@withLock
                if (BotGameStateSerializer.isSessionValid(session)) {
                    runCatching { dao.upsertSession(session.copy(gameId = key)) }
                }
            }
        }
    }

    /**
     * Persists a validated Vs Bot session directly (useful for suspend callers & tests).
     */
    suspend fun saveSession(session: SavedGameSession): Boolean {
        val key = session.gameId.lowercase()
        val seq = nextSeq(key)
        return mutexFor(key).withLock {
            if (currentSeq(key) != seq) return@withLock false
            if (!BotGameStateSerializer.isSessionValid(session)) return@withLock false
            runCatching {
                dao.upsertSession(session.copy(gameId = key))
                true
            }.getOrDefault(false)
        }
    }

    /**
     * Clears only [gameId]'s saved session asynchronously.
     */
    fun clearSessionAsync(gameId: String) {
        val key = gameId.lowercase()
        val seq = nextSeq(key)
        scope.launch(ioDispatcher) {
            mutexFor(key).withLock {
                if (currentSeq(key) != seq) return@withLock
                runCatching { dao.deleteSession(key) }
            }
        }
    }

    /**
     * Clears only [gameId]'s saved session directly.
     */
    suspend fun clearSession(gameId: String) {
        val key = gameId.lowercase()
        val seq = nextSeq(key)
        mutexFor(key).withLock {
            if (currentSeq(key) != seq) return@withLock
            runCatching { dao.deleteSession(key) }
        }
    }
}
