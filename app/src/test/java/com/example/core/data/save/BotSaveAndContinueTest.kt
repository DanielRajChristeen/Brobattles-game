package com.example.core.data.save

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.bot.BotDifficulty
import com.example.core.data.db.AppDatabase
import com.example.core.data.model.SavedGameSession
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.battleship.BattleshipBot
import com.example.core.engine.battleship.BattleshipEngine
import com.example.core.engine.battleship.BattleshipPhase
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.CellCoordinate
import com.example.core.engine.battleship.ShipOrientation
import com.example.core.engine.battleship.ShipType
import com.example.core.engine.checkers.CheckersDifficulty
import com.example.core.engine.checkers.CheckersEngine
import com.example.core.engine.checkers.CheckersGameState
import com.example.core.engine.checkers.CheckersMove
import com.example.core.engine.checkers.CheckersPiece
import com.example.core.engine.checkers.CheckersSquare
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessEngine
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotDifficulty
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.core.engine.ludo.LudoGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersMove
import com.example.core.engine.tugofwar.TugOfWarGameEngine
import com.example.core.engine.tugofwar.TugOfWarMove
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.registry.GameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BotSaveAndContinueTest {

    private fun createInMemoryDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun buildAllEightSessions(): Map<String, SavedGameSession> {
        // 1. Tic Tac Toe (XO) - mid-match with round 2, scores, and pieces placed
        val xoEngine = XOGameEngine(initialTargetScore = 5)
        xoEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(1), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(8), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(2), PlayerId.PLAYER_1) // P1 wins round 1
        xoEngine.startRound()
        // Round 2 starts with PLAYER_2
        xoEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        val xoSession = BotGameStateSerializer.encodeXO(xoEngine.state, BotDifficulty.HARD)!!

        // 2. Tug of War - mid-pull with scores & tap counts
        val tugEngine = TugOfWarGameEngine(initialTargetScore = 3)
        tugEngine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)
        tugEngine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)
        tugEngine.makeMove(TugOfWarMove(), PlayerId.PLAYER_2)
        val tugSession = BotGameStateSerializer.encodeTugOfWar(tugEngine.state)!!

        // 3. Snakes & Ladders - mid-board positions (roll 1 leaves start square + extra turn)
        val snakesEngine = SnakesAndLaddersGameEngine(initialPlayerCount = 2)
        snakesEngine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)
        snakesEngine.makeMove(SnakesAndLaddersMove(4), PlayerId.PLAYER_1)
        snakesEngine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_2)
        snakesEngine.makeMove(SnakesAndLaddersMove(5), PlayerId.PLAYER_2)
        val snakesSession = BotGameStateSerializer.encodeSnakes(snakesEngine.state, displayedDice = 5)!!

        // 4. Chess - mid-game with moves, capture, history
        val chessEngine = ChessEngine()
        assertTrue(chessEngine.makeMove(chessEngine.currentLegalMoves.first { it.from == ChessSquare(6, 4) && it.to == ChessSquare(4, 4) })) // e2-e4
        assertTrue(chessEngine.makeMove(chessEngine.currentLegalMoves.first { it.from == ChessSquare(1, 3) && it.to == ChessSquare(3, 3) })) // d7-d5
        assertTrue(chessEngine.makeMove(chessEngine.currentLegalMoves.first { it.from == ChessSquare(4, 4) && it.to == ChessSquare(3, 3) })) // exd5
        val chessSession = BotGameStateSerializer.encodeChess(
            state = chessEngine.state,
            difficulty = ChessDifficulty.HARD,
            playerColorPreference = ChessPlayerColor.AUTO,
            resolvedPlayerColor = PieceColor.BLACK,
            moveHistory = chessEngine.getMoveHistory()
        )!!

        // 5. Checkers - mid-game with king & multi-jump state
        val checkersBoard = mapOf(
            CheckersSquare(2, 1) to CheckersPiece(id = 1, player = PlayerId.PLAYER_1, isKing = true),
            CheckersSquare(5, 2) to CheckersPiece(id = 2, player = PlayerId.PLAYER_1, isKing = false),
            CheckersSquare(3, 2) to CheckersPiece(id = 13, player = PlayerId.PLAYER_2, isKing = false),
            CheckersSquare(1, 4) to CheckersPiece(id = 14, player = PlayerId.PLAYER_2, isKing = true)
        )
        val checkersState = CheckersGameState(
            board = checkersBoard,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress,
            mustContinueJumpingFrom = CheckersSquare(2, 1),
            movesWithoutCaptureOrPromotion = 4,
            lastMove = CheckersMove(CheckersSquare(4, 3), CheckersSquare(2, 1), CheckersSquare(3, 2))
        )
        val checkersSession = BotGameStateSerializer.encodeCheckers(checkersState, CheckersDifficulty.HARD)!!

        // 6. Dot Connections - custom LARGE board with completed box & scores
        val dotEngine = DotConnectionsEngine(DotBoardSize.LARGE)
        dotEngine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 0, 0))
        dotEngine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 1, 0))
        dotEngine.makeMove(DotEdge(EdgeOrientation.VERTICAL, 0, 0))
        dotEngine.makeMove(DotEdge(EdgeOrientation.VERTICAL, 0, 1)) // completes box (0,0)
        dotEngine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 0, 1))
        val dotSession = BotGameStateSerializer.encodeDotConnections(dotEngine.state, DotDifficulty.HARD)!!

        // 7. Ludo - rolled 1 to release soldier, then rolled 4 leaving mid-turn selectable token
        val ludoEngine = LudoGameEngine(initialMode = GameMode.SOLO, initialPlayerCount = 2)
        ludoEngine.rollDice(forcedValue = 1)
        ludoEngine.moveToken(0) // leaves yard onto track + grants extra turn
        ludoEngine.rollDice(forcedValue = 4) // mid-turn with selectable token!
        val ludoSession = BotGameStateSerializer.encodeLudo(
            state = ludoEngine.state,
            botDifficulty = BotDifficulty.HARD,
            displayedDice = 4
        )!!

        // 8. Battleship - active battle phase with hits, misses, and bot Target Mode activeHits
        val bsEngine = BattleshipEngine()
        assertTrue(bsEngine.setFleet(BattleshipPlayer.PLAYER_1, BattleshipEngine.generateRandomFleet()))
        assertTrue(bsEngine.setFleet(BattleshipPlayer.PLAYER_2, BattleshipEngine.generateRandomFleet()))
        bsEngine.setPhase(BattleshipPhase.BATTLE)
        // Fire a miss from P1 so turn passes to P2
        val p2Occupied = bsEngine.state.p2Fleet.placements.flatMap { it.occupiedCoordinates }.toSet()
        val p2WaterCell = (0 until 100).map { CellCoordinate.fromIndex(it) }.first { it !in p2Occupied }
        bsEngine.fireShot(BattleshipPlayer.PLAYER_1, p2WaterCell)
        val bsBot = BattleshipBot()
        val p1ShipCell = bsEngine.state.p1Fleet.placements.first().occupiedCoordinates.first()
        bsEngine.fireShot(BattleshipPlayer.PLAYER_2, p1ShipCell)
        bsBot.restoreActiveHits(listOf(p1ShipCell))
        val bsSession = BotGameStateSerializer.encodeBattleship(
            state = bsEngine.state,
            botActiveHits = bsBot.getActiveHits(),
            selectedShipType = null,
            deploymentOrientation = ShipOrientation.VERTICAL,
            activeBoardTab = 1
        )!!

        return linkedMapOf(
            BotGameStateSerializer.GAME_XO to xoSession,
            BotGameStateSerializer.GAME_TUG_OF_WAR to tugSession,
            BotGameStateSerializer.GAME_SNAKES to snakesSession,
            BotGameStateSerializer.GAME_CHESS to chessSession,
            BotGameStateSerializer.GAME_CHECKERS to checkersSession,
            BotGameStateSerializer.GAME_DOT_CONNECTIONS to dotSession,
            BotGameStateSerializer.GAME_LUDO to ludoSession,
            BotGameStateSerializer.GAME_BATTLESHIP to bsSession
        )
    }

    @Test
    fun `exact lossless round trip and engine restore for all 8 games`() {
        val sessions = buildAllEightSessions()
        assertEquals(8, sessions.size)
        sessions.values.forEach { session ->
            assertTrue("Session ${session.gameId} should be valid", BotGameStateSerializer.isSessionValid(session))
        }

        // 1. XO
        val decodedXO = BotGameStateSerializer.decodeXO(sessions[BotGameStateSerializer.GAME_XO]!!.payloadJson)
        assertNotNull(decodedXO)
        assertEquals(BotDifficulty.HARD, decodedXO!!.difficulty)
        assertEquals(2, decodedXO.state.roundNumber)
        assertEquals(1, decodedXO.state.player1Score)
        assertEquals(5, decodedXO.state.targetScore)
        val restoredXOEngine = XOGameEngine(initialTargetScore = decodedXO.state.targetScore)
        restoredXOEngine.syncState(decodedXO.state)
        assertEquals(decodedXO.state, restoredXOEngine.state)

        // 2. Tug of War
        val decodedTug = BotGameStateSerializer.decodeTugOfWar(sessions[BotGameStateSerializer.GAME_TUG_OF_WAR]!!.payloadJson)
        assertNotNull(decodedTug)
        assertEquals(2, decodedTug!!.state.p1Taps)
        assertEquals(1, decodedTug.state.p2Taps)
        assertEquals(3, decodedTug.state.targetScore)
        val restoredTugEngine = TugOfWarGameEngine(initialTargetScore = decodedTug.state.targetScore)
        restoredTugEngine.restoreState(decodedTug.state)
        assertEquals(decodedTug.state, restoredTugEngine.state)

        // 3. Snakes & Ladders
        val decodedSnakes = BotGameStateSerializer.decodeSnakes(sessions[BotGameStateSerializer.GAME_SNAKES]!!.payloadJson)
        assertNotNull(decodedSnakes)
        assertEquals(5, decodedSnakes!!.displayedDice)
        assertTrue(decodedSnakes.state.playerPositions[0] > 1)
        assertTrue(decodedSnakes.state.playerPositions[1] > 1)
        val restoredSnakes = SnakesAndLaddersGameEngine(initialPlayerCount = decodedSnakes.state.playerCount)
        restoredSnakes.syncState(decodedSnakes.state)
        assertEquals(decodedSnakes.state, restoredSnakes.state)

        // 4. Chess (including full Undo stack restoration!)
        val decodedChess = BotGameStateSerializer.decodeChess(sessions[BotGameStateSerializer.GAME_CHESS]!!.payloadJson)
        assertNotNull(decodedChess)
        assertEquals(ChessDifficulty.HARD, decodedChess!!.difficulty)
        assertEquals(ChessPlayerColor.AUTO, decodedChess.playerColorPreference)
        assertEquals(PieceColor.BLACK, decodedChess.resolvedPlayerColor)
        assertEquals(3, decodedChess.moveHistory.size)
        val restoredChess = ChessEngine()
        restoredChess.restoreState(decodedChess.state, decodedChess.moveHistory)
        assertTrue(decodedChess.state.board.contentEquals(restoredChess.state.board))
        assertEquals(decodedChess.state.activeColor, restoredChess.state.activeColor)
        assertEquals(decodedChess.state.capturedByWhite, restoredChess.state.capturedByWhite)
        assertEquals(decodedChess.state.fullmoveNumber, restoredChess.state.fullmoveNumber)
        assertTrue("Restored ChessEngine should preserve Undo history", restoredChess.canUndo())
        assertTrue(restoredChess.undoMove())
        assertEquals(2, restoredChess.getMoveHistory().size)

        // 5. Checkers
        val decodedCheckers = BotGameStateSerializer.decodeCheckers(sessions[BotGameStateSerializer.GAME_CHECKERS]!!.payloadJson)
        assertNotNull(decodedCheckers)
        assertEquals(CheckersDifficulty.HARD, decodedCheckers!!.difficulty)
        assertEquals(CheckersSquare(2, 1), decodedCheckers.state.mustContinueJumpingFrom)
        assertEquals(4, decodedCheckers.state.movesWithoutCaptureOrPromotion)
        val restoredCheckers = CheckersEngine()
        restoredCheckers.syncState(decodedCheckers.state)
        assertEquals(decodedCheckers.state, restoredCheckers.state)

        // 6. Dot Connections
        val decodedDot = BotGameStateSerializer.decodeDotConnections(sessions[BotGameStateSerializer.GAME_DOT_CONNECTIONS]!!.payloadJson)
        assertNotNull(decodedDot)
        assertEquals(DotBoardSize.LARGE, decodedDot!!.state.boardSize)
        assertEquals(DotDifficulty.HARD, decodedDot.difficulty)
        assertEquals(5, decodedDot.state.placedEdges.size)
        assertEquals(1, decodedDot.state.p1Score + decodedDot.state.p2Score)
        val restoredDot = DotConnectionsEngine(decodedDot.state.boardSize)
        restoredDot.restoreState(decodedDot.state)
        assertEquals(decodedDot.state, restoredDot.state)

        // 7. Ludo
        val decodedLudo = BotGameStateSerializer.decodeLudo(sessions[BotGameStateSerializer.GAME_LUDO]!!.payloadJson)
        assertNotNull(decodedLudo)
        assertEquals(BotDifficulty.HARD, decodedLudo!!.botDifficulty)
        assertEquals(4, decodedLudo.displayedDice)
        assertEquals(4, decodedLudo.state.diceRoll)
        assertFalse(decodedLudo.state.canRoll)
        assertTrue(decodedLudo.state.selectableTokenIds.isNotEmpty())
        val restoredLudo = LudoGameEngine(initialMode = GameMode.SOLO, initialPlayerCount = decodedLudo.state.players.size)
        restoredLudo.setState(decodedLudo.state)
        assertEquals(decodedLudo.state, restoredLudo.state)

        // 8. Battleship
        val decodedBs = BotGameStateSerializer.decodeBattleship(sessions[BotGameStateSerializer.GAME_BATTLESHIP]!!.payloadJson)
        assertNotNull(decodedBs)
        assertEquals(BattleshipPhase.BATTLE, decodedBs!!.state.phase)
        assertEquals(1, decodedBs.activeBoardTab)
        assertEquals(1, decodedBs.botActiveHits.size)
        val restoredBs = BattleshipEngine()
        restoredBs.restoreState(decodedBs.state)
        assertEquals(decodedBs.state, restoredBs.state)
        val restoredBot = BattleshipBot()
        restoredBot.restoreActiveHits(decodedBs.botActiveHits)
        assertEquals(decodedBs.botActiveHits, restoredBot.getActiveHits())
    }

    @Test
    fun `battleship partial deployment phase saves and restores cleanly`() {
        val engine = BattleshipEngine()
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER, 0, 0, ShipOrientation.HORIZONTAL))
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 2, 2, ShipOrientation.VERTICAL))

        val session = BotGameStateSerializer.encodeBattleship(
            state = engine.state,
            botActiveHits = emptyList(),
            selectedShipType = ShipType.BATTLESHIP,
            deploymentOrientation = ShipOrientation.VERTICAL,
            activeBoardTab = 0
        )
        assertNotNull(session)
        val decoded = BotGameStateSerializer.decodeBattleship(session!!.payloadJson)
        assertNotNull(decoded)
        assertEquals(BattleshipPhase.DEPLOYMENT_P1, decoded!!.state.phase)
        assertEquals(2, decoded.state.p1Fleet.placements.size)
        assertEquals(ShipType.BATTLESHIP, decoded.selectedShipType)
        assertEquals(ShipOrientation.VERTICAL, decoded.deploymentOrientation)
    }

    @Test
    fun `all 8 saves persist independently across simulated app restart and force stop`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_bot_saves_restart_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        try {
            val allSessions = buildAllEightSessions()

            // Session 1: write all 8 saves to file-backed Room DB, then close DB (simulating process death)
            val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val manager1 = BotGameSaveManager(
                dao = db1.savedGameSessionDao(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined
            )
            allSessions.values.forEach { session ->
                assertTrue(manager1.saveSession(session))
            }
            db1.close()

            // Session 2: reopen fresh DB instance and verify all 8 games survive completely intact
            val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val manager2 = BotGameSaveManager(
                dao = db2.savedGameSessionDao(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined
            )
            val restoredMap = manager2.observeAllValidSessions().first()
            assertEquals(8, restoredMap.size)
            allSessions.forEach { (gameId, original) ->
                val persisted = restoredMap[gameId]
                assertNotNull("Missing persisted session for $gameId", persisted)
                assertEquals(original.payloadJson, persisted!!.payloadJson)
                assertEquals(original.checksum, persisted.checksum)
                assertEquals(original.summary, persisted.summary)
            }

            // Overwriting or clearing 1 game (e.g. Chess) must NOT affect the other 7 games
            manager2.clearSession(BotGameStateSerializer.GAME_CHESS)
            val afterClear = manager2.observeAllValidSessions().first()
            assertEquals(7, afterClear.size)
            assertNull(afterClear[BotGameStateSerializer.GAME_CHESS])
            assertNotNull(afterClear[BotGameStateSerializer.GAME_XO])
            assertNotNull(afterClear[BotGameStateSerializer.GAME_BATTLESHIP])
            assertNotNull(afterClear[BotGameStateSerializer.GAME_LUDO])

            db2.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `terminal and empty game states are rejected and never saved`() {
        // Empty initial states -> null
        assertNull(BotGameStateSerializer.encodeXO(XOGameEngine().state, BotDifficulty.MEDIUM))
        assertNull(BotGameStateSerializer.encodeTugOfWar(TugOfWarGameEngine().state))
        assertNull(BotGameStateSerializer.encodeSnakes(SnakesAndLaddersGameEngine().state, 1))
        assertNull(
            BotGameStateSerializer.encodeChess(
                ChessEngine().state,
                ChessDifficulty.MEDIUM,
                ChessPlayerColor.WHITE,
                PieceColor.WHITE,
                emptyList()
            )
        )
        assertNull(BotGameStateSerializer.encodeCheckers(CheckersEngine().state, CheckersDifficulty.MEDIUM))
        assertNull(BotGameStateSerializer.encodeDotConnections(DotConnectionsEngine().state, DotDifficulty.MEDIUM))
        assertNull(BotGameStateSerializer.encodeLudo(LudoGameEngine().state, BotDifficulty.MEDIUM, 1))
        assertNull(
            BotGameStateSerializer.encodeBattleship(
                BattleshipEngine().state,
                emptyList(),
                ShipType.CARRIER,
                ShipOrientation.HORIZONTAL,
                0
            )
        )

        // Completed XO match -> null
        val finishedXO = XOGameEngine(initialTargetScore = 1)
        finishedXO.makeMove(XOMove(0), PlayerId.PLAYER_1)
        finishedXO.makeMove(XOMove(3), PlayerId.PLAYER_2)
        finishedXO.makeMove(XOMove(1), PlayerId.PLAYER_1)
        finishedXO.makeMove(XOMove(4), PlayerId.PLAYER_2)
        finishedXO.makeMove(XOMove(2), PlayerId.PLAYER_1)
        assertTrue(finishedXO.state.status is GameStatus.MatchWon)
        assertNull(BotGameStateSerializer.encodeXO(finishedXO.state, BotDifficulty.MEDIUM))
    }

    @Test
    fun `corrupted tampered truncated and mismatched schema saves are safely rejected and pruned`() = runBlocking {
        val db = createInMemoryDb()
        try {
            val dao = db.savedGameSessionDao()
            val manager = BotGameSaveManager(
                dao = dao,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined
            )

            val validSessions = buildAllEightSessions()
            val validXO = validSessions[BotGameStateSerializer.GAME_XO]!!
            dao.upsertSession(validXO)

            // 1. Truncated/malformed JSON
            dao.upsertSession(
                SavedGameSession(
                    gameId = BotGameStateSerializer.GAME_CHESS,
                    schemaVersion = BotGameStateSerializer.SCHEMA_VERSION,
                    payloadJson = "{\"board\":[{\"truncated",
                    checksum = BotGameStateSerializer.computeChecksum(
                        BotGameStateSerializer.GAME_CHESS,
                        BotGameStateSerializer.SCHEMA_VERSION,
                        "{\"board\":[{\"truncated"
                    ),
                    summary = "Corrupt",
                    updatedAt = 100L
                )
            )

            // 2. Tampered checksum (valid JSON payload, wrong checksum)
            val validLudo = validSessions[BotGameStateSerializer.GAME_LUDO]!!
            dao.upsertSession(validLudo.copy(checksum = "deadbeef0000"))

            // 3. Incompatible future schema version
            val validSnakes = validSessions[BotGameStateSerializer.GAME_SNAKES]!!
            dao.upsertSession(validSnakes.copy(schemaVersion = 999))

            // 4. Out-of-bounds / invalid payload even with valid checksum
            val badPayload = """{"board":["PLAYER_1"],"activePlayer":"PLAYER_1","status":{"type":"InProgress"}}"""
            dao.upsertSession(
                BotGameStateSerializer.buildSession(
                    gameId = BotGameStateSerializer.GAME_CHECKERS,
                    payloadJson = badPayload,
                    summary = "Bad"
                )
            )

            // Observe valid sessions: only XO should survive; all 4 corrupted rows should be ignored & pruned
            val validMap = manager.observeAllValidSessions().first()
            assertEquals(1, validMap.size)
            assertNotNull(validMap[BotGameStateSerializer.GAME_XO])
            assertNull(validMap[BotGameStateSerializer.GAME_CHESS])
            assertNull(validMap[BotGameStateSerializer.GAME_LUDO])
            assertNull(validMap[BotGameStateSerializer.GAME_SNAKES])
            assertNull(validMap[BotGameStateSerializer.GAME_CHECKERS])

            // Direct getValidSession on corrupted rows also returns null & deletes
            assertNull(manager.getValidSession(BotGameStateSerializer.GAME_CHESS))
            assertNull(dao.getSession(BotGameStateSerializer.GAME_CHESS))
            assertNotNull(manager.getValidSession(BotGameStateSerializer.GAME_XO))
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 2 to 3 creates saved_bot_sessions while preserving existing v2 tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_2_3_${System.nanoTime()}.db"
        try {
            val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `match_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gameId` TEXT NOT NULL, `winnerName` TEXT NOT NULL, `loserName` TEXT NOT NULL, `scoreSummary` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isBotMatch` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `user_settings` (`id` INTEGER NOT NULL, `soundEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `isDarkMode` INTEGER NOT NULL, `username` TEXT NOT NULL, PRIMARY KEY(`id`))"
                        )
                        db.execSQL(
                            "INSERT INTO `user_settings` (`id`, `soundEnabled`, `vibrationEnabled`, `isDarkMode`, `username`) VALUES (1, 1, 1, 1, 'VeteranBro')"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
            val sqliteDb = helper.writableDatabase
            AppDatabase.MIGRATION_2_3.migrate(sqliteDb)

            // Verify existing user_settings row is intact
            sqliteDb.query("SELECT username FROM user_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("VeteranBro", cursor.getString(0))
            }

            // Verify saved_bot_sessions table exists and accepts inserts
            sqliteDb.execSQL(
                "INSERT INTO `saved_bot_sessions` (`gameId`, `schemaVersion`, `payloadJson`, `checksum`, `summary`, `updatedAt`) VALUES ('xo', 1, '{}', 'abc', 'Round 1', 12345)"
            )
            sqliteDb.query("SELECT gameId, summary FROM saved_bot_sessions WHERE gameId = 'xo'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("xo", cursor.getString(0))
                assertEquals("Round 1", cursor.getString(1))
            }
            helper.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
