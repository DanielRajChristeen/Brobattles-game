package com.example.core.engine.checkers

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable snapshot of the entire Checkers board and match progress.
 */
data class CheckersGameState(
    val board: Map<CheckersSquare, CheckersPiece> = emptyMap(),
    val activePlayer: PlayerId = PlayerId.PLAYER_1,
    val status: GameStatus = GameStatus.InProgress,
    val mustContinueJumpingFrom: CheckersSquare? = null,
    val movesWithoutCaptureOrPromotion: Int = 0,
    val lastMove: CheckersMove? = null
) {
    val redPiecesRemaining: Int
        get() = board.values.count { it.player == PlayerId.PLAYER_1 }

    val blackPiecesRemaining: Int
        get() = board.values.count { it.player == PlayerId.PLAYER_2 }

    val redKings: Int
        get() = board.values.count { it.player == PlayerId.PLAYER_1 && it.isKing }

    val blackKings: Int
        get() = board.values.count { it.player == PlayerId.PLAYER_2 && it.isKing }

    companion object {
        /**
         * Standard initial Checkers board layout:
         * Rows 0..2: 12 Black pieces (Player 2)
         * Rows 3..4: Empty
         * Rows 5..7: 12 Red pieces (Player 1)
         */
        fun initial(): CheckersGameState {
            val pieces = mutableMapOf<CheckersSquare, CheckersPiece>()
            var idCounter = 1

            // Player 2 (Black) in rows 0..2 on dark squares
            for (row in 0..2) {
                for (col in 0..7) {
                    val sq = CheckersSquare(row, col)
                    if (sq.isDarkPlayable) {
                        pieces[sq] = CheckersPiece(id = idCounter++, player = PlayerId.PLAYER_2, isKing = false)
                    }
                }
            }

            // Player 1 (Red) in rows 5..7 on dark squares
            for (row in 5..7) {
                for (col in 0..7) {
                    val sq = CheckersSquare(row, col)
                    if (sq.isDarkPlayable) {
                        pieces[sq] = CheckersPiece(id = idCounter++, player = PlayerId.PLAYER_1, isKing = false)
                    }
                }
            }

            return CheckersGameState(
                board = pieces,
                activePlayer = PlayerId.PLAYER_1,
                status = GameStatus.InProgress,
                mustContinueJumpingFrom = null,
                movesWithoutCaptureOrPromotion = 0,
                lastMove = null
            )
        }
    }
}

/**
 * Serializes the Checkers state into a JSON string for Wi-Fi multiplayer synchronization.
 */
fun serializeCheckersState(state: CheckersGameState): String {
    val json = JSONObject()
    json.put("activePlayer", state.activePlayer.name)

    val statusObj = JSONObject()
    when (val s = state.status) {
        is GameStatus.InProgress -> statusObj.put("type", "InProgress")
        is GameStatus.RoundWon -> {
            statusObj.put("type", "RoundWon")
            statusObj.put("winner", s.winner.name)
        }
        is GameStatus.RoundDraw -> statusObj.put("type", "RoundDraw")
        is GameStatus.MatchWon -> {
            statusObj.put("type", "MatchWon")
            statusObj.put("winner", s.matchWinner.name)
        }
        else -> statusObj.put("type", "NotStarted")
    }
    json.put("status", statusObj)

    state.mustContinueJumpingFrom?.let {
        json.put("mustJumpRow", it.row)
        json.put("mustJumpCol", it.col)
    }

    json.put("movesWithoutCaptureOrPromotion", state.movesWithoutCaptureOrPromotion)

    val piecesArray = JSONArray()
    for ((sq, piece) in state.board) {
        val pJson = JSONObject()
        pJson.put("row", sq.row)
        pJson.put("col", sq.col)
        pJson.put("id", piece.id)
        pJson.put("player", piece.player.name)
        pJson.put("isKing", piece.isKing)
        piecesArray.put(pJson)
    }
    json.put("pieces", piecesArray)

    state.lastMove?.let { lm ->
        val lmJson = JSONObject()
        lmJson.put("fromRow", lm.from.row)
        lmJson.put("fromCol", lm.from.col)
        lmJson.put("toRow", lm.to.row)
        lmJson.put("toCol", lm.to.col)
        lm.capturedSquare?.let { cs ->
            lmJson.put("capRow", cs.row)
            lmJson.put("capCol", cs.col)
        }
        json.put("lastMove", lmJson)
    }

    return json.toString()
}

/**
 * Deserializes JSON string back into a CheckersGameState.
 */
fun deserializeCheckersState(raw: String): CheckersGameState? {
    return try {
        val json = JSONObject(raw)
        val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))

        val statusObj = json.getJSONObject("status")
        val statusType = statusObj.getString("type")
        val status: GameStatus = when (statusType) {
            "RoundWon" -> GameStatus.RoundWon(PlayerId.valueOf(statusObj.getString("winner")))
            "MatchWon" -> GameStatus.MatchWon(PlayerId.valueOf(statusObj.getString("winner")))
            "RoundDraw" -> GameStatus.RoundDraw
            "InProgress" -> GameStatus.InProgress
            else -> GameStatus.NotStarted
        }

        val mustContinue = if (json.has("mustJumpRow") && json.has("mustJumpCol")) {
            CheckersSquare(json.getInt("mustJumpRow"), json.getInt("mustJumpCol"))
        } else null

        val movesWithoutCaptureOrPromotion = json.optInt("movesWithoutCaptureOrPromotion", 0)

        val pieces = mutableMapOf<CheckersSquare, CheckersPiece>()
        val piecesArray = json.getJSONArray("pieces")
        for (i in 0 until piecesArray.length()) {
            val pJson = piecesArray.getJSONObject(i)
            val sq = CheckersSquare(pJson.getInt("row"), pJson.getInt("col"))
            val piece = CheckersPiece(
                id = pJson.getInt("id"),
                player = PlayerId.valueOf(pJson.getString("player")),
                isKing = pJson.getBoolean("isKing")
            )
            pieces[sq] = piece
        }

        val lastMove = if (json.has("lastMove")) {
            val lmJson = json.getJSONObject("lastMove")
            val from = CheckersSquare(lmJson.getInt("fromRow"), lmJson.getInt("fromCol"))
            val to = CheckersSquare(lmJson.getInt("toRow"), lmJson.getInt("toCol"))
            val cap = if (lmJson.has("capRow")) {
                CheckersSquare(lmJson.getInt("capRow"), lmJson.getInt("capCol"))
            } else null
            CheckersMove(from, to, cap)
        } else null

        CheckersGameState(
            board = pieces,
            activePlayer = activePlayer,
            status = status,
            mustContinueJumpingFrom = mustContinue,
            movesWithoutCaptureOrPromotion = movesWithoutCaptureOrPromotion,
            lastMove = lastMove
        )
    } catch (_: Exception) {
        null
    }
}
