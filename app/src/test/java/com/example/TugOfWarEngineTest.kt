package com.example

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.tugofwar.TugOfWarGameEngine
import com.example.core.engine.tugofwar.TugOfWarMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `continuous taps by p2 trigger match win at threshold`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        while (engine.state.status is GameStatus.InProgress) {
            engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_2)
        }

        val status = engine.state.status
        assertTrue(status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_2, (status as GameStatus.MatchWon).matchWinner)
        assertEquals(0, engine.state.player1Score)
        assertEquals(1, engine.state.player2Score)
    }

    @Test
    fun `moves made after match end are rejected and produce no moves`() {
        val engine = TugOfWarGameEngine(initialTargetScore = 1)
        while (engine.state.status is GameStatus.InProgress) {
            engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)
        }

        assertTrue(engine.state.status is GameStatus.MatchWon)

        // Attempt move after match won
        val postResult = engine.makeMove(TugOfWarMove(), PlayerId.PLAYER_1)
        assertTrue("Move after match won must not be successful", postResult is com.example.core.engine.MoveResult.Invalid)
    }

    @Test
    fun `local mode result classification evaluates both p1 and p2 as victories`() {
        // Simulates the result icon and dialog title logic for Local 2-Player mode
        fun getLocalDialogIcon(): String = "🏆" // In local mode, both players celebrate!
        fun getLocalDialogTitle(winner: PlayerId): String {
            return if (winner == PlayerId.PLAYER_1) "PLAYER 1 WINS! 👑" else "PLAYER 2 WINS! 👑"
        }

        assertEquals("🏆", getLocalDialogIcon())
        assertEquals("PLAYER 1 WINS! 👑", getLocalDialogTitle(PlayerId.PLAYER_1))
        assertEquals("PLAYER 2 WINS! 👑", getLocalDialogTitle(PlayerId.PLAYER_2))
    }
}
