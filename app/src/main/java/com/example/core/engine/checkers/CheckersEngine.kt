package com.example.core.engine.checkers

import com.example.core.engine.PlayerId

/**
 * State manager and authoritative game engine for Checkers.
 *
 * Enforces move finality with NO undo or move rollback.
 */
class CheckersEngine {

    var state: CheckersGameState = CheckersGameState.initial()
        private set

    /**
     * Attempts to execute a move. Returns [CheckersMoveResult] with outcome details.
     */
    fun makeMove(move: CheckersMove): CheckersMoveResult {
        val (newState, result) = CheckersRules.applyMove(state, move)
        if (result.success) {
            state = newState
        }
        return result
    }

    /**
     * Returns all legal moves in the current state.
     */
    fun getLegalMoves(): List<CheckersMove> = CheckersRules.getLegalMoves(state)

    /**
     * Returns legal moves for a specific square.
     */
    fun getLegalMovesForSquare(square: CheckersSquare): List<CheckersMove> =
        CheckersRules.getLegalMovesForSquare(state, square)

    /**
     * Checks if a move is valid without applying it.
     */
    fun validateMove(move: CheckersMove): Boolean {
        val legal = CheckersRules.getLegalMoves(state)
        return legal.any { it.from == move.from && it.to == move.to }
    }

    /**
     * Resets the board to the standard starting position.
     */
    fun resetGame() {
        state = CheckersGameState.initial()
    }

    /**
     * Synchronizes state directly from an authoritative host peer during Wi-Fi multiplayer.
     */
    fun syncState(newState: CheckersGameState) {
        state = newState
    }
}
