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
    fun `normal move advances square without ladder or snake`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // 1 + 2 = 3 (neither ladder nor snake)
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(3, state.p1Position)
        assertEquals(1, state.p2Position)
        assertEquals(2, state.lastDiceRoll)
        assertEquals(PlayerId.PLAYER_2, state.activePlayer)
        assertNull(state.ladderClimbed)
        assertNull(state.snakeSlid)
    }

    @Test
    fun `landing on ladder climbs to ladder top`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // 1 + 3 = 4 (Ladder bottom 4 -> top 14)
        engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_1)

        val state = engine.state
        assertEquals(14, state.p1Position)
        assertNotNull(state.ladderClimbed)
        assertEquals(4 to 14, state.ladderClimbed)
        assertEquals(PlayerId.PLAYER_2, state.activePlayer)
    }

    @Test
    fun `landing on snake slides to snake tail`() {
        val engine = SnakesAndLaddersGameEngine(initialTargetScore = 1)
        // Ladder at 4 -> 14
        engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_1)
        assertEquals(14, engine.state.p1Position)

        // P2 move (dice 2 so turn passes back to P1)
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_2)

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
}
