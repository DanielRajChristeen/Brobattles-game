package com.example.core.engine.xo

import com.example.core.engine.GameState
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import org.json.JSONArray
import org.json.JSONObject

data class XOState(
    val board: List<PlayerId?> = List(9) { null },
    override val activePlayer: PlayerId = PlayerId.PLAYER_1,
    override val status: GameStatus = GameStatus.InProgress,
    override val roundNumber: Int = 1,
    override val player1Score: Int = 0,
    override val player2Score: Int = 0,
    override val targetScore: Int = 5,
    val winningPattern: List<Int>? = null
) : GameState {

    fun serialize(lastMove: XOMove? = null): String = serializeXOState(this, lastMove)

    companion object {
        fun deserialize(jsonStr: String): XOState? = deserializeXOState(jsonStr)
    }
}

fun serializeXOState(state: XOState, lastMove: XOMove? = null): String {
    val json = JSONObject()
    val boardArr = JSONArray()
    state.board.forEach { cell ->
        if (cell != null) {
            boardArr.put(cell.name)
        } else {
            boardArr.put(JSONObject.NULL)
        }
    }
    json.put("board", boardArr)
    json.put("activePlayer", state.activePlayer.name)
    json.put("roundNumber", state.roundNumber)
    json.put("p1Score", state.player1Score)
    json.put("p2Score", state.player2Score)
    json.put("targetScore", state.targetScore)

    when (val s = state.status) {
        is GameStatus.InProgress -> json.put("status", "InProgress")
        is GameStatus.RoundWon -> {
            json.put("status", "RoundWon")
            json.put("winner", s.winner.name)
        }
        is GameStatus.RoundDraw -> json.put("status", "RoundDraw")
        is GameStatus.MatchWon -> {
            json.put("status", "MatchWon")
            json.put("matchWinner", s.matchWinner.name)
        }
        else -> json.put("status", "InProgress")
    }

    if (state.winningPattern != null) {
        val patternArr = JSONArray()
        state.winningPattern.forEach { patternArr.put(it) }
        json.put("winningPattern", patternArr)
    }

    if (lastMove != null) {
        if (lastMove.fromIndex != null) {
            json.put("lastFrom", lastMove.fromIndex)
        }
        json.put("lastTo", lastMove.toIndex)
    }

    return json.toString()
}

fun deserializeXOState(jsonStr: String): XOState? {
    return try {
        val json = JSONObject(jsonStr)
        val boardArr = json.getJSONArray("board")
        val board = List(9) { idx ->
            if (boardArr.isNull(idx)) {
                null
            } else {
                PlayerId.valueOf(boardArr.getString(idx))
            }
        }
        val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))
        val roundNumber = json.optInt("roundNumber", 1)
        val p1Score = json.optInt("p1Score", 0)
        val p2Score = json.optInt("p2Score", 0)
        val targetScore = json.optInt("targetScore", 5)

        val statusStr = json.optString("status", "InProgress")
        val status = when (statusStr) {
            "RoundWon" -> {
                val winnerName = json.optString("winner", "PLAYER_1")
                GameStatus.RoundWon(PlayerId.valueOf(winnerName))
            }
            "RoundDraw" -> GameStatus.RoundDraw
            "MatchWon" -> {
                val winnerName = json.optString("matchWinner", "PLAYER_1")
                GameStatus.MatchWon(PlayerId.valueOf(winnerName))
            }
            else -> GameStatus.InProgress
        }

        val winningPattern = if (json.has("winningPattern") && !json.isNull("winningPattern")) {
            val arr = json.getJSONArray("winningPattern")
            List(arr.length()) { arr.getInt(it) }
        } else {
            null
        }

        XOState(
            board = board,
            activePlayer = activePlayer,
            status = status,
            roundNumber = roundNumber,
            player1Score = p1Score,
            player2Score = p2Score,
            targetScore = targetScore,
            winningPattern = winningPattern
        )
    } catch (e: Exception) {
        null
    }
}
