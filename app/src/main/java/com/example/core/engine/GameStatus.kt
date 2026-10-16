package com.example.core.engine

sealed interface GameStatus {
    data object NotStarted : GameStatus
    data object InProgress : GameStatus
    data class RoundWon(val winner: PlayerId) : GameStatus
    data object RoundDraw : GameStatus
    data class MatchWon(val matchWinner: PlayerId) : GameStatus

    val isFinished: Boolean
        get() = this is MatchWon

    val isRoundOver: Boolean
        get() = this is RoundWon || this is RoundDraw || this is MatchWon
}
