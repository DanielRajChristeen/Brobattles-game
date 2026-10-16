package com.example.core.engine.dotconnections

enum class DotBoardSize(
    val dots: Int,
    val title: String,
    val subtitle: String
) {
    SMALL(dots = 4, title = "SMALL", subtitle = "4×4 • 9 boxes"),
    MEDIUM(dots = 5, title = "MEDIUM", subtitle = "5×5 • 16 boxes"),
    LARGE(dots = 6, title = "LARGE", subtitle = "6×6 • 25 boxes");

    val squaresCount: Int get() = (dots - 1) * (dots - 1)
    val totalHorizontalEdges: Int get() = dots * (dots - 1)
    val totalVerticalEdges: Int get() = (dots - 1) * dots
    val totalEdges: Int get() = totalHorizontalEdges + totalVerticalEdges
}
