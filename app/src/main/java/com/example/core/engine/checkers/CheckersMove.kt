package com.example.core.engine.checkers

/**
 * Represents a move from one square to another, optionally capturing an intermediate piece.
 */
data class CheckersMove(
    val from: CheckersSquare,
    val to: CheckersSquare,
    val capturedSquare: CheckersSquare? = null
) {
    val isJump: Boolean
        get() = capturedSquare != null
}

/**
 * Result of executing a move on the Checkers engine.
 */
data class CheckersMoveResult(
    val success: Boolean,
    val isCapture: Boolean = false,
    val isKingPromotion: Boolean = false,
    val multiJumpAvailable: Boolean = false,
    val errorReason: String? = null
)
