package com.example.core.engine.checkers

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import kotlin.math.abs

/**
 * Pure rule engine implementing complete American Checkers (Draughts) rules:
 * - Alternating turns
 * - Forward diagonal movement for men
 * - Forward & backward diagonal movement for Kings
 * - Mandatory captures (if any capture exists, non-captures are illegal)
 * - Multi-jumps with the same piece
 * - King promotion on reaching opponent back rank (row 0 for Red, row 7 for Black)
 * - Turn ends upon promotion to King
 * - Win condition: opponent has 0 pieces or 0 legal moves
 * - Draw condition: 40 full moves (80 turns) without capture or promotion
 */
object CheckersRules {

    /** Maximum consecutive non-capture, non-promotion turns before automatic draw */
    const val DRAW_TURNS_THRESHOLD = 80

    /**
     * Returns all legal moves for the active player in the current state.
     */
    fun getLegalMoves(state: CheckersGameState): List<CheckersMove> {
        if (state.status.isFinished || state.status is GameStatus.RoundDraw) {
            return emptyList()
        }

        val activePlayer = state.activePlayer

        // If locked into continuing a multi-jump, only jumps from that specific square are allowed
        if (state.mustContinueJumpingFrom != null) {
            return getJumpMovesForSquare(state, state.mustContinueJumpingFrom)
        }

        // Collect all jump moves across all pieces of the active player
        val allJumps = mutableListOf<CheckersMove>()
        for ((sq, piece) in state.board) {
            if (piece.player == activePlayer) {
                allJumps.addAll(getJumpMovesForSquare(state, sq))
            }
        }

        // Mandatory Captures Rule:
        // If ANY capture is available, the player MUST capture.
        if (allJumps.isNotEmpty()) {
            return allJumps
        }

        // If no captures are available, collect standard 1-step diagonal moves
        val allSimpleMoves = mutableListOf<CheckersMove>()
        for ((sq, piece) in state.board) {
            if (piece.player == activePlayer) {
                allSimpleMoves.addAll(getSimpleMovesForSquare(state, sq))
            }
        }

        return allSimpleMoves
    }

    /**
     * Returns legal moves originating strictly from the given square.
     */
    fun getLegalMovesForSquare(state: CheckersGameState, square: CheckersSquare): List<CheckersMove> {
        val piece = state.board[square] ?: return emptyList()
        if (piece.player != state.activePlayer) return emptyList()

        val allLegal = getLegalMoves(state)
        return allLegal.filter { it.from == square }
    }

    /**
     * Collects all diagonal jump moves (captures) originating from [square].
     */
    fun getJumpMovesForSquare(state: CheckersGameState, square: CheckersSquare): List<CheckersMove> {
        val piece = state.board[square] ?: return emptyList()
        val jumps = mutableListOf<CheckersMove>()

        val directions = getDirections(piece)
        for ((dRow, dCol) in directions) {
            val midRow = square.row + dRow
            val midCol = square.col + dCol
            val targetRow = square.row + (dRow * 2)
            val targetCol = square.col + (dCol * 2)

            val midSq = CheckersSquare(midRow, midCol)
            val targetSq = CheckersSquare(targetRow, targetCol)

            if (targetSq.isValid && targetSq.isDarkPlayable && !state.board.containsKey(targetSq)) {
                val midPiece = state.board[midSq]
                if (midPiece != null && midPiece.player != piece.player) {
                    jumps.add(
                        CheckersMove(
                            from = square,
                            to = targetSq,
                            capturedSquare = midSq
                        )
                    )
                }
            }
        }

        return jumps
    }

    /**
     * Collects standard single-step diagonal moves originating from [square].
     */
    fun getSimpleMovesForSquare(state: CheckersGameState, square: CheckersSquare): List<CheckersMove> {
        val piece = state.board[square] ?: return emptyList()
        val moves = mutableListOf<CheckersMove>()

        val directions = getDirections(piece)
        for ((dRow, dCol) in directions) {
            val targetRow = square.row + dRow
            val targetCol = square.col + dCol
            val targetSq = CheckersSquare(targetRow, targetCol)

            if (targetSq.isValid && targetSq.isDarkPlayable && !state.board.containsKey(targetSq)) {
                moves.add(
                    CheckersMove(
                        from = square,
                        to = targetSq,
                        capturedSquare = null
                    )
                )
            }
        }

        return moves
    }

    /**
     * Returns valid movement direction deltas `(dRow, dCol)` for the given piece.
     */
    private fun getDirections(piece: CheckersPiece): List<Pair<Int, Int>> {
        if (piece.isKing) {
            return listOf(
                Pair(-1, -1),
                Pair(-1, 1),
                Pair(1, -1),
                Pair(1, 1)
            )
        }
        return if (piece.player == PlayerId.PLAYER_1) {
            // Red moves forward towards row 0
            listOf(Pair(-1, -1), Pair(-1, 1))
        } else {
            // Black moves forward towards row 7
            listOf(Pair(1, -1), Pair(1, 1))
        }
    }

    /**
     * Checks if moving to [square] triggers King promotion for [piece].
     */
    fun isPromotionSquare(piece: CheckersPiece, square: CheckersSquare): Boolean {
        if (piece.isKing) return false
        return if (piece.player == PlayerId.PLAYER_1) {
            square.row == 0
        } else {
            square.row == 7
        }
    }

    /**
     * Applies a valid [move] to [state], producing the next [CheckersGameState].
     * Returns a pair of (new state, move result).
     */
    fun applyMove(state: CheckersGameState, move: CheckersMove): Pair<CheckersGameState, CheckersMoveResult> {
        val legalMoves = getLegalMoves(state)
        val validMove = legalMoves.firstOrNull { it.from == move.from && it.to == move.to }
        if (validMove == null) {
            return Pair(
                state,
                CheckersMoveResult(success = false, errorReason = "Illegal move or capture is mandatory")
            )
        }

        val piece = state.board[move.from]
            ?: return Pair(state, CheckersMoveResult(success = false, errorReason = "No piece at from square"))

        val newBoard = state.board.toMutableMap()
        newBoard.remove(move.from)

        val isCapture = validMove.capturedSquare != null
        if (validMove.capturedSquare != null) {
            newBoard.remove(validMove.capturedSquare)
        }

        val promoted = isPromotionSquare(piece, validMove.to)
        val placedPiece = if (promoted) piece.promoteToKing() else piece
        newBoard[validMove.to] = placedPiece

        // Check if multi-jump continuation is required
        var mustContinueJumpingFrom: CheckersSquare? = null
        var turnEnds = true

        if (isCapture && !promoted) {
            // Temporary state to evaluate subsequent jumps for this exact piece
            val tempState = state.copy(board = newBoard)
            val subsequentJumps = getJumpMovesForSquare(tempState, validMove.to)
            if (subsequentJumps.isNotEmpty()) {
                mustContinueJumpingFrom = validMove.to
                turnEnds = false
            }
        }

        val nextPlayer = if (turnEnds) state.activePlayer.other() else state.activePlayer
        val movesWithoutProgress = if (isCapture || promoted) 0 else state.movesWithoutCaptureOrPromotion + 1

        val intermediateState = CheckersGameState(
            board = newBoard,
            activePlayer = nextPlayer,
            status = GameStatus.InProgress,
            mustContinueJumpingFrom = mustContinueJumpingFrom,
            movesWithoutCaptureOrPromotion = movesWithoutProgress,
            lastMove = validMove
        )

        // Evaluate terminal game status (Win / Draw)
        val finalStatus = evaluateGameStatus(intermediateState)
        val finalState = intermediateState.copy(status = finalStatus)

        return Pair(
            finalState,
            CheckersMoveResult(
                success = true,
                isCapture = isCapture,
                isKingPromotion = promoted,
                multiJumpAvailable = mustContinueJumpingFrom != null
            )
        )
    }

    /**
     * Evaluates whether the game is won, drawn, or ongoing.
     */
    fun evaluateGameStatus(state: CheckersGameState): GameStatus {
        val p1Count = state.board.values.count { it.player == PlayerId.PLAYER_1 }
        val p2Count = state.board.values.count { it.player == PlayerId.PLAYER_2 }

        if (p1Count == 0) {
            return GameStatus.MatchWon(matchWinner = PlayerId.PLAYER_2)
        }
        if (p2Count == 0) {
            return GameStatus.MatchWon(matchWinner = PlayerId.PLAYER_1)
        }

        // Check if active player has ANY legal moves
        val legalMoves = getLegalMoves(state)
        if (legalMoves.isEmpty()) {
            // Player with no legal moves loses
            val winner = state.activePlayer.other()
            return GameStatus.MatchWon(matchWinner = winner)
        }

        // Draw condition: 40 full moves (80 turns) without capture or promotion
        if (state.movesWithoutCaptureOrPromotion >= DRAW_TURNS_THRESHOLD) {
            return GameStatus.RoundDraw
        }

        return GameStatus.InProgress
    }
}
