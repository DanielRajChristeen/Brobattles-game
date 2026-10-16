package com.example.core.engine

sealed interface MoveResult<out S : GameState> {
    data class Success<S : GameState>(val newState: S) : MoveResult<S>
    data class Invalid(val reason: String) : MoveResult<Nothing>
}
