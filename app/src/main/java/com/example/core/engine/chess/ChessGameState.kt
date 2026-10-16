package com.example.core.engine.chess

sealed class ChessGameStatus {
    object InProgress : ChessGameStatus()
    data class Checkmate(val winner: PieceColor) : ChessGameStatus()
    object Stalemate : ChessGameStatus()
    object DrawByFiftyMoves : ChessGameStatus()
    object DrawByInsufficientMaterial : ChessGameStatus()

    val isGameOver: Boolean
        get() = this !is InProgress
}

data class ChessGameState(
    val board: Array<ChessPiece?>,
    val activeColor: PieceColor = PieceColor.WHITE,
    val whiteCanCastleKingside: Boolean = true,
    val whiteCanCastleQueenside: Boolean = true,
    val blackCanCastleKingside: Boolean = true,
    val blackCanCastleQueenside: Boolean = true,
    val enPassantTarget: ChessSquare? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
    val status: ChessGameStatus = ChessGameStatus.InProgress,
    val inCheck: Boolean = false,
    val capturedByWhite: List<PieceType> = emptyList(), // Black pieces captured by White
    val capturedByBlack: List<PieceType> = emptyList(), // White pieces captured by Black
    val lastMove: ChessMove? = null
) {
    fun pieceAt(square: ChessSquare): ChessPiece? = board[square.index]
    fun pieceAt(row: Int, col: Int): ChessPiece? =
        if (ChessSquare.isValid(row, col)) board[row * 8 + col] else null

    fun findKing(color: PieceColor): ChessSquare? {
        for (i in 0 until 64) {
            val p = board[i]
            if (p != null && p.type == PieceType.KING && p.color == color) {
                return ChessSquare.fromIndex(i)
            }
        }
        return null
    }

    fun copyBoard(): Array<ChessPiece?> = board.clone()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChessGameState
        if (!board.contentEquals(other.board)) return false
        if (activeColor != other.activeColor) return false
        if (whiteCanCastleKingside != other.whiteCanCastleKingside) return false
        if (whiteCanCastleQueenside != other.whiteCanCastleQueenside) return false
        if (blackCanCastleKingside != other.blackCanCastleKingside) return false
        if (blackCanCastleQueenside != other.blackCanCastleQueenside) return false
        if (enPassantTarget != other.enPassantTarget) return false
        if (halfmoveClock != other.halfmoveClock) return false
        if (fullmoveNumber != other.fullmoveNumber) return false
        if (status != other.status) return false
        if (inCheck != other.inCheck) return false
        if (lastMove != other.lastMove) return false
        return true
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + activeColor.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    companion object {
        fun initial(): ChessGameState {
            val b = arrayOfNulls<ChessPiece>(64)

            // Rank 8 (row 0) - Black back rank
            b[0] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
            b[1] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
            b[2] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
            b[3] = ChessPiece(PieceType.QUEEN, PieceColor.BLACK)
            b[4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
            b[5] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
            b[6] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
            b[7] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)

            // Rank 7 (row 1) - Black pawns
            for (c in 0..7) {
                b[1 * 8 + c] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
            }

            // Rank 2 (row 6) - White pawns
            for (c in 0..7) {
                b[6 * 8 + c] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
            }

            // Rank 1 (row 7) - White back rank
            b[7 * 8 + 0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
            b[7 * 8 + 1] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
            b[7 * 8 + 2] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
            b[7 * 8 + 3] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE)
            b[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
            b[7 * 8 + 5] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
            b[7 * 8 + 6] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
            b[7 * 8 + 7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)

            return ChessGameState(board = b)
        }
    }
}
