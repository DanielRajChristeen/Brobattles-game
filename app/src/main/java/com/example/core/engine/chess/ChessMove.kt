package com.example.core.engine.chess

data class ChessMove(
    val from: ChessSquare,
    val to: ChessSquare,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false,
    val capturedPiece: ChessPiece? = null
) {
    val uciString: String
        get() {
            val prom = promotion?.symbol?.lowercase() ?: ""
            return "${from.algebraic}${to.algebraic}$prom"
        }
}
