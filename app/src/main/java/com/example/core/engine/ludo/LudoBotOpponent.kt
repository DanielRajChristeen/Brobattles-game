package com.example.core.engine.ludo

import com.example.core.bot.BotDifficulty
import kotlin.random.Random

/**
 * Intelligent AI Opponent for Ludo (VS Bot mode).
 * Selects legal moves prioritizing:
 * 1. Winning the match (4th token to center)
 * 2. Completing a token to center
 * 3. Capturing an enemy token
 * 4. Escaping danger (when an enemy token is 1..6 steps behind on a non-safe cell)
 * 5. Entering the home stretch (permanently safe)
 * 6. Landing on a star safe cell
 * 7. Releasing a token from the yard
 * 8. Advancing a token closest to home
 */
class LudoBotOpponent {

    fun decideMove(
        state: LudoGameState,
        difficulty: BotDifficulty = BotDifficulty.MEDIUM
    ): Int? {
        val legalIds = state.selectableTokenIds
        if (legalIds.isEmpty()) return null
        if (legalIds.size == 1) return legalIds.first()

        val roll = state.diceRoll ?: return legalIds.first()
        val botPlayer = state.currentPlayer

        // For Easy difficulty, occasionally choose a random legal move (60% chance)
        if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.60f) {
            return legalIds.random()
        }

        // Score each candidate move
        val scoredMoves = legalIds.map { tokenId ->
            val token = botPlayer.tokens.first { it.id == tokenId }
            val score = evaluateMove(token, roll, botPlayer, state)
            Pair(tokenId, score)
        }.sortedByDescending { it.second }

        // Medium difficulty picks top move 85% of the time, otherwise second best
        if (difficulty == BotDifficulty.MEDIUM && scoredMoves.size > 1 && Random.nextFloat() < 0.15f) {
            return scoredMoves[1].first
        }

        return scoredMoves.first().first
    }

    private fun evaluateMove(
        token: LudoToken,
        roll: Int,
        botPlayer: LudoPlayerState,
        state: LudoGameState
    ): Double {
        var score = 0.0

        if (token.isInYard) {
            // Releasing token from yard
            score += 650.0

            // Check if start square is safe or has enemy to capture
            val startIndex = botPlayer.color.startIndex
            val enemyOnStart = findEnemyTokensAtTrackIndex(startIndex, botPlayer.color, state)
            if (enemyOnStart.isNotEmpty()) {
                // Capturing on release!
                score += 1800.0
            }
            return score
        }

        val targetStep = token.step + roll

        // 1. Winning the match (4th token finished)
        if (targetStep == LudoToken.STEP_FINISH) {
            val otherFinished = botPlayer.tokens.count { it.id != token.id && it.isFinished }
            if (otherFinished == 3) {
                return 100000.0 // Instant Win
            }
            // Completing a token into Home Center
            score += 15000.0
            return score
        }

        // 2. Entering or advancing in Home Stretch (safe forever)
        if (targetStep >= LudoToken.STEP_FIRST_HOME_STRETCH) {
            if (token.step < LudoToken.STEP_FIRST_HOME_STRETCH) {
                // Entering home stretch from outer track
                score += 2500.0
            } else {
                // Advancing closer to center inside home stretch
                score += 1200.0 + (targetStep * 20)
            }
            return score
        }

        // Target is on outer track
        val targetTrackIndex = (botPlayer.color.startIndex + targetStep) % 52
        val isTargetSafe = LudoBoardCoordinates.isSafeSquare(targetTrackIndex)

        // 3. Capturing enemy token
        val enemiesAtTarget = findEnemyTokensAtTrackIndex(targetTrackIndex, botPlayer.color, state)
        if (!isTargetSafe && enemiesAtTarget.isNotEmpty()) {
            val maxEnemyProgress = enemiesAtTarget.maxOfOrNull { it.step } ?: 0
            score += 5000.0 + (maxEnemyProgress * 30.0)
        }

        // 4. Escaping danger
        val isCurrentlyInDanger = isTokenVulnerable(token, botPlayer.color, state)
        if (isCurrentlyInDanger) {
            score += 2200.0
        }

        // 5. Landing on a safe star cell
        if (isTargetSafe) {
            score += 1400.0
        } else {
            // Check if target square puts this token in danger of an enemy 1..6 squares behind
            if (isTrackIndexVulnerable(targetTrackIndex, botPlayer.color, state)) {
                score -= 1600.0
            }
        }

        // 6. General advancement
        score += targetStep * 15.0

        return score
    }

    private fun findEnemyTokensAtTrackIndex(
        trackIndex: Int,
        myColor: LudoColor,
        state: LudoGameState
    ): List<LudoToken> {
        val result = mutableListOf<LudoToken>()
        for (player in state.players) {
            if (player.color == myColor) continue
            for (enemyToken in player.tokens) {
                if (enemyToken.isOnTrack && enemyToken.trackIndex == trackIndex) {
                    result.add(enemyToken)
                }
            }
        }
        return result
    }

    private fun isTokenVulnerable(
        token: LudoToken,
        myColor: LudoColor,
        state: LudoGameState
    ): Boolean {
        if (!token.isOnTrack) return false
        val myTrackIdx = token.trackIndex ?: return false
        if (LudoBoardCoordinates.isSafeSquare(myTrackIdx)) return false

        return isTrackIndexVulnerable(myTrackIdx, myColor, state)
    }

    private fun isTrackIndexVulnerable(
        trackIndex: Int,
        myColor: LudoColor,
        state: LudoGameState
    ): Boolean {
        for (player in state.players) {
            if (player.color == myColor) continue
            for (enemyToken in player.tokens) {
                if (enemyToken.isOnTrack) {
                    val enemyTrackIdx = enemyToken.trackIndex ?: continue
                    // Distance behind this square (1..6)
                    val dist = (trackIndex - enemyTrackIdx + 52) % 52
                    if (dist in 1..6) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
