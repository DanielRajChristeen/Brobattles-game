package com.example.core.engine.archery

enum class ArcheryHitZone(val displayName: String) {
    HEAD("HEAD"),
    CHEST("CHEST"),
    LEGS("LEGS"),
    FEET("FEET"),
    NONE("MISS")
}

data class AttachedArrow(
    val id: Long = System.nanoTime(),
    val zone: ArcheryHitZone,
    val localOffsetX: Float,
    val localOffsetY: Float,
    val angleDegrees: Float
)

data class ArrowProjectile(
    val id: Long = System.nanoTime(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angleDegrees: Float = 0f,
    val shooterIsLeft: Boolean,
    var isFlying: Boolean = true,
    var hasProcessedImpact: Boolean = false,
    val trail: MutableList<androidx.compose.ui.geometry.Offset> = mutableListOf()
)

enum class ArcheryDifficulty(val title: String) {
    EASY("EASY"),
    MEDIUM("MEDIUM"),
    HARD("HARD")
}

data class ShotAngleReference(
    val angleDegrees: Float,
    val power: Float,
    val launchX: Float,
    val launchY: Float,
    val shooterIsLeft: Boolean,
    val isPullMode: Boolean = false,
    val resultText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ArcheryCharacterAnimState {
    IDLE,
    AIMING,
    RECOIL,
    KNOCKED_OUT,
    CELEBRATING
}

data class ArcheryDamageEffect(
    val newHealth: Int,
    val isHeadshot: Boolean,
    val zone: ArcheryHitZone
)

object ArcheryRules {
    /**
     * Applies damage according to the strict hit-zone rules:
     * - HEAD: Instant knockout (0 HP).
     * - CHEST: -50 HP (health -= 50). Two chest hits cause KO!
     * - LEGS: Decreases current health by 20% (-20 HP).
     * - FEET: Decreases current health by 5% (-5 HP).
     */
    fun calculateDamage(currentHealth: Int, hitZone: ArcheryHitZone): ArcheryDamageEffect {
        return when (hitZone) {
            ArcheryHitZone.HEAD -> {
                ArcheryDamageEffect(newHealth = 0, isHeadshot = true, zone = ArcheryHitZone.HEAD)
            }
            ArcheryHitZone.CHEST -> {
                // Chest hit rule: Health decreases by 50 HP (health -= 50)
                val newHp = (currentHealth - 50).coerceAtLeast(0)
                ArcheryDamageEffect(newHealth = newHp, isHeadshot = false, zone = ArcheryHitZone.CHEST)
            }
            ArcheryHitZone.LEGS -> {
                val newHp = (currentHealth - 20).coerceAtLeast(0)
                ArcheryDamageEffect(newHealth = newHp, isHeadshot = false, zone = ArcheryHitZone.LEGS)
            }
            ArcheryHitZone.FEET -> {
                val newHp = (currentHealth - 5).coerceAtLeast(0)
                ArcheryDamageEffect(newHealth = newHp, isHeadshot = false, zone = ArcheryHitZone.FEET)
            }
            ArcheryHitZone.NONE -> {
                ArcheryDamageEffect(newHealth = currentHealth, isHeadshot = false, zone = ArcheryHitZone.NONE)
            }
        }
    }

    /**
     * In VS Bot and same-device 2 Players, targetScore is the exact total number of rounds to play (3, 5, or 10).
     * The match must not end early when a player reaches the selected number of round wins.
     * In Nearby mode, the legacy first-to-targetScore condition is preserved.
     */
    fun isMatchFinished(
        currentRound: Int,
        targetScore: Int,
        isNearbyMode: Boolean,
        p1Score: Int,
        p2Score: Int
    ): Boolean {
        return if (isNearbyMode) {
            p1Score >= targetScore || p2Score >= targetScore
        } else {
            currentRound >= targetScore
        }
    }

    /**
     * Resolves the match winner when all selected rounds finish.
     * The player with the most round wins is declared the winner, or DRAW if tied.
     */
    fun determineMatchWinner(p1Score: Int, p2Score: Int): String {
        return when {
            p1Score > p2Score -> "PLAYER_1"
            p2Score > p1Score -> "PLAYER_2"
            else -> "DRAW"
        }
    }
}
