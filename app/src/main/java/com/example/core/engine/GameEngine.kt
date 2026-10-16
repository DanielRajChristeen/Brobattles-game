package com.example.core.engine

/**
 * Universal contract for all game engines in Chill Games.
 * The engine is pure, deterministic, and has zero dependencies on Android UI or Bluetooth transport.
 */
interface GameEngine<S : GameState, M : Move> {
    /**
     * Current authoritative game state.
     */
    val state: S

    /**
     * Resets and initializes the game with a target score.
     */
    fun initializeGame(targetScore: Int = 5): S

    /**
     * Advances to the next round while preserving match scores.
     */
    fun startRound(): S

    /**
     * Resets the entire match score to 0-0.
     */
    fun resetGame(targetScore: Int = 5): S

    /**
     * Validates if a move is allowed according to the current state and player turn.
     */
    fun validateMove(move: M, player: PlayerId): Boolean

    /**
     * Applies a valid move and produces an updated state or invalid result.
     */
    fun makeMove(move: M, player: PlayerId): MoveResult<S>

    /**
     * Applies a generic serialized or cross-device event.
     */
    fun applyGameEvent(event: GameEvent): MoveResult<S>

    /**
     * Returns all legal moves currently available for the given player.
     */
    fun getAvailableMoves(player: PlayerId): List<M>

    /**
     * Checks and evaluates the current round/match status.
     */
    fun checkGameStatus(): GameStatus

    /**
     * Returns the match winner if one has reached target score, or null.
     */
    fun getWinner(): PlayerId?
}
