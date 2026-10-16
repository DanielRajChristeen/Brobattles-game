package com.example.core.engine.chess

data class ChessSquare(
    val row: Int, // 0 = rank 8 (Black backrank), 7 = rank 1 (White backrank)
    val col: Int  // 0 = file a, 7 = file h
) {
    val index: Int get() = row * 8 + col
    val algebraic: String get() = "${('a' + col)}${8 - row}"

    val isLightSquare: Boolean get() = (row + col) % 2 == 0

    companion object {
        fun fromIndex(index: Int): ChessSquare = ChessSquare(index / 8, index % 8)

        fun fromAlgebraic(alg: String): ChessSquare? {
            if (alg.length != 2) return null
            val fileChar = alg[0].lowercaseChar()
            val rankChar = alg[1]
            if (fileChar !in 'a'..'h' || rankChar !in '1'..'8') return null
            val col = fileChar - 'a'
            val row = 8 - (rankChar - '0')
            return ChessSquare(row, col)
        }

        fun isValid(row: Int, col: Int): Boolean = row in 0..7 && col in 0..7
    }
}
