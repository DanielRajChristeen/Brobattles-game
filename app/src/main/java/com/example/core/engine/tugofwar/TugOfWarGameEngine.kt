package com.example.core.engine.tugofwar

import com.example.core.engine.GameEngine
import com.example.core.engine.GameEvent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId

class TugOfWarGameEngine(
    initialTargetScore: Int = 1
) : GameEngine<TugOfWarState, TugOfWarMove> {

    companion object {
        const val WIN_THRESHOLD = 100.0f
        const val BASE_TAP_FORCE = 1.75f
        const val TENSION_DECAY = 0.08f
    }

    private var _state: TugOfWarState = TugOfWarState(targetScore = initialTargetScore)
    override val state: TugOfWarState get() = _state

    override fun initializeGame(targetScore: Int): TugOfWarState {
        _state = TugOfWarState(
            ropePosition = 0.0f,
            p1Taps = 0,
            p2Taps = 0,
            p1PullEnergy = 0.0f,
            p2PullEnergy = 0.0f,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress,
            roundNumber = 1,
            player1Score = 0,
            player2Score = 0,
            targetScore = targetScore,
            lastTapPlayer = null
        )
        return _state
    }

    override fun startRound(): TugOfWarState {
        _state = _state.copy(
            ropePosition = 0.0f,
            p1Taps = 0,
            p2Taps = 0,
            p1PullEnergy = 0.0f,
            p2PullEnergy = 0.0f,
            status = GameStatus.InProgress,
            roundNumber = _state.roundNumber + 1,
            lastTapPlayer = null
        )
        return _state
    }

    override fun resetGame(targetScore: Int): TugOfWarState {
        return initializeGame(targetScore)
    }

    /**
     * Synchronizes authoritative remote state.
     * Prevents delayed or duplicate in-progress messages from overwriting an ended match.
     */
    fun syncState(newState: TugOfWarState): TugOfWarState {
        if (_state.status is GameStatus.MatchWon && newState.status !is GameStatus.MatchWon) {
            return _state
        }
        _state = newState
        return _state
    }

    override fun validateMove(move: TugOfWarMove, player: PlayerId): Boolean {
        return _state.status is GameStatus.InProgress
    }

    override fun makeMove(move: TugOfWarMove, player: PlayerId): MoveResult<TugOfWarState> {
        if (!validateMove(move, player)) {
            return MoveResult.Invalid("Match is not in progress")
        }

        val delta = BASE_TAP_FORCE * move.force
        val newPosition = when (player) {
            PlayerId.PLAYER_1 -> (_state.ropePosition - delta).coerceIn(-WIN_THRESHOLD, WIN_THRESHOLD)
            PlayerId.PLAYER_2 -> (_state.ropePosition + delta).coerceIn(-WIN_THRESHOLD, WIN_THRESHOLD)
            else -> _state.ropePosition
        }

        val newP1Taps = if (player == PlayerId.PLAYER_1) _state.p1Taps + 1 else _state.p1Taps
        val newP2Taps = if (player == PlayerId.PLAYER_2) _state.p2Taps + 1 else _state.p2Taps

        val newP1Energy = if (player == PlayerId.PLAYER_1) 1.0f else (_state.p1PullEnergy * 0.85f)
        val newP2Energy = if (player == PlayerId.PLAYER_2) 1.0f else (_state.p2PullEnergy * 0.85f)

        // Evaluate win condition
        val (newStatus, newP1Score, newP2Score) = when {
            newPosition <= -WIN_THRESHOLD -> {
                val nextScore = _state.player1Score + 1
                val status = if (nextScore >= _state.targetScore) {
                    GameStatus.MatchWon(PlayerId.PLAYER_1)
                } else {
                    GameStatus.RoundWon(PlayerId.PLAYER_1)
                }
                Triple(status, nextScore, _state.player2Score)
            }
            newPosition >= WIN_THRESHOLD -> {
                val nextScore = _state.player2Score + 1
                val status = if (nextScore >= _state.targetScore) {
                    GameStatus.MatchWon(PlayerId.PLAYER_2)
                } else {
                    GameStatus.RoundWon(PlayerId.PLAYER_2)
                }
                Triple(status, _state.player1Score, nextScore)
            }
            else -> Triple(_state.status, _state.player1Score, _state.player2Score)
        }

        _state = _state.copy(
            ropePosition = newPosition,
            p1Taps = newP1Taps,
            p2Taps = newP2Taps,
            p1PullEnergy = newP1Energy,
            p2PullEnergy = newP2Energy,
            status = newStatus,
            player1Score = newP1Score,
            player2Score = newP2Score,
            lastTapPlayer = player
        )

        return MoveResult.Success(_state)
    }

    /**
     * Applies subtle physical decay back towards center to keep the game active.
     */
    fun applyFriction(): TugOfWarState {
        if (_state.status !is GameStatus.InProgress) return _state
        val current = _state.ropePosition
        if (current == 0f) return _state

        val decayed = if (current > 0f) {
            (current - TENSION_DECAY).coerceAtLeast(0f)
        } else {
            (current + TENSION_DECAY).coerceAtMost(0f)
        }

        _state = _state.copy(
            ropePosition = decayed,
            p1PullEnergy = (_state.p1PullEnergy * 0.92f).coerceAtLeast(0f),
            p2PullEnergy = (_state.p2PullEnergy * 0.92f).coerceAtLeast(0f)
        )
        return _state
    }

    override fun applyGameEvent(event: GameEvent): MoveResult<TugOfWarState> {
        return when (event) {
            is GameEvent.MovePlayed -> {
                val force = event.payload.toFloatOrNull() ?: 1.0f
                makeMove(TugOfWarMove(force = force), event.player)
            }
            is GameEvent.RoundStarted -> {
                startRound()
                MoveResult.Success(_state)
            }
            is GameEvent.MatchRestarted -> {
                resetGame(event.targetScore)
                MoveResult.Success(_state)
            }
        }
    }

    override fun getAvailableMoves(player: PlayerId): List<TugOfWarMove> {
        return if (_state.status is GameStatus.InProgress) listOf(TugOfWarMove()) else emptyList()
    }

    override fun checkGameStatus(): GameStatus = _state.status

    override fun getWinner(): PlayerId? {
        return (_state.status as? GameStatus.MatchWon)?.matchWinner
    }
}
