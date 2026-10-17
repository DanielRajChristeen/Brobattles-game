package com.example

import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.tugofwar.TugOfWarGameEngine
import com.example.core.engine.tugofwar.TugOfWarMove
import com.example.core.engine.tugofwar.TugOfWarState
import com.example.core.engine.tugofwar.deserializeTugState
import com.example.core.engine.tugofwar.serializeTugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TugOfWarEngineTest {

    @Test
    fun `initial state is centered at 50-50`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        val state = engine.state

        assertEquals(0.0f, state.ropePosition, 0.001f)
        assertEquals(50, state.p1Percentage)
        assertEquals(50, state.p2Percentage)
        assertEquals(100, state.p1Percentage + state.p2Percentage)
        assertTrue(state.status is GameStatus.InProgress)
    }

    @Test
    fun `p1 tap moves rope toward left and increases p1 percentage`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)

        val state = engine.state
        assertTrue("Rope should be negative (towards P1)", state.ropePosition < 0f)
        assertTrue("P1 percentage should be > 50", state.p1Percentage > 50)
        assertEquals(100, state.p1Percentage + state.p2Percentage)
    }

    @Test
    fun `p2 tap moves rope toward right and increases p2 percentage`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_2)

        val state = engine.state
        assertTrue("Rope should be positive (towards P2)", state.ropePosition > 0f)
        assertTrue("P2 percentage should be > 50", state.p2Percentage > 50)
        assertEquals(100, state.p1Percentage + state.p2Percentage)
    }

    @Test
    fun `continuous taps by p1 trigger match win at threshold`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        while (engine.state.status is GameStatus.InProgress) {
            engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)
        }

        val status = engine.state.status
        assertTrue(status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (status as GameStatus.MatchWon).matchWinner)
        assertEquals(1, engine.state.player1Score)
        assertEquals(0, engine.state.player2Score)
    }

    @Test
    fun `friction decays rope position slightly towards center`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        engine.makeMove(TugOfWarMove(force = 5.0f), PlayerId.PLAYER_1)
        val posBefore = engine.state.ropePosition

        engine.applyFriction()
        val posAfter = engine.state.ropePosition
        assertTrue("Friction should pull rope back towards 0", posAfter > posBefore)
    }

    @Test
    fun `serialize and deserialize roundtrip preserves in-progress state`() {
        val original = TugOfWarState(
            ropePosition = -25.5f,
            p1Taps = 15,
            p2Taps = 10,
            p1PullEnergy = 0.8f,
            p2PullEnergy = 0.4f,
            status = GameStatus.InProgress,
            roundNumber = 2,
            player1Score = 1,
            player2Score = 0,
            targetScore = 3
        )

        val json = serializeTugState(original)
        assertNotNull(json)
        assertTrue(json.contains("-25.5"))
        assertTrue(json.contains("IN_PROGRESS"))

        val restored = deserializeTugState(json)
        assertNotNull(restored)
        assertEquals(original.ropePosition, restored!!.ropePosition, 0.001f)
        assertEquals(original.p1Taps, restored.p1Taps)
        assertEquals(original.p2Taps, restored.p2Taps)
        assertEquals(original.player1Score, restored.player1Score)
        assertEquals(original.player2Score, restored.player2Score)
        assertEquals(original.roundNumber, restored.roundNumber)
        assertEquals(original.targetScore, restored.targetScore)
        assertTrue(restored.status is GameStatus.InProgress)
    }

    @Test
    fun `serialize and deserialize roundtrip preserves final match won state and winner`() {
        val original = TugOfWarState(
            ropePosition = -100.0f,
            p1Taps = 58,
            p2Taps = 42,
            p1PullEnergy = 1.0f,
            p2PullEnergy = 0.0f,
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 1,
            player1Score = 1,
            player2Score = 0,
            targetScore = 1
        )

        val json = serializeTugState(original)
        assertNotNull(json)
        assertTrue(json.contains("MATCH_WON"))
        assertTrue(json.contains("PLAYER_1"))

        val restored = deserializeTugState(json)
        assertNotNull(restored)
        assertTrue(restored!!.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (restored.status as GameStatus.MatchWon).matchWinner)
        assertEquals(-100.0f, restored.ropePosition, 0.001f)
        assertEquals(1, restored.player1Score)
        assertEquals(0, restored.player2Score)
    }

    @Test
    fun `host victory scenario - host reaches win threshold, state is broadcast, client syncs and enters MatchWon`() {
        val hostEngine = TugOfWarGameEngine(initialTargetScore = 1)
        val clientEngine = TugOfWarGameEngine(initialTargetScore = 1)

        // Host taps until winning threshold is reached
        while (hostEngine.state.status is GameStatus.InProgress) {
            hostEngine.makeMove(TugOfWarMove(force = 5.0f), PlayerId.PLAYER_1)
        }

        assertTrue("Host must detect MatchWon", hostEngine.state.status is GameStatus.MatchWon)
        val hostWinner = (hostEngine.state.status as GameStatus.MatchWon).matchWinner
        assertEquals(PlayerId.PLAYER_1, hostWinner)

        // Host serializes final state and broadcasts to client
        val finalStateJson = serializeTugState(hostEngine.state)
        val clientReceivedState = deserializeTugState(finalStateJson)
        assertNotNull(clientReceivedState)

        // Client applies authoritative state
        clientEngine.syncState(clientReceivedState!!)

        assertTrue("Client must enter MatchWon", clientEngine.state.status is GameStatus.MatchWon)
        val clientWinner = (clientEngine.state.status as GameStatus.MatchWon).matchWinner
        assertEquals("Host and Client must have identical winner", hostWinner, clientWinner)
        assertEquals("Host and Client must have identical P1 score", hostEngine.state.player1Score, clientEngine.state.player1Score)
        assertEquals("Host and Client must have identical P2 score", hostEngine.state.player2Score, clientEngine.state.player2Score)
        assertEquals("Host and Client must have identical rope position", hostEngine.state.ropePosition, clientEngine.state.ropePosition, 0.001f)
    }

    @Test
    fun `client victory scenario - client makes winning move validated by host, host broadcasts final state, both enter MatchWon`() {
        val hostEngine = TugOfWarGameEngine(initialTargetScore = 1)
        val clientEngine = TugOfWarGameEngine(initialTargetScore = 1)

        // Client taps until host validates win
        while (hostEngine.state.status is GameStatus.InProgress) {
            // Client sends tap request to host; host validates and applies it
            hostEngine.makeMove(TugOfWarMove(force = 5.0f), PlayerId.PLAYER_2)
        }

        assertTrue("Host must detect Client MatchWon", hostEngine.state.status is GameStatus.MatchWon)
        val hostWinner = (hostEngine.state.status as GameStatus.MatchWon).matchWinner
        assertEquals(PlayerId.PLAYER_2, hostWinner)

        // Host broadcasts final state to client
        val finalStateJson = serializeTugState(hostEngine.state)
        val clientReceivedState = deserializeTugState(finalStateJson)
        assertNotNull(clientReceivedState)

        // Client applies authoritative state
        clientEngine.syncState(clientReceivedState!!)

        assertTrue("Client must enter MatchWon", clientEngine.state.status is GameStatus.MatchWon)
        val clientWinner = (clientEngine.state.status as GameStatus.MatchWon).matchWinner
        assertEquals("Host and Client must both declare Player 2 as winner", PlayerId.PLAYER_2, clientWinner)
        assertEquals(0, clientEngine.state.player1Score)
        assertEquals(1, clientEngine.state.player2Score)
        assertEquals(100.0f, clientEngine.state.ropePosition, 0.001f)
    }

    @Test
    fun `gameplay inputs rejected on engine after match won`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        while (engine.state.status is GameStatus.InProgress) {
            engine.makeMove(TugOfWarMove(force = 10.0f), PlayerId.PLAYER_1)
        }
        assertTrue(engine.state.status is GameStatus.MatchWon)
        val posAtWin = engine.state.ropePosition

        // Further moves after match is won must return Invalid and not modify state
        val moveAfterWin = engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_2)
        assertTrue(moveAfterWin is MoveResult.Invalid)
        assertEquals(posAtWin, engine.state.ropePosition, 0.001f)
        assertTrue(engine.state.status is GameStatus.MatchWon)
    }

    @Test
    fun `duplicate and out-of-order in-progress state messages cannot restore match won back to in-progress`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        while (engine.state.status is GameStatus.InProgress) {
            engine.makeMove(TugOfWarMove(force = 10.0f), PlayerId.PLAYER_1)
        }
        assertTrue(engine.state.status is GameStatus.MatchWon)

        // Simulate an out-of-order in-progress state arriving after match ended
        val delayedInProgressState = TugOfWarState(
            ropePosition = -50.0f,
            status = GameStatus.InProgress,
            player1Score = 0,
            player2Score = 0
        )

        // syncState must preserve MatchWon and discard in-progress reversion
        val resultingState = engine.syncState(delayedInProgressState)
        assertTrue("Engine must stay in MatchWon", resultingState.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (resultingState.status as GameStatus.MatchWon).matchWinner)
    }

    @Test
    fun `duplicate match won sync messages are idempotent`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        val wonState = TugOfWarState(
            ropePosition = -100.0f,
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            player1Score = 1,
            player2Score = 0
        )

        engine.syncState(wonState)
        assertTrue(engine.state.status is GameStatus.MatchWon)

        // Apply same wonState again
        val duplicatedState = engine.syncState(wonState)
        assertTrue(duplicatedState.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (duplicatedState.status as GameStatus.MatchWon).matchWinner)
        assertEquals(1, duplicatedState.player1Score)
    }
}
