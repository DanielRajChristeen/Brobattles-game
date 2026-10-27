package com.example.core.engine.checkers

/**
 * An 8x8 board coordinate (0-indexed).
 *
 * row: 0..7 (0 is opponent's back rank for Red; 7 is Red's back rank)
 * col: 0..7 (0 is A, 7 is H)
 */
data class CheckersSquare(
    val row: Int,
    val col: Int
) {
    val isValid: Boolean
        get() = row in 0..7 && col in 0..7

    val isDarkPlayable: Boolean
        get() = isValid && (row + col) % 2 != 0

    val notation: String
        get() {
            val fileChar = ('A' + col)
            val rankNum = 8 - row
            return "$fileChar$rankNum"
        }

    override fun toString(): String = notation
}
