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
    initialBoardSize: DotBoardSize = DotBoardSize.MEDIUM
) {
    var state: DotConnectionsGameState = DotConnectionsGameState.initial(initialBoardSize)
        private set

    fun resetGame(boardSize: DotBoardSize = state.boardSize) {
        state = DotConnectionsGameState.initial(boardSize)
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
        val newP1Score = state.p1Score + if (player == DotPlayer.PLAYER_1) addedBoxes else 0
        val newP2Score = state.p2Score + if (player == DotPlayer.PLAYER_2) addedBoxes else 0

        val extraTurn = addedBoxes > 0
        val nextPlayer = if (extraTurn) player else player.opponent()

        val isGameOver = newBoxes.size >= state.totalSquares
        val winner = if (isGameOver) {
            when {
                newP1Score > newP2Score -> DotPlayer.PLAYER_1
                newP2Score > newP1Score -> DotPlayer.PLAYER_2
                else -> null
            }
        } else null
        val isDraw = isGameOver && newP1Score == newP2Score

        val newState = state.copy(
            placedEdges = newPlacedEdges,
            boxes = newBoxes,
            activePlayer = nextPlayer,
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
