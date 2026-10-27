package com.example.core.engine.checkers

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import kotlin.random.Random

/**
 * AI Opponent for Checkers supporting Easy, Medium, and Hard difficulty levels.
 */
object CheckersBot {

    /**
     * Decides the best move for [botPlayer] based on [difficulty].
     */
    fun decideMove(
        state: CheckersGameState,
        difficulty: CheckersDifficulty,
        botPlayer: PlayerId = PlayerId.PLAYER_2
    ): CheckersMove? {
        val legalMoves = CheckersRules.getLegalMoves(state)
        if (legalMoves.isEmpty()) return null
        if (legalMoves.size == 1) return legalMoves.first()

        return when (difficulty) {
            CheckersDifficulty.EASY -> pickEasyMove(legalMoves)
            CheckersDifficulty.MEDIUM -> pickMinimaxMove(state, botPlayer, maxDepth = 2)
            CheckersDifficulty.HARD -> pickMinimaxMove(state, botPlayer, maxDepth = 4)
        }
    }

    private fun pickEasyMove(legalMoves: List<CheckersMove>): CheckersMove {
        // Prefer jumps if available (rules mandate it anyway)
        val jumps = legalMoves.filter { it.isJump }
        return if (jumps.isNotEmpty()) {
            jumps[Random.nextInt(jumps.size)]
        } else {
            legalMoves[Random.nextInt(legalMoves.size)]
        }
    }

    private fun pickMinimaxMove(
        state: CheckersGameState,
        botPlayer: PlayerId,
        maxDepth: Int
    ): CheckersMove {
        val legalMoves = CheckersRules.getLegalMoves(state)
        var bestScore = Int.MIN_VALUE
        var bestMoves = mutableListOf<CheckersMove>()

        for (move in legalMoves) {
            val (nextState, _) = CheckersRules.applyMove(state, move)
            // If move caused a multi-jump continuation for bot, let bot continue search
            val score = if (nextState.activePlayer == botPlayer) {
                minimax(nextState, depth = maxDepth, alpha = Int.MIN_VALUE, beta = Int.MAX_VALUE, isMaximizing = true, botPlayer = botPlayer)
            } else {
                minimax(nextState, depth = maxDepth - 1, alpha = Int.MIN_VALUE, beta = Int.MAX_VALUE, isMaximizing = false, botPlayer = botPlayer)
            }

            if (score > bestScore) {
                bestScore = score
                bestMoves = mutableListOf(move)
            } else if (score == bestScore) {
                bestMoves.add(move)
            }
        }

        return bestMoves[Random.nextInt(bestMoves.size)]
    }

    private fun minimax(
        state: CheckersGameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        botPlayer: PlayerId
    ): Int {
        when (val status = state.status) {
            is GameStatus.MatchWon -> {
                return if (status.matchWinner == botPlayer) 10000 + depth else -10000 - depth
            }
            is GameStatus.RoundDraw -> return 0
            else -> {}
        }

        if (depth <= 0) {
            return evaluateBoard(state, botPlayer)
        }

        val legalMoves = CheckersRules.getLegalMoves(state)
        if (legalMoves.isEmpty()) {
            // Player whose turn it is loses
            return if (isMaximizing) -10000 - depth else 10000 + depth
        }

        var curAlpha = alpha
        var curBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in legalMoves) {
                val (nextState, _) = CheckersRules.applyMove(state, move)
                val eval = if (nextState.activePlayer == botPlayer) {
                    minimax(nextState, depth, curAlpha, curBeta, isMaximizing = true, botPlayer = botPlayer)
                } else {
                    minimax(nextState, depth - 1, curAlpha, curBeta, isMaximizing = false, botPlayer = botPlayer)
                }
                maxEval = maxOf(maxEval, eval)
                curAlpha = maxOf(curAlpha, eval)
                if (curBeta <= curAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            val humanPlayer = botPlayer.other()
            for (move in legalMoves) {
                val (nextState, _) = CheckersRules.applyMove(state, move)
                val eval = if (nextState.activePlayer == humanPlayer) {
                    minimax(nextState, depth, curAlpha, curBeta, isMaximizing = false, botPlayer = botPlayer)
                } else {
                    minimax(nextState, depth - 1, curAlpha, curBeta, isMaximizing = true, botPlayer = botPlayer)
                }
                minEval = minOf(minEval, eval)
                curBeta = minOf(curBeta, eval)
                if (curBeta <= curAlpha) break
            }
            return minEval
        }
    }

    /**
     * Positional and material heuristic evaluation:
     * - Regular piece: 100 points
     * - King piece: 220 points
     * - Center control: +10 points
     * - Row advancement towards King row: +5 points per advanced row
     * - Back rank protection: +15 points
     */
    private fun evaluateBoard(state: CheckersGameState, botPlayer: PlayerId): Int {
        var score = 0
        val humanPlayer = botPlayer.other()

        for ((sq, piece) in state.board) {
            val isBot = piece.player == botPlayer
            val pieceVal = if (piece.isKing) 220 else 100

            // Positional bonus: Center board control
            var posBonus = 0
            if (sq.row in 3..4 && sq.col in 2..5) {
                posBonus += 10
            }

            // Advancement towards promotion
            if (!piece.isKing) {
                posBonus += if (piece.player == PlayerId.PLAYER_2) {
                    sq.row * 6 // Black advances downwards (row 0 to 7)
                } else {
                    (7 - sq.row) * 6 // Red advances upwards (row 7 to 0)
                }

                // Defend back rank
                if (piece.player == PlayerId.PLAYER_2 && sq.row == 0) posBonus += 15
                if (piece.player == PlayerId.PLAYER_1 && sq.row == 7) posBonus += 15
            }

            val totalPieceScore = pieceVal + posBonus
            if (isBot) {
                score += totalPieceScore
            } else {
                score -= totalPieceScore
            }
        }

        return score
    }
}
