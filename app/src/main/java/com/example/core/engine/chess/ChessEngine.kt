package com.example.core.engine.chess

class ChessEngine(initialState: ChessGameState = ChessGameState.initial()) {

    private val stateHistory = mutableListOf<ChessGameState>()
    private val moveHistory = mutableListOf<ChessMove>()

    var state: ChessGameState = initialState
        private set

    var currentLegalMoves: List<ChessMove> = ChessRules.generateLegalMoves(state)
        private set

    init {
        if (state.canonicalPositionKey.isEmpty()) {
            val key = ChessRules.computeCanonicalPositionKey(
                state.board,
                state.activeColor,
                state.whiteCanCastleKingside,
                state.whiteCanCastleQueenside,
                state.blackCanCastleKingside,
                state.blackCanCastleQueenside,
                ChessRules.getEffectiveEnPassant(state.board, state.activeColor, state.enPassantTarget)
            )
            val history = if (state.positionHistory.isEmpty()) listOf(key) else state.positionHistory
            state = state.copy(canonicalPositionKey = key, positionHistory = history)
        }
        stateHistory.add(state)
    }

    fun resetGame() {
        state = ChessGameState.initial()
        currentLegalMoves = ChessRules.generateLegalMoves(state)
        stateHistory.clear()
        stateHistory.add(state)
        moveHistory.clear()
    }

    fun getLegalMovesForSquare(square: ChessSquare): List<ChessMove> {
        if (state.status.isGameOver) return emptyList()
        return currentLegalMoves.filter { it.from == square }
    }

    fun makeMove(move: ChessMove): Boolean {
        if (state.status.isGameOver) return false
        if (!currentLegalMoves.contains(move)) return false
        state = ChessRules.applyMove(state, move)
        stateHistory.add(state)
        moveHistory.add(move)
        currentLegalMoves = if (state.status.isGameOver) emptyList() else ChessRules.generateLegalMoves(state)
        return true
    }

    /**
     * Reverts the most recent move, restoring the prior game state and repetition history.
     */
    fun undoMove(): Boolean {
        if (stateHistory.size <= 1) return false
        stateHistory.removeAt(stateHistory.size - 1)
        if (moveHistory.isNotEmpty()) {
            moveHistory.removeAt(moveHistory.size - 1)
        }
        state = stateHistory.last()
        currentLegalMoves = if (state.status.isGameOver) emptyList() else ChessRules.generateLegalMoves(state)
        return true
    }

    fun canUndo(): Boolean = stateHistory.size > 1 && !state.status.isGameOver

    /**
     * Claims a draw by Threefold Repetition if the current position has occurred 3 or more times.
     */
    fun claimThreefoldRepetitionDraw(): Boolean {
        if (state.isThreefoldRepetitionClaimable) {
            state = state.copy(status = ChessGameStatus.DrawByThreefoldRepetition)
            currentLegalMoves = emptyList()
            return true
        }
        return false
    }

    /**
     * Claims a draw under the 50-move rule if 100 half-moves have elapsed without a pawn move or capture.
     */
    fun claimFiftyMoveDraw(): Boolean {
        if (state.isFiftyMoveDrawClaimable) {
            state = state.copy(status = ChessGameStatus.DrawByFiftyMoves)
            currentLegalMoves = emptyList()
            return true
        }
        return false
    }
}
