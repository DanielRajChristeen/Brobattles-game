package com.example.core.bot

import com.example.core.engine.PlayerId
import com.example.core.engine.tugofwar.TugOfWarMove
import com.example.core.engine.tugofwar.TugOfWarState
import kotlinx.coroutines.delay
import kotlin.random.Random

class TugOfWarBotOpponent(
    override val difficulty: BotDifficulty = BotDifficulty.MEDIUM
) : BotOpponent<TugOfWarState, TugOfWarMove> {

    override suspend fun decideMove(state: TugOfWarState, botPlayer: PlayerId): TugOfWarMove? {
        val position = state.ropePosition // -100 is p1, +100 is p2 (bot)

        // Dynamic human-like tapping interval based on match urgency
        val baseDelay = when {
            position < -40f -> Random.nextLong(120, 155) // Behind - frantic comeback taps!
            position < -15f -> Random.nextLong(135, 175) // Slightly trailing - energetic
            position > 40f -> Random.nextLong(175, 225)  // Well ahead - relaxed rhythm
            else -> Random.nextLong(150, 195)            // Even battle - steady pace
        }

        delay(baseDelay)
        return TugOfWarMove(force = 1.0f)
    }
}
