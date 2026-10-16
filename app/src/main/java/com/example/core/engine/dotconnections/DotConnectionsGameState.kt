package com.example.core.engine.dotconnections

data class DotConnectionsGameState(
    val boardSize: DotBoardSize = DotBoardSize.MEDIUM,
    val placedEdges: Map<DotEdge, LinePlacement> = emptyMap(),
    val boxes: Map<Pair<Int, Int>, DotBox> = emptyMap(),
    val activePlayer: DotPlayer = DotPlayer.PLAYER_1,
    val p1Score: Int = 0,
    val p2Score: Int = 0,
    val lastMoveEdge: DotEdge? = null,
    val lastCompletedBoxes: List<Pair<Int, Int>> = emptyList(),
    val isGameOver: Boolean = false,
    val winner: DotPlayer? = null,
    val isDraw: Boolean = false
) {
    val totalSquares: Int get() = boardSize.squaresCount
    val totalEdges: Int get() = boardSize.totalEdges
    val remainingEdgesCount: Int get() = totalEdges - placedEdges.size
    val remainingSquaresCount: Int get() = totalSquares - (p1Score + p2Score)

    fun isEdgeOccupied(edge: DotEdge): Boolean = placedEdges.containsKey(edge)

    fun getBoxOwner(row: Int, col: Int): DotPlayer? = boxes[Pair(row, col)]?.owner

    /**
     * Count how many sides of a given square (boxRow, boxCol) are currently drawn.
     */
    fun countDrawnSides(boxRow: Int, boxCol: Int): Int {
        val edges = DotEdge.getBoundingEdgesForBox(boxRow, boxCol)
        return edges.count { isEdgeOccupied(it) }
    }

    companion object {
        fun initial(boardSize: DotBoardSize = DotBoardSize.MEDIUM): DotConnectionsGameState {
            return DotConnectionsGameState(
                boardSize = boardSize,
                placedEdges = emptyMap(),
                boxes = emptyMap(),
                activePlayer = DotPlayer.PLAYER_1,
                p1Score = 0,
                p2Score = 0,
                lastMoveEdge = null,
                lastCompletedBoxes = emptyList(),
                isGameOver = false,
                winner = null,
                isDraw = false
            )
        }
    }
}
