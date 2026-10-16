package com.example.core.engine.archery

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ArcheryBot {

    data class BotShot(
        val angleDegrees: Float,
        val power: Float,
        val vx: Float,
        val vy: Float,
        val targetedZone: ArcheryHitZone
    )

    data class BotDifficultyProfile(
        val minDelayMs: Long,
        val maxDelayMs: Long,
        val holdMinMs: Long,
        val holdMaxMs: Long,
        val headChance: Float,
        val chestChance: Float,
        val legsChance: Float,
        val feetChance: Float,
        val angleErrorDegRange: ClosedFloatingPointRange<Float>,
        val powerErrorFactorRange: ClosedFloatingPointRange<Float>,
        val elevationMinDeg: Float,
        val elevationMaxDeg: Float
    )

    val EASY_PROFILE = BotDifficultyProfile(
        minDelayMs = 2000L,
        maxDelayMs = 3200L,
        holdMinMs = 450L,
        holdMaxMs = 800L,
        headChance = 0.08f,
        chestChance = 0.35f,
        legsChance = 0.35f,
        feetChance = 0.22f,
        angleErrorDegRange = -7.5f..7.5f,
        powerErrorFactorRange = 0.82f..1.18f,
        elevationMinDeg = 30f,
        elevationMaxDeg = 52f
    )

    val MEDIUM_PROFILE = BotDifficultyProfile(
        minDelayMs = 1400L,
        maxDelayMs = 2400L,
        holdMinMs = 350L,
        holdMaxMs = 600L,
        headChance = 0.22f,
        chestChance = 0.52f,
        legsChance = 0.18f,
        feetChance = 0.08f,
        angleErrorDegRange = -3.0f..3.0f,
        powerErrorFactorRange = 0.94f..1.06f,
        elevationMinDeg = 34f,
        elevationMaxDeg = 48f
    )

    val HARD_PROFILE = BotDifficultyProfile(
        minDelayMs = 1000L,
        maxDelayMs = 1800L,
        holdMinMs = 240L,
        holdMaxMs = 420L,
        headChance = 0.42f,
        chestChance = 0.48f,
        legsChance = 0.08f,
        feetChance = 0.02f,
        angleErrorDegRange = -1.2f..1.2f,
        powerErrorFactorRange = 0.98f..1.025f,
        elevationMinDeg = 36f,
        elevationMaxDeg = 46f
    )

    fun getProfile(difficulty: ArcheryDifficulty): BotDifficultyProfile = when (difficulty) {
        ArcheryDifficulty.EASY -> EASY_PROFILE
        ArcheryDifficulty.MEDIUM -> MEDIUM_PROFILE
        ArcheryDifficulty.HARD -> HARD_PROFILE
    }

    /**
     * Calculates a shot tailored by difficulty profile with physics-based calculations.
     */
    fun calculateShot(
        botAnchorX: Float,
        playerAnchorX: Float,
        groundY: Float,
        difficulty: ArcheryDifficulty = ArcheryDifficulty.MEDIUM
    ): BotShot {
        val profile = getProfile(difficulty)

        // Choose target zone with varied probability by difficulty
        val roll = Random.nextFloat()
        val pHead = profile.headChance
        val pChest = pHead + profile.chestChance
        val pLegs = pChest + profile.legsChance

        val (targetZone, targetY) = when {
            roll < pHead -> ArcheryHitZone.HEAD to (groundY - 130f)
            roll < pChest -> ArcheryHitZone.CHEST to (groundY - 82f)
            roll < pLegs -> ArcheryHitZone.LEGS to (groundY - 35f)
            else -> ArcheryHitZone.FEET to (groundY - 5f)
        }

        val launchX = botAnchorX - 18f
        val launchY = groundY - 75f

        val dx = (playerAnchorX - launchX) // Negative because bot shoots leftward (-X)
        val g = ArcheryPhysics.GRAVITY

        // Base elevation angle above horizontal
        val baseElevationDeg = Random.nextDouble(
            profile.elevationMinDeg.toDouble(),
            profile.elevationMaxDeg.toDouble()
        ).toFloat()

        // Apply difficulty angle error
        val angleError = Random.nextDouble(
            profile.angleErrorDegRange.start.toDouble(),
            profile.angleErrorDegRange.endInclusive.toDouble()
        ).toFloat()
        val elevationDeg = (baseElevationDeg + angleError).coerceIn(15f, 75f)

        val elevationRad = elevationDeg * (PI / 180.0)
        val cosElev = cos(elevationRad)
        val sinElev = sin(elevationRad)
        val tanElev = sinElev / cosElev

        val dist = kotlin.math.abs(dx)
        val heightDiff = launchY - targetY
        val denom = 2.0 * cosElev * cosElev * (dist * tanElev - heightDiff)
        val idealVelocity = if (denom > 0) {
            sqrt((g * dist * dist) / denom).toFloat()
        } else {
            750f
        }

        // Apply difficulty power error
        val powerError = Random.nextDouble(
            profile.powerErrorFactorRange.start.toDouble(),
            profile.powerErrorFactorRange.endInclusive.toDouble()
        ).toFloat()

        val velocity = (idealVelocity * powerError).coerceIn(400f, 1600f)
        // In screen coordinates: moving left = negative vx; moving up = negative vy
        val vx = (-velocity * cosElev).toFloat()
        val vy = (-velocity * sinElev).toFloat()

        val power = (velocity / 1400f).coerceIn(0.15f, 1f)
        val finalAngleDeg = (-180f + elevationDeg)

        return BotShot(
            angleDegrees = finalAngleDeg,
            power = power,
            vx = vx,
            vy = vy,
            targetedZone = targetZone
        )
    }
}
