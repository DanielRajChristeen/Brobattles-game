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

fun serializeTugState(state: TugOfWarState): String {
    val json = org.json.JSONObject()
    json.put("ropePosition", state.ropePosition.toDouble())
    json.put("p1Taps", state.p1Taps)
    json.put("p2Taps", state.p2Taps)
    json.put("p1PullEnergy", state.p1PullEnergy.toDouble())
    json.put("p2PullEnergy", state.p2PullEnergy.toDouble())
    json.put("activePlayer", state.activePlayer.name)
    json.put("roundNumber", state.roundNumber)
    json.put("player1Score", state.player1Score)
    json.put("player2Score", state.player2Score)
    json.put("targetScore", state.targetScore)
    json.put("lastTapPlayer", state.lastTapPlayer?.name ?: "")

    val (statusType, winnerStr) = when (val s = state.status) {
        is GameStatus.MatchWon -> "MATCH_WON" to s.matchWinner.name
        is GameStatus.RoundWon -> "ROUND_WON" to s.winner.name
        is GameStatus.RoundDraw -> "ROUND_DRAW" to ""
        is GameStatus.InProgress -> "IN_PROGRESS" to ""
        is GameStatus.NotStarted -> "NOT_STARTED" to ""
    }
    json.put("statusType", statusType)
    json.put("winner", winnerStr)
    json.put("isGameOver", state.status is GameStatus.MatchWon)
    json.put("isRoundOver", state.status.isRoundOver)

    return json.toString()
}

fun deserializeTugState(jsonString: String): TugOfWarState? {
    return try {
        val json = org.json.JSONObject(jsonString)
        val ropePos = json.getDouble("ropePosition").toFloat()
        val p1Taps = json.optInt("p1Taps", 0)
        val p2Taps = json.optInt("p2Taps", 0)
        val p1Energy = json.optDouble("p1PullEnergy", 0.0).toFloat()
        val p2Energy = json.optDouble("p2PullEnergy", 0.0).toFloat()
        val activePlayer = PlayerId.valueOf(json.optString("activePlayer", PlayerId.PLAYER_1.name))
        val roundNum = json.optInt("roundNumber", 1)
        val p1Score = json.optInt("player1Score", 0)
        val p2Score = json.optInt("player2Score", 0)
        val targetScore = json.optInt("targetScore", 1)
        val lastTapStr = json.optString("lastTapPlayer", "")
        val lastTap = if (lastTapStr.isNotBlank()) PlayerId.valueOf(lastTapStr) else null

        val statusType = json.optString("statusType", "IN_PROGRESS")
        val winnerStr = json.optString("winner", "")
        val status: GameStatus = when (statusType) {
            "MATCH_WON" -> {
                val winner = if (winnerStr.isNotBlank()) PlayerId.valueOf(winnerStr) else PlayerId.PLAYER_1
                GameStatus.MatchWon(winner)
            }
            "ROUND_WON" -> {
                val winner = if (winnerStr.isNotBlank()) PlayerId.valueOf(winnerStr) else PlayerId.PLAYER_1
                GameStatus.RoundWon(winner)
            }
            "ROUND_DRAW" -> GameStatus.RoundDraw
            "NOT_STARTED" -> GameStatus.NotStarted
            else -> GameStatus.InProgress
        }

        TugOfWarState(
            ropePosition = ropePos,
            p1Taps = p1Taps,
            p2Taps = p2Taps,
            p1PullEnergy = p1Energy,
            p2PullEnergy = p2Energy,
            activePlayer = activePlayer,
            status = status,
            roundNumber = roundNum,
            player1Score = p1Score,
            player2Score = p2Score,
            targetScore = targetScore,
            lastTapPlayer = lastTap
        )
    } catch (e: Exception) {
        null
    }
}
