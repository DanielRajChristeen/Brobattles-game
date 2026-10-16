package com.example.core.engine.xo

import com.example.core.engine.GameEngine
import com.example.core.engine.GameEvent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId

class XOGameEngine(
    initialTargetScore: Int = 5
) : GameEngine<XOState, XOMove> {

    companion object {
        val WINNING_PATTERNS = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(0, 3, 6),
            listOf(1, 4, 7),
            listOf(2, 5, 8),
            listOf(0, 4, 8),
            listOf(2, 4, 6)
        )
    }

    private var _state: XOState = XOState(targetScore = initialTargetScore)
    override val state: XOState get() = _state

    override fun initializeGame(targetScore: Int): XOState {
        _state = XOState(
            board = List(9) { null },
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress,
            roundNumber = 1,
            player1Score = 0,
            player2Score = 0,
            targetScore = targetScore,
            winningPattern = null
        )
        return _state
    }

    override fun startRound(): XOState {
        val nextRoundNumber = _state.roundNumber + 1
        val nextActive = if (nextRoundNumber % 2 == 1) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
        _state = _state.copy(
            board = List(9) { null },
            activePlayer = nextActive,
            status = GameStatus.InProgress,
            roundNumber = nextRoundNumber,
            winningPattern = null
        )
        return _state
    }

    override fun resetGame(targetScore: Int): XOState {
        return initializeGame(targetScore)
    }

    fun syncState(newState: XOState): XOState {
        _state = newState
        return _state
    }

    override fun validateMove(move: XOMove, player: PlayerId): Boolean {
        if (_state.status !is GameStatus.InProgress) return false
        if (move.toIndex !in 0..8) return false
        if (_state.board[move.toIndex] != null) return false
        if (player != _state.activePlayer) return false

        val currentMarksCount = _state.board.count { it == player }
        if (currentMarksCount < 3) {
            // Placement phase: must place new mark, fromIndex must be null
            return move.fromIndex == null
        } else {
            // Movement phase: player already has 3 marks, MUST move an existing mark
            if (move.fromIndex == null) return false
            if (move.fromIndex !in 0..8) return false
            if (move.fromIndex == move.toIndex) return false
            if (_state.board[move.fromIndex] != player) return false
            return true
        }
    }

    override fun makeMove(move: XOMove, player: PlayerId): MoveResult<XOState> {
        if (!validateMove(move, player)) {
            return MoveResult.Invalid("Illegal move ($move) for $player")
        }

        val updatedBoard = _state.board.toMutableList()
        if (move.fromIndex != null) {
            updatedBoard[move.fromIndex] = null
        }
        updatedBoard[move.toIndex] = player

        val winPattern = checkWin(updatedBoard, player)
        if (winPattern != null) {
            val newP1Score = if (player == PlayerId.PLAYER_1) _state.player1Score + 1 else _state.player1Score
            val newP2Score = if (player == PlayerId.PLAYER_2) _state.player2Score + 1 else _state.player2Score
            val isMatchOver = _state.roundNumber >= _state.targetScore

            val newStatus = if (isMatchOver) {
                val matchWinner = when {
                    newP1Score > newP2Score -> PlayerId.PLAYER_1
                    newP2Score > newP1Score -> PlayerId.PLAYER_2
                    else -> player // Tie fallback awards winner of final round
                }
                GameStatus.MatchWon(matchWinner)
            } else {
                GameStatus.RoundWon(player)
            }

            _state = _state.copy(
                board = updatedBoard,
                player1Score = newP1Score,
                player2Score = newP2Score,
                status = newStatus,
                winningPattern = winPattern
            )
            return MoveResult.Success(_state)
        }

        // Check if board is full (Draw fallback, though 3 marks each occupies max 6 cells)
        if (updatedBoard.all { it != null }) {
            val isMatchOver = _state.roundNumber >= _state.targetScore
            val newStatus = if (isMatchOver) {
                val matchWinner = when {
                    _state.player1Score > _state.player2Score -> PlayerId.PLAYER_1
                    _state.player2Score > _state.player1Score -> PlayerId.PLAYER_2
                    else -> PlayerId.PLAYER_1
                }
                GameStatus.MatchWon(matchWinner)
            } else {
                GameStatus.RoundDraw
            }

            _state = _state.copy(
                board = updatedBoard,
                status = newStatus,
                winningPattern = null
            )
            return MoveResult.Success(_state)
        }

        // Continue to other player
        _state = _state.copy(
            board = updatedBoard,
            activePlayer = player.other(),
            status = GameStatus.InProgress
        )
        return MoveResult.Success(_state)
    }

    override fun applyGameEvent(event: GameEvent): MoveResult<XOState> {
        return when (event) {
            is GameEvent.MovePlayed -> {
                val parts = event.payload.split(",")
                val move = if (parts.size >= 2) {
                    val from = parts[0].toIntOrNull()
                    val to = parts[1].toIntOrNull()
                    if (to != null) XOMove(toIndex = to, fromIndex = from) else null
                } else {
                    val index = event.payload.toIntOrNull()
                    if (index != null && index in 0..8) XOMove(index) else null
                }
                if (move != null) {
                    makeMove(move, event.player)
                } else {
                    MoveResult.Invalid("Invalid event move payload: ${event.payload}")
                }
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

    override fun getAvailableMoves(player: PlayerId): List<XOMove> {
        if (_state.status !is GameStatus.InProgress || _state.activePlayer != player) {
            return emptyList()
        }
        val emptyIndices = _state.board.mapIndexedNotNull { idx, cell -> if (cell == null) idx else null }
        val playerMarks = _state.board.mapIndexedNotNull { idx, cell -> if (cell == player) idx else null }

        return if (playerMarks.size < 3) {
            emptyIndices.map { XOMove(it) }
        } else {
            val moves = mutableListOf<XOMove>()
            for (from in playerMarks) {
                for (to in emptyIndices) {
                    moves.add(XOMove(toIndex = to, fromIndex = from))
                }
            }
            moves
        }
    }

    override fun checkGameStatus(): GameStatus = _state.status

    override fun getWinner(): PlayerId? {
        return (_state.status as? GameStatus.MatchWon)?.matchWinner
    }

    private fun checkWin(board: List<PlayerId?>, player: PlayerId): List<Int>? {
        for (pattern in WINNING_PATTERNS) {
            if (board[pattern[0]] == player &&
                board[pattern[1]] == player &&
                board[pattern[2]] == player
            ) {
                return pattern
            }
        }
        return null
    }
}
