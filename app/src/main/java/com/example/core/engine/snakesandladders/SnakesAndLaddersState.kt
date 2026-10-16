package com.example.core.engine.snakesandladders

import com.example.core.engine.GameState
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId

data class SnakesAndLaddersState(
    val playerCount: Int = 2,
    val playerPositions: List<Int> = List(playerCount) { 1 },
    val lastDiceRoll: Int? = null,
    val lastMovePlayer: PlayerId? = null,
    val ladderClimbed: Pair<Int, Int>? = null, // from bottom -> top
    val snakeSlid: Pair<Int, Int>? = null,     // from head -> tail
    val wasOvershot: Boolean = false,
    val extraTurnGranted: Boolean = false,
    override val activePlayer: PlayerId = PlayerId.PLAYER_1,
    override val status: GameStatus = GameStatus.InProgress,
    override val roundNumber: Int = 1,
    override val player1Score: Int = 0,
    override val player2Score: Int = 0,
    override val targetScore: Int = 1
) : GameState {
    val p1Position: Int get() = playerPositions.getOrElse(0) { 1 }
    val p2Position: Int get() = playerPositions.getOrElse(1) { 1 }

    fun getPlayerPosition(player: PlayerId): Int {
        val index = player.ordinal
        return playerPositions.getOrElse(index) { 1 }
    }
}
