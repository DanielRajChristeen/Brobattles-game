package com.example.core.engine.xo

import com.example.core.bot.BotDifficulty
import com.example.core.bot.XOBotOpponent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XOGameEngineTest {

    @Test
    fun testOnly3MarksAllowedAndMovementRequired() {
        val engine = XOGameEngine(initialTargetScore = 3)
        engine.initializeGame(3)

        // P1 places mark 0
        assertTrue(engine.makeMove(XOMove(0), PlayerId.PLAYER_1) is MoveResult.Success)
        // P2 places mark 3
        assertTrue(engine.makeMove(XOMove(3), PlayerId.PLAYER_2) is MoveResult.Success)

        // P1 places mark 1
        assertTrue(engine.makeMove(XOMove(1), PlayerId.PLAYER_1) is MoveResult.Success)
        // P2 places mark 4
        assertTrue(engine.makeMove(XOMove(4), PlayerId.PLAYER_2) is MoveResult.Success)

        // P1 places mark 6 (P1 now has 3 marks: 0, 1, 6)
        assertTrue(engine.makeMove(XOMove(6), PlayerId.PLAYER_1) is MoveResult.Success)
        // P2 places mark 8 (P2 now has 3 marks: 3, 4, 8)
        assertTrue(engine.makeMove(XOMove(8), PlayerId.PLAYER_2) is MoveResult.Success)

        // P1 attempts to place a 4th mark at 7 without moving
        val fourthPlace = engine.makeMove(XOMove(7), PlayerId.PLAYER_1)
        assertTrue("Placing 4th mark must be invalid", fourthPlace is MoveResult.Invalid)

        // P1 attempts to move an opponent's mark (e.g. from 3 to 7)
        val moveOpponent = engine.makeMove(XOMove(toIndex = 7, fromIndex = 3), PlayerId.PLAYER_1)
        assertTrue("Moving opponent's mark must be invalid", moveOpponent is MoveResult.Invalid)

        // P1 attempts to move onto an occupied square (e.g. from 6 to 4)
        val moveOntoOccupied = engine.makeMove(XOMove(toIndex = 4, fromIndex = 6), PlayerId.PLAYER_1)
        assertTrue("Moving onto occupied square must be invalid", moveOntoOccupied is MoveResult.Invalid)

        // P1 moves own mark from 6 to 2 (completing 0, 1, 2 winning line!)
        val validMove = engine.makeMove(XOMove(toIndex = 2, fromIndex = 6), PlayerId.PLAYER_1)
        assertTrue("Moving own mark to empty square must succeed", validMove is MoveResult.Success)

        // Check winning state
        val state = engine.state
        assertEquals(null, state.board[6]) // Vacated square is empty
        assertEquals(PlayerId.PLAYER_1, state.board[2]) // New square is occupied by P1
        assertEquals(3, state.board.count { it == PlayerId.PLAYER_1 }) // Exactly 3 marks
        assertEquals(1, state.player1Score)
        assertTrue("Round 1 should be won, but match should not end early", state.status is GameStatus.RoundWon)
    }

    @Test
    fun testTargetEqualsTotalNumberOfRounds() {
        val engine = XOGameEngine(initialTargetScore = 3)
        engine.initializeGame(3)

        // Round 1: P1 wins quickly
        engine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(3), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(1), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        val r1Win = engine.makeMove(XOMove(2), PlayerId.PLAYER_1)
        assertTrue(r1Win is MoveResult.Success)
        assertEquals(1, engine.state.player1Score)
        assertEquals(0, engine.state.player2Score)
        assertEquals(1, engine.state.roundNumber)
        // Must NOT end match after 1 round when target is 3
        assertTrue(engine.state.status is GameStatus.RoundWon)

        // Round 2: P1 wins again (Score: 2 - 0). Target is 3 rounds.
        engine.startRound()
        assertEquals(2, engine.state.roundNumber)
        // Round 2 alternates to Player 2 starting
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)
        engine.makeMove(XOMove(3), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(1), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(6), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(2), PlayerId.PLAYER_1) // P1 wins round 2
        assertEquals(2, engine.state.player1Score)
        // Must NOT end match early even though P1 has 2 wins! Match must continue for all 3 rounds.
        assertTrue("Match must continue until exactly 3 rounds completed", engine.state.status is GameStatus.RoundWon)

        // Round 3: P2 wins round 3 (Score: 2 - 1)
        engine.startRound()
        assertEquals(3, engine.state.roundNumber)
        engine.makeMove(XOMove(6), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(3), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(7), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        engine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        engine.makeMove(XOMove(5), PlayerId.PLAYER_2) // P2 wins round 3 (3, 4, 5)

        // Now round 3 of 3 is complete. Match must end with P1 as match winner (2 wins > 1 win)
        val finalStatus = engine.state.status
        assertTrue("Match must finish after exactly 3 rounds", finalStatus is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (finalStatus as GameStatus.MatchWon).matchWinner)
        assertEquals(2, engine.state.player1Score)
        assertEquals(1, engine.state.player2Score)
    }

    @Test
    fun testBotDifficultiesDecideMoves() = runBlocking {
        for (difficulty in listOf(BotDifficulty.EASY, BotDifficulty.MEDIUM, BotDifficulty.HARD)) {
            val bot = XOBotOpponent(difficulty)
            val engine = XOGameEngine(5)
            engine.initializeGame(5)

            // Test placement move
            val move1 = bot.decideMove(engine.state, PlayerId.PLAYER_2)
            assertNotNull("Bot should decide a placement move for $difficulty", move1)
            assertTrue(move1!!.fromIndex == null)

            // Setup movement phase
            val boardWith3MarksEach = listOf(
                PlayerId.PLAYER_1, PlayerId.PLAYER_1, null,
                PlayerId.PLAYER_2, PlayerId.PLAYER_2, null,
                PlayerId.PLAYER_1, PlayerId.PLAYER_2, null
            )
            val movementState = XOState(
                board = boardWith3MarksEach,
                activePlayer = PlayerId.PLAYER_2,
                status = GameStatus.InProgress,
                roundNumber = 1,
                targetScore = 5
            )

            val move2 = bot.decideMove(movementState, PlayerId.PLAYER_2)
            assertNotNull("Bot should decide a movement move for $difficulty", move2)
            assertNotNull("In movement phase, bot fromIndex must be set", move2!!.fromIndex)
            assertTrue("fromIndex must belong to bot", boardWith3MarksEach[move2.fromIndex!!] == PlayerId.PLAYER_2)
            assertTrue("toIndex must be empty", boardWith3MarksEach[move2.toIndex] == null)
        }
    }

    @Test
    fun testTicTacToeTargetScoreOptionsOnly3_5_10() {
        val xoDef = com.example.core.registry.GameRegistry.XO
        assertEquals(listOf(3, 5, 10), xoDef.targetScoreOptions)
        assertEquals(5, xoDef.defaultTargetScore)
    }

    @Test
    fun testTicTacToe2PlayerModeHistoryTaggingLogic() {
        val recordP1 = com.example.core.data.model.MatchRecord(
            gameId = "xo",
            gameTitle = "Tic Tac Toe",
            mode = "LOCAL_1V1",
            player1Score = 3,
            player2Score = 1,
            winner = "PLAYER_1",
            durationSeconds = 60
        )
        val isXo = recordP1.gameId.equals("xo", ignoreCase = true)
        val isLocal2Player = recordP1.mode.equals("LOCAL_1V1", ignoreCase = true)
        val isP1Winner = recordP1.winner.equals("PLAYER_1", ignoreCase = true)
        val pillTextP1 = when {
            recordP1.winner == "DRAW" -> "TIE 🤝"
            isXo && isLocal2Player -> if (isP1Winner) "Player 1 wins" else "Player 2 wins"
            isP1Winner -> "YOU WON 🎉"
            else -> "OOF 😵"
        }
        assertEquals("Player 1 wins", pillTextP1)

        val recordP2 = recordP1.copy(winner = "PLAYER_2", player1Score = 1, player2Score = 3)
        val pillTextP2 = when {
            recordP2.winner == "DRAW" -> "TIE 🤝"
            isXo && isLocal2Player -> if (recordP2.winner.equals("PLAYER_1", ignoreCase = true)) "Player 1 wins" else "Player 2 wins"
            recordP2.winner == "PLAYER_1" -> "YOU WON 🎉"
            else -> "OOF 😵"
        }
        assertEquals("Player 2 wins", pillTextP2)
    }
}
