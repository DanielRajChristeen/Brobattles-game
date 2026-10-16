package com.example.core.engine.chess

import kotlin.random.Random

enum class ChessPlayerColor(val title: String, val emoji: String) {
    WHITE("WHITE", "⚪"),
    BLACK("BLACK", "⚫"),
    AUTO("AUTO", "🎲");

    fun resolve(): PieceColor {
        return when (this) {
            WHITE -> PieceColor.WHITE
            BLACK -> PieceColor.BLACK
            AUTO -> if (Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }
    }
}
