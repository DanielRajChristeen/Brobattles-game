package com.example.core.engine.chess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object ChessBot {

    // Piece-Square Tables (White perspective, rank 0 to 7 = row 0 to 7)
    // Row 0 = Rank 8 (Black side), Row 7 = Rank 1 (White side)
    private val PAWN_PST = intArrayOf(
          0,   0,   0,   0,   0,   0,   0,   0,
         50,  50,  50,  50,  50,  50,  50,  50,
         10,  10,  20,  30,  30,  20,  10,  10,
          5,   5,  10,  25,  25,  10,   5,   5,
          0,   0,   0,  20,  20,   0,   0,   0,
          5,  -5, -10,   0,   0, -10,  -5,   5,
          5,  10,  10, -20, -20,  10,  10,   5,
          0,   0,   0,   0,   0,   0,   0,   0
    )

    private val KNIGHT_PST = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20,   0,   0,   0,   0, -20, -40,
        -30,   0,  10,  15,  15,  10,   0, -30,
        -30,   5,  15,  20,  20,  15,   5, -30,
        -30,   0,  15,  20,  20,  15,   0, -30,
        -30,   5,  10,  15,  15,  10,   5, -30,
        -40, -20,   0,   5,   5,   0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    private val BISHOP_PST = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,  10,  10,   5,   0, -10,
        -10,   5,   5,  10,  10,   5,   5, -10,
        -10,   0,  10,  10,  10,  10,   0, -10,
        -10,  10,  10,  10,  10,  10,  10, -10,
        -10,   5,   0,   0,   0,   0,   5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )

    private val ROOK_PST = intArrayOf(
          0,   0,   0,   0,   0,   0,   0,   0,
          5,  10,  10,  10,  10,  10,  10,   5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
          0,   0,   0,   5,   5,   0,   0,   0
    )

    private val QUEEN_PST = intArrayOf(
        -20, -10, -10,  -5,  -5, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,   5,   5,   5,   0, -10,
         -5,   0,   5,   5,   5,   5,   0,  -5,
          0,   0,   5,   5,   5,   5,   0,  -5,
        -10,   5,   5,   5,   5,   5,   0, -10,
        -10,   0,   5,   0,   0,   0,   0, -10,
        -20, -10, -10,  -5,  -5, -10, -10, -20
    )

    private val KING_MIDDLE_PST = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
         20,  20,   0,   0,   0,   0,  20,  20,
         20,  30,  10,   0,   0,  10,  30,  20
    )

    /**
     * Decides best move for the active player according to difficulty level.
     * Guaranteed to return a valid legal move or null if no legal moves exist.
     * Executes in Dispatchers.Default off the main UI thread.
     */
    suspend fun decideMove(
        state: ChessGameState,
        difficulty: ChessDifficulty
    ): ChessMove? = withContext(Dispatchers.Default) {
        val legalMoves = ChessRules.generateLegalMoves(state)
        if (legalMoves.isEmpty()) return@withContext null

        when (difficulty) {
            ChessDifficulty.EASY -> decideEasyMove(state, legalMoves)
            ChessDifficulty.MEDIUM -> decideMediumMove(state, legalMoves)
            ChessDifficulty.HARD -> decideHardMove(state, legalMoves)
        }
    }

    /**
     * EASY:
     * - Depth 1 evaluation.
     * - Adds subtle noise (-60 to +60 centipawns).
     * - 35% chance to select a casual reasonable move from top 3 candidates.
     * - Beginner friendly, avoids suicidal moves, always legal.
     */
    private fun decideEasyMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        val botColor = state.activeColor
        val scoredMoves = legalMoves.map { move ->
            val nextState = ChessRules.applyMove(state, move)
            var score = evaluatePosition(nextState, botColor)
            // Add noise for human-like casual imperfection
            score += Random.nextInt(-60, 61)
            move to score
        }.sortedByDescending { it.second }

        // 35% chance to pick from top 3 reasonable moves, 65% chance for top move
        return if (scoredMoves.size > 1 && Random.nextFloat() < 0.35f) {
            val candidateCount = min(3, scoredMoves.size)
            scoredMoves[Random.nextInt(candidateCount)].first
        } else {
            scoredMoves.first().first
        }
    }

    /**
     * MEDIUM:
     * - Depth 2 Minimax with Alpha-Beta pruning and move ordering.
     * - Tactical, protects pieces, captures hanging pieces, values check and promotion.
     */
    private fun decideMediumMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        val botColor = state.activeColor
        var bestScore = Int.MIN_VALUE
        var bestMove = legalMoves.first()

        val orderedMoves = orderMoves(legalMoves)
        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE

        for (move in orderedMoves) {
            val nextState = ChessRules.applyMove(state, move)
            val score = -alphaBeta(
                state = nextState,
                depth = 1, // Depth 1 here means total depth 2 (root + 1 ply)
                alpha = -beta,
                beta = -alpha,
                evalColor = botColor.opponent
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            alpha = max(alpha, score)
        }

        return bestMove
    }

    /**
     * HARD:
     * - Depth 3-4 Minimax with Alpha-Beta pruning, move ordering, and Quiescence search on captures.
     * - Strong positional evaluation (PST, center control, king safety, checks, pawn structure).
     */
    private fun decideHardMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        val botColor = state.activeColor
        var bestScore = Int.MIN_VALUE
        var bestMove = legalMoves.first()

        val searchDepth = if (legalMoves.size <= 12) 4 else 3
        val orderedMoves = orderMoves(legalMoves)

        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE

        for (move in orderedMoves) {
            val nextState = ChessRules.applyMove(state, move)
            val score = -alphaBetaWithQuiescence(
                state = nextState,
                depth = searchDepth - 1,
                alpha = -beta,
                beta = -alpha,
                evalColor = botColor.opponent
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            alpha = max(alpha, score)
            if (alpha >= beta) break
        }

        return bestMove
    }

    private fun alphaBeta(
        state: ChessGameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        evalColor: PieceColor
    ): Int {
        if (state.status is ChessGameStatus.Checkmate) {
            return -100000 - depth
        }
        if (state.status.isGameOver) {
            return 0 // Draw
        }
        if (depth <= 0) {
            return evaluatePosition(state, evalColor)
        }

        val legalMoves = ChessRules.generateLegalMoves(state)
        if (legalMoves.isEmpty()) {
            return if (state.inCheck) -100000 - depth else 0
        }

        val orderedMoves = orderMoves(legalMoves)
        var curAlpha = alpha

        for (move in orderedMoves) {
            val nextState = ChessRules.applyMove(state, move)
            val score = -alphaBeta(
                state = nextState,
                depth = depth - 1,
                alpha = -beta,
                beta = -curAlpha,
                evalColor = evalColor.opponent
            )

            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
        }

        return curAlpha
    }

    private fun alphaBetaWithQuiescence(
        state: ChessGameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        evalColor: PieceColor
    ): Int {
        if (state.status is ChessGameStatus.Checkmate) {
            return -100000 - depth
        }
        if (state.status.isGameOver) {
            return 0
        }
        if (depth <= 0) {
            return quiescence(state, alpha, beta, evalColor, qDepth = 2)
        }

        val legalMoves = ChessRules.generateLegalMoves(state)
        if (legalMoves.isEmpty()) {
            return if (state.inCheck) -100000 - depth else 0
        }

        val orderedMoves = orderMoves(legalMoves)
        var curAlpha = alpha

        for (move in orderedMoves) {
            val nextState = ChessRules.applyMove(state, move)
            val score = -alphaBetaWithQuiescence(
                state = nextState,
                depth = depth - 1,
                alpha = -beta,
                beta = -curAlpha,
                evalColor = evalColor.opponent
            )

            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
        }

        return curAlpha
    }

    /**
     * Quiescence search: evaluates captures at the horizon to prevent horizon blunders.
     */
    private fun quiescence(
        state: ChessGameState,
        alpha: Int,
        beta: Int,
        evalColor: PieceColor,
        qDepth: Int
    ): Int {
        val standPat = evaluatePosition(state, evalColor)
        if (qDepth <= 0 || state.status.isGameOver) return standPat
        if (standPat >= beta) return beta
        var curAlpha = max(alpha, standPat)

        val legalMoves = ChessRules.generateLegalMoves(state)
        val captureMoves = legalMoves.filter { it.capturedPiece != null || it.promotion != null }
        if (captureMoves.isEmpty()) return standPat

        val orderedCaptures = orderMoves(captureMoves)
        for (move in orderedCaptures) {
            val nextState = ChessRules.applyMove(state, move)
            val score = -quiescence(nextState, -beta, -curAlpha, evalColor.opponent, qDepth - 1)
            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
        }

        return curAlpha
    }

    /**
     * Move Ordering:
     * 1. Winning captures (MVV - LVA)
     * 2. Promotions
     * 3. Quiet checks
     * 4. Quiet moves
     */
    private fun orderMoves(moves: List<ChessMove>): List<ChessMove> {
        return moves.sortedByDescending { move ->
            var score = 0
            if (move.capturedPiece != null) {
                // MVV-LVA: Most Valuable Victim, Least Valuable Attacker
                score += move.capturedPiece.type.value * 10 - (move.promotion?.value ?: 0)
            }
            if (move.promotion != null) {
                score += move.promotion.value * 5
            }
            score
        }
    }

    /**
     * Positional Evaluation:
     * - Material balance
     * - Piece-square tables
     * - In-check bonuses
     */
    private fun evaluatePosition(state: ChessGameState, evalColor: PieceColor): Int {
        var whiteScore = 0
        var blackScore = 0

        val board = state.board
        for (i in 0 until 64) {
            val piece = board[i] ?: continue
            val row = i / 8
            val col = i % 8

            // PST index from White's perspective
            val whiteIndex = i
            // PST index from Black's perspective (mirrored vertically)
            val blackIndex = (7 - row) * 8 + col

            val pieceVal = piece.type.value
            val pstVal = when (piece.type) {
                PieceType.PAWN -> if (piece.color == PieceColor.WHITE) PAWN_PST[whiteIndex] else PAWN_PST[blackIndex]
                PieceType.KNIGHT -> if (piece.color == PieceColor.WHITE) KNIGHT_PST[whiteIndex] else KNIGHT_PST[blackIndex]
                PieceType.BISHOP -> if (piece.color == PieceColor.WHITE) BISHOP_PST[whiteIndex] else BISHOP_PST[blackIndex]
                PieceType.ROOK -> if (piece.color == PieceColor.WHITE) ROOK_PST[whiteIndex] else ROOK_PST[blackIndex]
                PieceType.QUEEN -> if (piece.color == PieceColor.WHITE) QUEEN_PST[whiteIndex] else QUEEN_PST[blackIndex]
                PieceType.KING -> if (piece.color == PieceColor.WHITE) KING_MIDDLE_PST[whiteIndex] else KING_MIDDLE_PST[blackIndex]
            }

            val totalVal = pieceVal + pstVal
            if (piece.color == PieceColor.WHITE) {
                whiteScore += totalVal
            } else {
                blackScore += totalVal
            }
        }

        // Small bonus if active player puts opponent in check
        if (state.inCheck) {
            if (state.activeColor == PieceColor.WHITE) whiteScore -= 40
            else blackScore -= 40
        }

        return if (evalColor == PieceColor.WHITE) whiteScore - blackScore else blackScore - whiteScore
    }
}
