package com.example.core.engine.ludo

import com.example.core.registry.GameMode
import kotlin.random.Random

/**
 * Authoritative game engine for Ludo.
 *
 * Implements standard Ludo rules:
 * - 4 tokens per player starting in the home yard.
 * - Rolling 1 or 6 releases a token from the yard to the start cell.
 * - Extra turn granted on rolling 1 or 6, OR on capturing an enemy token (max 1 extra turn).
 * - Exact roll required to enter the center finish area.
 * - Safe cells (4 star squares + 4 starting squares) protect tokens from capture.
 * - Player wins when all 4 tokens reach the home center.
 */
class LudoGameEngine(
    initialMode: GameMode = GameMode.SOLO,
    playerCount: Int = 2,
    usernames: Map<LudoColor, String> = emptyMap(),
    initialPlayerCount: Int = playerCount
) {
    var state: LudoGameState
        private set

    init {
        val actualCount = if (playerCount != 2) playerCount else initialPlayerCount
        state = createInitialState(initialMode, actualCount, usernames)
    }

    fun setState(newState: LudoGameState) {
        state = newState
    }

    fun resetGame(
        mode: GameMode = state.mode,
        playerCount: Int = state.players.size,
        usernames: Map<LudoColor, String> = emptyMap()
    ) {
        state = createInitialState(mode, playerCount, usernames)
    }

    private fun createInitialState(
        mode: GameMode,
        playerCount: Int,
        usernames: Map<LudoColor, String>
    ): LudoGameState {
        val players = when (mode) {
            GameMode.SOLO -> {
                // VS Bot: Exactly two teams: Red (User) vs Yellow (Bot)
                listOf(
                    LudoPlayerState(
                        color = LudoColor.RED,
                        name = usernames[LudoColor.RED]?.takeIf { it.isNotBlank() } ?: "Player (Red)",
                        isBot = false
                    ),
                    LudoPlayerState(
                        color = LudoColor.YELLOW,
                        name = "Bot (Yellow)",
                        isBot = true
                    )
                )
            }
            else -> {
                // Local / Nearby Multiplayer: 2, 3, or 4 players
                val count = playerCount.coerceIn(2, 4)
                val colors = listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE).take(count)
                colors.mapIndexed { index, color ->
                    val defaultName = "Player ${index + 1} (${color.displayName})"
                    val customName = usernames[color]
                    LudoPlayerState(
                        color = color,
                        name = customName?.takeIf { it.isNotBlank() } ?: defaultName,
                        isBot = false
                    )
                }
            }
        }

        return LudoGameState(
            mode = mode,
            players = players,
            currentTurnIndex = 0,
            diceRoll = null,
            canRoll = true,
            selectableTokenIds = emptyList(),
            statusText = "${players[0].name}'s turn! Roll the dice 🎲",
            winner = null,
            lastMoveDescription = null,
            extraTurn = false,
            lastCapturedColor = null,
            seqNum = 1
        )
    }

    /**
     * Rolls the dice for the current player and computes legal token moves.
     */
    fun rollDice(forcedValue: Int? = null): LudoGameState {
        if (!state.canRoll || state.isGameOver) return state

        val roll = forcedValue ?: Random.nextInt(1, 7)
        val currentP = state.currentPlayer

        // Determine which tokens can legally move with this roll
        val legalTokenIds = currentP.tokens.filter { token ->
            isMoveLegal(token, roll)
        }.map { it.id }

        if (legalTokenIds.isEmpty()) {
            // No legal moves possible with this roll
            val nextTurnIndex = (state.currentTurnIndex + 1) % state.players.size
            val nextP = state.players[nextTurnIndex]

            state = state.copy(
                diceRoll = roll,
                canRoll = true,
                selectableTokenIds = emptyList(),
                currentTurnIndex = nextTurnIndex,
                statusText = "${currentP.name} rolled $roll but has no moves. ${nextP.name}'s turn!",
                lastMoveDescription = "${currentP.name} rolled $roll (No moves)",
                extraTurn = false,
                lastCapturedColor = null,
                seqNum = state.seqNum + 1
            )
        } else {
            // Legal moves exist, player must pick a token
            state = state.copy(
                diceRoll = roll,
                canRoll = false,
                selectableTokenIds = legalTokenIds,
                statusText = "${currentP.name} rolled $roll! Pick a token to move.",
                lastMoveDescription = "${currentP.name} rolled $roll",
                extraTurn = false,
                lastCapturedColor = null,
                seqNum = state.seqNum + 1
            )
        }
        return state
    }

    /**
     * Checks if moving a specific token with the given dice roll is legal.
     */
    fun isMoveLegal(token: LudoToken, roll: Int): Boolean {
        if (token.isFinished) return false

        return if (token.isInYard) {
            roll == 1 || roll == 6
        } else {
            // Exact roll required to reach or not overshoot Finish (step 57)
            token.step + roll <= LudoToken.STEP_FINISH
        }
    }

    /**
     * Moves the specified token using the current dice roll.
     */
    fun moveToken(tokenId: Int): LudoGameState {
        if (state.canRoll || state.isGameOver || state.diceRoll == null) return state
        if (tokenId !in state.selectableTokenIds) return state

        val roll = state.diceRoll!!
        val currentP = state.currentPlayer
        val token = currentP.tokens.firstOrNull { it.id == tokenId } ?: return state

        val newStep = if (token.isInYard) {
            LudoToken.STEP_START // Step 0
        } else {
            token.step + roll
        }

        val updatedToken = token.copy(step = newStep)
        val updatedTokens = currentP.tokens.map { if (it.id == tokenId) updatedToken else it }
        var capturedColor: LudoColor? = null
        var enemyTokenCaptured = false

        // Check for capture if landed on outer track
        val allPlayersUpdated = state.players.map { player ->
            if (player.color == currentP.color) {
                player.copy(tokens = updatedTokens)
            } else if (updatedToken.isOnTrack) {
                val targetTrackIdx = (currentP.color.startIndex + updatedToken.step) % 52
                if (!LudoBoardCoordinates.isSafeSquare(targetTrackIdx)) {
                    // Check if this enemy has a token on the same track square
                    val enemyTokensUpdated = player.tokens.map { enemyToken ->
                        if (enemyToken.isOnTrack && enemyToken.trackIndex == targetTrackIdx) {
                            enemyTokenCaptured = true
                            capturedColor = player.color
                            enemyToken.copy(step = LudoToken.STEP_YARD) // Send back to yard
                        } else {
                            enemyToken
                        }
                    }
                    player.copy(tokens = enemyTokensUpdated)
                } else {
                    player
                }
            } else {
                player
            }
        }

        // Check if current player has won (all 4 tokens finished)
        val updatedCurrentPlayer = allPlayersUpdated[state.currentTurnIndex]
        val isWon = updatedCurrentPlayer.tokens.all { it.isFinished }

        if (isWon) {
            state = state.copy(
                players = allPlayersUpdated,
                canRoll = false,
                diceRoll = roll,
                selectableTokenIds = emptyList(),
                winner = currentP.color,
                statusText = "🏆 ${currentP.name} WINS THE MATCH! 🎉",
                lastMoveDescription = "${currentP.name} moved token to Home Center and WON!",
                extraTurn = false,
                lastCapturedColor = capturedColor,
                seqNum = state.seqNum + 1
            )
            return state
        }

        // Determine if an extra turn is granted:
        // Rule: Extra turn on rolling 1 or 6, OR capturing an enemy token (max 1 extra turn).
        val extraTurnGranted = (roll == 1 || roll == 6) || enemyTokenCaptured

        if (extraTurnGranted) {
            val reason = when {
                enemyTokenCaptured && (roll == 1 || roll == 6) -> "Captured an enemy & rolled $roll"
                enemyTokenCaptured -> "Captured an enemy"
                else -> "Rolled $roll"
            }
            state = state.copy(
                players = allPlayersUpdated,
                canRoll = true,
                diceRoll = null,
                selectableTokenIds = emptyList(),
                statusText = "$reason! ${currentP.name} gets an extra turn 🎲",
                lastMoveDescription = "${currentP.name} moved. $reason!",
                extraTurn = true,
                lastCapturedColor = capturedColor,
                seqNum = state.seqNum + 1
            )
        } else {
            val nextTurnIndex = (state.currentTurnIndex + 1) % state.players.size
            val nextP = allPlayersUpdated[nextTurnIndex]
            state = state.copy(
                players = allPlayersUpdated,
                currentTurnIndex = nextTurnIndex,
                canRoll = true,
                diceRoll = null,
                selectableTokenIds = emptyList(),
                statusText = "${nextP.name}'s turn! Roll the dice 🎲",
                lastMoveDescription = "${currentP.name} moved token",
                extraTurn = false,
                lastCapturedColor = capturedColor,
                seqNum = state.seqNum + 1
            )
        }

        return state
    }
}
