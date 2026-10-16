package com.example.core.bot

import com.example.core.engine.GameState
import com.example.core.engine.Move
import com.example.core.engine.PlayerId

enum class BotDifficulty(val label: String) {
    EASY("Relaxed"),
    MEDIUM("Clever"),
    HARD("Unbeatable")
}

/**
 * Offline bot contract. Bot computation runs purely locally on background dispatchers,
 * requiring zero network or external services.
 */
interface BotOpponent<S : GameState, M : Move> {
    val difficulty: BotDifficulty

    /**
     * Determines the bot's next move deterministically or heuristically.
     */
    suspend fun decideMove(state: S, botPlayer: PlayerId): M?
}
