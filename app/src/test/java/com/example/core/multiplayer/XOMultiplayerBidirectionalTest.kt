package com.example.core.multiplayer

import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.XOState
import com.example.core.engine.xo.deserializeXOState
import com.example.core.engine.xo.serializeXOState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XOMultiplayerBidirectionalTest {

    /**
     * Simulated peer simulating either Host or Client in Tic Tac Toe multiplayer.
     */
    class SimulatedPeer(
        val name: String,
        val isHost: Boolean,
        val targetScore: Int = 3
    ) {
        val engine = XOGameEngine(initialTargetScore = targetScore)
        var gameState: XOState = engine.state
        val localPlayerId: PlayerId = if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
        var isPlaying: Boolean = false
        var lastSentAction: PlayerAction? = null

        fun startGame(configTarget: Int = targetScore) {
            engine.initializeGame(configTarget)
            gameState = engine.state
            isPlaying = true
        }

        /**
         * Local user attempts a move.
         */
        fun userMakeMove(move: XOMove, onSendToPeer: (PlayerAction) -> Unit): Boolean {
            if (gameState.status !is GameStatus.InProgress) return false
            if (gameState.activePlayer != localPlayerId) return false

            if (isHost) {
                // Authoritative host
                val res = engine.makeMove(move, PlayerId.PLAYER_1)
                if (res is MoveResult.Success) {
                    gameState = res.newState
                    val syncPayload = serializeXOState(res.newState, move)
                    val action = PlayerAction(name, "XO_SYNC", syncPayload)
                    lastSentAction = action
                    onSendToPeer(action)
                    return true
                }
                return false
            } else {
                // Client validates locally and sends request to host
                if (engine.validateMove(move, PlayerId.PLAYER_2)) {
                    val payload = if (move.fromIndex != null) "${move.fromIndex},${move.toIndex}" else "${move.toIndex}"
                    val action = PlayerAction(name, "XO_MOVE", payload)
                    lastSentAction = action
                    onSendToPeer(action)
                    // Optimistic update
                    val res = engine.makeMove(move, PlayerId.PLAYER_2)
                    if (res is MoveResult.Success) {
                        gameState = res.newState
                    }
                    return true
                }
                return false
            }
        }

        /**
         * Incoming action from the peer.
         */
        fun receiveAction(action: PlayerAction, onSendToPeer: (PlayerAction) -> Unit = {}) {
            when (action.actionType) {
                "XO_MOVE" -> {
                    val parts = action.payload.split(",")
                    val move = if (parts.size >= 2) {
                        val from = parts[0].toIntOrNull()
                        val to = parts[1].toIntOrNull()
                        if (from != null && to != null) XOMove(toIndex = to, fromIndex = from) else null
                    } else {
                        val to = action.payload.toIntOrNull()
                        if (to != null) XOMove(toIndex = to) else null
                    }
                    if (move != null && isHost) {
                        // Host validates and processes client move
                        if (gameState.status is GameStatus.InProgress && gameState.activePlayer == PlayerId.PLAYER_2) {
                            val res = engine.makeMove(move, PlayerId.PLAYER_2)
                            if (res is MoveResult.Success) {
                                gameState = res.newState
                                val syncAction = PlayerAction(name, "XO_SYNC", serializeXOState(res.newState, move))
                                lastSentAction = syncAction
                                onSendToPeer(syncAction)
                            } else {
                                // Reject: re-sync authoritative state
                                val syncAction = PlayerAction(name, "XO_SYNC", serializeXOState(gameState))
                                onSendToPeer(syncAction)
                            }
                        } else {
                            val syncAction = PlayerAction(name, "XO_SYNC", serializeXOState(gameState))
                            onSendToPeer(syncAction)
                        }
                    }
                }
                "XO_SYNC" -> {
                    val synced = deserializeXOState(action.payload)
                    if (synced != null) {
                        engine.syncState(synced)
                        gameState = synced
                    }
                }
            }
        }
    }

    @Test
    fun testScenario1_PhoneAHosts_PhoneBJoins_BothEnterGame() {
        val phoneA = SimulatedPeer("PhoneA", isHost = true, targetScore = 5)
        val phoneB = SimulatedPeer("PhoneB", isHost = false, targetScore = 3) // Phone B default was 3

        // Host launches game with config payload containing targetScore = 5
        val configPayload = JSONObject().apply {
            put("targetScore", 5)
            put("gameId", "xo")
        }.toString()

        val parsedConfig = JSONObject(configPayload)
        val targetScore = parsedConfig.getInt("targetScore")

        phoneA.startGame(targetScore)
        phoneB.startGame(targetScore)

        // Both enter the same game
        assertTrue(phoneA.isPlaying)
        assertTrue(phoneB.isPlaying)
        assertEquals(PlayerId.PLAYER_1, phoneA.localPlayerId)
        assertEquals(PlayerId.PLAYER_2, phoneB.localPlayerId)
        assertEquals(5, phoneA.gameState.targetScore)
        assertEquals(5, phoneB.gameState.targetScore)
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.activePlayer)
        assertEquals(List(9) { null }, phoneA.gameState.board)
        assertEquals(List(9) { null }, phoneB.gameState.board)
    }

    @Test
    fun testScenario2_PhoneAMakesMove_PhoneBSeesIt() {
        val phoneA = SimulatedPeer("PhoneA", isHost = true, targetScore = 3)
        val phoneB = SimulatedPeer("PhoneB", isHost = false, targetScore = 3)

        phoneA.startGame(3)
        phoneB.startGame(3)

        // Phone A makes move to index 0
        var actionSentToB: PlayerAction? = null
        val moveSuccess = phoneA.userMakeMove(XOMove(0)) { action ->
            actionSentToB = action
        }
        assertTrue(moveSuccess)
        assertNotNull(actionSentToB)
        assertEquals("XO_SYNC", actionSentToB?.actionType)

        // Phone B receives the action
        phoneB.receiveAction(actionSentToB!!)

        // Both phones see the identical board
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.board[0])
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.board[0])
        assertEquals(PlayerId.PLAYER_2, phoneA.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_2, phoneB.gameState.activePlayer)
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)
    }

    @Test
    fun testScenario3_PhoneBMakesMove_PhoneASeesIt() {
        val phoneA = SimulatedPeer("PhoneA", isHost = true, targetScore = 3)
        val phoneB = SimulatedPeer("PhoneB", isHost = false, targetScore = 3)

        phoneA.startGame(3)
        phoneB.startGame(3)

        // P1 moves to 0
        phoneA.userMakeMove(XOMove(0)) { action ->
            phoneB.receiveAction(action)
        }

        // Now it's Phone B's turn. Phone B moves to 4
        var moveActionToA: PlayerAction? = null
        val p2MoveSuccess = phoneB.userMakeMove(XOMove(4)) { action ->
            moveActionToA = action
        }
        assertTrue(p2MoveSuccess)
        assertNotNull(moveActionToA)
        assertEquals("XO_MOVE", moveActionToA?.actionType)
        assertEquals("4", moveActionToA?.payload)

        // Phone A receives the move, validates, processes, and sends XO_SYNC back to Phone B
        phoneA.receiveAction(moveActionToA!!) { syncAction ->
            phoneB.receiveAction(syncAction)
        }

        // Both phones have identical board and state
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.board[0])
        assertEquals(PlayerId.PLAYER_2, phoneA.gameState.board[4])
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.activePlayer)
    }

    @Test
    fun testScenario4_AlternatingMoves_And_3MarkMovementRule() {
        val phoneA = SimulatedPeer("PhoneA", isHost = true, targetScore = 3)
        val phoneB = SimulatedPeer("PhoneB", isHost = false, targetScore = 3)

        phoneA.startGame(3)
        phoneB.startGame(3)

        fun hostMove(move: XOMove) {
            phoneA.userMakeMove(move) { action ->
                phoneB.receiveAction(action)
            }
        }

        fun clientMove(move: XOMove) {
            phoneB.userMakeMove(move) { action ->
                phoneA.receiveAction(action) { syncAction ->
                    phoneB.receiveAction(syncAction)
                }
            }
        }

        // Mark 1: P1 to 0, P2 to 3
        hostMove(XOMove(0))
        clientMove(XOMove(3))
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)

        // Mark 2: P1 to 1, P2 to 4
        hostMove(XOMove(1))
        clientMove(XOMove(4))
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)

        // Mark 3: P1 to 6, P2 to 8
        hostMove(XOMove(6))
        clientMove(XOMove(8))
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)

        // Both now have exactly 3 marks on the board!
        assertEquals(3, phoneA.gameState.board.count { it == PlayerId.PLAYER_1 })
        assertEquals(3, phoneA.gameState.board.count { it == PlayerId.PLAYER_2 })

        // P1 cannot place a 4th mark (e.g. to 7)
        val illegalFourth = phoneA.userMakeMove(XOMove(7)) {}
        org.junit.Assert.assertFalse("Placing a 4th mark must be rejected", illegalFourth)

        // P1 moves existing mark from 6 to 2 (completing 0, 1, 2 winning line!)
        hostMove(XOMove(toIndex = 2, fromIndex = 6))

        // Check round win synchronization on both devices
        assertEquals(phoneA.gameState.board, phoneB.gameState.board)
        assertNull(phoneA.gameState.board[6])
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.board[2])
        assertEquals(1, phoneA.gameState.player1Score)
        assertEquals(1, phoneB.gameState.player1Score)
        assertTrue(phoneA.gameState.status is GameStatus.RoundWon)
        assertTrue(phoneB.gameState.status is GameStatus.RoundWon)
        assertEquals((phoneA.gameState.status as GameStatus.RoundWon).winner, (phoneB.gameState.status as GameStatus.RoundWon).winner)
        assertEquals(listOf(0, 1, 2), phoneA.gameState.winningPattern)
        assertEquals(listOf(0, 1, 2), phoneB.gameState.winningPattern)
    }

    @Test
    fun testScenario5_PhoneBHosts_PhoneAJoins_ReverseDirection() {
        // Reverse direction: Phone B is HOST, Phone A is CLIENT!
        val phoneB = SimulatedPeer("PhoneB", isHost = true, targetScore = 3)
        val phoneA = SimulatedPeer("PhoneA", isHost = false, targetScore = 3)

        phoneB.startGame(3)
        phoneA.startGame(3)

        assertEquals(PlayerId.PLAYER_1, phoneB.localPlayerId)
        assertEquals(PlayerId.PLAYER_2, phoneA.localPlayerId)

        fun hostMove(move: XOMove) {
            phoneB.userMakeMove(move) { action ->
                phoneA.receiveAction(action)
            }
        }

        fun clientMove(move: XOMove) {
            phoneA.userMakeMove(move) { action ->
                phoneB.receiveAction(action) { syncAction ->
                    phoneA.receiveAction(syncAction)
                }
            }
        }

        // Phone B (Host / P1) makes a move to 0 -> Phone A sees it
        hostMove(XOMove(0))
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.board[0])
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.board[0])
        assertEquals(PlayerId.PLAYER_2, phoneB.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_2, phoneA.gameState.activePlayer)

        // Phone A (Client / P2) makes a move to 4 -> Phone B validates and sees it
        clientMove(XOMove(4))
        assertEquals(PlayerId.PLAYER_2, phoneB.gameState.board[4])
        assertEquals(PlayerId.PLAYER_2, phoneA.gameState.board[4])
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, phoneA.gameState.activePlayer)

        // Alternating marks
        hostMove(XOMove(1)) // B mark 2
        clientMove(XOMove(5)) // A mark 2
        hostMove(XOMove(6)) // B mark 3 (has 0, 1, 6)
        clientMove(XOMove(7)) // A mark 3 (has 4, 5, 7)

        assertEquals(phoneB.gameState.board, phoneA.gameState.board)
        assertEquals(3, phoneB.gameState.board.count { it == PlayerId.PLAYER_1 })
        assertEquals(3, phoneB.gameState.board.count { it == PlayerId.PLAYER_2 })

        // Phone B moves existing mark from 6 to 2 (completing 0, 1, 2 winning line)
        hostMove(XOMove(toIndex = 2, fromIndex = 6))

        assertEquals(phoneB.gameState.board, phoneA.gameState.board)
        assertNull(phoneB.gameState.board[6])
        assertEquals(PlayerId.PLAYER_1, phoneB.gameState.board[2])
        assertEquals(1, phoneB.gameState.player1Score)
        assertEquals(1, phoneA.gameState.player1Score)
        assertTrue(phoneB.gameState.status is GameStatus.RoundWon)
        assertTrue(phoneA.gameState.status is GameStatus.RoundWon)
    }
}
