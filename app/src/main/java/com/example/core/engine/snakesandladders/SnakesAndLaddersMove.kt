package com.example.core.engine.snakesandladders

import com.example.core.engine.Move

data class SnakesAndLaddersMove(
    val diceRoll: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Move {
    init {
        require(diceRoll in 1..6) { "Dice roll must be between 1 and 6, was $diceRoll" }
    }
}
