package com.example.core.engine.chess

object ChessRules {

    private val KNIGHT_OFFSETS = arrayOf(
        intArrayOf(-2, -1), intArrayOf(-2, 1),
        intArrayOf(-1, -2), intArrayOf(-1, 2),
        intArrayOf(1, -2), intArrayOf(1, 2),
        intArrayOf(2, -1), intArrayOf(2, 1)
    )

    private val BISHOP_DIRS = arrayOf(
        intArrayOf(-1, -1), intArrayOf(-1, 1),
        intArrayOf(1, -1), intArrayOf(1, 1)
    )

    private val ROOK_DIRS = arrayOf(
        intArrayOf(-1, 0), intArrayOf(1, 0),
        intArrayOf(0, -1), intArrayOf(0, 1)
    )

    private val QUEEN_DIRS = arrayOf(
        intArrayOf(-1, -1), intArrayOf(-1, 1),
        intArrayOf(1, -1), intArrayOf(1, 1),
        intArrayOf(-1, 0), intArrayOf(1, 0),
        intArrayOf(0, -1), intArrayOf(0, 1)
    )

    fun isSquareAttacked(board: Array<ChessPiece?>, square: ChessSquare, byColor: PieceColor): Boolean {
        val r = square.row
        val c = square.col

        // 1. Attacked by Pawns
        val pawnPusherRow = if (byColor == PieceColor.WHITE) r + 1 else r - 1
        if (pawnPusherRow in 0..7) {
            if (c - 1 >= 0) {
                val p = board[pawnPusherRow * 8 + (c - 1)]
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
            if (c + 1 <= 7) {
                val p = board[pawnPusherRow * 8 + (c + 1)]
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }

        // 2. Attacked by Knights
        for (offset in KNIGHT_OFFSETS) {
            val nr = r + offset[0]
            val nc = c + offset[1]
            if (ChessSquare.isValid(nr, nc)) {
                val p = board[nr * 8 + nc]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // 3. Attacked by King
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr
                val nc = c + dc
                if (ChessSquare.isValid(nr, nc)) {
                    val p = board[nr * 8 + nc]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        // 4. Attacked by Rooks / Queens (Orthogonals)
        for (dir in ROOK_DIRS) {
            var step = 1
            while (true) {
                val nr = r + dir[0] * step
                val nc = c + dir[1] * step
                if (!ChessSquare.isValid(nr, nc)) break
                val p = board[nr * 8 + nc]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break // Blocked by piece
                }
                step++
            }
        }

        // 5. Attacked by Bishops / Queens (Diagonals)
        for (dir in BISHOP_DIRS) {
            var step = 1
            while (true) {
                val nr = r + dir[0] * step
                val nc = c + dir[1] * step
                if (!ChessSquare.isValid(nr, nc)) break
                val p = board[nr * 8 + nc]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break // Blocked by piece
                }
                step++
            }
        }

        return false
    }

    fun isKingInCheck(board: Array<ChessPiece?>, color: PieceColor): Boolean {
        var kingSquare: ChessSquare? = null
        for (i in 0 until 64) {
            val p = board[i]
            if (p != null && p.type == PieceType.KING && p.color == color) {
                kingSquare = ChessSquare.fromIndex(i)
                break
            }
        }
        if (kingSquare == null) return false
        return isSquareAttacked(board, kingSquare, color.opponent)
    }

    fun generateLegalMoves(state: ChessGameState): List<ChessMove> {
        val pseudoMoves = generatePseudoLegalMoves(state)
        val legalMoves = ArrayList<ChessMove>(pseudoMoves.size)

        for (move in pseudoMoves) {
            if (isMoveLegal(state, move)) {
                legalMoves.add(move)
            }
        }

        return legalMoves
    }

    private fun isMoveLegal(state: ChessGameState, move: ChessMove): Boolean {
        val clonedBoard = state.copyBoard()

        // Apply move on cloned board
        clonedBoard[move.to.index] = clonedBoard[move.from.index]
        clonedBoard[move.from.index] = null

        // Handle En Passant
        if (move.isEnPassant) {
            val capturedRow = move.from.row
            val capturedCol = move.to.col
            clonedBoard[capturedRow * 8 + capturedCol] = null
        }

        // Handle Castling (move the rook too)
        if (move.isCastling) {
            val r = move.from.row
            if (move.to.col == 6) { // Kingside
                clonedBoard[r * 8 + 5] = clonedBoard[r * 8 + 7]
                clonedBoard[r * 8 + 7] = null
            } else if (move.to.col == 2) { // Queenside
                clonedBoard[r * 8 + 3] = clonedBoard[r * 8 + 0]
                clonedBoard[r * 8 + 0] = null
            }
        }

        // Handle Promotion
        if (move.promotion != null) {
            clonedBoard[move.to.index] = ChessPiece(move.promotion, state.activeColor)
        }

        // Does move leave active color's king in check?
        return !isKingInCheck(clonedBoard, state.activeColor)
    }

    private fun generatePseudoLegalMoves(state: ChessGameState): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val color = state.activeColor
        val board = state.board
        val forwardDir = if (color == PieceColor.WHITE) -1 else 1
        val startPawnRow = if (color == PieceColor.WHITE) 6 else 1
        val promoRow = if (color == PieceColor.WHITE) 0 else 7

        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r * 8 + c] ?: continue
                if (piece.color != color) continue
                val from = ChessSquare(r, c)

                when (piece.type) {
                    PieceType.PAWN -> {
                        // 1. One square push
                        val nextR = r + forwardDir
                        if (ChessSquare.isValid(nextR, c) && board[nextR * 8 + c] == null) {
                            val to = ChessSquare(nextR, c)
                            if (nextR == promoRow) {
                                moves.add(ChessMove(from, to, promotion = PieceType.QUEEN))
                                moves.add(ChessMove(from, to, promotion = PieceType.ROOK))
                                moves.add(ChessMove(from, to, promotion = PieceType.BISHOP))
                                moves.add(ChessMove(from, to, promotion = PieceType.KNIGHT))
                            } else {
                                moves.add(ChessMove(from, to))
                            }

                            // 2. Two square push from initial rank
                            if (r == startPawnRow) {
                                val doubleR = r + 2 * forwardDir
                                if (board[doubleR * 8 + c] == null) {
                                    moves.add(ChessMove(from, ChessSquare(doubleR, c)))
                                }
                            }
                        }

                        // 3. Diagonal Captures (including En Passant)
                        for (dc in intArrayOf(-1, 1)) {
                            val capC = c + dc
                            if (ChessSquare.isValid(nextR, capC)) {
                                val targetSquare = ChessSquare(nextR, capC)
                                val targetPiece = board[nextR * 8 + capC]

                                if (targetPiece != null && targetPiece.color == color.opponent) {
                                    if (nextR == promoRow) {
                                        moves.add(ChessMove(from, targetSquare, promotion = PieceType.QUEEN, capturedPiece = targetPiece))
                                        moves.add(ChessMove(from, targetSquare, promotion = PieceType.ROOK, capturedPiece = targetPiece))
                                        moves.add(ChessMove(from, targetSquare, promotion = PieceType.BISHOP, capturedPiece = targetPiece))
                                        moves.add(ChessMove(from, targetSquare, promotion = PieceType.KNIGHT, capturedPiece = targetPiece))
                                    } else {
                                        moves.add(ChessMove(from, targetSquare, capturedPiece = targetPiece))
                                    }
                                } else if (state.enPassantTarget == targetSquare) {
                                    val capturedPawn = board[r * 8 + capC]
                                    moves.add(ChessMove(from, targetSquare, isEnPassant = true, capturedPiece = capturedPawn))
                                }
                            }
                        }
                    }

                    PieceType.KNIGHT -> {
                        for (offset in KNIGHT_OFFSETS) {
                            val nr = r + offset[0]
                            val nc = c + offset[1]
                            if (ChessSquare.isValid(nr, nc)) {
                                val destPiece = board[nr * 8 + nc]
                                if (destPiece == null || destPiece.color == color.opponent) {
                                    moves.add(ChessMove(from, ChessSquare(nr, nc), capturedPiece = destPiece))
                                }
                            }
                        }
                    }

                    PieceType.BISHOP -> {
                        generateRayMoves(from, BISHOP_DIRS, state, moves)
                    }

                    PieceType.ROOK -> {
                        generateRayMoves(from, ROOK_DIRS, state, moves)
                    }

                    PieceType.QUEEN -> {
                        generateRayMoves(from, QUEEN_DIRS, state, moves)
                    }

                    PieceType.KING -> {
                        // 1-step King moves
                        for (dr in -1..1) {
                            for (dc in -1..1) {
                                if (dr == 0 && dc == 0) continue
                                val nr = r + dr
                                val nc = c + dc
                                if (ChessSquare.isValid(nr, nc)) {
                                    val destPiece = board[nr * 8 + nc]
                                    if (destPiece == null || destPiece.color == color.opponent) {
                                        moves.add(ChessMove(from, ChessSquare(nr, nc), capturedPiece = destPiece))
                                    }
                                }
                            }
                        }

                        // Castling: Can only castle if NOT currently in check!
                        if (!state.inCheck) {
                            if (color == PieceColor.WHITE && r == 7 && c == 4) {
                                // White Kingside (e1 -> g1)
                                if (state.whiteCanCastleKingside &&
                                    board[7 * 8 + 5] == null && board[7 * 8 + 6] == null &&
                                    board[7 * 8 + 7]?.type == PieceType.ROOK && board[7 * 8 + 7]?.color == PieceColor.WHITE &&
                                    !isSquareAttacked(board, ChessSquare(7, 4), PieceColor.BLACK) &&
                                    !isSquareAttacked(board, ChessSquare(7, 5), PieceColor.BLACK) &&
                                    !isSquareAttacked(board, ChessSquare(7, 6), PieceColor.BLACK)
                                ) {
                                    moves.add(ChessMove(from, ChessSquare(7, 6), isCastling = true))
                                }
                                // White Queenside (e1 -> c1)
                                if (state.whiteCanCastleQueenside &&
                                    board[7 * 8 + 3] == null && board[7 * 8 + 2] == null && board[7 * 8 + 1] == null &&
                                    board[7 * 8 + 0]?.type == PieceType.ROOK && board[7 * 8 + 0]?.color == PieceColor.WHITE &&
                                    !isSquareAttacked(board, ChessSquare(7, 4), PieceColor.BLACK) &&
                                    !isSquareAttacked(board, ChessSquare(7, 3), PieceColor.BLACK) &&
                                    !isSquareAttacked(board, ChessSquare(7, 2), PieceColor.BLACK)
                                ) {
                                    moves.add(ChessMove(from, ChessSquare(7, 2), isCastling = true))
                                }
                            } else if (color == PieceColor.BLACK && r == 0 && c == 4) {
                                // Black Kingside (e8 -> g8)
                                if (state.blackCanCastleKingside &&
                                    board[0 * 8 + 5] == null && board[0 * 8 + 6] == null &&
                                    board[0 * 8 + 7]?.type == PieceType.ROOK && board[0 * 8 + 7]?.color == PieceColor.BLACK &&
                                    !isSquareAttacked(board, ChessSquare(0, 4), PieceColor.WHITE) &&
                                    !isSquareAttacked(board, ChessSquare(0, 5), PieceColor.WHITE) &&
                                    !isSquareAttacked(board, ChessSquare(0, 6), PieceColor.WHITE)
                                ) {
                                    moves.add(ChessMove(from, ChessSquare(0, 6), isCastling = true))
                                }
                                // Black Queenside (e8 -> c8)
                                if (state.blackCanCastleQueenside &&
                                    board[0 * 8 + 3] == null && board[0 * 8 + 2] == null && board[0 * 8 + 1] == null &&
                                    board[0 * 8 + 0]?.type == PieceType.ROOK && board[0 * 8 + 0]?.color == PieceColor.BLACK &&
                                    !isSquareAttacked(board, ChessSquare(0, 4), PieceColor.WHITE) &&
                                    !isSquareAttacked(board, ChessSquare(0, 3), PieceColor.WHITE) &&
                                    !isSquareAttacked(board, ChessSquare(0, 2), PieceColor.WHITE)
                                ) {
                                    moves.add(ChessMove(from, ChessSquare(0, 2), isCastling = true))
                                }
                            }
                        }
                    }
                }
            }
        }

        return moves
    }

    private fun generateRayMoves(
        from: ChessSquare,
        dirs: Array<IntArray>,
        state: ChessGameState,
        outMoves: MutableList<ChessMove>
    ) {
        val color = state.activeColor
        val board = state.board
        for (dir in dirs) {
            var step = 1
            while (true) {
                val nr = from.row + dir[0] * step
                val nc = from.col + dir[1] * step
                if (!ChessSquare.isValid(nr, nc)) break
                val targetPiece = board[nr * 8 + nc]
                if (targetPiece == null) {
                    outMoves.add(ChessMove(from, ChessSquare(nr, nc)))
                } else {
                    if (targetPiece.color == color.opponent) {
                        outMoves.add(ChessMove(from, ChessSquare(nr, nc), capturedPiece = targetPiece))
                    }
                    break // Ray is blocked
                }
                step++
            }
        }
    }

    fun applyMove(state: ChessGameState, move: ChessMove): ChessGameState {
        val newBoard = state.copyBoard()
        val movingPiece = newBoard[move.from.index] ?: return state
        val capturedByWhite = state.capturedByWhite.toMutableList()
        val capturedByBlack = state.capturedByBlack.toMutableList()

        // Place moving piece
        newBoard[move.to.index] = if (move.promotion != null) {
            ChessPiece(move.promotion, movingPiece.color)
        } else {
            movingPiece
        }
        newBoard[move.from.index] = null

        // En Passant capture
        if (move.isEnPassant) {
            val capturedRow = move.from.row
            val capturedCol = move.to.col
            val capPiece = newBoard[capturedRow * 8 + capturedCol]
            newBoard[capturedRow * 8 + capturedCol] = null
            if (capPiece != null) {
                if (state.activeColor == PieceColor.WHITE) capturedByWhite.add(capPiece.type)
                else capturedByBlack.add(capPiece.type)
            }
        } else if (move.capturedPiece != null) {
            if (state.activeColor == PieceColor.WHITE) capturedByWhite.add(move.capturedPiece.type)
            else capturedByBlack.add(move.capturedPiece.type)
        }

        // Castling rook move
        if (move.isCastling) {
            val r = move.from.row
            if (move.to.col == 6) { // Kingside
                newBoard[r * 8 + 5] = newBoard[r * 8 + 7]
                newBoard[r * 8 + 7] = null
            } else if (move.to.col == 2) { // Queenside
                newBoard[r * 8 + 3] = newBoard[r * 8 + 0]
                newBoard[r * 8 + 0] = null
            }
        }

        // Update Castling Rights
        var wKingside = state.whiteCanCastleKingside
        var wQueenside = state.whiteCanCastleQueenside
        var bKingside = state.blackCanCastleKingside
        var bQueenside = state.blackCanCastleQueenside

        if (movingPiece.type == PieceType.KING) {
            if (movingPiece.color == PieceColor.WHITE) {
                wKingside = false; wQueenside = false
            } else {
                bKingside = false; bQueenside = false
            }
        } else if (movingPiece.type == PieceType.ROOK) {
            if (move.from.row == 7 && move.from.col == 7) wKingside = false
            if (move.from.row == 7 && move.from.col == 0) wQueenside = false
            if (move.from.row == 0 && move.from.col == 7) bKingside = false
            if (move.from.row == 0 && move.from.col == 0) bQueenside = false
        }

        // If an opponent's rook was captured on its home square, opponent loses castling
        if (move.to.row == 7 && move.to.col == 7) wKingside = false
        if (move.to.row == 7 && move.to.col == 0) wQueenside = false
        if (move.to.row == 0 && move.to.col == 7) bKingside = false
        if (move.to.row == 0 && move.to.col == 0) bQueenside = false

        // Update En Passant Target
        val newEnPassant = if (movingPiece.type == PieceType.PAWN && Math.abs(move.to.row - move.from.row) == 2) {
            val midRow = (move.to.row + move.from.row) / 2
            ChessSquare(midRow, move.from.col)
        } else {
            null
        }

        // Halfmove Clock
        val newHalfmove = if (movingPiece.type == PieceType.PAWN || move.capturedPiece != null || move.isEnPassant) {
            0
        } else {
            state.halfmoveClock + 1
        }

        // Fullmove Number
        val nextColor = state.activeColor.opponent
        val newFullmove = if (state.activeColor == PieceColor.BLACK) state.fullmoveNumber + 1 else state.fullmoveNumber

        // Check if next player's king is in check
        val nextInCheck = isKingInCheck(newBoard, nextColor)

        // Create temporary intermediate state to evaluate legal moves for next color
        val nextTempState = ChessGameState(
            board = newBoard,
            activeColor = nextColor,
            whiteCanCastleKingside = wKingside,
            whiteCanCastleQueenside = wQueenside,
            blackCanCastleKingside = bKingside,
            blackCanCastleQueenside = bQueenside,
            enPassantTarget = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove,
            inCheck = nextInCheck,
            capturedByWhite = capturedByWhite,
            capturedByBlack = capturedByBlack,
            lastMove = move
        )

        val nextLegalMoves = generateLegalMoves(nextTempState)

        val finalStatus = when {
            nextLegalMoves.isEmpty() && nextInCheck -> ChessGameStatus.Checkmate(winner = state.activeColor)
            nextLegalMoves.isEmpty() && !nextInCheck -> ChessGameStatus.Stalemate
            newHalfmove >= 100 -> ChessGameStatus.DrawByFiftyMoves
            isInsufficientMaterial(newBoard) -> ChessGameStatus.DrawByInsufficientMaterial
            else -> ChessGameStatus.InProgress
        }

        return nextTempState.copy(status = finalStatus)
    }

    private fun isInsufficientMaterial(board: Array<ChessPiece?>): Boolean {
        var whiteKnights = 0; var whiteBishops = 0; var whiteOthers = 0
        var blackKnights = 0; var blackBishops = 0; var blackOthers = 0

        for (i in 0 until 64) {
            val p = board[i] ?: continue
            when (p.type) {
                PieceType.KING -> {}
                PieceType.BISHOP -> if (p.color == PieceColor.WHITE) whiteBishops++ else blackBishops++
                PieceType.KNIGHT -> if (p.color == PieceColor.WHITE) whiteKnights++ else blackKnights++
                else -> if (p.color == PieceColor.WHITE) whiteOthers++ else blackOthers++
            }
        }

        if (whiteOthers > 0 || blackOthers > 0) return false
        val whiteMinors = whiteKnights + whiteBishops
        val blackMinors = blackKnights + blackBishops

        // King vs King
        if (whiteMinors == 0 && blackMinors == 0) return true
        // King + Minor vs King
        if (whiteMinors <= 1 && blackMinors == 0) return true
        if (blackMinors <= 1 && whiteMinors == 0) return true

        return false
    }
}
