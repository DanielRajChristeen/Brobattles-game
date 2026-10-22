package com.example.core.engine.tugofwar

import com.example.core.engine.GameState
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import kotlin.math.roundToInt

data class TugOfWarState(
    val ropePosition: Float = 0.0f, // -100.0f (Player 1 winning line) to +100.0f (Player 2 winning line)
    val p1Taps: Int = 0,
    val p2Taps: Int = 0,
    val p1PullEnergy: Float = 0.0f,
    val p2PullEnergy: Float = 0.0f,
    override val activePlayer: PlayerId = PlayerId.PLAYER_1,
    override val status: GameStatus = GameStatus.InProgress,
    override val roundNumber: Int = 1,
    override val player1Score: Int = 0,
    override val player2Score: Int = 0,
    override val targetScore: Int = 1,
    val lastTapPlayer: PlayerId? = null
) : GameState {

    /**
     * Percentage advantage for Player 1: 0% to 100%.
     * When ropePosition = 0 (center), returns 50%.
     * When ropePosition = -100 (Player 1 reached left goal), returns 100%.
     * When ropePosition = +100 (Player 2 reached right goal), returns 0%.
     */
    val p1Percentage: Int
        get() = ((100f - ropePosition) / 2f).coerceIn(0f, 100f).roundToInt()

    val p2Percentage: Int
        get() = 100 - p1Percentage

    val advantagePlayer: PlayerId?
        get() = when {
            ropePosition < -2f -> PlayerId.PLAYER_1
            ropePosition > 2f -> PlayerId.PLAYER_2
            else -> null
        }
}
