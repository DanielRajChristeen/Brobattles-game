package com.example.core.data.save

import com.example.core.bot.BotDifficulty
import com.example.core.data.model.SavedGameSession
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.battleship.BattleshipEngine
import com.example.core.engine.battleship.BattleshipPhase
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.BattleshipState
import com.example.core.engine.battleship.CellCoordinate
import com.example.core.engine.battleship.PlayerFleetState
import com.example.core.engine.battleship.ShipOrientation
import com.example.core.engine.battleship.ShipPlacement
import com.example.core.engine.battleship.ShipType
import com.example.core.engine.battleship.ShotOutcome
import com.example.core.engine.battleship.TargetingRadar
import com.example.core.engine.checkers.CheckersDifficulty
import com.example.core.engine.checkers.CheckersGameState
import com.example.core.engine.checkers.CheckersMove
import com.example.core.engine.checkers.CheckersPiece
import com.example.core.engine.checkers.CheckersSquare
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessGameState
import com.example.core.engine.chess.ChessGameStatus
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessPiece
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.chess.PieceType
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotBox
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotConnectionsGameState
import com.example.core.engine.dotconnections.DotDifficulty
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.core.engine.dotconnections.LinePlacement
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoGameState
import com.example.core.engine.ludo.LudoPlayerState
import com.example.core.engine.ludo.LudoToken
import com.example.core.engine.snakesandladders.SnakesAndLaddersState
import com.example.core.engine.tugofwar.TugOfWarState
import com.example.core.engine.xo.XOState
import com.example.core.registry.GameMode
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class SavedXOSession(
    val state: XOState,
    val difficulty: BotDifficulty
)

data class SavedTugOfWarSession(
    val state: TugOfWarState
)

data class SavedSnakesSession(
    val state: SnakesAndLaddersState,
    val displayedDice: Int
)

data class SavedChessSession(
    val state: ChessGameState,
    val difficulty: ChessDifficulty,
    val playerColorPreference: ChessPlayerColor,
    val resolvedPlayerColor: PieceColor,
    val moveHistory: List<ChessMove>
)

data class SavedCheckersSession(
    val state: CheckersGameState,
    val difficulty: CheckersDifficulty
)

data class SavedDotConnectionsSession(
    val state: DotConnectionsGameState,
    val difficulty: DotDifficulty
)

data class SavedLudoSession(
    val state: LudoGameState,
    val botDifficulty: BotDifficulty,
    val displayedDice: Int
)

data class SavedBattleshipSession(
    val state: BattleshipState,
    val botActiveHits: List<CellCoordinate>,
    val selectedShipType: ShipType?,
    val deploymentOrientation: ShipOrientation,
    val activeBoardTab: Int
)

/**
 * Lossless JSON serialization, integrity checksum verification, and structural validation
 * for all 8 Vs Bot game sessions. Rejects corrupted, partial, or already-finished states.
 */
object BotGameStateSerializer {
    const val SCHEMA_VERSION = 1

    const val GAME_XO = "xo"
    const val GAME_TUG_OF_WAR = "tug_of_war"
    const val GAME_SNAKES = "snakes_and_ladders"
    const val GAME_CHESS = "chess"
    const val GAME_CHECKERS = "checkers"
    const val GAME_DOT_CONNECTIONS = "dot_connections"
    const val GAME_LUDO = "ludo"
    const val GAME_BATTLESHIP = "battleship"

    fun computeChecksum(gameId: String, schemaVersion: Int, payloadJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$schemaVersion|$gameId|$payloadJson".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildSession(
        gameId: String,
        payloadJson: String,
        summary: String,
        updatedAt: Long = System.currentTimeMillis()
    ): SavedGameSession {
        val normalizedId = gameId.lowercase()
        return SavedGameSession(
            gameId = normalizedId,
            schemaVersion = SCHEMA_VERSION,
            payloadJson = payloadJson,
            checksum = computeChecksum(normalizedId, SCHEMA_VERSION, payloadJson),
            summary = summary,
            updatedAt = updatedAt
        )
    }

    fun isSessionValid(session: SavedGameSession): Boolean {
        if (session.schemaVersion != SCHEMA_VERSION) return false
        if (session.payloadJson.isBlank()) return false
        val expectedChecksum = computeChecksum(session.gameId.lowercase(), session.schemaVersion, session.payloadJson)
        if (session.checksum != expectedChecksum) return false

        return when (session.gameId.lowercase()) {
            GAME_XO -> decodeXO(session.payloadJson) != null
            GAME_TUG_OF_WAR -> decodeTugOfWar(session.payloadJson) != null
            GAME_SNAKES -> decodeSnakes(session.payloadJson) != null
            GAME_CHESS -> decodeChess(session.payloadJson) != null
            GAME_CHECKERS -> decodeCheckers(session.payloadJson) != null
            GAME_DOT_CONNECTIONS -> decodeDotConnections(session.payloadJson) != null
            GAME_LUDO -> decodeLudo(session.payloadJson) != null
            GAME_BATTLESHIP -> decodeBattleship(session.payloadJson) != null
            else -> false
        }
    }

    // =========================================================================
    // 1. Tic Tac Toe (XO)
    // =========================================================================

    fun hasResumableXOProgress(state: XOState): Boolean {
        if (state.status is GameStatus.MatchWon) return false
        return state.board.any { it != null } ||
            state.roundNumber > 1 ||
            state.player1Score > 0 ||
            state.player2Score > 0
    }

    fun encodeXO(state: XOState, difficulty: BotDifficulty): SavedGameSession? {
        if (!hasResumableXOProgress(state)) return null
        val json = JSONObject()
        val boardArr = JSONArray()
        state.board.forEach { cell ->
            if (cell != null) boardArr.put(cell.name) else boardArr.put(JSONObject.NULL)
        }
        json.put("board", boardArr)
        json.put("activePlayer", state.activePlayer.name)
        json.put("status", encodeGameStatus(state.status))
        json.put("roundNumber", state.roundNumber)
        json.put("player1Score", state.player1Score)
        json.put("player2Score", state.player2Score)
        json.put("targetScore", state.targetScore)
        if (state.winningPattern != null) {
            val arr = JSONArray()
            state.winningPattern.forEach { arr.put(it) }
            json.put("winningPattern", arr)
        }
        json.put("difficulty", difficulty.name)

        val summary = "Round ${state.roundNumber} • Score ${state.player1Score}-${state.player2Score} (${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }})"
        return buildSession(GAME_XO, json.toString(), summary)
    }

    fun decodeXO(payloadJson: String): SavedXOSession? {
        return try {
            val json = JSONObject(payloadJson)
            val boardArr = json.getJSONArray("board")
            if (boardArr.length() != 9) return null
            val board = List(9) { idx ->
                if (boardArr.isNull(idx)) null else PlayerId.valueOf(boardArr.getString(idx))
            }
            val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))
            val status = decodeGameStatus(json.getJSONObject("status")) ?: return null
            if (status is GameStatus.MatchWon || status is GameStatus.NotStarted) return null

            val roundNumber = json.getInt("roundNumber")
            val p1Score = json.getInt("player1Score")
            val p2Score = json.getInt("player2Score")
            val targetScore = json.getInt("targetScore")
            if (roundNumber < 1 || p1Score < 0 || p2Score < 0 || targetScore < 1) return null

            val winningPattern = if (json.has("winningPattern") && !json.isNull("winningPattern")) {
                val arr = json.getJSONArray("winningPattern")
                val list = List(arr.length()) { arr.getInt(it) }
                if (list.any { it !in 0..8 }) return null
                list
            } else null

            val difficulty = BotDifficulty.valueOf(json.getString("difficulty"))
            val state = XOState(
                board = board,
                activePlayer = activePlayer,
                status = status,
                roundNumber = roundNumber,
                player1Score = p1Score,
                player2Score = p2Score,
                targetScore = targetScore,
                winningPattern = winningPattern
            )
            if (!hasResumableXOProgress(state)) return null
            SavedXOSession(state = state, difficulty = difficulty)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 2. Tug of War
    // =========================================================================

    fun hasResumableTugOfWarProgress(state: TugOfWarState): Boolean {
        if (state.status is GameStatus.MatchWon) return false
        return state.p1Taps > 0 ||
            state.p2Taps > 0 ||
            state.player1Score > 0 ||
            state.player2Score > 0 ||
            state.roundNumber > 1 ||
            state.ropePosition != 0f
    }

    fun encodeTugOfWar(state: TugOfWarState): SavedGameSession? {
        if (!hasResumableTugOfWarProgress(state)) return null
        val json = JSONObject()
        json.put("ropePosition", state.ropePosition.toDouble())
        json.put("p1Taps", state.p1Taps)
        json.put("p2Taps", state.p2Taps)
        json.put("p1PullEnergy", state.p1PullEnergy.toDouble())
        json.put("p2PullEnergy", state.p2PullEnergy.toDouble())
        json.put("activePlayer", state.activePlayer.name)
        json.put("status", encodeGameStatus(state.status))
        json.put("roundNumber", state.roundNumber)
        json.put("player1Score", state.player1Score)
        json.put("player2Score", state.player2Score)
        json.put("targetScore", state.targetScore)
        if (state.lastTapPlayer != null) {
            json.put("lastTapPlayer", state.lastTapPlayer.name)
        }

        val summary = "Round ${state.roundNumber} • Score ${state.player1Score}-${state.player2Score} (${state.p1Percentage}%-${state.p2Percentage}%)"
        return buildSession(GAME_TUG_OF_WAR, json.toString(), summary)
    }

    fun decodeTugOfWar(payloadJson: String): SavedTugOfWarSession? {
        return try {
            val json = JSONObject(payloadJson)
            val ropePosition = json.getDouble("ropePosition").toFloat()
            if (!ropePosition.isFinite() || ropePosition < -100f || ropePosition > 100f) return null
            val p1Taps = json.getInt("p1Taps")
            val p2Taps = json.getInt("p2Taps")
            val p1PullEnergy = json.getDouble("p1PullEnergy").toFloat()
            val p2PullEnergy = json.getDouble("p2PullEnergy").toFloat()
            if (p1Taps < 0 || p2Taps < 0 || !p1PullEnergy.isFinite() || !p2PullEnergy.isFinite()) return null

            val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))
            val status = decodeGameStatus(json.getJSONObject("status")) ?: return null
            if (status is GameStatus.MatchWon || status is GameStatus.NotStarted) return null

            val roundNumber = json.getInt("roundNumber")
            val p1Score = json.getInt("player1Score")
            val p2Score = json.getInt("player2Score")
            val targetScore = json.getInt("targetScore")
            if (roundNumber < 1 || p1Score < 0 || p2Score < 0 || targetScore < 1) return null

            val lastTapPlayer = if (json.has("lastTapPlayer") && !json.isNull("lastTapPlayer")) {
                PlayerId.valueOf(json.getString("lastTapPlayer"))
            } else null

            val state = TugOfWarState(
                ropePosition = ropePosition,
                p1Taps = p1Taps,
                p2Taps = p2Taps,
                p1PullEnergy = p1PullEnergy.coerceIn(0f, 2f),
                p2PullEnergy = p2PullEnergy.coerceIn(0f, 2f),
                activePlayer = activePlayer,
                status = status,
                roundNumber = roundNumber,
                player1Score = p1Score,
                player2Score = p2Score,
                targetScore = targetScore,
                lastTapPlayer = lastTapPlayer
            )
            if (!hasResumableTugOfWarProgress(state)) return null
            SavedTugOfWarSession(state = state)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 3. Snakes & Ladders
    // =========================================================================

    fun hasResumableSnakesProgress(state: SnakesAndLaddersState): Boolean {
        if (state.status is GameStatus.MatchWon) return false
        if (state.playerPositions.any { it >= 100 }) return false
        return state.playerPositions.any { it > 1 } ||
            state.lastDiceRoll != null ||
            state.activePlayer != PlayerId.PLAYER_1
    }

    fun encodeSnakes(state: SnakesAndLaddersState, displayedDice: Int): SavedGameSession? {
        if (!hasResumableSnakesProgress(state)) return null
        val json = JSONObject()
        json.put("playerCount", state.playerCount)
        val posArr = JSONArray()
        state.playerPositions.forEach { posArr.put(it) }
        json.put("playerPositions", posArr)
        if (state.lastDiceRoll != null) json.put("lastDiceRoll", state.lastDiceRoll)
        if (state.lastMovePlayer != null) json.put("lastMovePlayer", state.lastMovePlayer.name)
        state.ladderClimbed?.let { (from, to) ->
            json.put("ladderFrom", from)
            json.put("ladderTo", to)
        }
        state.snakeSlid?.let { (from, to) ->
            json.put("snakeFrom", from)
            json.put("snakeTo", to)
        }
        json.put("wasOvershot", state.wasOvershot)
        json.put("extraTurnGranted", state.extraTurnGranted)
        json.put("activePlayer", state.activePlayer.name)
        json.put("status", encodeGameStatus(state.status))
        json.put("roundNumber", state.roundNumber)
        json.put("player1Score", state.player1Score)
        json.put("player2Score", state.player2Score)
        json.put("targetScore", state.targetScore)
        json.put("displayedDice", displayedDice.coerceIn(1, 6))

        val summary = "You Sq ${state.p1Position} • Bot Sq ${state.p2Position}"
        return buildSession(GAME_SNAKES, json.toString(), summary)
    }

    fun decodeSnakes(payloadJson: String): SavedSnakesSession? {
        return try {
            val json = JSONObject(payloadJson)
            val playerCount = json.getInt("playerCount")
            if (playerCount != 2) return null
            val posArr = json.getJSONArray("playerPositions")
            if (posArr.length() != playerCount) return null
            val positions = List(playerCount) { posArr.getInt(it) }
            if (positions.any { it !in 1..99 }) return null

            val lastDiceRoll = if (json.has("lastDiceRoll") && !json.isNull("lastDiceRoll")) {
                val r = json.getInt("lastDiceRoll")
                if (r !in 1..6) return null
                r
            } else null

            val lastMovePlayer = if (json.has("lastMovePlayer") && !json.isNull("lastMovePlayer")) {
                PlayerId.valueOf(json.getString("lastMovePlayer"))
            } else null

            val ladderClimbed = if (json.has("ladderFrom") && json.has("ladderTo")) {
                Pair(json.getInt("ladderFrom"), json.getInt("ladderTo"))
            } else null

            val snakeSlid = if (json.has("snakeFrom") && json.has("snakeTo")) {
                Pair(json.getInt("snakeFrom"), json.getInt("snakeTo"))
            } else null

            val wasOvershot = json.optBoolean("wasOvershot", false)
            val extraTurnGranted = json.optBoolean("extraTurnGranted", false)
            val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))
            if (activePlayer != PlayerId.PLAYER_1 && activePlayer != PlayerId.PLAYER_2) return null

            val status = decodeGameStatus(json.getJSONObject("status")) ?: return null
            if (status !is GameStatus.InProgress) return null

            val roundNumber = json.optInt("roundNumber", 1)
            val p1Score = json.optInt("player1Score", 0)
            val p2Score = json.optInt("player2Score", 0)
            val targetScore = json.optInt("targetScore", 1)
            val displayedDice = json.optInt("displayedDice", lastDiceRoll ?: 1)
            if (displayedDice !in 1..6 || roundNumber < 1 || targetScore < 1) return null

            val state = SnakesAndLaddersState(
                playerCount = playerCount,
                playerPositions = positions,
                lastDiceRoll = lastDiceRoll,
                lastMovePlayer = lastMovePlayer,
                ladderClimbed = ladderClimbed,
                snakeSlid = snakeSlid,
                wasOvershot = wasOvershot,
                extraTurnGranted = extraTurnGranted,
                activePlayer = activePlayer,
                status = status,
                roundNumber = roundNumber,
                player1Score = p1Score,
                player2Score = p2Score,
                targetScore = targetScore
            )
            if (!hasResumableSnakesProgress(state)) return null
            SavedSnakesSession(state = state, displayedDice = displayedDice)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 4. Chess
    // =========================================================================

    fun hasResumableChessProgress(state: ChessGameState, moveHistory: List<ChessMove>): Boolean {
        if (state.status.isGameOver) return false
        return moveHistory.isNotEmpty() ||
            state.lastMove != null ||
            state.fullmoveNumber > 1 ||
            state.activeColor != PieceColor.WHITE
    }

    fun encodeChess(
        state: ChessGameState,
        difficulty: ChessDifficulty,
        playerColorPreference: ChessPlayerColor,
        resolvedPlayerColor: PieceColor,
        moveHistory: List<ChessMove>
    ): SavedGameSession? {
        if (!hasResumableChessProgress(state, moveHistory)) return null
        val json = JSONObject()

        val boardArr = JSONArray()
        for (i in 0 until 64) {
            val piece = state.board[i]
            if (piece == null) {
                boardArr.put(JSONObject.NULL)
            } else {
                val pj = JSONObject()
                pj.put("type", piece.type.name)
                pj.put("color", piece.color.name)
                boardArr.put(pj)
            }
        }
        json.put("board", boardArr)
        json.put("activeColor", state.activeColor.name)
        json.put("whiteCanCastleKingside", state.whiteCanCastleKingside)
        json.put("whiteCanCastleQueenside", state.whiteCanCastleQueenside)
        json.put("blackCanCastleKingside", state.blackCanCastleKingside)
        json.put("blackCanCastleQueenside", state.blackCanCastleQueenside)
        state.enPassantTarget?.let {
            json.put("enPassantRow", it.row)
            json.put("enPassantCol", it.col)
        }
        json.put("halfmoveClock", state.halfmoveClock)
        json.put("fullmoveNumber", state.fullmoveNumber)
        json.put("inCheck", state.inCheck)
        json.put("canonicalPositionKey", state.canonicalPositionKey)

        val posHistArr = JSONArray()
        state.positionHistory.forEach { posHistArr.put(it) }
        json.put("positionHistory", posHistArr)

        val capWhiteArr = JSONArray()
        state.capturedByWhite.forEach { capWhiteArr.put(it.name) }
        json.put("capturedByWhite", capWhiteArr)

        val capBlackArr = JSONArray()
        state.capturedByBlack.forEach { capBlackArr.put(it.name) }
        json.put("capturedByBlack", capBlackArr)

        state.lastMove?.let { json.put("lastMove", encodeChessMove(it)) }

        val movesArr = JSONArray()
        moveHistory.forEach { movesArr.put(encodeChessMove(it)) }
        json.put("moveHistory", movesArr)

        json.put("difficulty", difficulty.name)
        json.put("playerColorPreference", playerColorPreference.name)
        json.put("resolvedPlayerColor", resolvedPlayerColor.name)

        val turnLabel = if (state.activeColor == resolvedPlayerColor) "Your turn" else "Bot's turn"
        val summary = "Move ${state.fullmoveNumber} • $turnLabel (${difficulty.title})"
        return buildSession(GAME_CHESS, json.toString(), summary)
    }

    fun decodeChess(payloadJson: String): SavedChessSession? {
        return try {
            val json = JSONObject(payloadJson)
            val boardArr = json.getJSONArray("board")
            if (boardArr.length() != 64) return null
            val board = arrayOfNulls<ChessPiece>(64)
            var whiteKings = 0
            var blackKings = 0
            for (i in 0 until 64) {
                if (!boardArr.isNull(i)) {
                    val pj = boardArr.getJSONObject(i)
                    val type = PieceType.valueOf(pj.getString("type"))
                    val color = PieceColor.valueOf(pj.getString("color"))
                    board[i] = ChessPiece(type, color)
                    if (type == PieceType.KING) {
                        if (color == PieceColor.WHITE) whiteKings++ else blackKings++
                    }
                }
            }
            if (whiteKings != 1 || blackKings != 1) return null

            val activeColor = PieceColor.valueOf(json.getString("activeColor"))
            val wCK = json.getBoolean("whiteCanCastleKingside")
            val wCQ = json.getBoolean("whiteCanCastleQueenside")
            val bCK = json.getBoolean("blackCanCastleKingside")
            val bCQ = json.getBoolean("blackCanCastleQueenside")

            val enPassant = if (json.has("enPassantRow") && json.has("enPassantCol")) {
                val r = json.getInt("enPassantRow")
                val c = json.getInt("enPassantCol")
                if (!ChessSquare.isValid(r, c)) return null
                ChessSquare(r, c)
            } else null

            val halfmoveClock = json.getInt("halfmoveClock")
            val fullmoveNumber = json.getInt("fullmoveNumber")
            if (halfmoveClock < 0 || fullmoveNumber < 1) return null

            val inCheck = json.getBoolean("inCheck")
            val canonicalKey = json.optString("canonicalPositionKey", "")

            val posHistory = mutableListOf<String>()
            val posArr = json.optJSONArray("positionHistory")
            if (posArr != null) {
                for (i in 0 until posArr.length()) {
                    posHistory.add(posArr.getString(i))
                }
            }

            val capWhite = mutableListOf<PieceType>()
            val cwArr = json.optJSONArray("capturedByWhite")
            if (cwArr != null) {
                for (i in 0 until cwArr.length()) {
                    capWhite.add(PieceType.valueOf(cwArr.getString(i)))
                }
            }

            val capBlack = mutableListOf<PieceType>()
            val cbArr = json.optJSONArray("capturedByBlack")
            if (cbArr != null) {
                for (i in 0 until cbArr.length()) {
                    capBlack.add(PieceType.valueOf(cbArr.getString(i)))
                }
            }

            val lastMove = if (json.has("lastMove") && !json.isNull("lastMove")) {
                decodeChessMove(json.getJSONObject("lastMove")) ?: return null
            } else null

            val moveHistory = mutableListOf<ChessMove>()
            val movesArr = json.optJSONArray("moveHistory")
            if (movesArr != null) {
                for (i in 0 until movesArr.length()) {
                    val m = decodeChessMove(movesArr.getJSONObject(i)) ?: return null
                    moveHistory.add(m)
                }
            }

            val difficulty = ChessDifficulty.valueOf(json.getString("difficulty"))
            val colorPref = ChessPlayerColor.valueOf(json.getString("playerColorPreference"))
            val resolvedColor = PieceColor.valueOf(json.getString("resolvedPlayerColor"))

            val state = ChessGameState(
                board = board,
                activeColor = activeColor,
                whiteCanCastleKingside = wCK,
                whiteCanCastleQueenside = wCQ,
                blackCanCastleKingside = bCK,
                blackCanCastleQueenside = bCQ,
                enPassantTarget = enPassant,
                halfmoveClock = halfmoveClock,
                fullmoveNumber = fullmoveNumber,
                status = ChessGameStatus.InProgress,
                inCheck = inCheck,
                capturedByWhite = capWhite,
                capturedByBlack = capBlack,
                lastMove = lastMove,
                canonicalPositionKey = canonicalKey,
                positionHistory = posHistory
            )
            if (!hasResumableChessProgress(state, moveHistory)) return null

            SavedChessSession(
                state = state,
                difficulty = difficulty,
                playerColorPreference = colorPref,
                resolvedPlayerColor = resolvedColor,
                moveHistory = moveHistory
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeChessMove(move: ChessMove): JSONObject {
        val obj = JSONObject()
        obj.put("fromRow", move.from.row)
        obj.put("fromCol", move.from.col)
        obj.put("toRow", move.to.row)
        obj.put("toCol", move.to.col)
        move.promotion?.let { obj.put("promotion", it.name) }
        obj.put("isEnPassant", move.isEnPassant)
        obj.put("isCastling", move.isCastling)
        move.capturedPiece?.let {
            obj.put("capType", it.type.name)
            obj.put("capColor", it.color.name)
        }
        return obj
    }

    private fun decodeChessMove(obj: JSONObject): ChessMove? {
        return try {
            val fr = obj.getInt("fromRow")
            val fc = obj.getInt("fromCol")
            val tr = obj.getInt("toRow")
            val tc = obj.getInt("toCol")
            if (!ChessSquare.isValid(fr, fc) || !ChessSquare.isValid(tr, tc)) return null
            val promo = if (obj.has("promotion") && !obj.isNull("promotion")) {
                PieceType.valueOf(obj.getString("promotion"))
            } else null
            val capPiece = if (obj.has("capType") && obj.has("capColor")) {
                ChessPiece(
                    type = PieceType.valueOf(obj.getString("capType")),
                    color = PieceColor.valueOf(obj.getString("capColor"))
                )
            } else null
            ChessMove(
                from = ChessSquare(fr, fc),
                to = ChessSquare(tr, tc),
                promotion = promo,
                isEnPassant = obj.optBoolean("isEnPassant", false),
                isCastling = obj.optBoolean("isCastling", false),
                capturedPiece = capPiece
            )
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 5. Checkers
    // =========================================================================

    fun hasResumableCheckersProgress(state: CheckersGameState): Boolean {
        if (state.status !is GameStatus.InProgress) return false
        return state.lastMove != null ||
            state.movesWithoutCaptureOrPromotion > 0 ||
            state.activePlayer != PlayerId.PLAYER_1 ||
            state.redPiecesRemaining < 12 ||
            state.blackPiecesRemaining < 12
    }

    fun encodeCheckers(state: CheckersGameState, difficulty: CheckersDifficulty): SavedGameSession? {
        if (!hasResumableCheckersProgress(state)) return null
        val json = JSONObject()
        json.put("activePlayer", state.activePlayer.name)
        json.put("status", encodeGameStatus(state.status))
        state.mustContinueJumpingFrom?.let {
            json.put("mustJumpRow", it.row)
            json.put("mustJumpCol", it.col)
        }
        json.put("movesWithoutCaptureOrPromotion", state.movesWithoutCaptureOrPromotion)

        val piecesArr = JSONArray()
        for ((sq, piece) in state.board) {
            val pj = JSONObject()
            pj.put("row", sq.row)
            pj.put("col", sq.col)
            pj.put("id", piece.id)
            pj.put("player", piece.player.name)
            pj.put("isKing", piece.isKing)
            piecesArr.put(pj)
        }
        json.put("pieces", piecesArr)

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
        json.put("difficulty", difficulty.name)

        val summary = "Red ${state.redPiecesRemaining} vs Black ${state.blackPiecesRemaining} (${difficulty.title})"
        return buildSession(GAME_CHECKERS, json.toString(), summary)
    }

    fun decodeCheckers(payloadJson: String): SavedCheckersSession? {
        return try {
            val json = JSONObject(payloadJson)
            val activePlayer = PlayerId.valueOf(json.getString("activePlayer"))
            if (activePlayer != PlayerId.PLAYER_1 && activePlayer != PlayerId.PLAYER_2) return null

            val status = decodeGameStatus(json.getJSONObject("status")) ?: return null
            if (status !is GameStatus.InProgress) return null

            val movesNoCap = json.getInt("movesWithoutCaptureOrPromotion")
            if (movesNoCap < 0) return null

            val pieces = mutableMapOf<CheckersSquare, CheckersPiece>()
            val seenIds = mutableSetOf<Int>()
            val piecesArr = json.getJSONArray("pieces")
            for (i in 0 until piecesArr.length()) {
                val pj = piecesArr.getJSONObject(i)
                val sq = CheckersSquare(pj.getInt("row"), pj.getInt("col"))
                if (!sq.isValid || !sq.isDarkPlayable) return null
                if (pieces.containsKey(sq)) return null
                val id = pj.getInt("id")
                if (!seenIds.add(id)) return null
                val player = PlayerId.valueOf(pj.getString("player"))
                if (player != PlayerId.PLAYER_1 && player != PlayerId.PLAYER_2) return null
                pieces[sq] = CheckersPiece(id = id, player = player, isKing = pj.getBoolean("isKing"))
            }

            val mustJump = if (json.has("mustJumpRow") && json.has("mustJumpCol")) {
                val sq = CheckersSquare(json.getInt("mustJumpRow"), json.getInt("mustJumpCol"))
                if (!sq.isValid || !sq.isDarkPlayable) return null
                val piece = pieces[sq] ?: return null
                if (piece.player != activePlayer) return null
                sq
            } else null

            val lastMove = if (json.has("lastMove") && !json.isNull("lastMove")) {
                val lm = json.getJSONObject("lastMove")
                val from = CheckersSquare(lm.getInt("fromRow"), lm.getInt("fromCol"))
                val to = CheckersSquare(lm.getInt("toRow"), lm.getInt("toCol"))
                if (!from.isValid || !to.isValid) return null
                val cap = if (lm.has("capRow") && lm.has("capCol")) {
                    val c = CheckersSquare(lm.getInt("capRow"), lm.getInt("capCol"))
                    if (!c.isValid) return null
                    c
                } else null
                CheckersMove(from, to, cap)
            } else null

            val difficulty = CheckersDifficulty.valueOf(json.getString("difficulty"))
            val state = CheckersGameState(
                board = pieces,
                activePlayer = activePlayer,
                status = status,
                mustContinueJumpingFrom = mustJump,
                movesWithoutCaptureOrPromotion = movesNoCap,
                lastMove = lastMove
            )
            if (state.redPiecesRemaining == 0 || state.blackPiecesRemaining == 0) return null
            if (!hasResumableCheckersProgress(state)) return null

            SavedCheckersSession(state = state, difficulty = difficulty)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 6. Dot Connections
    // =========================================================================

    fun hasResumableDotConnectionsProgress(state: DotConnectionsGameState): Boolean {
        if (state.isGameOver) return false
        return state.placedEdges.isNotEmpty()
    }

    fun encodeDotConnections(state: DotConnectionsGameState, difficulty: DotDifficulty): SavedGameSession? {
        if (!hasResumableDotConnectionsProgress(state)) return null
        val json = JSONObject()
        json.put("boardSize", state.boardSize.name)
        json.put("activePlayer", state.activePlayer.name)
        json.put("p1Score", state.p1Score)
        json.put("p2Score", state.p2Score)
        json.put("difficulty", difficulty.name)

        val edgesArr = JSONArray()
        for ((edge, placement) in state.placedEdges) {
            val ej = JSONObject()
            ej.put("ori", edge.orientation.name)
            ej.put("row", edge.row)
            ej.put("col", edge.col)
            ej.put("owner", placement.owner.name)
            ej.put("ts", placement.timestampMs)
            edgesArr.put(ej)
        }
        json.put("placedEdges", edgesArr)

        val boxesArr = JSONArray()
        for ((_, box) in state.boxes) {
            val bj = JSONObject()
            bj.put("row", box.row)
            bj.put("col", box.col)
            box.owner?.let { bj.put("owner", it.name) }
            bj.put("ts", box.completedAtTimestamp)
            boxesArr.put(bj)
        }
        json.put("boxes", boxesArr)

        state.lastMoveEdge?.let { edge ->
            val lj = JSONObject()
            lj.put("ori", edge.orientation.name)
            lj.put("row", edge.row)
            lj.put("col", edge.col)
            json.put("lastMoveEdge", lj)
        }

        val lastBoxesArr = JSONArray()
        state.lastCompletedBoxes.forEach { (r, c) ->
            val lb = JSONObject()
            lb.put("row", r)
            lb.put("col", c)
            lastBoxesArr.put(lb)
        }
        json.put("lastCompletedBoxes", lastBoxesArr)

        val summary = "${state.boardSize.dots}×${state.boardSize.dots} • Score ${state.p1Score}-${state.p2Score} (${difficulty.title})"
        return buildSession(GAME_DOT_CONNECTIONS, json.toString(), summary)
    }

    fun decodeDotConnections(payloadJson: String): SavedDotConnectionsSession? {
        return try {
            val json = JSONObject(payloadJson)
            val boardSize = DotBoardSize.valueOf(json.getString("boardSize"))
            val activePlayer = DotPlayer.valueOf(json.getString("activePlayer"))
            val p1Score = json.getInt("p1Score")
            val p2Score = json.getInt("p2Score")
            if (p1Score < 0 || p2Score < 0) return null
            val difficulty = DotDifficulty.valueOf(json.getString("difficulty"))

            val placedEdges = mutableMapOf<DotEdge, LinePlacement>()
            val edgesArr = json.getJSONArray("placedEdges")
            for (i in 0 until edgesArr.length()) {
                val ej = edgesArr.getJSONObject(i)
                val edge = DotEdge(
                    orientation = EdgeOrientation.valueOf(ej.getString("ori")),
                    row = ej.getInt("row"),
                    col = ej.getInt("col")
                )
                if (!DotConnectionsEngine.isValidEdge(edge, boardSize)) return null
                val owner = DotPlayer.valueOf(ej.getString("owner"))
                val ts = ej.optLong("ts", 0L)
                placedEdges[edge] = LinePlacement(edge, owner, ts)
            }

            val maxBoxCoord = boardSize.dots - 1
            val boxes = mutableMapOf<Pair<Int, Int>, DotBox>()
            val boxesArr = json.getJSONArray("boxes")
            for (i in 0 until boxesArr.length()) {
                val bj = boxesArr.getJSONObject(i)
                val r = bj.getInt("row")
                val c = bj.getInt("col")
                if (r !in 0 until maxBoxCoord || c !in 0 until maxBoxCoord) return null
                val owner = if (bj.has("owner") && !bj.isNull("owner")) {
                    DotPlayer.valueOf(bj.getString("owner"))
                } else null
                val ts = bj.optLong("ts", 0L)
                boxes[Pair(r, c)] = DotBox(row = r, col = c, owner = owner, completedAtTimestamp = ts)
            }

            if (p1Score + p2Score != boxes.values.count { it.owner != null }) return null
            if (boxes.size >= boardSize.squaresCount) return null

            val lastMoveEdge = if (json.has("lastMoveEdge") && !json.isNull("lastMoveEdge")) {
                val lj = json.getJSONObject("lastMoveEdge")
                val edge = DotEdge(
                    orientation = EdgeOrientation.valueOf(lj.getString("ori")),
                    row = lj.getInt("row"),
                    col = lj.getInt("col")
                )
                if (!DotConnectionsEngine.isValidEdge(edge, boardSize)) return null
                edge
            } else null

            val lastCompletedBoxes = mutableListOf<Pair<Int, Int>>()
            val lbArr = json.optJSONArray("lastCompletedBoxes")
            if (lbArr != null) {
                for (i in 0 until lbArr.length()) {
                    val lb = lbArr.getJSONObject(i)
                    val r = lb.getInt("row")
                    val c = lb.getInt("col")
                    if (r !in 0 until maxBoxCoord || c !in 0 until maxBoxCoord) return null
                    lastCompletedBoxes.add(Pair(r, c))
                }
            }

            val state = DotConnectionsGameState(
                boardSize = boardSize,
                placedEdges = placedEdges,
                boxes = boxes,
                activePlayer = activePlayer,
                p1Score = p1Score,
                p2Score = p2Score,
                lastMoveEdge = lastMoveEdge,
                lastCompletedBoxes = lastCompletedBoxes,
                isGameOver = false,
                winner = null,
                isDraw = false
            )
            if (!hasResumableDotConnectionsProgress(state)) return null
            SavedDotConnectionsSession(state = state, difficulty = difficulty)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 7. Ludo
    // =========================================================================

    fun hasResumableLudoProgress(state: LudoGameState): Boolean {
        if (state.isGameOver || state.winner != null) return false
        return state.seqNum > 1 ||
            state.diceRoll != null ||
            state.currentTurnIndex != 0 ||
            state.players.any { p -> p.tokens.any { it.step != LudoToken.STEP_YARD } }
    }

    fun encodeLudo(
        state: LudoGameState,
        botDifficulty: BotDifficulty,
        displayedDice: Int
    ): SavedGameSession? {
        if (state.mode != GameMode.SOLO) return null
        if (!hasResumableLudoProgress(state)) return null

        val json = JSONObject()
        json.put("mode", state.mode.name)
        json.put("currentTurnIndex", state.currentTurnIndex)
        if (state.diceRoll != null) json.put("diceRoll", state.diceRoll)
        json.put("canRoll", state.canRoll)
        json.put("statusText", state.statusText)
        if (state.lastMoveDescription != null) json.put("lastMoveDescription", state.lastMoveDescription)
        json.put("extraTurn", state.extraTurn)
        if (state.lastCapturedColor != null) json.put("lastCapturedColor", state.lastCapturedColor.name)
        json.put("seqNum", state.seqNum)
        json.put("botDifficulty", botDifficulty.name)
        json.put("displayedDice", displayedDice.coerceIn(1, 6))

        val selectables = JSONArray()
        state.selectableTokenIds.forEach { selectables.put(it) }
        json.put("selectableTokenIds", selectables)

        val playersArr = JSONArray()
        state.players.forEach { p ->
            val pj = JSONObject()
            pj.put("color", p.color.name)
            pj.put("name", p.name)
            pj.put("isBot", p.isBot)
            val tokensArr = JSONArray()
            p.tokens.forEach { t ->
                val tj = JSONObject()
                tj.put("id", t.id)
                tj.put("step", t.step)
                tokensArr.put(tj)
            }
            pj.put("tokens", tokensArr)
            playersArr.put(pj)
        }
        json.put("players", playersArr)

        val p1Finished = state.players.firstOrNull()?.finishedCount ?: 0
        val p2Finished = state.players.getOrNull(1)?.finishedCount ?: 0
        val summary = "Home $p1Finished/4 vs Bot $p2Finished/4 (${botDifficulty.name.lowercase().replaceFirstChar { it.uppercase() }})"
        return buildSession(GAME_LUDO, json.toString(), summary)
    }

    fun decodeLudo(payloadJson: String): SavedLudoSession? {
        return try {
            val json = JSONObject(payloadJson)
            val mode = GameMode.valueOf(json.getString("mode"))
            if (mode != GameMode.SOLO) return null

            val playersArr = json.getJSONArray("players")
            if (playersArr.length() < 2) return null
            val players = mutableListOf<LudoPlayerState>()
            for (i in 0 until playersArr.length()) {
                val pj = playersArr.getJSONObject(i)
                val color = LudoColor.valueOf(pj.getString("color"))
                val name = pj.getString("name")
                val isBot = pj.getBoolean("isBot")
                val tokensArr = pj.getJSONArray("tokens")
                if (tokensArr.length() != 4) return null
                val tokens = mutableListOf<LudoToken>()
                for (j in 0 until tokensArr.length()) {
                    val tj = tokensArr.getJSONObject(j)
                    val id = tj.getInt("id")
                    val step = tj.getInt("step")
                    if (id !in 0..3 || step !in LudoToken.STEP_YARD..LudoToken.STEP_FINISH) return null
                    tokens.add(LudoToken(id = id, color = color, step = step))
                }
                val playerState = LudoPlayerState(color = color, name = name, isBot = isBot, tokens = tokens)
                if (playerState.isWinner) return null
                players.add(playerState)
            }

            val turnIdx = json.getInt("currentTurnIndex")
            if (turnIdx !in players.indices) return null

            val diceRoll = if (json.has("diceRoll") && !json.isNull("diceRoll")) {
                val d = json.getInt("diceRoll")
                if (d !in 1..6) return null
                d
            } else null

            val canRoll = json.getBoolean("canRoll")
            val statusText = json.optString("statusText", "Roll the dice!")
            val lastMoveDescription = if (json.has("lastMoveDescription") && !json.isNull("lastMoveDescription")) {
                json.getString("lastMoveDescription")
            } else null
            val extraTurn = json.optBoolean("extraTurn", false)
            val lastCapturedColor = if (json.has("lastCapturedColor") && !json.isNull("lastCapturedColor")) {
                LudoColor.valueOf(json.getString("lastCapturedColor"))
            } else null
            val seqNum = json.optInt("seqNum", 0)
            if (seqNum < 0) return null

            val selectableIds = mutableListOf<Int>()
            val selArr = json.optJSONArray("selectableTokenIds")
            if (selArr != null) {
                for (i in 0 until selArr.length()) {
                    val id = selArr.getInt(i)
                    if (id !in 0..3) return null
                    selectableIds.add(id)
                }
            }

            val difficulty = BotDifficulty.valueOf(json.getString("botDifficulty"))
            val displayedDice = json.optInt("displayedDice", diceRoll ?: 6)
            if (displayedDice !in 1..6) return null

            val state = LudoGameState(
                mode = mode,
                players = players,
                currentTurnIndex = turnIdx,
                diceRoll = diceRoll,
                canRoll = canRoll,
                selectableTokenIds = selectableIds,
                statusText = statusText,
                winner = null,
                lastMoveDescription = lastMoveDescription,
                extraTurn = extraTurn,
                lastCapturedColor = lastCapturedColor,
                seqNum = seqNum
            )
            if (!hasResumableLudoProgress(state)) return null

            SavedLudoSession(
                state = state,
                botDifficulty = difficulty,
                displayedDice = displayedDice
            )
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 8. Battleship
    // =========================================================================

    fun hasResumableBattleshipProgress(state: BattleshipState): Boolean {
        if (state.isGameOver || state.winner != null) return false
        return when (state.phase) {
            BattleshipPhase.BATTLE -> true
            BattleshipPhase.DEPLOYMENT_P1 -> state.p1Fleet.placements.isNotEmpty()
            else -> false
        }
    }

    fun encodeBattleship(
        state: BattleshipState,
        botActiveHits: List<CellCoordinate>,
        selectedShipType: ShipType?,
        deploymentOrientation: ShipOrientation,
        activeBoardTab: Int
    ): SavedGameSession? {
        if (!hasResumableBattleshipProgress(state)) return null
        val json = JSONObject()
        json.put("phase", state.phase.name)
        json.put("p1Fleet", encodeFleet(state.p1Fleet))
        json.put("p2Fleet", encodeFleet(state.p2Fleet))
        json.put("p1Radar", encodeRadar(state.p1Radar))
        json.put("p2Radar", encodeRadar(state.p2Radar))
        json.put("activePlayer", state.activePlayer.name)
        json.put("turnNumber", state.turnNumber)
        json.put("latestAnnouncement", state.latestAnnouncement)

        val hitsArr = JSONArray()
        botActiveHits.forEach { coord ->
            val cj = JSONObject()
            cj.put("row", coord.row)
            cj.put("col", coord.col)
            hitsArr.put(cj)
        }
        json.put("botActiveHits", hitsArr)
        if (selectedShipType != null) {
            json.put("selectedShipType", selectedShipType.name)
        }
        json.put("deploymentOrientation", deploymentOrientation.name)
        json.put("activeBoardTab", activeBoardTab.coerceIn(0, 1))

        val summary = if (state.phase == BattleshipPhase.DEPLOYMENT_P1) {
            "Deploying fleet • ${state.p1Fleet.placements.size}/5 ships placed"
        } else {
            "Battle Turn ${state.turnNumber} • Enemy ${state.p2Fleet.remainingShipsCount}/5 ships left"
        }
        return buildSession(GAME_BATTLESHIP, json.toString(), summary)
    }

    fun decodeBattleship(payloadJson: String): SavedBattleshipSession? {
        return try {
            val json = JSONObject(payloadJson)
            val phase = BattleshipPhase.valueOf(json.getString("phase"))
            if (phase != BattleshipPhase.DEPLOYMENT_P1 && phase != BattleshipPhase.BATTLE) return null

            val p1Fleet = decodeFleet(json.getJSONObject("p1Fleet"), BattleshipPlayer.PLAYER_1) ?: return null
            val p2Fleet = decodeFleet(json.getJSONObject("p2Fleet"), BattleshipPlayer.PLAYER_2) ?: return null
            val p1Radar = decodeRadar(json.getJSONObject("p1Radar")) ?: return null
            val p2Radar = decodeRadar(json.getJSONObject("p2Radar")) ?: return null

            if (phase == BattleshipPhase.BATTLE) {
                if (!BattleshipEngine.validateSubmittedFleet(p1Fleet.placements)) return null
                if (!BattleshipEngine.validateSubmittedFleet(p2Fleet.placements)) return null
                if (p1Fleet.isAllSunk || p2Fleet.isAllSunk) return null
            }

            val activePlayer = BattleshipPlayer.valueOf(json.getString("activePlayer"))
            val turnNumber = json.getInt("turnNumber")
            if (turnNumber < 1) return null
            val latestAnnouncement = json.optString("latestAnnouncement", "")

            val botActiveHits = mutableListOf<CellCoordinate>()
            val hitsArr = json.optJSONArray("botActiveHits")
            if (hitsArr != null) {
                for (i in 0 until hitsArr.length()) {
                    val cj = hitsArr.getJSONObject(i)
                    val coord = CellCoordinate.ofOrNull(cj.getInt("row"), cj.getInt("col")) ?: return null
                    botActiveHits.add(coord)
                }
            }

            val selectedShipType = if (json.has("selectedShipType") && !json.isNull("selectedShipType")) {
                ShipType.valueOf(json.getString("selectedShipType"))
            } else null
            val orientation = ShipOrientation.valueOf(
                json.optString("deploymentOrientation", ShipOrientation.HORIZONTAL.name)
            )
            val activeBoardTab = json.optInt("activeBoardTab", 0).coerceIn(0, 1)

            val state = BattleshipState(
                phase = phase,
                p1Fleet = p1Fleet,
                p2Fleet = p2Fleet,
                p1Radar = p1Radar,
                p2Radar = p2Radar,
                activePlayer = activePlayer,
                winner = null,
                turnNumber = turnNumber,
                latestAnnouncement = latestAnnouncement
            )
            if (!hasResumableBattleshipProgress(state)) return null

            SavedBattleshipSession(
                state = state,
                botActiveHits = botActiveHits,
                selectedShipType = selectedShipType,
                deploymentOrientation = orientation,
                activeBoardTab = activeBoardTab
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeFleet(fleet: PlayerFleetState): JSONObject {
        val obj = JSONObject()
        val placementsArr = JSONArray()
        fleet.placements.forEach { p ->
            val pj = JSONObject()
            pj.put("type", p.shipType.name)
            pj.put("row", p.start.row)
            pj.put("col", p.start.col)
            pj.put("ori", p.orientation.name)
            placementsArr.put(pj)
        }
        obj.put("placements", placementsArr)

        val shotsArr = JSONArray()
        for ((coord, isHit) in fleet.incomingShots) {
            val sj = JSONObject()
            sj.put("row", coord.row)
            sj.put("col", coord.col)
            sj.put("hit", isHit)
            shotsArr.put(sj)
        }
        obj.put("incomingShots", shotsArr)
        obj.put("isDeploymentComplete", fleet.isDeploymentComplete)
        return obj
    }

    private fun decodeFleet(obj: JSONObject, player: BattleshipPlayer): PlayerFleetState? {
        return try {
            val placementsArr = obj.getJSONArray("placements")
            val placements = mutableListOf<ShipPlacement>()
            val seenTypes = mutableSetOf<ShipType>()
            for (i in 0 until placementsArr.length()) {
                val pj = placementsArr.getJSONObject(i)
                val shipType = ShipType.valueOf(pj.getString("type"))
                if (!seenTypes.add(shipType)) return null
                val row = pj.getInt("row")
                val col = pj.getInt("col")
                val ori = ShipOrientation.valueOf(pj.getString("ori"))
                if (!BattleshipEngine.isValidPlacement(shipType, row, col, ori, placements)) return null
                placements.add(ShipPlacement(shipType, CellCoordinate(row, col), ori))
            }

            val shotsMap = mutableMapOf<CellCoordinate, Boolean>()
            val shotsArr = obj.getJSONArray("incomingShots")
            for (i in 0 until shotsArr.length()) {
                val sj = shotsArr.getJSONObject(i)
                val coord = CellCoordinate.ofOrNull(sj.getInt("row"), sj.getInt("col")) ?: return null
                shotsMap[coord] = sj.getBoolean("hit")
            }

            val isComplete = obj.getBoolean("isDeploymentComplete")
            PlayerFleetState(
                player = player,
                placements = placements,
                incomingShots = shotsMap,
                isDeploymentComplete = isComplete
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeRadar(radar: TargetingRadar): JSONObject {
        val obj = JSONObject()
        val shotsArr = JSONArray()
        for ((coord, isHit) in radar.shots) {
            val sj = JSONObject()
            sj.put("row", coord.row)
            sj.put("col", coord.col)
            sj.put("hit", isHit)
            shotsArr.put(sj)
        }
        obj.put("shots", shotsArr)

        val sunkArr = JSONArray()
        radar.sunkEnemyShips.forEach { sunkArr.put(it.name) }
        obj.put("sunkEnemyShips", sunkArr)
        obj.put("genericSunkCount", radar.genericSunkCount)

        when (val outcome = radar.lastShotOutcome) {
            is ShotOutcome.Hit -> {
                val oj = JSONObject()
                oj.put("kind", "HIT")
                oj.put("row", outcome.coordinate.row)
                oj.put("col", outcome.coordinate.col)
                oj.put("shipType", outcome.shipType.name)
                oj.put("isSunk", outcome.isSunk)
                outcome.sunkShip?.let { sp ->
                    val spj = JSONObject()
                    spj.put("type", sp.shipType.name)
                    spj.put("row", sp.start.row)
                    spj.put("col", sp.start.col)
                    spj.put("ori", sp.orientation.name)
                    oj.put("sunkShip", spj)
                }
                obj.put("lastShotOutcome", oj)
            }
            is ShotOutcome.Miss -> {
                val oj = JSONObject()
                oj.put("kind", "MISS")
                oj.put("row", outcome.coordinate.row)
                oj.put("col", outcome.coordinate.col)
                obj.put("lastShotOutcome", oj)
            }
            else -> {}
        }
        return obj
    }

    private fun decodeRadar(obj: JSONObject): TargetingRadar? {
        return try {
            val shotsMap = mutableMapOf<CellCoordinate, Boolean>()
            val shotsArr = obj.getJSONArray("shots")
            for (i in 0 until shotsArr.length()) {
                val sj = shotsArr.getJSONObject(i)
                val coord = CellCoordinate.ofOrNull(sj.getInt("row"), sj.getInt("col")) ?: return null
                shotsMap[coord] = sj.getBoolean("hit")
            }

            val sunkList = mutableListOf<ShipType>()
            val sunkArr = obj.getJSONArray("sunkEnemyShips")
            for (i in 0 until sunkArr.length()) {
                sunkList.add(ShipType.valueOf(sunkArr.getString(i)))
            }
            val genericSunk = obj.optInt("genericSunkCount", 0)
            if (genericSunk < 0) return null

            val lastOutcome = if (obj.has("lastShotOutcome") && !obj.isNull("lastShotOutcome")) {
                val oj = obj.getJSONObject("lastShotOutcome")
                val coord = CellCoordinate.ofOrNull(oj.getInt("row"), oj.getInt("col")) ?: return null
                when (oj.getString("kind")) {
                    "HIT" -> {
                        val shipType = ShipType.valueOf(oj.getString("shipType"))
                        val isSunk = oj.getBoolean("isSunk")
                        val sunkShip = if (oj.has("sunkShip") && !oj.isNull("sunkShip")) {
                            val spj = oj.getJSONObject("sunkShip")
                            val spType = ShipType.valueOf(spj.getString("type"))
                            val spStart = CellCoordinate.ofOrNull(spj.getInt("row"), spj.getInt("col")) ?: return null
                            val spOri = ShipOrientation.valueOf(spj.getString("ori"))
                            if (!BattleshipEngine.isValidPlacement(spType, spStart.row, spStart.col, spOri, emptyList())) return null
                            ShipPlacement(spType, spStart, spOri)
                        } else null
                        ShotOutcome.Hit(coord, shipType, isSunk, sunkShip)
                    }
                    "MISS" -> ShotOutcome.Miss(coord)
                    else -> null
                }
            } else null

            TargetingRadar(
                shots = shotsMap,
                sunkEnemyShips = sunkList,
                genericSunkCount = genericSunk,
                lastShotOutcome = lastOutcome
            )
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // Shared GameStatus Helpers
    // =========================================================================

    private fun encodeGameStatus(status: GameStatus): JSONObject {
        val obj = JSONObject()
        when (status) {
            is GameStatus.InProgress -> obj.put("type", "InProgress")
            is GameStatus.RoundWon -> {
                obj.put("type", "RoundWon")
                obj.put("winner", status.winner.name)
            }
            is GameStatus.RoundDraw -> obj.put("type", "RoundDraw")
            is GameStatus.MatchWon -> {
                obj.put("type", "MatchWon")
                obj.put("winner", status.matchWinner.name)
            }
            is GameStatus.NotStarted -> obj.put("type", "NotStarted")
        }
        return obj
    }

    private fun decodeGameStatus(obj: JSONObject): GameStatus? {
        return try {
            when (obj.getString("type")) {
                "InProgress" -> GameStatus.InProgress
                "RoundWon" -> GameStatus.RoundWon(PlayerId.valueOf(obj.getString("winner")))
                "RoundDraw" -> GameStatus.RoundDraw
                "MatchWon" -> GameStatus.MatchWon(PlayerId.valueOf(obj.getString("winner")))
                "NotStarted" -> GameStatus.NotStarted
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
