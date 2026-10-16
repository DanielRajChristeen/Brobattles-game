package com.example.ui.screens.detail.archery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.core.engine.archery.ArcheryCharacterAnimState
import com.example.core.engine.archery.ArcheryHitZone
import com.example.core.engine.archery.AttachedArrow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Expressive 2D skeletal stick figure with bow, dynamic articulated aiming,
 * cartoon hit reactions (Head KO, Chest recoil, Leg buckle, Foot hop),
 * health-based weariness, and persistent attached arrows.
 */
fun DrawScope.drawArcheryStickFigure(
    isLeft: Boolean,
    anchorX: Float,
    groundY: Float,
    health: Int,
    animState: ArcheryCharacterAnimState,
    aimAngleDegrees: Float,
    aimPower: Float,
    staggerProgress: Float, // 1.0f on impact down to 0.0f
    lastHitZone: ArcheryHitZone?,
    attachedArrows: List<AttachedArrow>,
    color: Color,
    bowColor: Color,
    strokeWidth: Float = 7.5f,
    timeMs: Long = System.currentTimeMillis()
) {
    val dir = if (isLeft) 1f else -1f // Facing direction (+1 = Right, -1 = Left)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val headRadius = 18f

    // ------------------------------------------------------------------------
    // 1. KNOCKED OUT / DEFEATED FALL
    // ------------------------------------------------------------------------
    if (animState == ArcheryCharacterAnimState.KNOCKED_OUT || health <= 0) {
        val fallDir = -dir // Knocked backwards
        val pelvisX = anchorX + (28f * fallDir)
        val pelvisY = groundY - 14f

        val neckX = pelvisX + (48f * fallDir)
        val neckY = groundY - 16f

        val headX = neckX + (22f * fallDir)
        val headY = groundY - 20f

        // Head with "X" eyes
        drawCircle(color = color, radius = headRadius, center = Offset(headX, headY), style = stroke)
        // Cartoon KO 'X' eye
        val eyeSize = 4.5f
        drawLine(color, Offset(headX - eyeSize, headY - eyeSize), Offset(headX + eyeSize, headY + eyeSize), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(color, Offset(headX - eyeSize, headY + eyeSize), Offset(headX + eyeSize, headY - eyeSize), strokeWidth = 2.5f, cap = StrokeCap.Round)

        // Spine flat on ground
        drawLine(color, Offset(pelvisX, pelvisY), Offset(neckX, neckY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // Flumped legs
        val leg1Knee = Offset(pelvisX - (18f * fallDir), groundY - 22f)
        val leg1Foot = Offset(pelvisX - (38f * fallDir), groundY - 4f)
        drawLine(color, Offset(pelvisX, pelvisY), leg1Knee, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, leg1Knee, leg1Foot, strokeWidth = strokeWidth, cap = StrokeCap.Round)

        val leg2Foot = Offset(pelvisX - (32f * fallDir), groundY - 2f)
        drawLine(color, Offset(pelvisX, pelvisY), leg2Foot, strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // Dropped arms
        drawLine(color, Offset(neckX, neckY), Offset(neckX + (12f * fallDir), groundY - 2f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(neckX, neckY), Offset(neckX - (14f * fallDir), groundY - 6f), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // Dropped bow lying on the ground
        val bowDropX = anchorX + (55f * fallDir)
        val bowDropY = groundY - 6f
        val bowPath = Path().apply {
            moveTo(bowDropX - 16f, bowDropY - 10f)
            quadraticTo(bowDropX + 4f, bowDropY - 2f, bowDropX + 22f, bowDropY - 8f)
        }
        drawPath(bowPath, bowColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round))

        // Render attached arrows on knocked-out body
        for (arrow in attachedArrows) {
            val arrowAnchor = when (arrow.zone) {
                ArcheryHitZone.HEAD -> Offset(headX + arrow.localOffsetX, headY + arrow.localOffsetY)
                ArcheryHitZone.CHEST -> Offset((pelvisX + neckX) / 2f + arrow.localOffsetX, neckY + arrow.localOffsetY)
                ArcheryHitZone.LEGS -> Offset(leg1Knee.x + arrow.localOffsetX, leg1Knee.y + arrow.localOffsetY)
                ArcheryHitZone.FEET -> Offset(leg1Foot.x + arrow.localOffsetX, groundY + arrow.localOffsetY)
                ArcheryHitZone.NONE -> Offset(neckX, neckY)
            }
            drawAttachedArrow(arrowAnchor, arrow.angleDegrees)
        }
        return
    }

    // ------------------------------------------------------------------------
    // 2. ACTIVE / STANDING ANATOMY WITH PROCEDURAL RIGGING
    // ------------------------------------------------------------------------
    val breathOffset = (sin(timeMs / 320.0) * 2.2f).toFloat()

    // Weariness factor from remaining health (0f at 100%, 1f at 0%)
    val weariness = ((100 - health) / 100f).coerceIn(0f, 1f)
    val slouchY = weariness * 10f
    val slouchX = weariness * 5f * dir

    // Hit reaction stagger offsets
    var recoilTorsoAngle = 0f
    var recoilX = 0f
    var hopFootY = 0f
    var buckleKneeY = 0f

    if (staggerProgress > 0.05f) {
        val s = staggerProgress
        when (lastHitZone) {
            ArcheryHitZone.CHEST -> {
                // Strong recoil: chest thrown backwards
                recoilTorsoAngle = -35f * s * dir
                recoilX = -22f * s * dir
            }
            ArcheryHitZone.LEGS -> {
                // Leg buckles, knees dip sharply
                buckleKneeY = 16f * s
                recoilX = -12f * s * dir
            }
            ArcheryHitZone.FEET -> {
                // Exaggerated comic foot hop!
                hopFootY = -24f * s
                recoilX = -8f * s * dir
            }
            else -> {
                recoilX = -14f * s * dir
            }
        }
    }

    // Celebrating bounce
    var celebBounceY = 0f
    if (animState == ArcheryCharacterAnimState.CELEBRATING) {
        celebBounceY = -kotlin.math.abs(sin(timeMs / 180.0) * 18f).toFloat()
    }

    // Base joint positions
    val pelvisX = anchorX + recoilX + slouchX
    val pelvisY = groundY - 48f + breathOffset * 0.4f + slouchY + buckleKneeY + celebBounceY

    // Spine length ~ 55px
    val baseSpineAngle = if (animState == ArcheryCharacterAnimState.AIMING) -8f * dir else 0f
    val spineAngleDeg = baseSpineAngle + recoilTorsoAngle
    val spineRad = (spineAngleDeg * PI / 180f).toFloat()

    val neckX = pelvisX + (sin(spineRad) * 54f)
    val neckY = pelvisY - (cos(spineRad) * 54f)

    val headX = neckX + (sin(spineRad) * 24f)
    val headY = neckY - (cos(spineRad) * 24f)

    // ------------------------------------------------------------------------
    // DRAW LEGS & FEET
    // ------------------------------------------------------------------------
    val backFootX = anchorX - (18f * dir)
    val frontFootX = anchorX + (18f * dir)
    val backFootY = groundY
    val frontFootY = groundY + hopFootY

    // Knee joints with natural bend
    val backKneeX = (pelvisX + backFootX) / 2f - (6f * dir)
    val backKneeY = (pelvisY + backFootY) / 2f + buckleKneeY
    val frontKneeX = (pelvisX + frontFootX) / 2f + (8f * dir)
    val frontKneeY = (pelvisY + frontFootY) / 2f + buckleKneeY + (hopFootY * 0.5f)

    // Draw Back Leg
    drawLine(color.copy(alpha = 0.85f), Offset(pelvisX, pelvisY), Offset(backKneeX, backKneeY), strokeWidth = strokeWidth * 0.95f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.85f), Offset(backKneeX, backKneeY), Offset(backFootX, backFootY), strokeWidth = strokeWidth * 0.95f, cap = StrokeCap.Round)
    // Back Foot (shoe/pad line)
    drawLine(color.copy(alpha = 0.85f), Offset(backFootX - 4f * dir, backFootY), Offset(backFootX + 10f * dir, backFootY), strokeWidth = strokeWidth * 0.85f, cap = StrokeCap.Round)

    // Draw Front Leg
    drawLine(color, Offset(pelvisX, pelvisY), Offset(frontKneeX, frontKneeY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(frontKneeX, frontKneeY), Offset(frontFootX, frontFootY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    // Front Foot
    drawLine(color, Offset(frontFootX - 4f * dir, frontFootY), Offset(frontFootX + 12f * dir, frontFootY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

    // ------------------------------------------------------------------------
    // DRAW TORSO / SPINE
    // ------------------------------------------------------------------------
    drawLine(color, Offset(pelvisX, pelvisY), Offset(neckX, neckY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

    // ------------------------------------------------------------------------
    // DRAW HEAD & EXPRESSIVE FACE
    // ------------------------------------------------------------------------
    drawCircle(color = color, radius = headRadius, center = Offset(headX, headY), style = stroke)

    // Expressive face
    val eyeOffsetX = 5f * dir
    val eyeY = headY - 2f
    if (staggerProgress > 0.1f) {
        // Ouch / Wince eye
        val ouchSize = 3.5f
        drawLine(color, Offset(headX + eyeOffsetX - ouchSize, eyeY - ouchSize), Offset(headX + eyeOffsetX + ouchSize, eyeY + ouchSize), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(color, Offset(headX + eyeOffsetX - ouchSize, eyeY + ouchSize), Offset(headX + eyeOffsetX + ouchSize, eyeY - ouchSize), strokeWidth = 2f, cap = StrokeCap.Round)
    } else if (animState == ArcheryCharacterAnimState.CELEBRATING) {
        // Happy arc eye ^_^
        val happyPath = Path().apply {
            moveTo(headX + eyeOffsetX - 4f, eyeY + 1f)
            quadraticTo(headX + eyeOffsetX, eyeY - 4f, headX + eyeOffsetX + 4f, eyeY + 1f)
        }
        drawPath(happyPath, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    } else {
        // Focused pupil
        drawCircle(color = color, radius = 2.8f, center = Offset(headX + eyeOffsetX, eyeY))
    }

    // ------------------------------------------------------------------------
    // DRAW ARMS & BOW
    // ------------------------------------------------------------------------
    when (animState) {
        ArcheryCharacterAnimState.AIMING -> {
            // Full articulated aiming:
            // Calculate aim vector from aimAngleDegrees
            val aimRad = (aimAngleDegrees * PI / 180f).toFloat()
            val aimCos = cos(aimRad)
            val aimSin = sin(aimRad)

            // Bow Hand extended towards target along aim angle
            val armReach = 46f
            val bowHandX = neckX + (aimCos * armReach)
            val bowHandY = neckY + (aimSin * armReach)

            // Shoulder to Bow Hand (Bow Arm)
            val bowElbowX = neckX + (aimCos * armReach * 0.48f) + (aimSin * 8f * dir)
            val bowElbowY = neckY + (aimSin * armReach * 0.48f) - (aimCos * 8f * dir)
            drawLine(color, Offset(neckX, neckY), Offset(bowElbowX, bowElbowY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(bowElbowX, bowElbowY), Offset(bowHandX, bowHandY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Drawing Hand pulls string back by drawDistance (based on aimPower)
            val drawDist = 12f + (aimPower * 26f)
            val drawHandX = bowHandX - (aimCos * drawDist)
            val drawHandY = bowHandY - (aimSin * drawDist)

            // Shoulder to Drawing Hand (Draw Arm)
            val drawElbowX = neckX - (aimCos * 14f) + (aimSin * 16f * dir)
            val drawElbowY = neckY - (aimSin * 14f) - (aimCos * 16f * dir)
            drawLine(color, Offset(neckX, neckY), Offset(drawElbowX, drawElbowY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(drawElbowX, drawElbowY), Offset(drawHandX, drawHandY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Draw Curved Bow
            val bowHalfLen = 34f
            val normalX = -aimSin
            val normalY = aimCos

            val bowTip1 = Offset(bowHandX + normalX * bowHalfLen, bowHandY + normalY * bowHalfLen)
            val bowTip2 = Offset(bowHandX - normalX * bowHalfLen, bowHandY - normalY * bowHalfLen)

            // Bow wood curve
            val bowCurve = Path().apply {
                moveTo(bowTip1.x, bowTip1.y)
                quadraticTo(bowHandX + (aimCos * 10f), bowHandY + (aimSin * 10f), bowTip2.x, bowTip2.y)
            }
            drawPath(bowCurve, bowColor, style = Stroke(width = 5f, cap = StrokeCap.Round))

            // Bowstring pulled back to drawHand
            val stringColor = Color.LightGray.copy(alpha = 0.9f)
            drawLine(stringColor, bowTip1, Offset(drawHandX, drawHandY), strokeWidth = 1.8f)
            drawLine(stringColor, Offset(drawHandX, drawHandY), bowTip2, strokeWidth = 1.8f)

            // Nocked Arrow resting on bow and held by draw hand
            val arrowLen = 52f
            val arrowTipX = drawHandX + (aimCos * arrowLen)
            val arrowTipY = drawHandY + (aimSin * arrowLen)
            // Arrow shaft
            drawLine(Color(0xFF78350F), Offset(drawHandX, drawHandY), Offset(arrowTipX, arrowTipY), strokeWidth = 3f, cap = StrokeCap.Round)
            // Arrow tip (metal arrowhead)
            drawLine(Color(0xFFE2E8F0), Offset(arrowTipX - aimCos * 8f, arrowTipY - aimSin * 8f), Offset(arrowTipX, arrowTipY), strokeWidth = 4.5f, cap = StrokeCap.Round)
            // Arrow fletching feathers
            val featherBack = Offset(drawHandX + aimCos * 4f, drawHandY + aimSin * 4f)
            val featherColor = if (isLeft) Color(0xFF0D9488) else Color(0xFFF97316)
            drawLine(featherColor, featherBack, Offset(drawHandX + aimCos * 12f, drawHandY + aimSin * 12f), strokeWidth = 4.5f, cap = StrokeCap.Round)
        }

        ArcheryCharacterAnimState.CELEBRATING -> {
            // Victory jump with bow held high!
            val bowHandX = neckX + (12f * dir)
            val bowHandY = neckY - 42f
            drawLine(color, Offset(neckX, neckY), Offset(bowHandX, bowHandY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Raised other hand
            val otherHandX = neckX - (18f * dir)
            val otherHandY = neckY - 36f
            drawLine(color, Offset(neckX, neckY), Offset(otherHandX, otherHandY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Vertical victory bow
            val bowCurve = Path().apply {
                moveTo(bowHandX - 8f, bowHandY - 26f)
                quadraticTo(bowHandX + 10f * dir, bowHandY, bowHandX - 8f, bowHandY + 26f)
            }
            drawPath(bowCurve, bowColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawLine(Color.LightGray.copy(alpha = 0.8f), Offset(bowHandX - 8f, bowHandY - 26f), Offset(bowHandX - 8f, bowHandY + 26f), strokeWidth = 1.8f)
        }

        else -> {
            // IDLE / RECOIL: Relaxed bow resting in front hand
            val handX = neckX + (22f * dir)
            val handY = neckY + 24f + (breathOffset * 0.8f)
            val elbowX = neckX + (10f * dir)
            val elbowY = neckY + 14f

            drawLine(color, Offset(neckX, neckY), Offset(elbowX, elbowY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(elbowX, elbowY), Offset(handX, handY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Other arm relaxed by hip
            val otherHandX = neckX - (12f * dir)
            val otherHandY = neckY + 26f
            drawLine(color, Offset(neckX, neckY), Offset(otherHandX, otherHandY), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Bow held casually downwards
            val bowCurve = Path().apply {
                moveTo(handX + 6f * dir, handY - 28f)
                quadraticTo(handX + 16f * dir, handY, handX + 6f * dir, handY + 28f)
            }
            drawPath(bowCurve, bowColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawLine(Color.LightGray.copy(alpha = 0.7f), Offset(handX + 6f * dir, handY - 28f), Offset(handX + 6f * dir, handY + 28f), strokeWidth = 1.6f)
        }
    }

    // ------------------------------------------------------------------------
    // DRAW PERSISTENT ATTACHED ARROWS
    // ------------------------------------------------------------------------
    // Attached arrows faithfully track their associated body bone even while moving/reacting
    for (arrow in attachedArrows) {
        val arrowAnchor = when (arrow.zone) {
            ArcheryHitZone.HEAD -> Offset(headX + arrow.localOffsetX, headY + arrow.localOffsetY)
            ArcheryHitZone.CHEST -> Offset((pelvisX + neckX) / 2f + arrow.localOffsetX, (pelvisY + neckY) / 2f + arrow.localOffsetY)
            ArcheryHitZone.LEGS -> Offset(frontKneeX + arrow.localOffsetX, frontKneeY + arrow.localOffsetY)
            ArcheryHitZone.FEET -> Offset(frontFootX + arrow.localOffsetX, frontFootY + arrow.localOffsetY)
            ArcheryHitZone.NONE -> Offset(neckX, neckY)
        }
        drawAttachedArrow(arrowAnchor, arrow.angleDegrees)
    }
}

/**
 * Draws an embedded arrow sticking into the character.
 */
private fun DrawScope.drawAttachedArrow(anchor: Offset, angleDegrees: Float) {
    val rad = (angleDegrees * PI / 180f).toFloat()
    val cosA = cos(rad)
    val sinA = sin(rad)

    val shaftLength = 40f
    // Shaft extends backwards from embedded point
    val tailX = anchor.x - (cosA * shaftLength)
    val tailY = anchor.y - (sinA * shaftLength)

    // Wooden shaft
    drawLine(
        color = Color(0xFF78350F), // Wood brown
        start = anchor,
        end = Offset(tailX, tailY),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Red/Teal fletching feather
    val fletchLen = 10f
    val fletchX = tailX + (cosA * fletchLen)
    val fletchY = tailY + (sinA * fletchLen)
    drawLine(
        color = Color(0xFFEF4444), // Red feathers
        start = Offset(tailX, tailY),
        end = Offset(fletchX, fletchY),
        strokeWidth = 5.5f,
        cap = StrokeCap.Round
    )

    // Small impact spark / dot
    drawCircle(
        color = Color(0xFFFBBF24),
        radius = 2.5f,
        center = anchor
    )
}

/**
 * Draws an elevated floating island / cliff platform for an archer.
 * Under the platform is the open gravity canyon.
 */
fun DrawScope.drawArcheryPlatform(
    isLeft: Boolean,
    platformStartX: Float,
    platformEndX: Float,
    groundY: Float,
    isDark: Boolean
) {
    val platformWidth = platformEndX - platformStartX
    val cliffDepth = 42f

    // 1. Rock Cliff Under-base
    val rockPath = Path().apply {
        if (isLeft) {
            moveTo(platformStartX, groundY)
            lineTo(platformEndX, groundY)
            // Rounded / tapered edge
            quadraticTo(platformEndX - 10f, groundY + cliffDepth * 0.6f, platformEndX - 28f, groundY + cliffDepth)
            lineTo(platformStartX, groundY + cliffDepth)
            close()
        } else {
            moveTo(platformStartX, groundY)
            lineTo(platformEndX, groundY)
            lineTo(platformEndX, groundY + cliffDepth)
            lineTo(platformStartX + 28f, groundY + cliffDepth)
            quadraticTo(platformStartX + 10f, groundY + cliffDepth * 0.6f, platformStartX, groundY)
            close()
        }
    }

    val rockColor = if (isDark) Color(0xFF334155) else Color(0xFF64748B)
    val rockShadow = if (isDark) Color(0xFF1E293B) else Color(0xFF475569)
    drawPath(rockPath, rockColor)

    // Rock stratification line / depth
    val strataY = groundY + 18f
    drawLine(
        color = rockShadow.copy(alpha = 0.6f),
        start = Offset(if (isLeft) platformStartX else platformStartX + 20f, strataY),
        end = Offset(if (isLeft) platformEndX - 20f else platformEndX, strataY),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // 2. Vibrant Top Grass Layer
    val grassColor = Color(0xFF10B981)
    val grassShadow = Color(0xFF059669)
    drawLine(
        color = grassShadow,
        start = Offset(platformStartX, groundY + 3.5f),
        end = Offset(platformEndX, groundY + 3.5f),
        strokeWidth = 7f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = grassColor,
        start = Offset(platformStartX, groundY),
        end = Offset(platformEndX, groundY),
        strokeWidth = 5.5f,
        cap = StrokeCap.Round
    )

    // Little grass blades / detail tufts on top of the platform
    val step = platformWidth / 5f
    for (i in 1..4) {
        val gx = platformStartX + (i * step)
        drawLine(
            color = grassColor,
            start = Offset(gx, groundY),
            end = Offset(gx + (if (isLeft) 4f else -4f), groundY - 7f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Renders the subtle historical aim angle reference markers for past shots.
 * Shows where the player previous aimed without acting as an aim lock.
 */
fun DrawScope.drawPreviousShotMarkers(
    shotReferences: List<com.example.core.engine.archery.ShotAngleReference>,
    baseColor: Color
) {
    if (shotReferences.isEmpty()) return

    shotReferences.take(4).forEachIndexed { index, ref ->
        val ageAlpha = (0.44f - (index * 0.09f)).coerceIn(0.12f, 0.44f)

        // Exact authoritative launch angle in world screen space:
        // Player 1 (left): elevation angle maps to -elevation (upward in screen Y)
        // Player 2 (right): elevation angle maps to -180° + elevation (up-left in screen Y)
        val worldAngle = if (ref.shooterIsLeft) -ref.angleDegrees else -180f + ref.angleDegrees
        val rad = (worldAngle * PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        // 1. Subtle angle vector line extending from launch point
        val tickLen = 30f
        val tickEnd = Offset(ref.launchX + cosA * tickLen, ref.launchY + sinA * tickLen)
        drawLine(
            color = baseColor.copy(alpha = ageAlpha),
            start = Offset(ref.launchX, ref.launchY),
            end = tickEnd,
            strokeWidth = if (index == 0) 2.2f else 1.6f,
            cap = StrokeCap.Round
        )
        // Little circular pip at the end of the tick
        drawCircle(
            color = baseColor.copy(alpha = ageAlpha),
            radius = if (index == 0) 2.6f else 1.8f,
            center = tickEnd
        )

        // 2. Short subtle dotted trajectory arc for the first 8 steps
        val speed = 500f + (ref.power * 850f)
        val preview = com.example.core.engine.archery.ArcheryPhysics.calculateTrajectoryPreview(
            startX = ref.launchX,
            startY = ref.launchY,
            vx = cosA * speed,
            vy = sinA * speed,
            maxSteps = 8
        )
        for (i in preview.indices) {
            val pt = preview[i]
            val dotAlpha = ageAlpha * (1f - (i.toFloat() / 10f)).coerceAtLeast(0.4f)
            drawCircle(
                color = baseColor.copy(alpha = dotAlpha),
                radius = if (index == 0) 2.2f else 1.6f,
                center = pt
            )
        }
    }
}

