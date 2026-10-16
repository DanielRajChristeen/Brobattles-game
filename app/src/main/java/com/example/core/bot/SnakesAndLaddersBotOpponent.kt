package com.example.core.bot

import com.example.core.engine.PlayerId
import com.example.core.engine.snakesandladders.SnakesAndLaddersMove
import com.example.core.engine.snakesandladders.SnakesAndLaddersState
import kotlinx.coroutines.delay
import kotlin.random.Random

class SnakesAndLaddersBotOpponent(
    override val difficulty: BotDifficulty = BotDifficulty.MEDIUM
) : BotOpponent<SnakesAndLaddersState, SnakesAndLaddersMove> {

    override suspend fun decideMove(state: SnakesAndLaddersState, botPlayer: PlayerId): SnakesAndLaddersMove? {
        // Human-like natural pause before rolling
        delay(Random.nextLong(450, 650))
        val roll = Random.nextInt(1, 7)
        return SnakesAndLaddersMove(diceRoll = roll)
    }
}
