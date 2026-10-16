package com.example.core.engine.snakesandladders

import com.example.core.engine.GameEngine
import com.example.core.engine.GameEvent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId

class SnakesAndLaddersGameEngine(
    initialTargetScore: Int = 1,
    initialPlayerCount: Int = 2
) : GameEngine<SnakesAndLaddersState, SnakesAndLaddersMove> {

    var playerCount: Int = initialPlayerCount.coerceIn(2, 5)
        private set

    private var _state: SnakesAndLaddersState = SnakesAndLaddersState(
        playerCount = playerCount,
        playerPositions = List(playerCount) { 1 },
        targetScore = initialTargetScore
    )
    override val state: SnakesAndLaddersState get() = _state

    fun setPlayerCount(count: Int) {
        playerCount = count.coerceIn(2, 5)
        resetGame(_state.targetScore)
    }

    fun getNextPlayer(current: PlayerId): PlayerId {
        val nextIndex = (current.ordinal + 1) % playerCount
        return PlayerId.values()[nextIndex]
    }

    override fun initializeGame(targetScore: Int): SnakesAndLaddersState {
        _state = SnakesAndLaddersState(
            playerCount = playerCount,
            playerPositions = List(playerCount) { 1 },
            lastDiceRoll = null,
            lastMovePlayer = null,
            ladderClimbed = null,
            snakeSlid = null,
            wasOvershot = false,
            extraTurnGranted = false,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress,
            roundNumber = 1,
            player1Score = 0,
            player2Score = 0,
            targetScore = targetScore
        )
        return _state
    }

    override fun startRound(): SnakesAndLaddersState {
        _state = SnakesAndLaddersState(
            playerCount = playerCount,
            playerPositions = List(playerCount) { 1 },
            lastDiceRoll = null,
            lastMovePlayer = null,
            ladderClimbed = null,
            snakeSlid = null,
            wasOvershot = false,
            extraTurnGranted = false,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress,
            roundNumber = _state.roundNumber + 1,
            player1Score = _state.player1Score,
            player2Score = _state.player2Score,
            targetScore = _state.targetScore
        )
        return _state
    }

    override fun resetGame(targetScore: Int): SnakesAndLaddersState {
        return initializeGame(targetScore)
    }

    override fun validateMove(move: SnakesAndLaddersMove, player: PlayerId): Boolean {
        if (_state.status !is GameStatus.InProgress) return false
        if (player != _state.activePlayer) return false
        return move.diceRoll in 1..6
    }

    override fun makeMove(move: SnakesAndLaddersMove, player: PlayerId): MoveResult<SnakesAndLaddersState> {
        if (!validateMove(move, player)) {
            return MoveResult.Invalid("Invalid move for $player with dice ${move.diceRoll}")
        }

        val playerIndex = player.ordinal
        val currentPos = _state.playerPositions.getOrElse(playerIndex) { 1 }
        val targetSquare = currentPos + move.diceRoll

        val isExtraTurn = (move.diceRoll == 1 || move.diceRoll == 6)

        // Exact roll rule: if over 100, do not move
        if (targetSquare > SnakesAndLaddersBoard.TOTAL_SQUARES) {
            val nextTurnPlayer = if (isExtraTurn) player else getNextPlayer(player)
            _state = _state.copy(
                lastDiceRoll = move.diceRoll,
                lastMovePlayer = player,
                ladderClimbed = null,
                snakeSlid = null,
                wasOvershot = true,
                extraTurnGranted = isExtraTurn,
                activePlayer = nextTurnPlayer
            )
            return MoveResult.Success(_state)
        }

        // Check if reached 100 directly
        if (targetSquare == SnakesAndLaddersBoard.TOTAL_SQUARES) {
            val newPositions = _state.playerPositions.toMutableList()
            if (playerIndex in newPositions.indices) {
                newPositions[playerIndex] = 100
            }

            _state = _state.copy(
                playerPositions = newPositions,
                lastDiceRoll = move.diceRoll,
                lastMovePlayer = player,
                ladderClimbed = null,
                snakeSlid = null,
                wasOvershot = false,
                extraTurnGranted = false,
                status = GameStatus.MatchWon(player),
                player1Score = if (player == PlayerId.PLAYER_1) 1 else _state.player1Score,
                player2Score = if (player == PlayerId.PLAYER_2) 1 else _state.player2Score,
                activePlayer = player
            )
            return MoveResult.Success(_state)
        }

        // Check for ladder or snake
        val ladderTop = SnakesAndLaddersBoard.LADDER_MAP[targetSquare]
        val snakeTail = SnakesAndLaddersBoard.SNAKE_MAP[targetSquare]

        val finalSquare = when {
            ladderTop != null -> ladderTop
            snakeTail != null -> snakeTail
            else -> targetSquare
        }

        val ladderClimb = if (ladderTop != null) (targetSquare to ladderTop) else null
        val snakeSlide = if (snakeTail != null) (targetSquare to snakeTail) else null

        val isWon = finalSquare == SnakesAndLaddersBoard.TOTAL_SQUARES
        val status = if (isWon) GameStatus.MatchWon(player) else GameStatus.InProgress
        val nextTurnPlayer = if (isWon) player else (if (isExtraTurn) player else getNextPlayer(player))

        val newPositions = _state.playerPositions.toMutableList()
        if (playerIndex in newPositions.indices) {
            newPositions[playerIndex] = finalSquare
        }

        _state = _state.copy(
            playerPositions = newPositions,
            lastDiceRoll = move.diceRoll,
            lastMovePlayer = player,
            ladderClimbed = ladderClimb,
            snakeSlid = snakeSlide,
            wasOvershot = false,
            extraTurnGranted = if (isWon) false else isExtraTurn,
            status = status,
            player1Score = if (isWon && player == PlayerId.PLAYER_1) _state.player1Score + 1 else _state.player1Score,
            player2Score = if (isWon && player == PlayerId.PLAYER_2) _state.player2Score + 1 else _state.player2Score,
            activePlayer = nextTurnPlayer
        )

        return MoveResult.Success(_state)
    }

    override fun applyGameEvent(event: GameEvent): MoveResult<SnakesAndLaddersState> {
        return when (event) {
            is GameEvent.MovePlayed -> {
                val dice = event.payload.toIntOrNull() ?: 1
                makeMove(SnakesAndLaddersMove(diceRoll = dice), event.player)
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

    override fun getAvailableMoves(player: PlayerId): List<SnakesAndLaddersMove> {
        return if (_state.status is GameStatus.InProgress && player == _state.activePlayer) {
            (1..6).map { SnakesAndLaddersMove(it) }
        } else emptyList()
    }

    override fun checkGameStatus(): GameStatus = _state.status

    override fun getWinner(): PlayerId? {
        return (_state.status as? GameStatus.MatchWon)?.matchWinner
    }
}
