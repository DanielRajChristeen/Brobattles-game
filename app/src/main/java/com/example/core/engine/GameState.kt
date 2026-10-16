package com.example.core.engine

/**
 * Base representation of any game state in Chill Games.
 * Must be pure serializable data without UI, context, or platform dependencies.
 */
interface GameState {
    val activePlayer: PlayerId
    val status: GameStatus
    val roundNumber: Int
    val player1Score: Int
    val player2Score: Int
    val targetScore: Int

    val leadingPlayer: PlayerId?
        get() = when {
            player1Score > player2Score -> PlayerId.PLAYER_1
            player2Score > player1Score -> PlayerId.PLAYER_2
            else -> null
        }
}
