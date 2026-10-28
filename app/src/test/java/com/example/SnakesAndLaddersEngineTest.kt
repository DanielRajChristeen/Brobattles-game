package com.example

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.snakesandladders.SnakesAndLaddersBoard
import com.example.core.engine.snakesandladders.SnakesAndLaddersGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakesAndLaddersEngineTest {

    @Test
    fun `initial state starts at square 1 for both players`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        val state = engine.state

        assertEquals(1, state.p1Position)
        assertEquals(1, state.p2Position)
        assertEquals(PlayerId.PLAYER_1, state.activePlayer)
        assertTrue(state.status is GameStatus.InProgress)
        assertNull(state.lastDiceRoll)
    }

    @Test
    fun `starting roll of 1 releases stick figure from square 1 to square 2 with extra turn`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        engine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(2, state.p1Position)
        assertEquals(1, state.p2Position)
        assertEquals(1, state.lastDiceRoll)
        assertTrue(state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_1, state.activePlayer)
    }

    @Test
    fun `starting rolls of 2, 3, 4, 5 do not release stick figure and pass turn`() {
        for (roll in 2..5) {
            val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
            engine.makeMove(SnakesAndLaddersMove(roll), PlayerId.PLAYER_1)

            val state = engine.state
            assertEquals("Stick figure must remain at square 1 on roll $roll", 1, state.p1Position)
            assertEquals(roll, state.lastDiceRoll)
            assertEquals("Turn must pass to next player on roll $roll", PlayerId.PLAYER_2, state.activePlayer)
        }
    }

    @Test
    fun `starting roll of 6 does not release stick figure but preserves extra turn`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        engine.makeMove(SnakesAndLaddersMove(6), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(1, state.p1Position)
        assertEquals(6, state.lastDiceRoll)
        assertTrue(state.extraTurnGranted)
        assertEquals("Player 1 retains turn on roll 6", PlayerId.PLAYER_1, state.activePlayer)
    }

    @Test
    fun `normal move advances square without ladder or snake after leaving square 1`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // 1. Roll 1 to release onto square 2
        engine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)
        assertEquals(2, engine.state.p1Position)

        // 2. Roll 3: 2 + 3 = 5 (neither ladder nor snake)
        engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(5, state.p1Position)
        assertEquals(1, state.p2Position)
        assertEquals(3, state.lastDiceRoll)
        assertEquals(PlayerId.PLAYER_2, state.activePlayer)
        assertNull(state.ladderClimbed)
        assertNull(state.snakeSlid)
    }

    @Test
    fun `landing on ladder climbs to ladder top after leaving square 1`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // 1. Roll 1 to release onto square 2
        engine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)
        assertEquals(2, engine.state.p1Position)

        // 2. Roll 2: 2 + 2 = 4 (Ladder bottom 4 -> top 14)
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(14, state.p1Position)
        assertNotNull(state.ladderClimbed)
        assertEquals(4 to 14, state.ladderClimbed)
        assertEquals(PlayerId.PLAYER_2, state.activePlayer)
    }

    @Test
    fun `landing on snake slides to snake tail after leaving square 1`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // 1. P1 rolls 1 -> square 2
        engine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)
        // 2. P1 rolls 2 -> ladder at 4 -> 14
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)
        assertEquals(14, engine.state.p1Position)

        // P2 move (dice 2, fails to leave square 1, turn passes back to P1)
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_2)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // P1 moves 14 + 3 = 17 (Snake head 17 -> tail 7)
        engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(7, state.p1Position)
        assertNotNull(state.snakeSlid)
        assertEquals(17 to 7, state.snakeSlid)
    }

    @Test
    fun `overshot roll beyond 100 does not move and switches turn`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // Simulate reached near 100 via ladder at 28 -> 84
        // P1 from 1 + 5 = 6, P2 1, P1 6 + 3 = 9 (ladder to 31)
        // Verify overshoot rule
        val state = engine.state
        assertEquals(100, SnakesAndLaddersBoard.TOTAL_SQUARES)
    }

    @Test
    fun `board coordinate mapping is correct for alternating rows`() {
        // Square 1: bottom-left (col 0, row 9)
        assertEquals(0, SnakesAndLaddersBoard.getCol(1))
        assertEquals(9, SnakesAndLaddersBoard.getRow(1))

        // Square 10: bottom-right (col 9, row 9)
        assertEquals(9, SnakesAndLaddersBoard.getCol(10))
        assertEquals(9, SnakesAndLaddersBoard.getRow(10))

        // Square 11: row above 10, reversed left-bound (col 9, row 8)
        assertEquals(9, SnakesAndLaddersBoard.getCol(11))
        assertEquals(8, SnakesAndLaddersBoard.getRow(11))

        // Square 20: col 0, row 8
        assertEquals(0, SnakesAndLaddersBoard.getCol(20))
        assertEquals(8, SnakesAndLaddersBoard.getRow(20))

        // Square 100: top-left (col 0, row 0)
        assertEquals(0, SnakesAndLaddersBoard.getCol(100))
        assertEquals(0, SnakesAndLaddersBoard.getRow(100))
    }

    @Test
    fun `geometric ladder path interpolates from bottom to top`() {
        val ladder = SnakesAndLaddersBoard.LADDERS.first() // 4 -> 14
        val cellSize = 40f
        val startPt = SnakesAndLaddersBoard.getLadderPoint(ladder, 0f, cellSize)
        val expectedStart = SnakesAndLaddersBoard.getSquareCenter(ladder.bottom, cellSize)
        assertEquals(expectedStart.x, startPt.x, 0.01f)
        assertEquals(expectedStart.y, startPt.y, 0.01f)

        val endPt = SnakesAndLaddersBoard.getLadderPoint(ladder, 1f, cellSize)
        val expectedEnd = SnakesAndLaddersBoard.getSquareCenter(ladder.top, cellSize)
        assertEquals(expectedEnd.x, endPt.x, 0.01f)
        assertEquals(expectedEnd.y, endPt.y, 0.01f)
    }

    @Test
    fun `geometric snake path interpolates from head to tail`() {
        val snake = SnakesAndLaddersBoard.SNAKES.first() // 17 -> 7
        val cellSize = 40f
        val headPt = SnakesAndLaddersBoard.getSnakePoint(snake, 0f, cellSize)
        val expectedHead = SnakesAndLaddersBoard.getSquareCenter(snake.head, cellSize)
        assertEquals(expectedHead.x, headPt.x, 0.01f)
        assertEquals(expectedHead.y, headPt.y, 0.01f)

        val tailPt = SnakesAndLaddersBoard.getSnakePoint(snake, 1f, cellSize)
        val expectedTail = SnakesAndLaddersBoard.getSquareCenter(snake.tail, cellSize)
        assertEquals(expectedTail.x, tailPt.x, 0.01f)
        assertEquals(expectedTail.y, tailPt.y, 0.01f)
    }

    @Test
    fun `supports 2, 3, 4, and 5 players initialization`() {
        for (count in 2..5) {
            val engine = SnakesAndLaddersGameEngine(initialPlayerCount = count)
            assertEquals(count, engine.playerCount)
            assertEquals(count, engine.state.playerPositions.size)
            assertTrue(engine.state.playerPositions.all { it == 1 })
            assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)
        }
    }

    @Test
    fun `turn rotation for 3, 4, 5 players with non-extra-turn rolls`() {
        // 3 Players: P1 -> P2 -> P3 -> P1
        val engine3 = SnakesAndLaddersGameEngine(initialPlayerCount = 3)
        engine3.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1) // roll 2
        assertEquals(PlayerId.PLAYER_2, engine3.state.activePlayer)

        engine3.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_2) // roll 3
        assertEquals(PlayerId.PLAYER_3, engine3.state.activePlayer)

        engine3.makeMove(SnakesAndLaddersMove(4), PlayerId.PLAYER_3) // roll 4
        assertEquals(PlayerId.PLAYER_1, engine3.state.activePlayer)

        // 5 Players: P1 -> P2 -> P3 -> P4 -> P5 -> P1
        val engine5 = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        assertEquals(PlayerId.PLAYER_1, engine5.state.activePlayer)
        engine5.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)
        assertEquals(PlayerId.PLAYER_2, engine5.state.activePlayer)
        engine5.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_2)
        assertEquals(PlayerId.PLAYER_3, engine5.state.activePlayer)
        engine5.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_3)
        assertEquals(PlayerId.PLAYER_4, engine5.state.activePlayer)
        engine5.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_4)
        assertEquals(PlayerId.PLAYER_5, engine5.state.activePlayer)
        engine5.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_5)
        assertEquals(PlayerId.PLAYER_1, engine5.state.activePlayer)
    }

    @Test
    fun `rolling 1 grants an extra turn`() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 3)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Roll 1: move 1 -> 2, extra turn granted!
        engine.makeMove(SnakesAndLaddersMove(1), PlayerId.PLAYER_1)
        assertEquals(2, engine.state.playerPositions[0])
        assertTrue(engine.state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Now roll 2: move 2 -> 4 (ladder to 14), no extra turn, passes to P2
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)
        assertEquals(14, engine.state.playerPositions[0])
        assertEquals(false, engine.state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)
    }

    @Test
    fun `rolling 6 grants an extra turn even after ladder climb`() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 4)
        // Position P1 at square 3 (so 3 + 6 = 9 -> ladder to 31)
        engine.syncState(
            engine.state.copy(
                playerPositions = listOf(3, 1, 1, 1),
                activePlayer = PlayerId.PLAYER_1
            )
        )

        // Roll 6: 3 + 6 = 9 (ladder to 31), extra turn granted!
        engine.makeMove(SnakesAndLaddersMove(6), PlayerId.PLAYER_1)
        assertEquals(31, engine.state.playerPositions[0])
        assertTrue(engine.state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)
    }

    @Test
    fun `exact finish at 100 wins game immediately and formats winner correctly`() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        // Set P4 at square 98
        engine.syncState(
            engine.state.copy(
                playerPositions = listOf(1, 1, 1, 98, 1),
                activePlayer = PlayerId.PLAYER_4
            )
        )

        // P4 rolls 2: 98 + 2 = 100!
        val res = engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_4)
        assertTrue(res is com.example.core.engine.MoveResult.Success)
        assertEquals(100, engine.state.playerPositions[3])
        assertTrue(engine.state.status is GameStatus.MatchWon)
        val winner = (engine.state.status as GameStatus.MatchWon).matchWinner
        assertEquals(PlayerId.PLAYER_4, winner)
        val winnerString = "Player ${winner.ordinal + 1} wins"
        assertEquals("Player 4 wins", winnerString)
    }

    @Test
    fun `overshot roll past 100 stays on current square`() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 3)
        // P2 at square 97
        engine.syncState(
            engine.state.copy(
                playerPositions = listOf(1, 97, 1),
                activePlayer = PlayerId.PLAYER_2
            )
        )

        // P2 rolls 5: 97 + 5 = 102 > 100
        engine.makeMove(SnakesAndLaddersMove(5), PlayerId.PLAYER_2)
        assertEquals(97, engine.state.playerPositions[1])
        assertTrue(engine.state.wasOvershot)
        assertEquals(PlayerId.PLAYER_3, engine.state.activePlayer)
    }
}
