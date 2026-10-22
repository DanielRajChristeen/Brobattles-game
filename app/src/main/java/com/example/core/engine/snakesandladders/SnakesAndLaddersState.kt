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
    val p3Position: Int get() = playerPositions.getOrElse(2) { 1 }
    val p4Position: Int get() = playerPositions.getOrElse(3) { 1 }
    val p5Position: Int get() = playerPositions.getOrElse(4) { 1 }

    fun getPlayerPosition(player: PlayerId): Int {
        val index = player.ordinal
        return playerPositions.getOrElse(index) { 1 }
    }
}

fun serializeSnakesState(
    state: SnakesAndLaddersState,
    lastRoll: Int? = null,
    lastPlayer: PlayerId? = null
): String {
    val json = org.json.JSONObject()
    json.put("playerCount", state.playerCount)
    val posArr = org.json.JSONArray()
    state.playerPositions.forEach { posArr.put(it) }
    json.put("playerPositions", posArr)
    json.put("activePlayer", state.activePlayer.name)
    val roll = lastRoll ?: state.lastDiceRoll
    if (roll != null) json.put("lastDiceRoll", roll)
    val moveP = lastPlayer ?: state.lastMovePlayer
    if (moveP != null) json.put("lastMovePlayer", moveP.name)
    json.put("wasOvershot", state.wasOvershot)
    json.put("extraTurnGranted", state.extraTurnGranted)
    when (val s = state.status) {
        is GameStatus.MatchWon -> {
            json.put("status", "MatchWon")
            json.put("matchWinner", s.matchWinner.name)
        }
        else -> json.put("status", "InProgress")
    }
    return json.toString()
}

fun deserializeSnakesState(jsonStr: String): SnakesAndLaddersState? {
    return try {
        val json = org.json.JSONObject(jsonStr)
        val pCount = json.optInt("playerCount", 2)
        val posArr = json.optJSONArray("playerPositions")
        val positions = if (posArr != null) {
            List(posArr.length()) { posArr.getInt(it) }
        } else {
            List(pCount) { 1 }
        }
        val activePlayer = PlayerId.valueOf(json.optString("activePlayer", "PLAYER_1"))
        val lastDiceRoll = if (json.has("lastDiceRoll")) json.getInt("lastDiceRoll") else null
        val lastMovePlayer = if (json.has("lastMovePlayer")) PlayerId.valueOf(json.getString("lastMovePlayer")) else null
        val wasOvershot = json.optBoolean("wasOvershot", false)
        val extraTurnGranted = json.optBoolean("extraTurnGranted", false)
        val statusStr = json.optString("status", "InProgress")
        val status = if (statusStr == "MatchWon") {
            val winnerName = json.optString("matchWinner", "PLAYER_1")
            GameStatus.MatchWon(PlayerId.valueOf(winnerName))
        } else {
            GameStatus.InProgress
        }

        SnakesAndLaddersState(
            playerCount = pCount,
            playerPositions = positions,
            lastDiceRoll = lastDiceRoll,
            lastMovePlayer = lastMovePlayer,
            wasOvershot = wasOvershot,
            extraTurnGranted = extraTurnGranted,
            activePlayer = activePlayer,
            status = status
        )
    } catch (e: Exception) {
        null
    }
}

