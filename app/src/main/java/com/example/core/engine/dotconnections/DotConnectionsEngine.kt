package com.example.core.engine.dotconnections

sealed interface DotMoveResult {
    data class Success(
        val newState: DotConnectionsGameState,
        val completedBoxes: List<Pair<Int, Int>>,
        val extraTurn: Boolean
    ) : DotMoveResult

    data object EdgeAlreadyOccupied : DotMoveResult
    data object InvalidEdge : DotMoveResult
    data object GameOver : DotMoveResult
    data object NotPlayerTurn : DotMoveResult
}

class DotConnectionsEngine(
    initialBoardSize: DotBoardSize = DotBoardSize.MEDIUM,
    val initialPlayerCount: Int = 2
) {
    var state: DotConnectionsGameState = DotConnectionsGameState.initial(initialBoardSize, initialPlayerCount)
        private set

    fun resetGame(boardSize: DotBoardSize = state.boardSize, playerCount: Int = state.playerCount) {
        state = DotConnectionsGameState.initial(boardSize, playerCount)
    }

    fun syncState(newState: DotConnectionsGameState) {
        this.state = newState
    }

    fun makeMove(edge: DotEdge, player: DotPlayer = state.activePlayer): DotMoveResult {
        if (state.isGameOver) return DotMoveResult.GameOver
        if (state.activePlayer != player) return DotMoveResult.NotPlayerTurn
        if (!isValidEdge(edge, state.boardSize)) return DotMoveResult.InvalidEdge
        if (state.isEdgeOccupied(edge)) return DotMoveResult.EdgeAlreadyOccupied

        val placement = LinePlacement(edge, player, System.currentTimeMillis())
        val newPlacedEdges = state.placedEdges.toMutableMap()
        newPlacedEdges[edge] = placement

        val adjacentCoords = edge.getAdjacentBoxCoordinates(state.boardSize)
        val newlyCompleted = mutableListOf<Pair<Int, Int>>()
        val newBoxes = state.boxes.toMutableMap()

        for (coord in adjacentCoords) {
            val (r, c) = coord
            // If already claimed, skip
            if (newBoxes[coord]?.owner != null) continue

            // Check if all 4 bounding edges are now placed
            val boundingEdges = DotEdge.getBoundingEdgesForBox(r, c)
            val isBoxCompleted = boundingEdges.all { newPlacedEdges.containsKey(it) }

            if (isBoxCompleted) {
                newlyCompleted.add(coord)
                newBoxes[coord] = DotBox(
                    row = r,
                    col = c,
                    owner = player,
                    completedAtTimestamp = System.currentTimeMillis()
                )
            }
        }

        val addedBoxes = newlyCompleted.size
        val newScores = state.playerScores.toMutableMap()
        val curScore = newScores[player] ?: 0
        newScores[player] = curScore + addedBoxes

        val newP1Score = newScores[DotPlayer.PLAYER_1] ?: 0
        val newP2Score = newScores[DotPlayer.PLAYER_2] ?: 0

        val extraTurn = addedBoxes > 0
        val nextPlayer = if (extraTurn) player else player.next(state.playerCount)

        val isGameOver = newBoxes.size >= state.totalSquares || newPlacedEdges.size >= state.totalEdges

        val activePlayers = state.participatingPlayers
        val rankings = activePlayers.map { it to (newScores[it] ?: 0) }.sortedByDescending { it.second }
        val maxScore = rankings.firstOrNull()?.second ?: 0
        val topScorers = rankings.filter { it.second == maxScore }
        val isDraw = isGameOver && topScorers.size > 1
        val winner = if (isGameOver && topScorers.size == 1) topScorers.first().first else null

        val newState = state.copy(
            placedEdges = newPlacedEdges,
            boxes = newBoxes,
            activePlayer = nextPlayer,
            playerScores = newScores,
            p1Score = newP1Score,
            p2Score = newP2Score,
            lastMoveEdge = edge,
            lastCompletedBoxes = newlyCompleted,
            isGameOver = isGameOver,
            winner = winner,
            isDraw = isDraw
        )

        state = newState
        return DotMoveResult.Success(newState, newlyCompleted, extraTurn)
    }

    companion object {
        fun isValidEdge(edge: DotEdge, boardSize: DotBoardSize): Boolean {
            val dots = boardSize.dots
            return when (edge.orientation) {
                EdgeOrientation.HORIZONTAL -> {
                    edge.row in 0 until dots && edge.col in 0 until (dots - 1)
                }
                EdgeOrientation.VERTICAL -> {
                    edge.row in 0 until (dots - 1) && edge.col in 0 until dots
                }
            }
        }

        fun getAllPossibleEdges(boardSize: DotBoardSize): List<DotEdge> {
            val dots = boardSize.dots
            val list = mutableListOf<DotEdge>()
            // Horizontal edges
            for (r in 0 until dots) {
                for (c in 0 until (dots - 1)) {
                    list.add(DotEdge(EdgeOrientation.HORIZONTAL, r, c))
                }
            }
            // Vertical edges
            for (r in 0 until (dots - 1)) {
                for (c in 0 until dots) {
                    list.add(DotEdge(EdgeOrientation.VERTICAL, r, c))
                }
            }
            return list
        }
    }
}
