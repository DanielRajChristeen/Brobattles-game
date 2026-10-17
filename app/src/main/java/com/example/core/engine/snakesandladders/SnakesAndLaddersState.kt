package com.example.core.engine.snakesandladders

import com.example.core.engine.GameState
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import org.json.JSONArray
import org.json.JSONObject

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

fun serializeSnakesState(state: SnakesAndLaddersState, roll: Int? = state.lastDiceRoll): String {
    val json = JSONObject()
    json.put("playerCount", state.playerCount)
    val posArray = JSONArray()
    state.playerPositions.forEach { posArray.put(it) }
    json.put("playerPositions", posArray)
    json.put("lastDiceRoll", roll ?: JSONObject.NULL)
    json.put("lastMovePlayer", state.lastMovePlayer?.name ?: JSONObject.NULL)
    if (state.ladderClimbed != null) {
        val ladderArray = JSONArray()
        ladderArray.put(state.ladderClimbed.first)
        ladderArray.put(state.ladderClimbed.second)
        json.put("ladderClimbed", ladderArray)
    }
    if (state.snakeSlid != null) {
        val snakeArray = JSONArray()
        snakeArray.put(state.snakeSlid.first)
        snakeArray.put(state.snakeSlid.second)
        json.put("snakeSlid", snakeArray)
    }
    json.put("wasOvershot", state.wasOvershot)
    json.put("extraTurnGranted", state.extraTurnGranted)
    json.put("activePlayer", state.activePlayer.name)
    when (val s = state.status) {
        is GameStatus.MatchWon -> {
            json.put("statusType", "MatchWon")
            json.put("winner", s.matchWinner.name)
        }
        else -> {
            json.put("statusType", "InProgress")
        }
    }
    return json.toString()
}

fun deserializeSnakesState(jsonStr: String): SnakesAndLaddersState? {
    return try {
        val json = JSONObject(jsonStr)
        val playerCount = json.optInt("playerCount", 2).coerceIn(2, 5)
        val posArray = json.optJSONArray("playerPositions")
        val positions = mutableListOf<Int>()
        if (posArray != null) {
            for (i in 0 until posArray.length()) {
                positions.add(posArray.getInt(i))
            }
        }
        while (positions.size < playerCount) {
            positions.add(1)
        }
        val lastRoll = if (json.isNull("lastDiceRoll")) null else json.optInt("lastDiceRoll")
        val lastMovePlayer = if (json.isNull("lastMovePlayer")) null else try {
            PlayerId.valueOf(json.getString("lastMovePlayer"))
        } catch (e: Exception) { null }
        val ladder = if (json.has("ladderClimbed")) {
            val arr = json.getJSONArray("ladderClimbed")
            arr.getInt(0) to arr.getInt(1)
        } else null
        val snake = if (json.has("snakeSlid")) {
            val arr = json.getJSONArray("snakeSlid")
            arr.getInt(0) to arr.getInt(1)
        } else null
        val wasOvershot = json.optBoolean("wasOvershot", false)
        val extraTurnGranted = json.optBoolean("extraTurnGranted", false)
        val activePlayer = try {
            PlayerId.valueOf(json.optString("activePlayer", PlayerId.PLAYER_1.name))
        } catch (e: Exception) { PlayerId.PLAYER_1 }
        val statusType = json.optString("statusType", "InProgress")
        val status = if (statusType == "MatchWon" && json.has("winner")) {
            val winner = try { PlayerId.valueOf(json.getString("winner")) } catch (e: Exception) { PlayerId.PLAYER_1 }
            GameStatus.MatchWon(winner)
        } else {
            GameStatus.InProgress
        }
        SnakesAndLaddersState(
            playerCount = playerCount,
            playerPositions = positions.take(playerCount),
            lastDiceRoll = lastRoll,
            lastMovePlayer = lastMovePlayer,
            ladderClimbed = ladder,
            snakeSlid = snake,
            wasOvershot = wasOvershot,
            extraTurnGranted = extraTurnGranted,
            activePlayer = activePlayer,
            status = status
        )
    } catch (e: Exception) {
        null
    }
}
