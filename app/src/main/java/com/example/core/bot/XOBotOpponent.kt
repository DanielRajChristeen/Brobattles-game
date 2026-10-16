package com.example.core.bot

import com.example.core.engine.PlayerId
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.XOState
import kotlinx.coroutines.delay

class XOBotOpponent(
    override val difficulty: BotDifficulty = BotDifficulty.MEDIUM
) : BotOpponent<XOState, XOMove> {

    override suspend fun decideMove(state: XOState, botPlayer: PlayerId): XOMove? {
        val opponent = botPlayer.other()
        val board = state.board

        // Natural offline pacing so moves feel responsive yet human
        delay(350)

        val botMarks = board.mapIndexedNotNull { idx, cell -> if (cell == botPlayer) idx else null }
        val oppMarks = board.mapIndexedNotNull { idx, cell -> if (cell == opponent) idx else null }
        val emptyIndices = board.mapIndexedNotNull { idx, cell -> if (cell == null) idx else null }

        if (emptyIndices.isEmpty()) return null

        val availableMoves = if (botMarks.size < 3) {
            emptyIndices.map { XOMove(toIndex = it) }
        } else {
            val moves = mutableListOf<XOMove>()
            for (from in botMarks) {
                for (to in emptyIndices) {
                    moves.add(XOMove(toIndex = to, fromIndex = from))
                }
            }
            moves
        }

        if (availableMoves.isEmpty()) return null

        return when (difficulty) {
            BotDifficulty.EASY -> decideEasyMove(availableMoves, board, botPlayer)
            BotDifficulty.MEDIUM -> decideMediumMove(availableMoves, board, botPlayer, opponent, botMarks.size >= 3)
            BotDifficulty.HARD -> decideHardMove(availableMoves, board, botPlayer, opponent, botMarks.size >= 3)
        }
    }

    private fun decideEasyMove(
        availableMoves: List<XOMove>,
        board: List<PlayerId?>,
        botPlayer: PlayerId
    ): XOMove {
        // EASY:
        // Noticeably weaker/random decisions.
        // 20% of the time, if an immediate win is right there, it might take it, otherwise mostly casual random choices
        val winMove = availableMoves.firstOrNull { simulatesWin(board, it, botPlayer) }
        if (winMove != null && Math.random() < 0.25) {
            return winMove
        }
        return availableMoves.random()
    }

    private fun decideMediumMove(
        availableMoves: List<XOMove>,
        board: List<PlayerId?>,
        botPlayer: PlayerId,
        opponent: PlayerId,
        isMovementPhase: Boolean
    ): XOMove {
        // MEDIUM:
        // 1. Detect immediate winning opportunity
        val winMove = availableMoves.firstOrNull { simulatesWin(board, it, botPlayer) }
        if (winMove != null) return winMove

        // 2. Block obvious immediate opponent win
        val blockMove = findBlockingMove(availableMoves, board, botPlayer, opponent, isMovementPhase)
        if (blockMove != null) return blockMove

        // 3. Take center if available or moving to center
        val centerMove = availableMoves.firstOrNull { it.toIndex == 4 }
        if (centerMove != null && Math.random() < 0.7) {
            return centerMove
        }

        // 4. Prefer corners over edges
        val corners = listOf(0, 2, 6, 8)
        val cornerMoves = availableMoves.filter { it.toIndex in corners }
        if (cornerMoves.isNotEmpty() && Math.random() < 0.75) {
            return cornerMoves.random()
        }

        return availableMoves.random()
    }

    private fun decideHardMove(
        availableMoves: List<XOMove>,
        board: List<PlayerId?>,
        botPlayer: PlayerId,
        opponent: PlayerId,
        isMovementPhase: Boolean
    ): XOMove {
        // HARD:
        // 1. Prioritize immediate winning moves
        val winMove = availableMoves.firstOrNull { simulatesWin(board, it, botPlayer) }
        if (winMove != null) return winMove

        // 2. Block opponent winning moves
        val blockMove = findBlockingMove(availableMoves, board, botPlayer, opponent, isMovementPhase)
        if (blockMove != null) return blockMove

        // 3. Strategic heuristic scoring:
        // Evaluate each possible move:
        // - Prevent opponent future threats
        // - Create double threats (forks)
        // - Control center (cell 4) and corners
        // - In movement phase: do NOT vacate a cell that gives opponent an instant win
        var bestMove = availableMoves.first()
        var bestScore = Int.MIN_VALUE

        for (move in availableMoves) {
            val score = evaluateMove(board, move, botPlayer, opponent, isMovementPhase)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove
    }

    private fun findBlockingMove(
        availableMoves: List<XOMove>,
        board: List<PlayerId?>,
        botPlayer: PlayerId,
        opponent: PlayerId,
        isMovementPhase: Boolean
    ): XOMove? {
        val oppMarks = board.mapIndexedNotNull { idx, cell -> if (cell == opponent) idx else null }

        // Find which empty squares would give opponent a win
        val threatIndices = mutableSetOf<Int>()
        for (pattern in XOGameEngine.WINNING_PATTERNS) {
            val p0 = board[pattern[0]]
            val p1 = board[pattern[1]]
            val p2 = board[pattern[2]]

            if (p0 == opponent && p1 == opponent && p2 == null) threatIndices.add(pattern[2])
            if (p0 == opponent && p2 == opponent && p1 == null) threatIndices.add(pattern[1])
            if (p1 == opponent && p2 == opponent && p0 == null) threatIndices.add(pattern[0])
        }

        if (threatIndices.isEmpty()) return null

        // Try to find a move that occupies one of these threat cells
        // In movement phase, ensure fromIndex does not create a new winning line for opponent
        for (threat in threatIndices) {
            val candidate = availableMoves.firstOrNull { it.toIndex == threat }
            if (candidate != null) {
                if (candidate.fromIndex == null) return candidate

                // Check that vacating fromIndex doesn't immediately open an opponent win
                val boardAfter = board.toMutableList()
                boardAfter[candidate.fromIndex] = null
                boardAfter[candidate.toIndex] = botPlayer

                val opponentCanStillWin = canPlayerWinNext(boardAfter, opponent)
                if (!opponentCanStillWin) {
                    return candidate
                }
            }
        }

        return availableMoves.firstOrNull { it.toIndex in threatIndices }
    }

    private fun evaluateMove(
        board: List<PlayerId?>,
        move: XOMove,
        botPlayer: PlayerId,
        opponent: PlayerId,
        isMovementPhase: Boolean
    ): Int {
        val nextBoard = board.toMutableList()
        if (move.fromIndex != null) {
            nextBoard[move.fromIndex] = null
        }
        nextBoard[move.toIndex] = botPlayer

        // Severe penalty if this move allows opponent to win on next turn
        if (canPlayerWinNext(nextBoard, opponent)) {
            return -1000
        }

        var score = 0

        // Bonus for creating multiple winning threats (fork)
        val botThreats = countThreats(nextBoard, botPlayer)
        score += botThreats * 150

        // Center control
        if (move.toIndex == 4) score += 40

        // Corner control
        if (move.toIndex in listOf(0, 2, 6, 8)) score += 20

        // Moving out of center penalty in movement phase unless creating a threat
        if (move.fromIndex == 4 && botThreats == 0) score -= 30

        return score
    }

    private fun canPlayerWinNext(board: List<PlayerId?>, player: PlayerId): Boolean {
        val emptyIndices = board.mapIndexedNotNull { idx, cell -> if (cell == null) idx else null }
        val playerMarks = board.mapIndexedNotNull { idx, cell -> if (cell == player) idx else null }

        if (playerMarks.size < 3) {
            return emptyIndices.any { simulatesWin(board, XOMove(toIndex = it), player) }
        } else {
            for (from in playerMarks) {
                for (to in emptyIndices) {
                    if (simulatesWin(board, XOMove(toIndex = to, fromIndex = from), player)) {
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun countThreats(board: List<PlayerId?>, player: PlayerId): Int {
        var count = 0
        for (pattern in XOGameEngine.WINNING_PATTERNS) {
            val c0 = board[pattern[0]]
            val c1 = board[pattern[1]]
            val c2 = board[pattern[2]]

            val pCount = listOf(c0, c1, c2).count { it == player }
            val emptyCount = listOf(c0, c1, c2).count { it == null }
            if (pCount == 2 && emptyCount == 1) {
                count++
            }
        }
        return count
    }

    private fun simulatesWin(board: List<PlayerId?>, move: XOMove, player: PlayerId): Boolean {
        val temp = board.toMutableList()
        if (move.fromIndex != null) {
            temp[move.fromIndex] = null
        }
        temp[move.toIndex] = player

        for (pattern in XOGameEngine.WINNING_PATTERNS) {
            if (temp[pattern[0]] == player &&
                temp[pattern[1]] == player &&
                temp[pattern[2]] == player
            ) {
                return true
            }
        }
        return false
    }
}
