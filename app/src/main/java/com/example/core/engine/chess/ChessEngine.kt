package com.example.core.engine.chess

class ChessEngine {

    var state: ChessGameState = ChessGameState.initial()
        private set

    var currentLegalMoves: List<ChessMove> = ChessRules.generateLegalMoves(state)
        private set

    fun resetGame() {
        state = ChessGameState.initial()
        currentLegalMoves = ChessRules.generateLegalMoves(state)
    }

    fun getLegalMovesForSquare(square: ChessSquare): List<ChessMove> {
        return currentLegalMoves.filter { it.from == square }
    }

    fun makeMove(move: ChessMove): Boolean {
        if (!currentLegalMoves.contains(move)) return false
        state = ChessRules.applyMove(state, move)
        currentLegalMoves = ChessRules.generateLegalMoves(state)
        return true
    }
}
