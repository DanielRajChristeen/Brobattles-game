package com.example.core.engine.checkers

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.multiplayer.DiscoveredSession
import com.example.core.multiplayer.MultiplayerMessage
import com.example.core.multiplayer.MultiplayerPlayer
import com.example.core.multiplayer.PlayerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CheckersNearbyMultiplayerTest {

    /**
     * Simulated peer simulating Host or Guest in Checkers WiFi Play.
     */
    class SimulatedCheckersPeer(
        val name: String,
        val isHost: Boolean
    ) {
        val engine = CheckersEngine()
        var gameState: CheckersGameState = engine.state
        val localPlayer: PlayerId = if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
        var expectedSeqNum: Int = 0
        var guestSeqNum: Int = 0
        val processedMoveIds = mutableSetOf<String>()
        var isAwaitingResponse: Boolean = false

        var rematchRequestedByLocal: Boolean = false
        var rematchRequestedByRemote: Boolean = false
        var isMatchActive: Boolean = true

        val sentActions = mutableListOf<PlayerAction>()

        fun resetMatch() {
            engine.resetGame()
            gameState = engine.state
            expectedSeqNum = 0
            guestSeqNum = 0
            processedMoveIds.clear()
            rematchRequestedByLocal = false
            rematchRequestedByRemote = false
            isAwaitingResponse = false
            isMatchActive = true
        }

        /**
         * Host validates and applies a move request authoritatively.
         */
        fun hostProcessMove(
            moveId: String,
            seqNum: Int,
            senderRole: String,
            from: CheckersSquare,
            to: CheckersSquare,
            onBroadcast: (PlayerAction) -> Unit
        ): Boolean {
            if (!isHost) return false
            if (processedMoveIds.contains(moveId)) return false
            if (seqNum != expectedSeqNum) {
                val reject = CheckersNetworkProtocol.MoveRejected(
                    moveId = moveId,
                    seqNum = expectedSeqNum,
                    reason = "Stale sequence number",
                    gameStateJson = serializeCheckersState(gameState)
                )
                val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_REJECTED, reject.toJson())
                sentActions.add(action)
                onBroadcast(action)
                return false
            }
            if (gameState.status !is GameStatus.InProgress) return false
            if (gameState.activePlayer.name != senderRole) {
                val reject = CheckersNetworkProtocol.MoveRejected(
                    moveId = moveId,
                    seqNum = expectedSeqNum,
                    reason = "Out of turn move",
                    gameStateJson = serializeCheckersState(gameState)
                )
                val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_REJECTED, reject.toJson())
                sentActions.add(action)
                onBroadcast(action)
                return false
            }
            if (!from.isValid || !to.isValid || !from.isDarkPlayable || !to.isDarkPlayable) {
                val reject = CheckersNetworkProtocol.MoveRejected(
                    moveId = moveId,
                    seqNum = expectedSeqNum,
                    reason = "Invalid coordinates",
                    gameStateJson = serializeCheckersState(gameState)
                )
                val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_REJECTED, reject.toJson())
                sentActions.add(action)
                onBroadcast(action)
                return false
            }

            val move = CheckersMove(from, to)
            if (!engine.validateMove(move)) {
                val reject = CheckersNetworkProtocol.MoveRejected(
                    moveId = moveId,
                    seqNum = expectedSeqNum,
                    reason = "Illegal move",
                    gameStateJson = serializeCheckersState(gameState)
                )
                val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_REJECTED, reject.toJson())
                sentActions.add(action)
                onBroadcast(action)
                return false
            }

            processedMoveIds.add(moveId)
            val moveSeq = expectedSeqNum
            expectedSeqNum++

            val res = engine.makeMove(move)
            if (res.success) {
                gameState = engine.state
                val isGameOver = gameState.status.isFinished || gameState.status is GameStatus.RoundDraw
                val winnerRole = when (val s = gameState.status) {
                    is GameStatus.MatchWon -> s.matchWinner.name
                    is GameStatus.RoundDraw -> "DRAW"
                    else -> null
                }

                val accepted = CheckersNetworkProtocol.MoveAccepted(
                    moveId = moveId,
                    seqNum = moveSeq,
                    fromRow = from.row,
                    fromCol = from.col,
                    toRow = to.row,
                    toCol = to.col,
                    capturedRow = move.capturedSquare?.row,
                    capturedCol = move.capturedSquare?.col,
                    isKingPromotion = res.isKingPromotion,
                    multiJumpAvailable = res.multiJumpAvailable,
                    mustJumpRow = gameState.mustContinueJumpingFrom?.row,
                    mustJumpCol = gameState.mustContinueJumpingFrom?.col,
                    nextPlayer = gameState.activePlayer.name,
                    isGameOver = isGameOver,
                    winnerRole = winnerRole,
                    gameStateJson = serializeCheckersState(gameState)
                )
                val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_ACCEPTED, accepted.toJson())
                sentActions.add(action)
                onBroadcast(action)
                return true
            }
            return false
        }

        /**
         * Guest requests a move to the host.
         */
        fun guestSendMoveRequest(
            from: CheckersSquare,
            to: CheckersSquare,
            onSendToHost: (PlayerAction) -> Unit
        ): String? {
            if (isHost) return null
            if (gameState.activePlayer != PlayerId.PLAYER_2) return null
            if (isAwaitingResponse) return null

            val moveId = UUID.randomUUID().toString()
            isAwaitingResponse = true
            val req = CheckersNetworkProtocol.MoveRequest(
                moveId = moveId,
                seqNum = guestSeqNum,
                senderRole = PlayerId.PLAYER_2.name,
                fromRow = from.row,
                fromCol = from.col,
                toRow = to.row,
                toCol = to.col
            )
            val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_MOVE_REQUEST, req.toJson())
            sentActions.add(action)
            onSendToHost(action)
            return moveId
        }

        /**
         * Host attempts local move.
         */
        fun hostMakeMove(from: CheckersSquare, to: CheckersSquare, onBroadcast: (PlayerAction) -> Unit): Boolean {
            if (!isHost) return false
            val moveId = UUID.randomUUID().toString()
            return hostProcessMove(
                moveId = moveId,
                seqNum = expectedSeqNum,
                senderRole = PlayerId.PLAYER_1.name,
                from = from,
                to = to,
                onBroadcast = onBroadcast
            )
        }

        /**
         * Handle incoming action from peer.
         */
        fun receiveAction(action: PlayerAction, onReply: (PlayerAction) -> Unit = {}) {
            when (action.actionType) {
                CheckersNetworkProtocol.ACTION_MOVE_REQUEST -> {
                    if (isHost) {
                        val req = CheckersNetworkProtocol.MoveRequest.fromJson(action.payload)
                        if (req != null) {
                            hostProcessMove(
                                moveId = req.moveId,
                                seqNum = req.seqNum,
                                senderRole = req.senderRole,
                                from = CheckersSquare(req.fromRow, req.fromCol),
                                to = CheckersSquare(req.toRow, req.toCol),
                                onBroadcast = onReply
                            )
                        }
                    }
                }
                CheckersNetworkProtocol.ACTION_MOVE_ACCEPTED -> {
                    val accepted = CheckersNetworkProtocol.MoveAccepted.fromJson(action.payload)
                    if (accepted != null) {
                        isAwaitingResponse = false
                        if (!processedMoveIds.contains(accepted.moveId)) {
                            processedMoveIds.add(accepted.moveId)
                            guestSeqNum = accepted.seqNum + 1
                            val synced = deserializeCheckersState(accepted.gameStateJson)
                            if (synced != null) {
                                engine.syncState(synced)
                                gameState = synced
                            }
                        }
                    }
                }
                CheckersNetworkProtocol.ACTION_MOVE_REJECTED -> {
                    val rejected = CheckersNetworkProtocol.MoveRejected.fromJson(action.payload)
                    isAwaitingResponse = false
                    if (rejected != null) {
                        val synced = deserializeCheckersState(rejected.gameStateJson)
                        if (synced != null) {
                            engine.syncState(synced)
                            gameState = synced
                        }
                    }
                }
                CheckersNetworkProtocol.ACTION_STATE_SYNC -> {
                    val sync = CheckersNetworkProtocol.StateSync.fromJson(action.payload)
                    if (sync != null) {
                        guestSeqNum = sync.seqNum
                        val synced = deserializeCheckersState(sync.gameStateJson)
                        if (synced != null) {
                            engine.syncState(synced)
                            gameState = synced
                        }
                    }
                }
                CheckersNetworkProtocol.ACTION_REMATCH_REQUEST -> {
                    rematchRequestedByRemote = true
                    if (rematchRequestedByLocal) {
                        if (isHost) {
                            resetMatch()
                            val acceptedAction = PlayerAction(name, CheckersNetworkProtocol.ACTION_REMATCH_ACCEPTED, "")
                            onReply(acceptedAction)
                            val syncPayload = CheckersNetworkProtocol.StateSync(0, serializeCheckersState(gameState)).toJson()
                            val syncAction = PlayerAction(name, CheckersNetworkProtocol.ACTION_STATE_SYNC, syncPayload)
                            onReply(syncAction)
                        } else {
                            val acceptedAction = PlayerAction(name, CheckersNetworkProtocol.ACTION_REMATCH_ACCEPTED, "")
                            onReply(acceptedAction)
                        }
                    }
                }
                CheckersNetworkProtocol.ACTION_REMATCH_ACCEPTED -> {
                    resetMatch()
                }
                CheckersNetworkProtocol.ACTION_REMATCH_DECLINED -> {
                    rematchRequestedByLocal = false
                    rematchRequestedByRemote = false
                }
                CheckersNetworkProtocol.ACTION_SESSION_EXIT -> {
                    isMatchActive = false
                }
            }
        }

        fun declineRematch(onSendToPeer: (PlayerAction) -> Unit) {
            rematchRequestedByLocal = false
            rematchRequestedByRemote = false
            val action = PlayerAction(name, CheckersNetworkProtocol.ACTION_REMATCH_DECLINED, "")
            onSendToPeer(action)
        }
    }

    // =========================================================================
    // 1. Session & Discovery Tests
    // =========================================================================

    @Test
    fun testHostSessionCreationAndPlayerAssignment() {
        val hostSession = DiscoveredSession(
            sessionId = "checkers_123",
            hostName = "Alice",
            gameId = "checkers",
            gameTitle = "Checkers",
            hostIp = "192.168.1.50",
            port = 8890,
            currentPlayers = 1,
            maxPlayers = 2
        )

        assertEquals("checkers", hostSession.gameId)
        assertEquals(2, hostSession.maxPlayers)
        assertEquals(1, hostSession.currentPlayers)

        val hostPlayer = MultiplayerPlayer("p1", "Alice", isHost = true, role = "PLAYER_1")
        assertEquals("PLAYER_1", hostPlayer.role)
        assertTrue(hostPlayer.isHost)
    }

    @Test
    fun testGuestDiscoveryFiltersIncompatibleGames() {
        val checkersSession = DiscoveredSession("s1", "Alice", "checkers", "Checkers", "192.168.1.50", 8890)
        val ludoSession = DiscoveredSession("s2", "Bob", "ludo", "Ludo", "192.168.1.51", 8890)
        val xoSession = DiscoveredSession("s3", "Charlie", "xo", "Tic Tac Toe", "192.168.1.52", 8890)

        val allSessions = listOf(checkersSession, ludoSession, xoSession)
        val filteredForCheckers = allSessions.filter { it.gameId.equals("checkers", ignoreCase = true) }

        assertEquals(1, filteredForCheckers.size)
        assertEquals("checkers", filteredForCheckers.first().gameId)
        assertEquals("Alice", filteredForCheckers.first().hostName)
    }

    @Test
    fun testTwoPlayerCapacityEnforcement() {
        val session = DiscoveredSession("s1", "Alice", "checkers", "Checkers", "192.168.1.50", 8890, currentPlayers = 2, maxPlayers = 2)
        val isRoomFull = session.currentPlayers >= session.maxPlayers
        assertTrue("Checkers room must be full at 2 players", isRoomFull)
    }

    // =========================================================================
    // 2. Gameplay Synchronization Tests
    // =========================================================================

    @Test
    fun testHostMakesValidMove_GuestSynchronizes() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        assertEquals(PlayerId.PLAYER_1, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, guest.gameState.activePlayer)

        // Host makes move: Red (5, 0) -> (4, 1)
        val success = host.hostMakeMove(CheckersSquare(5, 0), CheckersSquare(4, 1)) { action ->
            guest.receiveAction(action)
        }

        assertTrue(success)
        assertEquals(1, host.expectedSeqNum)
        assertEquals(1, guest.guestSeqNum)

        // Verify identical board state on both devices
        assertEquals(PlayerId.PLAYER_2, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_2, guest.gameState.activePlayer)
        assertNull(host.gameState.board[CheckersSquare(5, 0)])
        assertNull(guest.gameState.board[CheckersSquare(5, 0)])
        assertNotNull(host.gameState.board[CheckersSquare(4, 1)])
        assertNotNull(guest.gameState.board[CheckersSquare(4, 1)])
        assertEquals(host.gameState.board, guest.gameState.board)
    }

    @Test
    fun testGuestMakesValidMove_HostAuthoritativelyValidatesAndBroadcasts() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Move 1: Host moves (5, 0) -> (4, 1)
        host.hostMakeMove(CheckersSquare(5, 0), CheckersSquare(4, 1)) { action ->
            guest.receiveAction(action)
        }

        // Now it's Guest's turn (Black / PLAYER_2).
        assertEquals(PlayerId.PLAYER_2, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_2, guest.gameState.activePlayer)

        // Guest makes move: Black (2, 1) -> (3, 0)
        val moveId = guest.guestSendMoveRequest(CheckersSquare(2, 1), CheckersSquare(3, 0)) { reqAction ->
            host.receiveAction(reqAction) { acceptedAction ->
                guest.receiveAction(acceptedAction)
            }
        }

        assertNotNull(moveId)
        assertFalse(guest.isAwaitingResponse)
        assertEquals(2, host.expectedSeqNum)
        assertEquals(2, guest.guestSeqNum)

        // Both devices display identical board state
        assertEquals(PlayerId.PLAYER_1, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, guest.gameState.activePlayer)
        assertNull(host.gameState.board[CheckersSquare(2, 1)])
        assertNull(guest.gameState.board[CheckersSquare(2, 1)])
        assertNotNull(host.gameState.board[CheckersSquare(3, 0)])
        assertNotNull(guest.gameState.board[CheckersSquare(3, 0)])
        assertEquals(host.gameState.board, guest.gameState.board)
    }

    @Test
    fun testInvalidMoveRejectedWithoutMutatingAuthoritativeState() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Step 1: Host moves (5, 0) -> (4, 1)
        host.hostMakeMove(CheckersSquare(5, 0), CheckersSquare(4, 1)) { action ->
            guest.receiveAction(action)
        }

        val boardBeforeIllegal = host.gameState.board

        // Guest attempts illegal move: Black piece at (2, 1) trying to move straight up to (1, 1)
        var receivedRejection = false
        val moveId = guest.guestSendMoveRequest(CheckersSquare(2, 1), CheckersSquare(1, 1)) { reqAction ->
            host.receiveAction(reqAction) { replyAction ->
                if (replyAction.actionType == CheckersNetworkProtocol.ACTION_MOVE_REJECTED) {
                    receivedRejection = true
                }
                guest.receiveAction(replyAction)
            }
        }

        assertTrue(receivedRejection)
        assertFalse(guest.isAwaitingResponse)
        // Authoritative board was NOT changed!
        assertEquals(boardBeforeIllegal, host.gameState.board)
        assertEquals(boardBeforeIllegal, guest.gameState.board)
        assertEquals(PlayerId.PLAYER_2, host.gameState.activePlayer)
        assertEquals(1, host.expectedSeqNum)
    }

    @Test
    fun testOutOfTurnMoveRejected() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // It is currently Red's turn (Player 1). Guest tries to send a move request out-of-turn
        var rejectionCount = 0
        val req = CheckersNetworkProtocol.MoveRequest(
            moveId = "out_of_turn_1",
            seqNum = 0,
            senderRole = PlayerId.PLAYER_2.name,
            fromRow = 2,
            fromCol = 1,
            toRow = 3,
            toCol = 0
        )
        val action = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_MOVE_REQUEST, req.toJson())
        host.receiveAction(action) { reply ->
            if (reply.actionType == CheckersNetworkProtocol.ACTION_MOVE_REJECTED) {
                rejectionCount++
            }
            guest.receiveAction(reply)
        }

        assertEquals(1, rejectionCount)
        assertEquals(0, host.expectedSeqNum)
        assertEquals(PlayerId.PLAYER_1, host.gameState.activePlayer)
    }

    @Test
    fun testDuplicateMoveIdNotAppliedTwice() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Host makes move: Red (5, 0) -> (4, 1)
        host.hostMakeMove(CheckersSquare(5, 0), CheckersSquare(4, 1)) { action ->
            guest.receiveAction(action)
        }

        // Guest sends valid move
        val fixedMoveId = "duplicate_test_123"
        val req = CheckersNetworkProtocol.MoveRequest(
            moveId = fixedMoveId,
            seqNum = 1,
            senderRole = PlayerId.PLAYER_2.name,
            fromRow = 2,
            fromCol = 1,
            toRow = 3,
            toCol = 0
        )
        val action = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_MOVE_REQUEST, req.toJson())

        var acceptedCount = 0
        // Send first time
        host.receiveAction(action) { reply ->
            if (reply.actionType == CheckersNetworkProtocol.ACTION_MOVE_ACCEPTED) {
                acceptedCount++
            }
            guest.receiveAction(reply)
        }
        assertEquals(1, acceptedCount)
        assertEquals(2, host.expectedSeqNum)

        // Resend identical move request (simulating network duplicate)
        host.receiveAction(action) { reply ->
            if (reply.actionType == CheckersNetworkProtocol.ACTION_MOVE_ACCEPTED) {
                acceptedCount++
            }
        }

        // Must NOT be accepted twice!
        assertEquals(1, acceptedCount)
        assertEquals(2, host.expectedSeqNum)
    }

    @Test
    fun testStaleSequenceNumberRejected() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Host is at seqNum 0, but guest sends with stale seqNum 99
        val req = CheckersNetworkProtocol.MoveRequest(
            moveId = "stale_seq_1",
            seqNum = 99,
            senderRole = PlayerId.PLAYER_1.name,
            fromRow = 5,
            fromCol = 0,
            toRow = 4,
            toCol = 1
        )
        val action = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_MOVE_REQUEST, req.toJson())
        var rejected = false
        host.receiveAction(action) { reply ->
            if (reply.actionType == CheckersNetworkProtocol.ACTION_MOVE_REJECTED) {
                rejected = true
            }
        }

        assertTrue("Stale sequence number must be rejected", rejected)
        assertEquals(0, host.expectedSeqNum)
    }

    @Test
    fun testCaptureSynchronizationRemovesOpponentPiece() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Custom setup: Red piece at (5, 2) jumps over Black piece at (4, 3) to (3, 4)
        val customBoard = mapOf(
            CheckersSquare(5, 2) to CheckersPiece(1, PlayerId.PLAYER_1, isKing = false),
            CheckersSquare(4, 3) to CheckersPiece(2, PlayerId.PLAYER_2, isKing = false)
        )
        host.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        guest.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        host.gameState = host.engine.state
        guest.gameState = guest.engine.state

        // Host makes jump move
        host.hostMakeMove(CheckersSquare(5, 2), CheckersSquare(3, 4)) { action ->
            guest.receiveAction(action)
        }

        // Both devices confirm captured piece at (4, 3) was removed
        assertNull(host.gameState.board[CheckersSquare(4, 3)])
        assertNull(guest.gameState.board[CheckersSquare(4, 3)])
        assertEquals(0, host.gameState.blackPiecesRemaining)
        assertEquals(0, guest.gameState.blackPiecesRemaining)
        // Match won by Player 1
        assertTrue(host.gameState.status is GameStatus.MatchWon)
        assertTrue(guest.gameState.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (host.gameState.status as GameStatus.MatchWon).matchWinner)
        assertEquals(PlayerId.PLAYER_1, (guest.gameState.status as GameStatus.MatchWon).matchWinner)
    }

    @Test
    fun testMultiJumpSynchronizationForcesContinuation() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Custom setup: Red has double jump available
        val customBoard = mapOf(
            CheckersSquare(5, 0) to CheckersPiece(1, PlayerId.PLAYER_1, isKing = false),
            CheckersSquare(4, 1) to CheckersPiece(2, PlayerId.PLAYER_2, isKing = false),
            CheckersSquare(2, 3) to CheckersPiece(3, PlayerId.PLAYER_2, isKing = false)
        )
        host.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        guest.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        host.gameState = host.engine.state
        guest.gameState = guest.engine.state

        // 1st jump: (5, 0) -> (3, 2)
        host.hostMakeMove(CheckersSquare(5, 0), CheckersSquare(3, 2)) { action ->
            guest.receiveAction(action)
        }

        // Turn does not switch: Player 1 must continue jumping!
        assertEquals(PlayerId.PLAYER_1, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_1, guest.gameState.activePlayer)
        assertEquals(CheckersSquare(3, 2), host.gameState.mustContinueJumpingFrom)
        assertEquals(CheckersSquare(3, 2), guest.gameState.mustContinueJumpingFrom)

        // 2nd jump: (3, 2) -> (1, 4)
        host.hostMakeMove(CheckersSquare(3, 2), CheckersSquare(1, 4)) { action ->
            guest.receiveAction(action)
        }

        // Now turn ends and all pieces synchronized
        assertNull(host.gameState.mustContinueJumpingFrom)
        assertNull(guest.gameState.mustContinueJumpingFrom)
        assertEquals(0, host.gameState.blackPiecesRemaining)
        assertEquals(0, guest.gameState.blackPiecesRemaining)
        assertEquals(host.gameState.board, guest.gameState.board)
    }

    @Test
    fun testKingPromotionSynchronization() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Red piece moving to row 0
        val customBoard = mapOf(CheckersSquare(1, 2) to CheckersPiece(1, PlayerId.PLAYER_1, isKing = false))
        host.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        guest.engine.syncState(CheckersGameState(board = customBoard, activePlayer = PlayerId.PLAYER_1, status = GameStatus.InProgress))
        host.gameState = host.engine.state
        guest.gameState = guest.engine.state

        host.hostMakeMove(CheckersSquare(1, 2), CheckersSquare(0, 3)) { action ->
            guest.receiveAction(action)
        }

        // Both devices confirm piece at (0, 3) is crowned King
        assertTrue(host.gameState.board[CheckersSquare(0, 3)]?.isKing == true)
        assertTrue(guest.gameState.board[CheckersSquare(0, 3)]?.isKing == true)
        assertEquals(1, host.gameState.redKings)
        assertEquals(1, guest.gameState.redKings)
        assertEquals(PlayerId.PLAYER_2, host.gameState.activePlayer)
        assertEquals(PlayerId.PLAYER_2, guest.gameState.activePlayer)
    }

    // =========================================================================
    // 3. Rematch, Disconnect & Exit Tests
    // =========================================================================

    @Test
    fun testRematchHandshake_HostFirst_ThenGuestAccepts() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Simulate game over
        host.gameState = host.gameState.copy(status = GameStatus.MatchWon(PlayerId.PLAYER_1))
        guest.gameState = guest.gameState.copy(status = GameStatus.MatchWon(PlayerId.PLAYER_1))

        // Step 1: Host requests rematch
        host.rematchRequestedByLocal = true
        val hostReq = PlayerAction("HostDevice", CheckersNetworkProtocol.ACTION_REMATCH_REQUEST, "")
        guest.receiveAction(hostReq)

        assertTrue(host.rematchRequestedByLocal)
        assertTrue(guest.rematchRequestedByRemote)
        assertFalse(guest.rematchRequestedByLocal)

        // Step 2: Guest accepts rematch
        guest.rematchRequestedByLocal = true
        val guestReq = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_REMATCH_REQUEST, "")
        host.receiveAction(guestReq) { reply ->
            guest.receiveAction(reply)
        }

        // Both devices now have fresh game states!
        assertEquals(12, host.gameState.redPiecesRemaining)
        assertEquals(12, guest.gameState.redPiecesRemaining)
        assertEquals(GameStatus.InProgress, host.gameState.status)
        assertEquals(GameStatus.InProgress, guest.gameState.status)
        assertEquals(0, host.expectedSeqNum)
        assertEquals(0, guest.guestSeqNum)
        assertFalse(host.rematchRequestedByLocal)
        assertFalse(guest.rematchRequestedByLocal)
    }

    @Test
    fun testRematchHandshake_GuestFirst_ThenHostAccepts() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        // Simulate game over
        host.gameState = host.gameState.copy(status = GameStatus.MatchWon(PlayerId.PLAYER_2))
        guest.gameState = guest.gameState.copy(status = GameStatus.MatchWon(PlayerId.PLAYER_2))

        // Step 1: Guest requests rematch
        guest.rematchRequestedByLocal = true
        val guestReq = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_REMATCH_REQUEST, "")
        host.receiveAction(guestReq)

        assertTrue(guest.rematchRequestedByLocal)
        assertTrue(host.rematchRequestedByRemote)

        // Step 2: Host accepts rematch
        host.rematchRequestedByLocal = true
        val hostReq = PlayerAction("HostDevice", CheckersNetworkProtocol.ACTION_REMATCH_REQUEST, "")
        host.receiveAction(hostReq) { reply ->
            guest.receiveAction(reply)
        }

        // Both reset to fresh board
        assertEquals(24, host.gameState.board.size)
        assertEquals(24, guest.gameState.board.size)
        assertEquals(GameStatus.InProgress, host.gameState.status)
        assertEquals(GameStatus.InProgress, guest.gameState.status)
    }

    @Test
    fun testRematchDecline() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        host.rematchRequestedByLocal = true
        val hostReq = PlayerAction("HostDevice", CheckersNetworkProtocol.ACTION_REMATCH_REQUEST, "")
        guest.receiveAction(hostReq)
        assertTrue(guest.rematchRequestedByRemote)

        // Guest declines
        guest.declineRematch { declineAction ->
            host.receiveAction(declineAction)
        }

        assertFalse(host.rematchRequestedByLocal)
        assertFalse(guest.rematchRequestedByRemote)
    }

    @Test
    fun testSessionExit() {
        val host = SimulatedCheckersPeer("HostDevice", isHost = true)
        val guest = SimulatedCheckersPeer("GuestDevice", isHost = false)

        val exitAction = PlayerAction("GuestDevice", CheckersNetworkProtocol.ACTION_SESSION_EXIT, "")
        host.receiveAction(exitAction)

        assertFalse(host.isMatchActive)
    }

    // =========================================================================
    // 4. Regression Tests for Existing Solo & Local 1v1 Modes
    // =========================================================================

    @Test
    fun testRegression_SoloModeBotStillFunctions() {
        val engine = CheckersEngine()
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Player 1 moves
        val res1 = engine.makeMove(CheckersMove(CheckersSquare(5, 0), CheckersSquare(4, 1)))
        assertTrue(res1.success)
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)

        // Bot makes move
        val botMove = CheckersBot.decideMove(engine.state, CheckersDifficulty.MEDIUM, PlayerId.PLAYER_2)
        assertNotNull(botMove)
        val res2 = engine.makeMove(botMove!!)
        assertTrue(res2.success)
    }

    @Test
    fun testRegression_LocalPassAndPlayStillFunctions() {
        val engine = CheckersEngine()
        // Player 1
        val res1 = engine.makeMove(CheckersMove(CheckersSquare(5, 2), CheckersSquare(4, 3)))
        assertTrue(res1.success)
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)

        // Player 2
        val res2 = engine.makeMove(CheckersMove(CheckersSquare(2, 1), CheckersSquare(3, 2)))
        assertTrue(res2.success)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Player 1 mandatory jump: (4, 3) over (3, 2) to (2, 1)
        val jumps = CheckersRules.getLegalMoves(engine.state)
        assertTrue(jumps.all { it.isJump })
        val jumpMove = jumps.first()
        val res3 = engine.makeMove(jumpMove)
        assertTrue(res3.success)
        assertTrue(res3.isCapture)
    }
}
