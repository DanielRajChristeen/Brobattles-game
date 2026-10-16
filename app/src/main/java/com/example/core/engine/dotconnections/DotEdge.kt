package com.example.core.engine.dotconnections

enum class EdgeOrientation {
    HORIZONTAL,
    VERTICAL
}

data class DotEdge(
    val orientation: EdgeOrientation,
    val row: Int,
    val col: Int
) {
    val key: String get() = "${orientation.name.first()}_${row}_${col}"

    /**
     * Returns the coordinates of adjacent squares (0, 1, or 2 squares) that share this edge.
     */
    fun getAdjacentBoxCoordinates(boardSize: DotBoardSize): List<Pair<Int, Int>> {
        val boxesPerSide = boardSize.dots - 1
        val list = mutableListOf<Pair<Int, Int>>()
        when (orientation) {
            EdgeOrientation.HORIZONTAL -> {
                // Square above this edge: (row - 1, col)
                if (row > 0) {
                    list.add(Pair(row - 1, col))
                }
                // Square below this edge: (row, col)
                if (row < boxesPerSide) {
                    list.add(Pair(row, col))
                }
            }
            EdgeOrientation.VERTICAL -> {
                // Square to the left of this edge: (row, col - 1)
                if (col > 0) {
                    list.add(Pair(row, col - 1))
                }
                // Square to the right of this edge: (row, col)
                if (col < boxesPerSide) {
                    list.add(Pair(row, col))
                }
            }
        }
        return list
    }

    companion object {
        fun getBoundingEdgesForBox(boxRow: Int, boxCol: Int): List<DotEdge> = listOf(
            DotEdge(EdgeOrientation.HORIZONTAL, boxRow, boxCol),         // Top
            DotEdge(EdgeOrientation.HORIZONTAL, boxRow + 1, boxCol),     // Bottom
            DotEdge(EdgeOrientation.VERTICAL, boxRow, boxCol),           // Left
            DotEdge(EdgeOrientation.VERTICAL, boxRow, boxCol + 1)        // Right
        )
    }
}

data class LinePlacement(
    val edge: DotEdge,
    val owner: DotPlayer,
    val timestampMs: Long = System.currentTimeMillis()
)
