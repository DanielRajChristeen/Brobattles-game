package com.example.core.engine.dotconnections

data class DotConnectionsGameState(
    val boardSize: DotBoardSize = DotBoardSize.MEDIUM,
    val playerCount: Int = 2,
    val placedEdges: Map<DotEdge, LinePlacement> = emptyMap(),
    val boxes: Map<Pair<Int, Int>, DotBox> = emptyMap(),
    val activePlayer: DotPlayer = DotPlayer.PLAYER_1,
    val playerScores: Map<DotPlayer, Int> = emptyMap(),
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
    val remainingSquaresCount: Int get() = totalSquares - boxes.size

    fun isEdgeOccupied(edge: DotEdge): Boolean = placedEdges.containsKey(edge)

    fun getBoxOwner(row: Int, col: Int): DotPlayer? = boxes[Pair(row, col)]?.owner

    fun getScore(player: DotPlayer): Int = playerScores[player] ?: when (player) {
        DotPlayer.PLAYER_1 -> p1Score
        DotPlayer.PLAYER_2 -> p2Score
        else -> 0
    }

    val participatingPlayers: List<DotPlayer>
        get() = DotPlayer.activePlayers(playerCount)

    val rankings: List<Pair<DotPlayer, Int>>
        get() = participatingPlayers.map { it to getScore(it) }.sortedByDescending { it.second }

    /**
     * Count how many sides of a given square (boxRow, boxCol) are currently drawn.
     */
    fun countDrawnSides(boxRow: Int, boxCol: Int): Int {
        val edges = DotEdge.getBoundingEdgesForBox(boxRow, boxCol)
        return edges.count { isEdgeOccupied(it) }
    }

    companion object {
        fun initial(
            boardSize: DotBoardSize = DotBoardSize.MEDIUM,
            playerCount: Int = 2
        ): DotConnectionsGameState {
            val safeCount = playerCount.coerceIn(2, 5)
            val initialScores = DotPlayer.activePlayers(safeCount).associateWith { 0 }
            return DotConnectionsGameState(
                boardSize = boardSize,
                playerCount = safeCount,
                placedEdges = emptyMap(),
                boxes = emptyMap(),
                activePlayer = DotPlayer.PLAYER_1,
                playerScores = initialScores,
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
