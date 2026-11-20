package com.example.core.multiplayer

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
import com.example.core.engine.checkers.CheckersGameState
import com.example.core.engine.checkers.deserializeCheckersState
import com.example.core.engine.checkers.serializeCheckersState
import com.example.core.engine.chess.ChessGameState
import com.example.core.engine.chess.ChessGameStatus
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessPiece
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.chess.PieceType
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotBox
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotConnectionsGameState
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.core.engine.dotconnections.LinePlacement
import com.example.core.engine.ludo.LudoGameState
import com.example.core.engine.ludo.deserializeLudoState
import com.example.core.engine.ludo.serializeLudoState
import com.example.core.engine.snakesandladders.SnakesAndLaddersState
import com.example.core.engine.snakesandladders.deserializeSnakesState
import com.example.core.engine.snakesandladders.serializeSnakesState
import com.example.core.engine.tugofwar.TugOfWarState
import com.example.core.engine.xo.XOState
import com.example.core.engine.xo.deserializeXOState
import com.example.core.engine.xo.serializeXOState
import org.json.JSONArray
import org.json.JSONObject

data class ChessMultiplayerSnapshot(
    val state: ChessGameState,
    val moveHistory: List<ChessMove>
)

/**
 * Lossless JSON state snapshot helpers used exclusively when the Host accepts
 * a disconnected player's rejoin request to synchronize the live ongoing match state.
 */
object MultiplayerStateSnapshot {

    const val ACTION_REJOIN_STATE_SYNC = "REJOIN_STATE_SYNC"

    // 1. Tic Tac Toe (XO)
    fun encodeXO(state: XOState): String = serializeXOState(state)
    fun decodeXO(payload: String): XOState? = deserializeXOState(payload)

    // 2. Snakes & Ladders
    fun encodeSnakes(state: SnakesAndLaddersState): String = serializeSnakesState(state)
    fun decodeSnakes(payload: String): SnakesAndLaddersState? = deserializeSnakesState(payload)

    // 3. Checkers
    fun encodeCheckers(state: CheckersGameState): String = serializeCheckersState(state)
    fun decodeCheckers(payload: String): CheckersGameState? = deserializeCheckersState(payload)

    // 4. Ludo
    fun encodeLudo(state: LudoGameState): String = serializeLudoState(state)
    fun decodeLudo(payload: String): LudoGameState? = deserializeLudoState(payload)

    // 5. Tug of War
    fun encodeTugOfWar(state: TugOfWarState): String {
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
        state.lastTapPlayer?.let { json.put("lastTapPlayer", it.name) }
        return json.toString()
    }

    fun decodeTugOfWar(payload: String): TugOfWarState? {
        return try {
            val json = JSONObject(payload)
            val status = decodeGameStatus(json.getJSONObject("status")) ?: return null
            val lastTap = if (json.has("lastTapPlayer") && !json.isNull("lastTapPlayer")) {
                PlayerId.valueOf(json.getString("lastTapPlayer"))
            } else null
            TugOfWarState(
                ropePosition = json.getDouble("ropePosition").toFloat().coerceIn(-100f, 100f),
                p1Taps = json.getInt("p1Taps"),
                p2Taps = json.getInt("p2Taps"),
                p1PullEnergy = json.getDouble("p1PullEnergy").toFloat().coerceIn(0f, 2f),
                p2PullEnergy = json.getDouble("p2PullEnergy").toFloat().coerceIn(0f, 2f),
                activePlayer = PlayerId.valueOf(json.getString("activePlayer")),
                status = status,
                roundNumber = json.getInt("roundNumber"),
                player1Score = json.getInt("player1Score"),
                player2Score = json.getInt("player2Score"),
                targetScore = json.getInt("targetScore"),
                lastTapPlayer = lastTap
            )
        } catch (_: Exception) {
            null
        }
    }

    // 6. Chess
    fun encodeChess(state: ChessGameState, moveHistory: List<ChessMove>): String {
        val json = JSONObject()
        val boardArr = JSONArray()
        for (i in 0 until 64) {
            val piece = state.board[i]
            if (piece == null) {
                boardArr.put(JSONObject.NULL)
            } else {
                boardArr.put(JSONObject().apply {
                    put("type", piece.type.name)
                    put("color", piece.color.name)
                })
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
        json.put("status", encodeChessStatus(state.status))
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
        return json.toString()
    }

    fun decodeChess(payload: String): ChessMultiplayerSnapshot? {
        return try {
            val json = JSONObject(payload)
            val boardArr = json.getJSONArray("board")
            if (boardArr.length() != 64) return null
            val board = arrayOfNulls<ChessPiece>(64)
            for (i in 0 until 64) {
                if (!boardArr.isNull(i)) {
                    val pj = boardArr.getJSONObject(i)
                    board[i] = ChessPiece(
                        type = PieceType.valueOf(pj.getString("type")),
                        color = PieceColor.valueOf(pj.getString("color"))
                    )
                }
            }
            val enPassant = if (json.has("enPassantRow") && json.has("enPassantCol")) {
                ChessSquare(json.getInt("enPassantRow"), json.getInt("enPassantCol"))
            } else null

            val posHistory = mutableListOf<String>()
            val posArr = json.optJSONArray("positionHistory")
            if (posArr != null) {
                for (i in 0 until posArr.length()) posHistory.add(posArr.getString(i))
            }

            val capWhite = mutableListOf<PieceType>()
            val cwArr = json.optJSONArray("capturedByWhite")
            if (cwArr != null) {
                for (i in 0 until cwArr.length()) capWhite.add(PieceType.valueOf(cwArr.getString(i)))
            }

            val capBlack = mutableListOf<PieceType>()
            val cbArr = json.optJSONArray("capturedByBlack")
            if (cbArr != null) {
                for (i in 0 until cbArr.length()) capBlack.add(PieceType.valueOf(cbArr.getString(i)))
            }

            val lastMove = if (json.has("lastMove") && !json.isNull("lastMove")) {
                decodeChessMove(json.getJSONObject("lastMove"))
            } else null

            val moveHistory = mutableListOf<ChessMove>()
            val movesArr = json.optJSONArray("moveHistory")
            if (movesArr != null) {
                for (i in 0 until movesArr.length()) {
                    decodeChessMove(movesArr.getJSONObject(i))?.let { moveHistory.add(it) }
                }
            }

            val status = decodeChessStatus(json.optJSONObject("status"))
            val state = ChessGameState(
                board = board,
                activeColor = PieceColor.valueOf(json.getString("activeColor")),
                whiteCanCastleKingside = json.getBoolean("whiteCanCastleKingside"),
                whiteCanCastleQueenside = json.getBoolean("whiteCanCastleQueenside"),
                blackCanCastleKingside = json.getBoolean("blackCanCastleKingside"),
                blackCanCastleQueenside = json.getBoolean("blackCanCastleQueenside"),
                enPassantTarget = enPassant,
                halfmoveClock = json.getInt("halfmoveClock"),
                fullmoveNumber = json.getInt("fullmoveNumber"),
                status = status,
                inCheck = json.getBoolean("inCheck"),
                capturedByWhite = capWhite,
                capturedByBlack = capBlack,
                lastMove = lastMove,
                canonicalPositionKey = json.optString("canonicalPositionKey", ""),
                positionHistory = posHistory
            )
            ChessMultiplayerSnapshot(state = state, moveHistory = moveHistory)
        } catch (_: Exception) {
            null
        }
    }

    // 7. Dot Connections
    fun encodeDotConnections(state: DotConnectionsGameState): String {
        val json = JSONObject()
        json.put("boardSize", state.boardSize.name)
        json.put("activePlayer", state.activePlayer.name)
        json.put("p1Score", state.p1Score)
        json.put("p2Score", state.p2Score)
        json.put("isGameOver", state.isGameOver)
        state.winner?.let { json.put("winner", it.name) }
        json.put("isDraw", state.isDraw)

        val edgesArr = JSONArray()
        for ((edge, placement) in state.placedEdges) {
            edgesArr.put(JSONObject().apply {
                put("ori", edge.orientation.name)
                put("row", edge.row)
                put("col", edge.col)
                put("owner", placement.owner.name)
                put("ts", placement.timestampMs)
            })
        }
        json.put("placedEdges", edgesArr)

        val boxesArr = JSONArray()
        for ((_, box) in state.boxes) {
            boxesArr.put(JSONObject().apply {
                put("row", box.row)
                put("col", box.col)
                box.owner?.let { put("owner", it.name) }
                put("ts", box.completedAtTimestamp)
            })
        }
        json.put("boxes", boxesArr)

        state.lastMoveEdge?.let { edge ->
            json.put("lastMoveEdge", JSONObject().apply {
                put("ori", edge.orientation.name)
                put("row", edge.row)
                put("col", edge.col)
            })
        }

        val lastBoxesArr = JSONArray()
        state.lastCompletedBoxes.forEach { (r, c) ->
            lastBoxesArr.put(JSONObject().apply {
                put("row", r)
                put("col", c)
            })
        }
        json.put("lastCompletedBoxes", lastBoxesArr)
        return json.toString()
    }

    fun decodeDotConnections(payload: String): DotConnectionsGameState? {
        return try {
            val json = JSONObject(payload)
            val boardSize = DotBoardSize.valueOf(json.getString("boardSize"))
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
                placedEdges[edge] = LinePlacement(
                    edge = edge,
                    owner = DotPlayer.valueOf(ej.getString("owner")),
                    timestampMs = ej.optLong("ts", 0L)
                )
            }

            val boxes = mutableMapOf<Pair<Int, Int>, DotBox>()
            val boxesArr = json.getJSONArray("boxes")
            for (i in 0 until boxesArr.length()) {
                val bj = boxesArr.getJSONObject(i)
                val r = bj.getInt("row")
                val c = bj.getInt("col")
                val owner = if (bj.has("owner") && !bj.isNull("owner")) {
                    DotPlayer.valueOf(bj.getString("owner"))
                } else null
                boxes[Pair(r, c)] = DotBox(r, c, owner, bj.optLong("ts", 0L))
            }

            val lastMoveEdge = if (json.has("lastMoveEdge") && !json.isNull("lastMoveEdge")) {
                val lj = json.getJSONObject("lastMoveEdge")
                DotEdge(
                    orientation = EdgeOrientation.valueOf(lj.getString("ori")),
                    row = lj.getInt("row"),
                    col = lj.getInt("col")
                )
            } else null

            val lastCompletedBoxes = mutableListOf<Pair<Int, Int>>()
            val lbArr = json.optJSONArray("lastCompletedBoxes")
            if (lbArr != null) {
                for (i in 0 until lbArr.length()) {
                    val lb = lbArr.getJSONObject(i)
                    lastCompletedBoxes.add(Pair(lb.getInt("row"), lb.getInt("col")))
                }
            }

            val winner = if (json.has("winner") && !json.isNull("winner")) {
                DotPlayer.valueOf(json.getString("winner"))
            } else null

            DotConnectionsGameState(
                boardSize = boardSize,
                placedEdges = placedEdges,
                boxes = boxes,
                activePlayer = DotPlayer.valueOf(json.getString("activePlayer")),
                p1Score = json.getInt("p1Score"),
                p2Score = json.getInt("p2Score"),
                lastMoveEdge = lastMoveEdge,
                lastCompletedBoxes = lastCompletedBoxes,
                isGameOver = json.optBoolean("isGameOver", false),
                winner = winner,
                isDraw = json.optBoolean("isDraw", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    // 8. Battleship
    fun encodeBattleship(state: BattleshipState): String {
        val json = JSONObject()
        json.put("phase", state.phase.name)
        json.put("p1Fleet", encodeFleet(state.p1Fleet))
        json.put("p2Fleet", encodeFleet(state.p2Fleet))
        json.put("p1Radar", encodeRadar(state.p1Radar))
        json.put("p2Radar", encodeRadar(state.p2Radar))
        json.put("activePlayer", state.activePlayer.name)
        state.winner?.let { json.put("winner", it.name) }
        json.put("turnNumber", state.turnNumber)
        json.put("latestAnnouncement", state.latestAnnouncement)
        return json.toString()
    }

    fun decodeBattleship(payload: String): BattleshipState? {
        return try {
            val json = JSONObject(payload)
            val phase = BattleshipPhase.valueOf(json.getString("phase"))
            val p1Fleet = decodeFleet(json.getJSONObject("p1Fleet"), BattleshipPlayer.PLAYER_1) ?: return null
            val p2Fleet = decodeFleet(json.getJSONObject("p2Fleet"), BattleshipPlayer.PLAYER_2) ?: return null
            val p1Radar = decodeRadar(json.getJSONObject("p1Radar")) ?: return null
            val p2Radar = decodeRadar(json.getJSONObject("p2Radar")) ?: return null
            val winner = if (json.has("winner") && !json.isNull("winner")) {
                BattleshipPlayer.valueOf(json.getString("winner"))
            } else null

            BattleshipState(
                phase = phase,
                p1Fleet = p1Fleet,
                p2Fleet = p2Fleet,
                p1Radar = p1Radar,
                p2Radar = p2Radar,
                activePlayer = BattleshipPlayer.valueOf(json.getString("activePlayer")),
                winner = winner,
                turnNumber = json.getInt("turnNumber"),
                latestAnnouncement = json.optString("latestAnnouncement", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeChessStatus(status: ChessGameStatus): JSONObject {
        val obj = JSONObject()
        when (status) {
            is ChessGameStatus.InProgress -> obj.put("type", "InProgress")
            is ChessGameStatus.Checkmate -> {
                obj.put("type", "Checkmate")
                obj.put("winner", status.winner.name)
            }
            is ChessGameStatus.Stalemate -> obj.put("type", "Stalemate")
            is ChessGameStatus.DrawByFiftyMoves -> obj.put("type", "DrawByFiftyMoves")
            is ChessGameStatus.DrawBySeventyFiveMoves -> obj.put("type", "DrawBySeventyFiveMoves")
            is ChessGameStatus.DrawByThreefoldRepetition -> obj.put("type", "DrawByThreefoldRepetition")
            is ChessGameStatus.DrawByFivefoldRepetition -> obj.put("type", "DrawByFivefoldRepetition")
            is ChessGameStatus.DrawByInsufficientMaterial -> obj.put("type", "DrawByInsufficientMaterial")
        }
        return obj
    }

    private fun decodeChessStatus(obj: JSONObject?): ChessGameStatus {
        if (obj == null) return ChessGameStatus.InProgress
        return when (obj.optString("type", "InProgress")) {
            "Checkmate" -> ChessGameStatus.Checkmate(PieceColor.valueOf(obj.getString("winner")))
            "Stalemate" -> ChessGameStatus.Stalemate
            "DrawByFiftyMoves" -> ChessGameStatus.DrawByFiftyMoves
            "DrawBySeventyFiveMoves" -> ChessGameStatus.DrawBySeventyFiveMoves
            "DrawByThreefoldRepetition" -> ChessGameStatus.DrawByThreefoldRepetition
            "DrawByFivefoldRepetition" -> ChessGameStatus.DrawByFivefoldRepetition
            "DrawByInsufficientMaterial" -> ChessGameStatus.DrawByInsufficientMaterial
            else -> ChessGameStatus.InProgress
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
            val cap = if (obj.has("capType") && obj.has("capColor")) {
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
                capturedPiece = cap
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeFleet(fleet: PlayerFleetState): JSONObject {
        val obj = JSONObject()
        val placementsArr = JSONArray()
        fleet.placements.forEach { p ->
            placementsArr.put(JSONObject().apply {
                put("type", p.shipType.name)
                put("row", p.start.row)
                put("col", p.start.col)
                put("ori", p.orientation.name)
            })
        }
        obj.put("placements", placementsArr)
        val shotsArr = JSONArray()
        for ((coord, isHit) in fleet.incomingShots) {
            shotsArr.put(JSONObject().apply {
                put("row", coord.row)
                put("col", coord.col)
                put("hit", isHit)
            })
        }
        obj.put("incomingShots", shotsArr)
        obj.put("isDeploymentComplete", fleet.isDeploymentComplete)
        return obj
    }

    private fun decodeFleet(obj: JSONObject, player: BattleshipPlayer): PlayerFleetState? {
        return try {
            val placementsArr = obj.getJSONArray("placements")
            val placements = mutableListOf<ShipPlacement>()
            for (i in 0 until placementsArr.length()) {
                val pj = placementsArr.getJSONObject(i)
                val shipType = ShipType.valueOf(pj.getString("type"))
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
            PlayerFleetState(
                player = player,
                placements = placements,
                incomingShots = shotsMap,
                isDeploymentComplete = obj.getBoolean("isDeploymentComplete")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeRadar(radar: TargetingRadar): JSONObject {
        val obj = JSONObject()
        val shotsArr = JSONArray()
        for ((coord, isHit) in radar.shots) {
            shotsArr.put(JSONObject().apply {
                put("row", coord.row)
                put("col", coord.col)
                put("hit", isHit)
            })
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
                    oj.put("sunkShip", JSONObject().apply {
                        put("type", sp.shipType.name)
                        put("row", sp.start.row)
                        put("col", sp.start.col)
                        put("ori", sp.orientation.name)
                    })
                }
                obj.put("lastShotOutcome", oj)
            }
            is ShotOutcome.Miss -> {
                obj.put("lastShotOutcome", JSONObject().apply {
                    put("kind", "MISS")
                    put("row", outcome.coordinate.row)
                    put("col", outcome.coordinate.col)
                })
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
                genericSunkCount = obj.optInt("genericSunkCount", 0),
                lastShotOutcome = lastOutcome
            )
        } catch (_: Exception) {
            null
        }
    }

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
