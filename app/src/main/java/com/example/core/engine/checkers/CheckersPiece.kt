package com.example.core.engine.checkers

import com.example.core.engine.PlayerId

/**
 * Represents a single Checkers piece.
 *
 * @param id Unique ID for animation and identification.
 * @param player The owner (PLAYER_1 = Red, PLAYER_2 = Black).
 * @param isKing Whether this piece has been crowned King.
 */
data class CheckersPiece(
    val id: Int,
    val player: PlayerId,
    val isKing: Boolean = false
) {
    fun promoteToKing(): CheckersPiece = copy(isKing = true)
}
