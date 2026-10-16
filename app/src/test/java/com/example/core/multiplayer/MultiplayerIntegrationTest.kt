package com.example.core.multiplayer

import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.deserializeXOState
import com.example.core.engine.xo.serializeXOState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiplayerIntegrationTest {

    @Test
    fun testXOStateSerializationAndDeserialization() {
        val engine = XOGameEngine(initialTargetScore = 3)
        engine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(1), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(3), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(6), PlayerId.PLAYER_1) // Win for P1

        val state = engine.state
        assertTrue(state.status is GameStatus.RoundWon)

        val json = serializeXOState(state)
        val deserialized = deserializeXOState(json)

        assertNotNull(deserialized)
        assertEquals(state.board, deserialized?.board)
        assertEquals(state.player1Score, deserialized?.player1Score)
        assertEquals(state.player2Score, deserialized?.player2Score)
        assertEquals(state.roundNumber, deserialized?.roundNumber)
        assertEquals(state.targetScore, deserialized?.targetScore)
        assertEquals(state.activePlayer, deserialized?.activePlayer)
        assertTrue(deserialized?.status is GameStatus.RoundWon)
    }

    @Test
    fun testBidirectionalMoveFlowAndAuthoritativeSync() {
        // Phone A (Host, PLAYER_1) and Phone B (Client, PLAYER_2)
        val hostEngine = XOGameEngine(initialTargetScore = 5)
        val clientEngine = XOGameEngine(initialTargetScore = 5)

        // 1. Phone A makes move to cell 0
        val p1Move = XOMove(toIndex = 0)
        val hostMoveRes = hostEngine.makeMove(p1Move, PlayerId.PLAYER_1)
        assertTrue(hostMoveRes is MoveResult.Success)

        // Host broadcasts XO_SYNC
        val syncPayload1 = serializeXOState(hostEngine.state, p1Move)
        val clientSynced1 = deserializeXOState(syncPayload1)
        assertNotNull(clientSynced1)
        clientEngine.syncState(clientSynced1!!)

        assertEquals(hostEngine.state.board, clientEngine.state.board)
        assertEquals(PlayerId.PLAYER_2, clientEngine.state.activePlayer)

        // 2. Phone B (Client) makes move to cell 4
        val p2Move = XOMove(toIndex = 4)
        assertTrue(clientEngine.validateMove(p2Move, PlayerId.PLAYER_2))

        // Client sends move to Host; Host executes authoritative validation
        val hostProcessP2 = hostEngine.makeMove(p2Move, PlayerId.PLAYER_2)
        assertTrue(hostProcessP2 is MoveResult.Success)

        // Host broadcasts XO_SYNC back to Client
        val syncPayload2 = serializeXOState(hostEngine.state, p2Move)
        val clientSynced2 = deserializeXOState(syncPayload2)
        assertNotNull(clientSynced2)
        clientEngine.syncState(clientSynced2!!)

        assertEquals(hostEngine.state.board, clientEngine.state.board)
        assertEquals(PlayerId.PLAYER_1, clientEngine.state.activePlayer)
    }

    @Test
    fun testThreeMarkMovementPhaseSynchronization() {
        val hostEngine = XOGameEngine(initialTargetScore = 3)
        val clientEngine = XOGameEngine(initialTargetScore = 3)

        // P1: 0, P2: 3
        hostEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        clientEngine.syncState(hostEngine.state)

        hostEngine.makeMove(XOMove(3), PlayerId.PLAYER_2)
        clientEngine.syncState(hostEngine.state)

        // P1: 1, P2: 4
        hostEngine.makeMove(XOMove(1), PlayerId.PLAYER_1)
        clientEngine.syncState(hostEngine.state)

        hostEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        clientEngine.syncState(hostEngine.state)

        // P1: 8, P2: 6
        hostEngine.makeMove(XOMove(8), PlayerId.PLAYER_1)
        clientEngine.syncState(hostEngine.state)

        hostEngine.makeMove(XOMove(6), PlayerId.PLAYER_2)
        clientEngine.syncState(hostEngine.state)

        // Board:
        // P1 at: 0, 1, 8
        // P2 at: 3, 4, 6
        // Neither has 3 in a row. Both have 3 marks.
        // It's P1's turn (activePlayer == PLAYER_1).
        // P1 must relocate an existing mark: e.g. from index 8 to index 2 (making 0, 1, 2 = win!)
        val moveReposition = XOMove(toIndex = 2, fromIndex = 8)
        val res = hostEngine.makeMove(moveReposition, PlayerId.PLAYER_1)
        assertTrue(res is MoveResult.Success)

        // Host syncs state to client
        val syncPayload = serializeXOState(hostEngine.state, moveReposition)
        val clientSynced = deserializeXOState(syncPayload)
        assertNotNull(clientSynced)
        clientEngine.syncState(clientSynced!!)

        assertEquals(null, clientEngine.state.board[8])
        assertEquals(PlayerId.PLAYER_1, clientEngine.state.board[2])
        assertEquals(hostEngine.state.board, clientEngine.state.board)
        assertTrue(clientEngine.state.status is GameStatus.RoundWon)
    }
}
