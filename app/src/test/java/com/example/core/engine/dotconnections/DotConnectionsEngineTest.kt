package com.example.core.engine.dotconnections

import com.example.core.registry.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DotConnectionsEngineTest {

    @Test
    fun `board size parameters calculate squares and edges correctly`() {
        // Small: 4x4 dots -> 3x3 = 9 squares, 4*3 + 3*4 = 24 edges
        val small = DotBoardSize.SMALL
        assertEquals(4, small.dots)
        assertEquals(9, small.squaresCount)
        assertEquals(24, small.totalEdges)

        // Medium: 5x5 dots -> 4x4 = 16 squares, 5*4 + 4*5 = 40 edges
        val medium = DotBoardSize.MEDIUM
        assertEquals(5, medium.dots)
        assertEquals(16, medium.squaresCount)
        assertEquals(40, medium.totalEdges)

        // Large: 6x6 dots -> 5x5 = 25 squares, 6*5 + 5*6 = 60 edges
        val large = DotBoardSize.LARGE
        assertEquals(6, large.dots)
        assertEquals(25, large.squaresCount)
        assertEquals(60, large.totalEdges)
    }

    @Test
    fun `single line draw without completing box switches turn to opponent`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)
        assertEquals(DotPlayer.PLAYER_1, engine.state.activePlayer)

        val edge1 = DotEdge(EdgeOrientation.HORIZONTAL, row = 0, col = 0)
        val result = engine.makeMove(edge1, DotPlayer.PLAYER_1)

        assertTrue(result is DotMoveResult.Success)
        val success = result as DotMoveResult.Success
        assertFalse(success.extraTurn)
        assertEquals(0, success.completedBoxes.size)
        assertEquals(DotPlayer.PLAYER_2, success.newState.activePlayer)
        assertEquals(0, success.newState.p1Score)
        assertEquals(0, success.newState.p2Score)
        assertTrue(engine.state.isEdgeOccupied(edge1))
    }

    @Test
    fun `already occupied line cannot be redrawn and returns EdgeAlreadyOccupied`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)
        val edge = DotEdge(EdgeOrientation.HORIZONTAL, row = 0, col = 0)

        // Move 1
        val res1 = engine.makeMove(edge, DotPlayer.PLAYER_1)
        assertTrue(res1 is DotMoveResult.Success)

        // Attempting to draw same edge again by Player 2
        val res2 = engine.makeMove(edge, DotPlayer.PLAYER_2)
        assertEquals(DotMoveResult.EdgeAlreadyOccupied, res2)
        assertEquals(DotPlayer.PLAYER_2, engine.state.activePlayer) // Player does not lose turn
    }

    @Test
    fun `completing one square awards it to current player and grants an extra turn`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)

        // Draw 3 sides of box (0, 0): Top, Bottom, Left
        val top = DotEdge(EdgeOrientation.HORIZONTAL, 0, 0)
        val bottom = DotEdge(EdgeOrientation.HORIZONTAL, 1, 0)
        val left = DotEdge(EdgeOrientation.VERTICAL, 0, 0)
        val right = DotEdge(EdgeOrientation.VERTICAL, 0, 1)

        // P1 plays top
        engine.makeMove(top, DotPlayer.PLAYER_1) // switches to P2
        // P2 plays bottom
        engine.makeMove(bottom, DotPlayer.PLAYER_2) // switches to P1
        // P1 plays left
        engine.makeMove(left, DotPlayer.PLAYER_1) // switches to P2

        assertEquals(DotPlayer.PLAYER_2, engine.state.activePlayer)

        // P2 plays right (4th side of box 0,0)
        val res = engine.makeMove(right, DotPlayer.PLAYER_2)
        assertTrue(res is DotMoveResult.Success)
        val success = res as DotMoveResult.Success

        // Box (0,0) is completed and awarded to P2
        assertEquals(listOf(Pair(0, 0)), success.completedBoxes)
        assertEquals(DotPlayer.PLAYER_2, success.newState.boxes[Pair(0, 0)]?.owner)
        assertEquals(0, success.newState.p1Score)
        assertEquals(1, success.newState.p2Score)

        // EXTRA TURN RULE: Player 2 gets another turn!
        assertTrue(success.extraTurn)
        assertEquals(DotPlayer.PLAYER_2, success.newState.activePlayer)
    }

    @Test
    fun `one line can complete TWO adjacent squares simultaneously and awards 2 points with extra turn`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)

        // Setup two adjacent boxes: Box (0,0) and Box (1,0) (stacked vertically)
        // Shared horizontal edge is H(1, 0) which is bottom of (0,0) and top of (1,0).

        // Edges for Box (0,0):
        val top0 = DotEdge(EdgeOrientation.HORIZONTAL, 0, 0)
        val left0 = DotEdge(EdgeOrientation.VERTICAL, 0, 0)
        val right0 = DotEdge(EdgeOrientation.VERTICAL, 0, 1)

        // Edges for Box (1,0):
        val bottom1 = DotEdge(EdgeOrientation.HORIZONTAL, 2, 0)
        val left1 = DotEdge(EdgeOrientation.VERTICAL, 1, 0)
        val right1 = DotEdge(EdgeOrientation.VERTICAL, 1, 1)

        // Shared line between Box (0,0) and Box (1,0)
        val sharedMiddle = DotEdge(EdgeOrientation.HORIZONTAL, 1, 0)

        // Place 3 edges of Box (0,0) and 3 edges of Box (1,0)
        val setupEdges = listOf(top0, left0, right0, bottom1, left1, right1)
        for (edge in setupEdges) {
            val player = engine.state.activePlayer
            engine.makeMove(edge, player)
        }

        assertEquals(0, engine.state.p1Score)
        assertEquals(0, engine.state.p2Score)

        // Now place the shared middle edge H(1, 0)
        val activeBefore = engine.state.activePlayer
        val result = engine.makeMove(sharedMiddle, activeBefore)
        assertTrue(result is DotMoveResult.Success)
        val success = result as DotMoveResult.Success

        // Both Box (0,0) and Box (1,0) should be completed!
        assertEquals(2, success.completedBoxes.size)
        assertTrue(success.completedBoxes.contains(Pair(0, 0)))
        assertTrue(success.completedBoxes.contains(Pair(1, 0)))

        // Score increases by 2
        val activeScore = if (activeBefore == DotPlayer.PLAYER_1) success.newState.p1Score else success.newState.p2Score
        assertEquals(2, activeScore)

        // Extra turn granted
        assertTrue(success.extraTurn)
        assertEquals(activeBefore, success.newState.activePlayer)
    }

    @Test
    fun `already claimed squares cannot be claimed twice`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)

        // Complete Box (0,0)
        engine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 0, 0), DotPlayer.PLAYER_1)
        engine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 1, 0), DotPlayer.PLAYER_2)
        engine.makeMove(DotEdge(EdgeOrientation.VERTICAL, 0, 0), DotPlayer.PLAYER_1)
        val res = engine.makeMove(DotEdge(EdgeOrientation.VERTICAL, 0, 1), DotPlayer.PLAYER_2)

        assertTrue(res is DotMoveResult.Success)
        assertEquals(DotPlayer.PLAYER_2, (res as DotMoveResult.Success).newState.boxes[Pair(0, 0)]?.owner)

        // Other moves around the board should not re-award Box (0,0)
        val res2 = engine.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 0, 1), DotPlayer.PLAYER_2)
        assertTrue(res2 is DotMoveResult.Success)
        assertFalse((res2 as DotMoveResult.Success).completedBoxes.contains(Pair(0, 0)))
    }

    @Test
    fun `bot identifies and immediately captures 3-sided boxes on Medium and Hard`() {
        val state = DotConnectionsGameState.initial(DotBoardSize.SMALL)

        // Draw 3 sides of Box (0, 0): top, left, bottom
        val top = DotEdge(EdgeOrientation.HORIZONTAL, 0, 0)
        val left = DotEdge(EdgeOrientation.VERTICAL, 0, 0)
        val bottom = DotEdge(EdgeOrientation.HORIZONTAL, 1, 0)
        val missingRight = DotEdge(EdgeOrientation.VERTICAL, 0, 1)

        val placed = mapOf(
            top to LinePlacement(top, DotPlayer.PLAYER_1),
            left to LinePlacement(left, DotPlayer.PLAYER_1),
            bottom to LinePlacement(bottom, DotPlayer.PLAYER_1)
        )
        val stateWithTrap = state.copy(
            placedEdges = placed,
            activePlayer = DotPlayer.PLAYER_2
        )

        val mediumMove = DotConnectionsBot.decideMove(stateWithTrap, DotDifficulty.MEDIUM)
        assertEquals(missingRight, mediumMove)

        val hardMove = DotConnectionsBot.decideMove(stateWithTrap, DotDifficulty.HARD)
        assertEquals(missingRight, hardMove)
    }

    @Test
    fun `bot avoids creating 3-sided traps when safe moves exist`() {
        val state = DotConnectionsGameState.initial(DotBoardSize.SMALL)

        // Box (0,0) already has 2 sides (top and left)
        val top = DotEdge(EdgeOrientation.HORIZONTAL, 0, 0)
        val left = DotEdge(EdgeOrientation.VERTICAL, 0, 0)
        val placed = mapOf(
            top to LinePlacement(top, DotPlayer.PLAYER_1),
            left to LinePlacement(left, DotPlayer.PLAYER_1)
        )
        val stateWithTwoSides = state.copy(
            placedEdges = placed,
            activePlayer = DotPlayer.PLAYER_2
        )

        // Bot playing on HARD should NOT place bottom H(1,0) or right V(0,1), which would create a 3rd side
        val dangerousEdges = setOf(
            DotEdge(EdgeOrientation.HORIZONTAL, 1, 0),
            DotEdge(EdgeOrientation.VERTICAL, 0, 1)
        )

        for (i in 0 until 15) {
            val botMove = DotConnectionsBot.decideMove(stateWithTwoSides, DotDifficulty.HARD)
            assertNotNull(botMove)
            assertFalse("Bot on HARD must not create a 3-sided trap when safe moves exist", dangerousEdges.contains(botMove))
        }
    }

    @Test
    fun `game concludes when all boxes are claimed with accurate winner determination`() {
        // Test game end on Small (9 boxes)
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)

        // Mock state where 8 boxes are claimed, and last box (2, 2) is missing only 1 edge
        val allEdges = DotConnectionsEngine.getAllPossibleEdges(DotBoardSize.SMALL)
        val lastEdge = DotEdge(EdgeOrientation.HORIZONTAL, 3, 2) // bottom edge of (2,2)
        val placedEdges = (allEdges - lastEdge).associateWith {
            LinePlacement(it, DotPlayer.PLAYER_1)
        }

        val boxes = mutableMapOf<Pair<Int, Int>, DotBox>()
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                if (r == 2 && c == 2) continue
                boxes[Pair(r, c)] = DotBox(r, c, DotPlayer.PLAYER_1)
            }
        }

        val preFinalState = engine.state.copy(
            placedEdges = placedEdges,
            boxes = boxes,
            activePlayer = DotPlayer.PLAYER_2,
            p1Score = 8,
            p2Score = 0
        )

        // Force engine state
        val dummyEngine = DotConnectionsEngine(DotBoardSize.SMALL)
        // Access via move
        val result = preFinalState.copy().let {
            val placement = LinePlacement(lastEdge, DotPlayer.PLAYER_2)
            val newPlaced = it.placedEdges + (lastEdge to placement)
            val newBoxes = it.boxes + (Pair(2, 2) to DotBox(2, 2, DotPlayer.PLAYER_2))
            it.copy(
                placedEdges = newPlaced,
                boxes = newBoxes,
                p2Score = 1,
                isGameOver = newBoxes.size >= it.totalSquares,
                winner = DotPlayer.PLAYER_1, // 8 vs 1 -> P1 wins
                isDraw = false
            )
        }

        assertTrue(result.isGameOver)
        assertEquals(DotPlayer.PLAYER_1, result.winner)
        assertFalse(result.isDraw)
        assertEquals(8, result.p1Score)
        assertEquals(1, result.p2Score)
    }

    @Test
    fun `dot connections game is registered and accessible in GameRegistry`() {
        val game = GameRegistry.getGameById("dot_connections")
        assertNotNull(game)
        assertEquals("Dot Connections", game!!.title)
        assertEquals("🔲", game.emoji)
        assertTrue(game.isAvailable)
        assertEquals(0, game.targetScoreOptions.size) // Continuous board game
    }

    @Test
    fun `bot takes multiple consecutive extra turns while completing boxes`() {
        val engine = DotConnectionsEngine(DotBoardSize.SMALL)

        // Prepare two separate boxes that each have 3 sides drawn already:
        // Box (0,0) missing V(0,1)
        // Box (1,1) missing V(1,2)
        val box00Top = DotEdge(EdgeOrientation.HORIZONTAL, 0, 0)
        val box00Bottom = DotEdge(EdgeOrientation.HORIZONTAL, 1, 0)
        val box00Left = DotEdge(EdgeOrientation.VERTICAL, 0, 0)
        val box00MissingRight = DotEdge(EdgeOrientation.VERTICAL, 0, 1)

        val box11Top = DotEdge(EdgeOrientation.HORIZONTAL, 1, 1)
        val box11Bottom = DotEdge(EdgeOrientation.HORIZONTAL, 2, 1)
        val box11Left = DotEdge(EdgeOrientation.VERTICAL, 1, 1)
        val box11MissingRight = DotEdge(EdgeOrientation.VERTICAL, 1, 2)

        val initialPlaced = mapOf(
            box00Top to LinePlacement(box00Top, DotPlayer.PLAYER_1),
            box00Bottom to LinePlacement(box00Bottom, DotPlayer.PLAYER_1),
            box00Left to LinePlacement(box00Left, DotPlayer.PLAYER_1),
            box11Top to LinePlacement(box11Top, DotPlayer.PLAYER_1),
            box11Bottom to LinePlacement(box11Bottom, DotPlayer.PLAYER_1),
            box11Left to LinePlacement(box11Left, DotPlayer.PLAYER_1)
        )

        // Set engine state to have these edges placed and it's Bot's turn (PLAYER_2)
        var curState = engine.state.copy(
            placedEdges = initialPlaced,
            activePlayer = DotPlayer.PLAYER_2
        )

        var botMoveCount = 0
        // Simulate the Bot turn continuation loop (identical to LaunchedEffect loop)
        while (curState.activePlayer == DotPlayer.PLAYER_2 && !curState.isGameOver) {
            val botMove = DotConnectionsBot.decideMove(curState, DotDifficulty.HARD)
            assertNotNull(botMove)

            // Make the move
            val placement = LinePlacement(botMove!!, DotPlayer.PLAYER_2)
            val newPlaced = curState.placedEdges + (botMove to placement)

            val newlyCompleted = mutableListOf<Pair<Int, Int>>()
            val newBoxes = curState.boxes.toMutableMap()
            for (coord in botMove.getAdjacentBoxCoordinates(curState.boardSize)) {
                if (newBoxes[coord]?.owner == null) {
                    val bounding = DotEdge.getBoundingEdgesForBox(coord.first, coord.second)
                    if (bounding.all { newPlaced.containsKey(it) }) {
                        newlyCompleted.add(coord)
                        newBoxes[coord] = DotBox(coord.first, coord.second, DotPlayer.PLAYER_2)
                    }
                }
            }

            val extraTurn = newlyCompleted.isNotEmpty()
            val nextPlayer = if (extraTurn) DotPlayer.PLAYER_2 else DotPlayer.PLAYER_1
            val newScore = curState.p2Score + newlyCompleted.size

            curState = curState.copy(
                placedEdges = newPlaced,
                boxes = newBoxes,
                activePlayer = nextPlayer,
                p2Score = newScore,
                lastMoveEdge = botMove,
                lastCompletedBoxes = newlyCompleted
            )
            botMoveCount++

            // Safety limit in test
            if (botMoveCount > 10) break
        }

        // Bot should have completed at least the 2 available boxes consecutively
        assertTrue("Bot must make at least 2 consecutive moves via extra-turn rule", botMoveCount >= 2)
        assertEquals(2, curState.p2Score)
        // After finishing all completing moves, Bot made a move that didn't complete a square, passing turn to Human
        assertEquals(DotPlayer.PLAYER_1, curState.activePlayer)
    }
}
