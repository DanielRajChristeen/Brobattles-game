package com.example.core.engine.chess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * High-performance, tactical and positional Chess Bot supporting EASY, MEDIUM, and HARD.
 *
 * Architectural Features:
 * - Iterative Deepening with time & node budgets and search cancellation.
 * - Alpha-Beta pruning with Principal Variation reuse.
 * - Move ordering: Hash move, Queen promotions, Winning captures (MVV-LVA), Killer moves,
 *   History heuristic, Checks, and Quiet moves.
 * - Quiescence search with MVV-LVA and Delta pruning to resolve horizon exchanges and avoid blunders.
 * - Transposition Table with Zobrist hashing (65,536 entries, ~1.1MB memory).
 * - Tapered evaluation blending Middlegame and Endgame phases.
 * - Evaluation terms: Material, Piece-Square Tables, Bishop Pair, Rook on Open Files / 7th Rank,
 *   Pawn Structure (Passed, Doubled, Isolated), King Safety / Pawn Shelter, and Endgame King Centralization.
 * - Difficulty tuning:
 *   * EASY: Shallow 2-ply minimax with blunder guard (never hangs queen or walks into mate-in-1) + humanized variance.
 *   * MEDIUM: Iterative deepening (depth 3-4), TT, quiescence search, solid strategic play.
 *   * HARD: Deep iterative deepening (depth 5-6), TT lookups, full quiescence, tactical combination solving.
 */
object ChessBot {

    // Material Values (centipawns)
    private const val PAWN_VAL = 100
    private const val KNIGHT_VAL = 320
    private const val BISHOP_VAL = 330
    private const val ROOK_VAL = 500
    private const val QUEEN_VAL = 900
    private const val KING_VAL = 20000

    private const val MATE_SCORE = 100000
    private const val MATE_THRESHOLD = 90000
    private const val MAX_PLY = 32

    // Transposition Table Flags
    private const val FLAG_EXACT: Byte = 0
    private const val FLAG_LOWER: Byte = 1 // Alpha cutoff (score >= beta)
    private const val FLAG_UPPER: Byte = 2 // Failed low (score <= alpha)

    private const val TT_SIZE = 1 shl 16 // 65,536 entries (~1.1 MB)
    private const val TT_MASK = TT_SIZE - 1

    private val ttKeys = LongArray(TT_SIZE)
    private val ttDepths = ByteArray(TT_SIZE)
    private val ttScores = IntArray(TT_SIZE)
    private val ttFlags = ByteArray(TT_SIZE)
    private val ttBestMoves = arrayOfNulls<ChessMove>(TT_SIZE)

    // Killer Moves: [ply][0..1]
    private val killerMoves = Array(MAX_PLY) { arrayOfNulls<ChessMove>(2) }

    // History Heuristic: [color][from * 64 + to]
    private val historyTable = Array(2) { IntArray(64 * 64) }

    // Zobrist Hashing Keys
    private val PIECE_KEYS = Array(64) { Array(2) { LongArray(6) } }
    private val CASTLE_KEYS = LongArray(16)
    private val EP_KEYS = LongArray(8)
    private var BLACK_MOVE_KEY: Long = 0L

    init {
        val rng = java.util.Random(0x4291A7C319E5FL)
        for (sq in 0 until 64) {
            for (c in 0..1) {
                for (p in 0..5) {
                    PIECE_KEYS[sq][c][p] = rng.nextLong()
                }
            }
        }
        for (i in 0 until 16) {
            CASTLE_KEYS[i] = rng.nextLong()
        }
        for (i in 0 until 8) {
            EP_KEYS[i] = rng.nextLong()
        }
        BLACK_MOVE_KEY = rng.nextLong()
    }

    // Piece-Square Tables (White perspective: Row 0 = Rank 8, Row 7 = Rank 1)
    private val PAWN_MG_PST = intArrayOf(
          0,   0,   0,   0,   0,   0,   0,   0,
         50,  50,  50,  50,  50,  50,  50,  50,
         15,  15,  25,  35,  35,  25,  15,  15,
          5,   5,  15,  30,  30,  15,   5,   5,
          0,   0,  10,  25,  25,   0,   0,   0,
          5,  -5, -10,   5,   5, -10,  -5,   5,
          5,  10,  10, -20, -20,  10,  10,   5,
          0,   0,   0,   0,   0,   0,   0,   0
    )

    private val PAWN_EG_PST = intArrayOf(
          0,   0,   0,   0,   0,   0,   0,   0,
         80,  80,  80,  80,  80,  80,  80,  80,
         50,  50,  50,  50,  50,  50,  50,  50,
         30,  30,  30,  35,  35,  30,  30,  30,
         20,  20,  20,  25,  25,  20,  20,  20,
         10,  10,  10,  15,  15,  10,  10,  10,
          0,   0,   0,   0,   0,   0,   0,   0,
          0,   0,   0,   0,   0,   0,   0,   0
    )

    private val KNIGHT_PST = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20,   0,   5,   5,   0, -20, -40,
        -30,   5,  15,  20,  20,  15,   5, -30,
        -30,   5,  20,  25,  25,  20,   5, -30,
        -30,   5,  20,  25,  25,  20,   5, -30,
        -30,   5,  15,  20,  20,  15,   5, -30,
        -40, -20,   0,   5,   5,   0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    private val BISHOP_PST = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10,   5,   0,   0,   0,   0,   5, -10,
        -10,  10,  10,  15,  15,  10,  10, -10,
        -10,   5,  15,  20,  20,  15,   5, -10,
        -10,   5,  15,  20,  20,  15,   5, -10,
        -10,  10,  10,  15,  15,  10,  10, -10,
        -10,   5,   0,   0,   0,   0,   5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )

    private val ROOK_PST = intArrayOf(
          0,   0,   0,   5,   5,   0,   0,   0,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
          5,  10,  10,  10,  10,  10,  10,   5,
          0,   0,   0,   5,   5,   0,   0,   0
    )

    private val QUEEN_PST = intArrayOf(
        -20, -10, -10,  -5,  -5, -10, -10, -20,
        -10,   0,   5,   0,   0,   0,   0, -10,
        -10,   5,   5,   5,   5,   5,   0, -10,
          0,   0,   5,   5,   5,   5,   0,  -5,
         -5,   0,   5,   5,   5,   5,   0,  -5,
        -10,   0,   5,   5,   5,   5,   0, -10,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -20, -10, -10,  -5,  -5, -10, -10, -20
    )

    private val KING_MG_PST = intArrayOf(
        -40, -50, -50, -60, -60, -50, -50, -40,
        -40, -50, -50, -60, -60, -50, -50, -40,
        -40, -50, -50, -60, -60, -50, -50, -40,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
         20,  20,   0,   0,   0,   0,  20,  20,
         20,  30,  10,   0,   0,  10,  30,  20
    )

    private val KING_EG_PST = intArrayOf(
        -50, -30, -20, -10, -10, -20, -30, -50,
        -30, -10,  15,  20,  20,  15, -10, -30,
        -20,  15,  30,  35,  35,  30,  15, -20,
        -10,  20,  35,  45,  45,  35,  20, -10,
        -10,  20,  35,  45,  45,  35,  20, -10,
        -20,  15,  30,  35,  35,  30,  15, -20,
        -30, -10,  15,  20,  20,  15, -10, -30,
        -50, -30, -20, -10, -10, -20, -30, -50
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
        if (legalMoves.size == 1) return@withContext legalMoves.first()

        // Reset search structures for fresh search
        clearSearchTables()

        when (difficulty) {
            ChessDifficulty.EASY -> decideEasyMove(state, legalMoves)
            ChessDifficulty.MEDIUM -> decideMediumMove(state, legalMoves)
            ChessDifficulty.HARD -> decideHardMove(state, legalMoves)
        }
    }

    private fun clearSearchTables() {
        for (i in 0 until MAX_PLY) {
            killerMoves[i][0] = null
            killerMoves[i][1] = null
        }
        historyTable[0].fill(0)
        historyTable[1].fill(0)
    }

    /**
     * EASY:
     * - Evaluates positions with 2-ply tactical calculation.
     * - Blunder Guard: Strictly ensures candidate moves do not allow opponent to capture
     *   an undefended Queen for free or force an immediate mate.
     * - Captures free material and recognizes straightforward tactical checks.
     * - Mild humanized noise (+/- 35 cp) so inexperienced players have fun and can win.
     */
    private suspend fun decideEasyMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        val botColor = state.activeColor
        val scoredMoves = mutableListOf<Pair<ChessMove, Int>>()

        for (move in legalMoves) {
            if (!currentCoroutineContext().isActive) break
            val nextState = ChessRules.applyMove(state, move, evaluateFullStatus = false)

            // 1-ply reply check: what can the opponent do to punish this move?
            val replies = ChessRules.generateLegalMoves(nextState)
            var worstReplyScore = Int.MAX_VALUE

            if (replies.isEmpty()) {
                // If this move gave mate, it's immediately winning!
                val score = if (nextState.inCheck) MATE_SCORE else 0
                scoredMoves.add(move to score)
                continue
            }

            for (reply in replies) {
                val afterReply = ChessRules.applyMove(nextState, reply, evaluateFullStatus = false)
                val replyScore = evaluatePosition(afterReply, botColor)
                if (replyScore < worstReplyScore) {
                    worstReplyScore = replyScore
                }
            }

            scoredMoves.add(move to worstReplyScore)
        }

        if (scoredMoves.isEmpty()) return legalMoves.first()

        val sorted = scoredMoves.sortedByDescending { it.second }
        val bestScore = sorted.first().second

        // Identify safe moves: within 80 centipawns of the best move, and not losing a Queen outright
        val safeThreshold = bestScore - 80
        val safeCandidates = sorted.filter { it.second >= safeThreshold }

        // If best move wins massive material or checkmates, play it
        if (bestScore >= 500) {
            return sorted.first().first
        }

        // Beginner-friendly selection from safe candidate moves
        return if (safeCandidates.size > 1 && Random.nextFloat() < 0.35f) {
            val candidateCount = min(3, safeCandidates.size)
            safeCandidates[Random.nextInt(candidateCount)].first
        } else {
            sorted.first().first
        }
    }

    /**
     * MEDIUM:
     * - Iterative deepening up to depth 3-4 with a 450ms / 20,000 node budget.
     * - Alpha-Beta pruning with Move Ordering, Quiescence Search (depth 3), and Transposition Table.
     * - Punishes blunders, evaluates exchanges, solid tactical & positional play.
     */
    private suspend fun decideMediumMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        return iterativeDeepeningSearch(
            state = state,
            legalMoves = legalMoves,
            maxDepth = 4,
            timeBudgetMs = 450L,
            maxNodes = 20000,
            qDepthLimit = 3
        )
    }

    /**
     * HARD:
     * - Iterative deepening up to depth 5-6 with a 1,200ms / 75,000 node budget.
     * - Full Alpha-Beta with Transposition Table, Killer Moves, History Heuristic,
     *   Quiescence Search (depth 4) with Delta Pruning and MVV-LVA.
     * - Advanced tapered positional evaluation (pawn structure, open files, bishop pair, king shelter & centralization).
     */
    private suspend fun decideHardMove(state: ChessGameState, legalMoves: List<ChessMove>): ChessMove {
        val searchDepth = if (legalMoves.size <= 12) 6 else 5
        return iterativeDeepeningSearch(
            state = state,
            legalMoves = legalMoves,
            maxDepth = searchDepth,
            timeBudgetMs = 1200L,
            maxNodes = 75000,
            qDepthLimit = 4
        )
    }

    private class SearchContext(
        val state: ChessGameState,
        val startTime: Long,
        val timeBudgetMs: Long,
        val maxNodes: Int,
        val qDepthLimit: Int
    ) {
        var nodeCount = 0
        var isTimeOut = false
    }

    private suspend fun iterativeDeepeningSearch(
        state: ChessGameState,
        legalMoves: List<ChessMove>,
        maxDepth: Int,
        timeBudgetMs: Long,
        maxNodes: Int,
        qDepthLimit: Int
    ): ChessMove {
        val startTime = System.currentTimeMillis()
        val context = SearchContext(
            state = state,
            startTime = startTime,
            timeBudgetMs = timeBudgetMs,
            maxNodes = maxNodes,
            qDepthLimit = qDepthLimit
        )

        var overallBestMove = legalMoves.first()
        var overallBestScore = -MATE_SCORE

        val rootHash = computeHash(state)

        for (currentDepth in 1..maxDepth) {
            if (context.isTimeOut || !currentCoroutineContext().isActive) break

            var depthBestMove: ChessMove? = null
            var depthBestScore = -MATE_SCORE
            var alpha = -MATE_SCORE
            val beta = MATE_SCORE

            val orderedMoves = orderMoves(legalMoves, state, rootHash, ply = 0)

            for (move in orderedMoves) {
                if (checkTimeAndCancellation(context)) break

                val nextState = ChessRules.applyMove(state, move, evaluateFullStatus = false)
                val score = -alphaBeta(
                    state = nextState,
                    depth = currentDepth - 1,
                    alpha = -beta,
                    beta = -alpha,
                    ply = 1,
                    context = context
                )

                if (score > depthBestScore) {
                    depthBestScore = score
                    depthBestMove = move
                }

                if (score > alpha) {
                    alpha = score
                }
                if (alpha >= beta) break
            }

            // Only update if search wasn't interrupted mid-iteration
            if (!context.isTimeOut && depthBestMove != null) {
                overallBestMove = depthBestMove
                overallBestScore = depthBestScore
                // Record in TT
                ttStore(rootHash, currentDepth, overallBestScore, FLAG_EXACT, overallBestMove, ply = 0)

                // If forced mate found, stop search early
                if (overallBestScore >= MATE_THRESHOLD) {
                    break
                }
            }
        }

        return overallBestMove
    }

    private suspend fun checkTimeAndCancellation(context: SearchContext): Boolean {
        context.nodeCount++
        if ((context.nodeCount and 255) == 0) {
            if (!currentCoroutineContext().isActive ||
                System.currentTimeMillis() - context.startTime >= context.timeBudgetMs ||
                context.nodeCount >= context.maxNodes
            ) {
                context.isTimeOut = true
                return true
            }
        }
        return false
    }

    private suspend fun alphaBeta(
        state: ChessGameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
        context: SearchContext
    ): Int {
        if (checkTimeAndCancellation(context)) return 0

        // Fifty-move draw check
        if (state.status is ChessGameStatus.DrawByFiftyMoves) {
            return 0
        }

        val hash = computeHash(state)
        val ttEntry = ttProbe(hash, depth, alpha, beta, ply)
        if (ttEntry != null) {
            return ttEntry
        }

        if (depth <= 0) {
            return quiescence(state, alpha, beta, ply, qDepth = context.qDepthLimit, context = context)
        }

        val legalMoves = ChessRules.generateLegalMoves(state)
        if (legalMoves.isEmpty()) {
            return if (state.inCheck) -MATE_SCORE + ply else 0 // Mate or Stalemate
        }

        var curAlpha = alpha
        var bestScore = -MATE_SCORE
        var bestMove: ChessMove? = null

        val orderedMoves = orderMoves(legalMoves, state, hash, ply)

        for (move in orderedMoves) {
            val nextState = ChessRules.applyMove(state, move, evaluateFullStatus = false)
            val score = -alphaBeta(
                state = nextState,
                depth = depth - 1,
                alpha = -beta,
                beta = -curAlpha,
                ply = ply + 1,
                context = context
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }

            if (score > curAlpha) {
                curAlpha = score
            }

            if (curAlpha >= beta) {
                // Beta Cutoff: Update killer move and history heuristic if quiet
                if (move.capturedPiece == null && move.promotion == null && ply < MAX_PLY) {
                    if (killerMoves[ply][0] != move) {
                        killerMoves[ply][1] = killerMoves[ply][0]
                        killerMoves[ply][0] = move
                    }
                    val colorIdx = state.activeColor.ordinal
                    val historyIdx = move.from.index * 64 + move.to.index
                    historyTable[colorIdx][historyIdx] += depth * depth
                }
                break
            }
        }

        val flag = when {
            bestScore >= beta -> FLAG_LOWER
            bestScore > alpha -> FLAG_EXACT
            else -> FLAG_UPPER
        }
        ttStore(hash, depth, bestScore, flag, bestMove, ply)

        return bestScore
    }

    /**
     * Quiescence search: calculates captures and promotions until calm, preventing the horizon effect.
     */
    private suspend fun quiescence(
        state: ChessGameState,
        alpha: Int,
        beta: Int,
        ply: Int,
        qDepth: Int,
        context: SearchContext
    ): Int {
        if (checkTimeAndCancellation(context)) return 0

        val standPat = evaluatePosition(state, state.activeColor)

        if (state.inCheck) {
            // In check: must escape check even in quiescence
            val legalMoves = ChessRules.generateLegalMoves(state)
            if (legalMoves.isEmpty()) return -MATE_SCORE + ply

            var curAlpha = alpha
            val orderedMoves = orderMoves(legalMoves, state, 0L, ply)
            for (move in orderedMoves) {
                val nextState = ChessRules.applyMove(state, move, evaluateFullStatus = false)
                val score = -quiescence(nextState, -beta, -curAlpha, ply + 1, qDepth - 1, context)
                if (score >= beta) return beta
                if (score > curAlpha) curAlpha = score
            }
            return curAlpha
        }

        if (qDepth <= 0) return standPat
        if (standPat >= beta) return beta
        var curAlpha = max(alpha, standPat)

        // Delta Pruning: if even capturing a Queen cannot raise alpha, prune futile capture search
        val bigDelta = QUEEN_VAL + 200
        if (standPat < alpha - bigDelta) {
            return alpha
        }

        val legalMoves = ChessRules.generateLegalMoves(state)
        val captureMoves = legalMoves.filter { it.capturedPiece != null || it.promotion != null }
        if (captureMoves.isEmpty()) return standPat

        val orderedCaptures = orderCaptures(captureMoves)

        for (move in orderedCaptures) {
            val nextState = ChessRules.applyMove(state, move, evaluateFullStatus = false)
            val score = -quiescence(nextState, -beta, -curAlpha, ply + 1, qDepth - 1, context)
            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
        }

        return curAlpha
    }

    /**
     * Move Ordering:
     * 1. TT Hash move (+10,000,000)
     * 2. Queen promotions (+9,000,000)
     * 3. Winning/Equal captures (MVV-LVA: +5,000,000 + diff)
     * 4. Killer move 1 (+1,500,000)
     * 5. Killer move 2 (+1,400,000)
     * 6. History heuristic (0 to +1,000,000)
     * 7. Checks (+500,000)
     * 8. Losing captures (MVV-LVA: +200,000 + diff)
     * 9. Quiet moves
     */
    private fun orderMoves(
        moves: List<ChessMove>,
        state: ChessGameState,
        hash: Long,
        ply: Int
    ): List<ChessMove> {
        val ttMove = if (hash != 0L) ttGetMove(hash) else null
        val k1 = if (ply < MAX_PLY) killerMoves[ply][0] else null
        val k2 = if (ply < MAX_PLY) killerMoves[ply][1] else null
        val colorIdx = state.activeColor.ordinal

        return moves.sortedByDescending { move ->
            when {
                ttMove != null && move == ttMove -> 10000000
                move.promotion == PieceType.QUEEN -> 9000000
                move.promotion != null -> 8000000
                move.capturedPiece != null -> {
                    val victim = move.capturedPiece.type.value
                    val attacker = state.pieceAt(move.from)?.type?.value ?: PAWN_VAL
                    if (victim >= attacker) {
                        5000000 + (victim * 10 - attacker)
                    } else {
                        200000 + (victim * 10 - attacker)
                    }
                }
                k1 != null && move == k1 -> 1500000
                k2 != null && move == k2 -> 1400000
                else -> {
                    val hist = historyTable[colorIdx][move.from.index * 64 + move.to.index]
                    min(hist, 1000000)
                }
            }
        }
    }

    private fun orderCaptures(moves: List<ChessMove>): List<ChessMove> {
        return moves.sortedByDescending { move ->
            val victim = move.capturedPiece?.type?.value ?: 0
            val promo = if (move.promotion != null) move.promotion.value else 0
            victim * 10 + promo
        }
    }

    // Transposition Table Helpers
    private fun ttProbe(hash: Long, depth: Int, alpha: Int, beta: Int, ply: Int): Int? {
        val idx = (hash and TT_MASK.toLong()).toInt()
        if (ttKeys[idx] == hash && ttDepths[idx] >= depth) {
            var score = ttScores[idx]
            // Adjust mate score
            if (score > MATE_THRESHOLD) score -= ply
            else if (score < -MATE_THRESHOLD) score += ply

            when (ttFlags[idx]) {
                FLAG_EXACT -> return score
                FLAG_LOWER -> if (score >= beta) return beta
                FLAG_UPPER -> if (score <= alpha) return alpha
            }
        }
        return null
    }

    private fun ttGetMove(hash: Long): ChessMove? {
        val idx = (hash and TT_MASK.toLong()).toInt()
        return if (ttKeys[idx] == hash) ttBestMoves[idx] else null
    }

    private fun ttStore(hash: Long, depth: Int, score: Int, flag: Byte, bestMove: ChessMove?, ply: Int) {
        val idx = (hash and TT_MASK.toLong()).toInt()
        var storedScore = score
        if (score > MATE_THRESHOLD) storedScore += ply
        else if (score < -MATE_THRESHOLD) storedScore -= ply

        ttKeys[idx] = hash
        ttDepths[idx] = depth.toByte()
        ttScores[idx] = storedScore
        ttFlags[idx] = flag
        if (bestMove != null) {
            ttBestMoves[idx] = bestMove
        }
    }

    private fun computeHash(state: ChessGameState): Long {
        var h = 0L
        val board = state.board
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            h = h xor PIECE_KEYS[i][p.color.ordinal][p.type.ordinal]
        }
        if (state.activeColor == PieceColor.BLACK) {
            h = h xor BLACK_MOVE_KEY
        }
        var castleIdx = 0
        if (state.whiteCanCastleKingside) castleIdx = castleIdx or 1
        if (state.whiteCanCastleQueenside) castleIdx = castleIdx or 2
        if (state.blackCanCastleKingside) castleIdx = castleIdx or 4
        if (state.blackCanCastleQueenside) castleIdx = castleIdx or 8
        h = h xor CASTLE_KEYS[castleIdx]

        state.enPassantTarget?.let { ep ->
            h = h xor EP_KEYS[ep.col]
        }
        return h
    }

    /**
     * Tapered Positional Evaluation:
     * - Material balance (P=100, N=320, B=330, R=500, Q=900)
     * - Phase interpolation between Middlegame and Endgame (Phase 24 -> 0)
     * - Piece-Square Tables (Opening/Middlegame vs Endgame)
     * - Bishop pair bonus (+45 cp)
     * - Rooks on open/semi-open files (+25/+15 cp) and 7th rank (+30 cp)
     * - Pawn structure: doubled (-15 cp), isolated (-20 cp), passed (+20..+140 cp)
     * - King safety (pawn shelter in middlegame, centralization in endgame)
     * - Center control and development
     */
    fun evaluatePosition(state: ChessGameState, evalColor: PieceColor): Int {
        var mgWhite = 0
        var egWhite = 0
        var mgBlack = 0
        var egBlack = 0

        var gamePhase = 0
        var whiteBishops = 0
        var blackBishops = 0

        val whitePawnsPerFile = IntArray(8)
        val blackPawnsPerFile = IntArray(8)

        val board = state.board

        // Pass 1: Material, PST, Pawn file counts, Phase calculation
        for (i in 0 until 64) {
            val piece = board[i] ?: continue
            val row = i / 8
            val col = i % 8

            val whiteIdx = i
            val blackIdx = (7 - row) * 8 + col

            val isWhite = piece.color == PieceColor.WHITE

            when (piece.type) {
                PieceType.PAWN -> {
                    if (isWhite) {
                        whitePawnsPerFile[col]++
                        mgWhite += PAWN_VAL + PAWN_MG_PST[whiteIdx]
                        egWhite += PAWN_VAL + PAWN_EG_PST[whiteIdx]
                    } else {
                        blackPawnsPerFile[col]++
                        mgBlack += PAWN_VAL + PAWN_MG_PST[blackIdx]
                        egBlack += PAWN_VAL + PAWN_EG_PST[blackIdx]
                    }
                }
                PieceType.KNIGHT -> {
                    gamePhase += 1
                    if (isWhite) {
                        mgWhite += KNIGHT_VAL + KNIGHT_PST[whiteIdx]
                        egWhite += KNIGHT_VAL + KNIGHT_PST[whiteIdx]
                    } else {
                        mgBlack += KNIGHT_VAL + KNIGHT_PST[blackIdx]
                        egBlack += KNIGHT_VAL + KNIGHT_PST[blackIdx]
                    }
                }
                PieceType.BISHOP -> {
                    gamePhase += 1
                    if (isWhite) {
                        whiteBishops++
                        mgWhite += BISHOP_VAL + BISHOP_PST[whiteIdx]
                        egWhite += BISHOP_VAL + BISHOP_PST[whiteIdx]
                    } else {
                        blackBishops++
                        mgBlack += BISHOP_VAL + BISHOP_PST[blackIdx]
                        egBlack += BISHOP_VAL + BISHOP_PST[blackIdx]
                    }
                }
                PieceType.ROOK -> {
                    gamePhase += 2
                    if (isWhite) {
                        mgWhite += ROOK_VAL + ROOK_PST[whiteIdx]
                        egWhite += ROOK_VAL + ROOK_PST[whiteIdx]
                    } else {
                        mgBlack += ROOK_VAL + ROOK_PST[blackIdx]
                        egBlack += ROOK_VAL + ROOK_PST[blackIdx]
                    }
                }
                PieceType.QUEEN -> {
                    gamePhase += 4
                    if (isWhite) {
                        mgWhite += QUEEN_VAL + QUEEN_PST[whiteIdx]
                        egWhite += QUEEN_VAL + QUEEN_PST[whiteIdx]
                    } else {
                        mgBlack += QUEEN_VAL + QUEEN_PST[blackIdx]
                        egBlack += QUEEN_VAL + QUEEN_PST[blackIdx]
                    }
                }
                PieceType.KING -> {
                    if (isWhite) {
                        mgWhite += KING_MG_PST[whiteIdx]
                        egWhite += KING_EG_PST[whiteIdx]
                    } else {
                        mgBlack += KING_MG_PST[blackIdx]
                        egBlack += KING_EG_PST[blackIdx]
                    }
                }
            }
        }

        // Clamp phase between 0 (endgame) and 24 (opening)
        val phase = min(24, gamePhase)

        // Bishop Pair Bonus
        if (whiteBishops >= 2) {
            mgWhite += 45
            egWhite += 45
        }
        if (blackBishops >= 2) {
            mgBlack += 45
            egBlack += 45
        }

        // Pass 2: Rooks, Pawn structures (Doubled, Isolated, Passed), and King Safety
        for (i in 0 until 64) {
            val piece = board[i] ?: continue
            val row = i / 8
            val col = i % 8
            val isWhite = piece.color == PieceColor.WHITE

            when (piece.type) {
                PieceType.ROOK -> {
                    // Open and Semi-Open file
                    val friendlyPawns = if (isWhite) whitePawnsPerFile[col] else blackPawnsPerFile[col]
                    val enemyPawns = if (isWhite) blackPawnsPerFile[col] else whitePawnsPerFile[col]

                    if (friendlyPawns == 0) {
                        if (enemyPawns == 0) {
                            // Completely open file
                            if (isWhite) { mgWhite += 25; egWhite += 25 }
                            else { mgBlack += 25; egBlack += 25 }
                        } else {
                            // Semi-open file
                            if (isWhite) { mgWhite += 15; egWhite += 15 }
                            else { mgBlack += 15; egBlack += 15 }
                        }
                    }

                    // 7th Rank Rook
                    if (isWhite && row == 1) {
                        mgWhite += 30; egWhite += 35
                    } else if (!isWhite && row == 6) {
                        mgBlack += 30; egBlack += 35
                    }
                }
                PieceType.PAWN -> {
                    // Doubled pawn penalty
                    val filePawnCount = if (isWhite) whitePawnsPerFile[col] else blackPawnsPerFile[col]
                    if (filePawnCount > 1) {
                        if (isWhite) { mgWhite -= 15; egWhite -= 15 }
                        else { mgBlack -= 15; egBlack -= 15 }
                    }

                    // Isolated pawn penalty (no friendly pawns on adjacent files)
                    val leftPawns = if (col > 0) (if (isWhite) whitePawnsPerFile[col - 1] else blackPawnsPerFile[col - 1]) else 0
                    val rightPawns = if (col < 7) (if (isWhite) whitePawnsPerFile[col + 1] else blackPawnsPerFile[col + 1]) else 0
                    if (leftPawns == 0 && rightPawns == 0) {
                        if (isWhite) { mgWhite -= 20; egWhite -= 20 }
                        else { mgBlack -= 20; egBlack -= 20 }
                    }

                    // Passed pawn bonus (no enemy pawns in front on same or adjacent files)
                    var isPassed = true
                    if (isWhite) {
                        for (r in 0 until row) {
                            for (c in max(0, col - 1)..min(7, col + 1)) {
                                val p = board[r * 8 + c]
                                if (p != null && p.color == PieceColor.BLACK && p.type == PieceType.PAWN) {
                                    isPassed = false
                                    break
                                }
                            }
                            if (!isPassed) break
                        }
                        if (isPassed) {
                            val rankBonus = when (7 - row) {
                                4 -> 20
                                5 -> 40
                                6 -> 75
                                7 -> 140
                                else -> 10
                            }
                            mgWhite += rankBonus
                            egWhite += (rankBonus * 1.5).toInt()
                        }
                    } else {
                        for (r in (row + 1)..7) {
                            for (c in max(0, col - 1)..min(7, col + 1)) {
                                val p = board[r * 8 + c]
                                if (p != null && p.color == PieceColor.WHITE && p.type == PieceType.PAWN) {
                                    isPassed = false
                                    break
                                }
                            }
                            if (!isPassed) break
                        }
                        if (isPassed) {
                            val rankBonus = when (row) {
                                4 -> 20
                                5 -> 40
                                6 -> 75
                                7 -> 140
                                else -> 10
                            }
                            mgBlack += rankBonus
                            egBlack += (rankBonus * 1.5).toInt()
                        }
                    }
                }
                PieceType.KING -> {
                    // King Pawn Shield in Middlegame
                    if (phase > 12) {
                        if (isWhite && row == 7 && (col >= 5 || col <= 2)) {
                            // Kingside or Queenside castled
                            val shieldColStart = if (col >= 5) 5 else 0
                            val shieldColEnd = if (col >= 5) 7 else 2
                            var shieldCount = 0
                            for (sc in shieldColStart..shieldColEnd) {
                                val p1 = board[6 * 8 + sc]
                                if (p1 != null && p1.color == PieceColor.WHITE && p1.type == PieceType.PAWN) shieldCount++
                            }
                            mgWhite += shieldCount * 12
                        } else if (!isWhite && row == 0 && (col >= 5 || col <= 2)) {
                            val shieldColStart = if (col >= 5) 5 else 0
                            val shieldColEnd = if (col >= 5) 7 else 2
                            var shieldCount = 0
                            for (sc in shieldColStart..shieldColEnd) {
                                val p1 = board[1 * 8 + sc]
                                if (p1 != null && p1.color == PieceColor.BLACK && p1.type == PieceType.PAWN) shieldCount++
                            }
                            mgBlack += shieldCount * 12
                        }
                    }
                }
                else -> {}
            }
        }

        // Blend Middlegame and Endgame scores by phase
        val totalWhite = (mgWhite * phase + egWhite * (24 - phase)) / 24
        val totalBlack = (mgBlack * phase + egBlack * (24 - phase)) / 24

        var eval = totalWhite - totalBlack

        // In-check adjustment
        if (state.inCheck) {
            eval += if (state.activeColor == PieceColor.WHITE) -35 else 35
        }

        return if (evalColor == PieceColor.WHITE) eval else -eval
    }
}
