package com.example.ui.screens.detail.tugofwar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Advanced 2D Skeletal Stick Figure for Tug of War.
 * Renders full articulated anatomy: Head, Neck, Torso, Shoulders, Upper Arms,
 * Forearms, Hands (gripping rope), Pelvis, Thighs, Shins, and Feet.
 *
 * Implements the full pulling cycle:
 * Phase A (Brace) -> Phase B (Pull) -> Phase C (Strain) -> Phase D (Recovery)
 * Along with dynamic struggle postures, humorous comic falling, and triumphant celebration.
 */
fun DrawScope.drawSkeletalTugFigure(
    isLeft: Boolean,
    anchorX: Float,
    groundY: Float,
    ropeY: Float,
    pullingAdvantage: Float, // -1.0f (severely dragged/losing) to +1.0f (deeply leveraged/winning)
    pullPhase: Float,        // 0.0f to 1.0f continuous pulling cycle
    energy: Float,           // 0.0f to 1.0f instantaneous tap impulse
    isWinner: Boolean,
    isLoser: Boolean,
    finishProgress: Float,   // 0.0f to 1.0f for ending animation
    color: Color,
    strokeWidth: Float = 11f
) {
    val dir = if (isLeft) -1f else 1f // Left pulls left (-X), Right pulls right (+X)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val headRadius = 26f

    // ------------------------------------------------------------------------
    // 1. LOSER ANIMATION: Cartoon-humorous stumbling fall forward
    // ------------------------------------------------------------------------
    if (isLoser) {
        val forwardDir = -dir // Pulled toward opponent side
        val t = finishProgress.coerceIn(0f, 1f)

        // Torso rotates from upright down to horizontal floor
        val fallAngle = (t * 85f).coerceAtMost(85f)
        val slideDistance = (t * 60f) * forwardDir

        val pelvisX = anchorX + (30f * forwardDir) + slideDistance
        val pelvisY = (groundY - 50f + (t * 30f)).coerceAtMost(groundY - 18f)

        val spineLength = 70f
        val rad = (fallAngle * PI / 180f).toFloat()
        val neckX = pelvisX + (spineLength * sin(rad) * forwardDir)
        val neckY = pelvisY - (spineLength * cos(rad))

        val headX = neckX + (28f * sin(rad) * forwardDir)
        val headY = neckY - (28f * cos(rad))

        // Draw Head with "OOF!" dizzy tilt
        drawCircle(
            color = color,
            radius = headRadius,
            center = Offset(headX, headY),
            style = stroke
        )

        // Draw Torso
        drawLine(
            color = color,
            start = Offset(pelvisX, pelvisY),
            end = Offset(neckX, neckY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Flailing arms (lost grip on rope)
        val armFlail1X = neckX + (35f * forwardDir)
        val armFlail1Y = neckY - 20f + (t * 35f)
        drawLine(
            color = color,
            start = Offset(neckX, neckY + 8f),
            end = Offset(armFlail1X, armFlail1Y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val armFlail2X = neckX + (45f * forwardDir)
        val armFlail2Y = neckY + 15f + (t * 10f)
        drawLine(
            color = color,
            start = Offset(neckX, neckY + 10f),
            end = Offset(armFlail2X, armFlail2Y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Stumbled / sprawled legs
        val backLegEndX = pelvisX - (55f * forwardDir)
        val backLegEndY = groundY - 8f
        drawLine(
            color = color,
            start = Offset(pelvisX, pelvisY),
            end = Offset((pelvisX + backLegEndX) / 2f, groundY - 25f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset((pelvisX + backLegEndX) / 2f, groundY - 25f),
            end = Offset(backLegEndX, backLegEndY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val frontLegEndX = pelvisX - (15f * forwardDir)
        val frontLegEndY = groundY - 4f
        drawLine(
            color = color,
            start = Offset(pelvisX, pelvisY),
            end = Offset(frontLegEndX, frontLegEndY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        return
    }

    // ------------------------------------------------------------------------
    // 2. WINNER CELEBRATION: Joyful victory leap with arms raised (\O/)
    // ------------------------------------------------------------------------
    if (isWinner) {
        val t = finishProgress.coerceIn(0f, 1f)
        // High bouncy jump
        val jumpHeight = (sin(t * PI.toFloat() * 3f) * 40f).coerceAtLeast(0f) + (t * 15f)

        val pelvisX = anchorX
        val pelvisY = groundY - 80f - jumpHeight

        val neckX = anchorX
        val neckY = pelvisY - 72f

        val headX = anchorX
        val headY = neckY - headRadius - 4f

        // Head
        drawCircle(
            color = color,
            radius = headRadius,
            center = Offset(headX, headY),
            style = stroke
        )

        // Torso
        drawLine(
            color = color,
            start = Offset(pelvisX, pelvisY),
            end = Offset(neckX, neckY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left Victory Arm (\)
        val leftElbow = Offset(neckX - 30f, neckY - 25f)
        val leftHand = Offset(neckX - 52f, neckY - 60f)
        drawLine(
            color = color,
            start = Offset(neckX, neckY + 10f),
            end = leftElbow,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = leftElbow,
            end = leftHand,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right Victory Arm (/)
        val rightElbow = Offset(neckX + 30f, neckY - 25f)
        val rightHand = Offset(neckX + 52f, neckY - 60f)
        drawLine(
            color = color,
            start = Offset(neckX, neckY + 10f),
            end = rightElbow,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = rightElbow,
            end = rightHand,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Joyful kicking legs
        val leftKnee = Offset(pelvisX - 25f, groundY - jumpHeight - 20f)
        val leftFoot = Offset(pelvisX - 35f, groundY - jumpHeight + 5f)
        drawLine(color = color, start = Offset(pelvisX, pelvisY), end = leftKnee, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = leftKnee, end = leftFoot, strokeWidth = strokeWidth, cap = StrokeCap.Round)

        val rightKnee = Offset(pelvisX + 25f, groundY - jumpHeight - 20f)
        val rightFoot = Offset(pelvisX + 35f, groundY - jumpHeight + 5f)
        drawLine(color = color, start = Offset(pelvisX, pelvisY), end = rightKnee, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = rightKnee, end = rightFoot, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        return
    }

    // ------------------------------------------------------------------------
    // 3. ARTICULATED ACTIVE PULLING CYCLE (PHASES A, B, C, D)
    // ------------------------------------------------------------------------

    // Strain shudder: when pulling hard or under high strain
    val strainTremor = if (pullingAdvantage > 0.4f || energy > 0.4f) {
        sin(pullPhase * 40f) * (2.5f * energy.coerceAtLeast(0.3f))
    } else 0f

    // Pull impulse curve from pullPhase (0..1)
    // Peak pull occurs around pullPhase = 0.5 (Phase B/C)
    val pullWeight = sin(pullPhase * PI.toFloat()).coerceIn(0f, 1f)

    // Baseline backward lean based on advantage + current pull impulse
    val baseLean = 40f + (pullingAdvantage * 32f) + (pullWeight * 26f * (0.8f + energy * 0.5f))
    val totalLean = (baseLean * dir) + strainTremor

    // Pelvis (Hips)
    val pelvisX = anchorX + totalLean
    val pelvisY = groundY - 76f - (pullWeight * 8f) // Crouches slightly during pull

    // Spine & Neck (Torso)
    val spineAngleOffset = (18f + (pullingAdvantage * 16f) + (pullWeight * 18f)) * dir
    val neckX = pelvisX + spineAngleOffset + strainTremor
    val neckY = pelvisY - 68f

    // Head (leans in line with torso with slight chin tuck)
    val headTilt = (10f * dir)
    val headX = neckX + (12f * dir) + headTilt
    val headY = neckY - headRadius - 3f

    // 1. Draw Head
    drawCircle(
        color = color,
        radius = headRadius,
        center = Offset(headX, headY),
        style = stroke
    )

    // 2. Draw Torso / Spine
    drawLine(
        color = color,
        start = Offset(pelvisX, pelvisY),
        end = Offset(neckX, neckY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 3. Articulated Legs: Dig in hard against ground
    // Front leg (planted toward rope for leverage)
    val frontStanceDist = (45f - (pullingAdvantage * 15f)).coerceIn(20f, 65f)
    val frontFootX = anchorX - (frontStanceDist * dir)
    val frontFootY = groundY

    // Front Knee (bends dynamically as body pulls back)
    val frontKneeBend = 16f * dir
    val frontKneeX = (pelvisX + frontFootX) / 2f + frontKneeBend
    val frontKneeY = (pelvisY + frontFootY) / 2f + 8f

    // Thigh (pelvis to knee)
    drawLine(
        color = color,
        start = Offset(pelvisX, pelvisY),
        end = Offset(frontKneeX, frontKneeY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Shin (knee to ankle)
    drawLine(
        color = color,
        start = Offset(frontKneeX, frontKneeY),
        end = Offset(frontFootX, frontFootY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Foot line planted
    drawLine(
        color = color,
        start = Offset(frontFootX - (8f * dir), frontFootY),
        end = Offset(frontFootX + (14f * dir), frontFootY),
        strokeWidth = strokeWidth * 0.9f,
        cap = StrokeCap.Round
    )

    // Rear leg (braced deeply backward to anchor pull)
    val rearFootX = pelvisX + (38f * dir)
    val rearFootY = groundY
    val rearKneeX = (pelvisX + rearFootX) / 2f + (6f * dir)
    val rearKneeY = (pelvisY + rearFootY) / 2f + 4f

    drawLine(
        color = color,
        start = Offset(pelvisX, pelvisY),
        end = Offset(rearKneeX, rearKneeY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(rearKneeX, rearKneeY),
        end = Offset(rearFootX, rearFootY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(rearFootX - (10f * dir), rearFootY),
        end = Offset(rearFootX + (10f * dir), rearFootY),
        strokeWidth = strokeWidth * 0.9f,
        cap = StrokeCap.Round
    )

    // 4. Articulated Arms: Upper arm -> Elbow -> Forearm -> Hand on Rope
    // Front hand grips rope forward; Rear hand grips slightly back
    val handTensionShift = (pullWeight * 14f) * dir
    val frontHandX = anchorX - (18f * dir) + handTensionShift
    val backHandX = anchorX + (16f * dir) + handTensionShift

    // Front Arm (Shoulder -> Elbow -> Hand)
    // During pull, elbow pulls down and inward toward ribs
    val frontShoulder = Offset(neckX - (4f * dir), neckY + 12f)
    val frontHand = Offset(frontHandX, ropeY)
    val frontElbowBend = (22f + (pullWeight * 18f))
    val frontElbowX = (frontShoulder.x + frontHand.x) / 2f + (frontElbowBend * dir)
    val frontElbowY = (frontShoulder.y + frontHand.y) / 2f + 16f
    val frontElbow = Offset(frontElbowX, frontElbowY)

    drawLine(color = color, start = frontShoulder, end = frontElbow, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = frontElbow, end = frontHand, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    // Front Hand Clench
    drawCircle(color = color, radius = strokeWidth * 0.65f, center = frontHand)

    // Back Arm (Shoulder -> Elbow -> Hand)
    val backShoulder = Offset(neckX + (6f * dir), neckY + 14f)
    val backHand = Offset(backHandX, ropeY)
    val backElbowBend = (20f + (pullWeight * 16f))
    val backElbowX = (backShoulder.x + backHand.x) / 2f + (backElbowBend * dir)
    val backElbowY = (backShoulder.y + backHand.y) / 2f + 18f
    val backElbow = Offset(backElbowX, backElbowY)

    drawLine(color = color, start = backShoulder, end = backElbow, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = backElbow, end = backHand, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    // Back Hand Clench
    drawCircle(color = color, radius = strokeWidth * 0.65f, center = backHand)
}
