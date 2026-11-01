package com.example.ui.screens.detail.battleship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.CellCoordinate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Centrally configurable attack animation timings for Battleship combat.
 * Durations are tuned to provide deliberate, visually clear naval warfare animations (~6.9s total).
 */
object AttackTimingConfig {
    const val THINKING_MS = 1200L
    const val TARGET_LOCK_MS = 1000L
    const val PROJECTILE_FLIGHT_MS = 1500L
    const val IMPACT_MS = 1200L
    const val RESULT_ANNOUNCEMENT_MS = 1500L
    const val TURN_TRANSITION_MS = 500L

    val TOTAL_SEQUENCE_MS = THINKING_MS + TARGET_LOCK_MS + PROJECTILE_FLIGHT_MS + IMPACT_MS + RESULT_ANNOUNCEMENT_MS + TURN_TRANSITION_MS
}

enum class AttackAnimationStage {
    IDLE,
    THINKING,
    TARGET_LOCK,
    PROJECTILE_FLIGHT,
    IMPACT,
    RESULT_ANNOUNCEMENT
}

enum class BoardType {
    RADAR,      // Enemy targeting board (Human attacks)
    MY_FLEET    // Player fleet defense board (Bot attacks)
}

/**
 * Single source of truth for attack animation state.
 * Encapsulates immutable attacker identity, board, grid coordinate, and exact measured screen center.
 */
data class AttackAnimationData(
    val attacker: BattleshipPlayer,
    val isBotAttacker: Boolean,
    val targetCoord: CellCoordinate,
    val targetBoard: BoardType,
    val targetCenterInOverlay: Offset,
    val cellSizePx: Float,
    val isHit: Boolean,
    val isSunk: Boolean,
    val sunkShipName: String? = null,
    val stage: AttackAnimationStage = AttackAnimationStage.IDLE,
    val bannerTitle: String = "",
    val bannerSubtitle: String = "",
    val resultText: String = "",
    val announcement: String = ""
) {
    val targetRow: Int get() = targetCoord.row
    val targetColumn: Int get() = targetCoord.col
}

typealias ActiveAttackAnimation = AttackAnimationData

/**
 * Prominent banner that unmistakably identifies WHO is attacking throughout the animation.
 * Features color coding:
 * - Human attack: Tactical Blue / Cyan theme ("⚔️ YOUR ATTACK", "Targeting enemy fleet...")
 * - Bot attack: Hazard Red / Orange theme ("🤖 BOT ATTACKING YOU!", "Incoming naval strike...")
 */
@Composable
fun AttackerIdentificationBanner(
    activeAttack: AttackAnimationData,
    modifier: Modifier = Modifier
) {
    val isBot = activeAttack.isBotAttacker
    val borderColor = if (isBot) Color(0xFFEF4444) else Color(0xFF38BDF8)
    val bgGradients = if (isBot) {
        listOf(Color(0xFF7F1D1D), Color(0xFF991B1B), Color(0xFF450A0A))
    } else {
        listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF0C4A6E))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(bgGradients))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("attacker_identification_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Attacker Title
                Text(
                    text = activeAttack.bannerTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle / Announcement text
                val subtitleText = when (activeAttack.stage) {
                    AttackAnimationStage.RESULT_ANNOUNCEMENT -> {
                        if (activeAttack.resultText.isNotBlank()) {
                            "${activeAttack.resultText} ${activeAttack.announcement}"
                        } else {
                            activeAttack.announcement
                        }
                    }
                    AttackAnimationStage.IMPACT -> {
                        if (activeAttack.resultText.isNotBlank()) activeAttack.resultText else activeAttack.bannerSubtitle
                    }
                    else -> activeAttack.bannerSubtitle
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBot) Color(0xFFFDE047) else Color(0xFFE0F2FE)
                )
            }

            // Stage Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBot) Color(0xFFDC2626) else Color(0xFF0284C7))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val stageLabel = when (activeAttack.stage) {
                    AttackAnimationStage.TARGET_LOCK -> "LOCKING 🎯"
                    AttackAnimationStage.PROJECTILE_FLIGHT -> "DROPPING 💣"
                    AttackAnimationStage.IMPACT -> if (activeAttack.isHit) "IMPACT 💥" else "SPLASH 🌊"
                    AttackAnimationStage.RESULT_ANNOUNCEMENT -> "RESULT 📢"
                    else -> "STRIKE ⚔️"
                }
                Text(
                    text = stageLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * High-performance localized attack visual effects overlay:
 * - Stage 1 (TARGET_LOCK): Rotating reticle, locking brackets, pulsing core pip at exact cell center.
 * - Stage 2 (PROJECTILE_FLIGHT / BOMB DESCENT): Top-down aerial bomb falling straight down vertically into exact cell center.
 *   Crosshair remains stationary on selected cell.
 * - Stage 3 (IMPACT): Expanding shockwave, fireball explosion or water splash ripples at exact cell center.
 */
@Composable
fun AttackEffectOverlay(
    attackData: AttackAnimationData,
    modifier: Modifier = Modifier
) {
    AttackEffectOverlay(
        targetCoord = attackData.targetCoord,
        stage = attackData.stage,
        isHit = attackData.isHit,
        isBotAttacker = attackData.isBotAttacker,
        targetCellCenter = attackData.targetCenterInOverlay,
        cellSizePx = attackData.cellSizePx,
        modifier = modifier
    )
}

@Composable
fun AttackEffectOverlay(
    targetCoord: CellCoordinate,
    stage: AttackAnimationStage,
    isHit: Boolean,
    isBotAttacker: Boolean,
    targetCellCenter: Offset,
    cellSizePx: Float,
    modifier: Modifier = Modifier
) {
    if (stage == AttackAnimationStage.IDLE || targetCellCenter == Offset.Zero) return

    val progress = remember { Animatable(0f) }

    LaunchedEffect(stage) {
        progress.snapTo(0f)
        when (stage) {
            AttackAnimationStage.TARGET_LOCK -> progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(AttackTimingConfig.TARGET_LOCK_MS.toInt(), easing = LinearEasing)
            )
            AttackAnimationStage.PROJECTILE_FLIGHT -> progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    AttackTimingConfig.PROJECTILE_FLIGHT_MS.toInt(),
                    easing = LinearEasing
                )
            )
            AttackAnimationStage.IMPACT -> progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(AttackTimingConfig.IMPACT_MS.toInt(), easing = FastOutSlowInEasing)
            )
            else -> {}
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        when (stage) {
            AttackAnimationStage.TARGET_LOCK -> {
                drawTargetCrosshair(targetCellCenter, cellSizePx, progress.value, isBotAttacker, isLocking = true)
            }
            AttackAnimationStage.PROJECTILE_FLIGHT -> {
                // Keep target crosshair stationary on selected cell
                drawTargetCrosshair(targetCellCenter, cellSizePx, 1f, isBotAttacker, isLocking = false)
                // Drop aerial bomb vertically straight down into target cell center
                drawVerticalFallingBomb(targetCellCenter, cellSizePx, progress.value, isBotAttacker)
            }
            AttackAnimationStage.IMPACT -> {
                // Target cell remains marked
                drawTargetCrosshair(targetCellCenter, cellSizePx, 1f, isBotAttacker, isLocking = false)
                if (isHit) {
                    drawExplosionImpact(targetCellCenter, cellSizePx, progress.value)
                } else {
                    drawWaterSplashImpact(targetCellCenter, cellSizePx, progress.value)
                }
            }
            AttackAnimationStage.RESULT_ANNOUNCEMENT -> {
                drawTargetCrosshair(targetCellCenter, cellSizePx, 1f, isBotAttacker, isLocking = false)
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawTargetCrosshair(
    center: Offset,
    sizePx: Float,
    progress: Float,
    isBotAttacker: Boolean,
    isLocking: Boolean = true
) {
    val themeColor = if (isBotAttacker) Color(0xFFEF4444) else Color(0xFF38BDF8)
    val halfCell = sizePx / 2f

    // 1. Highlight the exact target cell boundary
    drawRect(
        color = themeColor.copy(alpha = if (isLocking) 0.12f + 0.08f * progress else 0.15f),
        topLeft = Offset(center.x - halfCell, center.y - halfCell),
        size = androidx.compose.ui.geometry.Size(sizePx, sizePx)
    )
    drawRect(
        color = themeColor.copy(alpha = if (isLocking) 0.45f + 0.35f * progress else 0.70f),
        topLeft = Offset(center.x - halfCell, center.y - halfCell),
        size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
        style = Stroke(width = 1.8f)
    )

    // 2. Crosshair reticle (contracts inward during lock, stationary thereafter)
    val baseRadius = sizePx * 0.44f
    val radius = if (isLocking) baseRadius * (1.18f - 0.18f * progress) else baseRadius
    val rotationAngle = if (isLocking) progress * 120f else 0f

    rotate(rotationAngle, pivot = center) {
        // Outer targeting reticle circle
        drawCircle(
            color = themeColor.copy(alpha = 0.85f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.2f)
        )

        // Corner lock brackets
        val bracketOffset = radius * 1.15f
        val bracketArm = radius * 0.35f
        val bracketColor = themeColor.copy(alpha = 0.9f)

        // Top-Left bracket
        drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y - bracketOffset), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset, center.y - bracketOffset + bracketArm), strokeWidth = 2.5f)

        // Top-Right bracket
        drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y - bracketOffset), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset, center.y - bracketOffset + bracketArm), strokeWidth = 2.5f)

        // Bottom-Left bracket
        drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y + bracketOffset), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset, center.y + bracketOffset - bracketArm), strokeWidth = 2.5f)

        // Bottom-Right bracket
        drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y + bracketOffset), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset, center.y + bracketOffset - bracketArm), strokeWidth = 2.5f)

        // Cardinal tick marks
        val tickLen = radius * 0.42f
        drawLine(themeColor, Offset(center.x, center.y - radius - tickLen), Offset(center.x, center.y - radius + 2f), strokeWidth = 2.2f)
        drawLine(themeColor, Offset(center.x, center.y + radius + tickLen), Offset(center.x, center.y + radius - 2f), strokeWidth = 2.2f)
        drawLine(themeColor, Offset(center.x - radius - tickLen, center.y), Offset(center.x - radius + 2f, center.y), strokeWidth = 2.2f)
        drawLine(themeColor, Offset(center.x + radius + tickLen, center.y), Offset(center.x + radius - 2f, center.y), strokeWidth = 2.2f)

        // Center dot
        val dotRadius = if (isLocking) 3.5f + 1.5f * sin(progress * PI.toFloat() * 3f) else 3.5f
        drawCircle(themeColor, radius = dotRadius, center = center)
    }
}

/**
 * Top-Down Aerial Bomb Drop Animation:
 * - Falling straight down vertically: Bomb X is STRICTLY equal to targetCenter.x at all times.
 * - Gravity acceleration: Y increases smoothly from above the grid down to targetCenter.y.
 * - Features authentic aerial bomb design: rounded ogive nose, steel casing, tail fins assembly,
 *   yellow high-explosive ordnance hazard marking, and contact detonator fuze pin.
 * - Expanding ground target shadow on ocean surface directly below bomb.
 * - No diagonal trajectories, no rocket thrusters, no missile contrails.
 */
private fun DrawScope.drawVerticalFallingBomb(
    targetCenter: Offset,
    sizePx: Float,
    progress: Float,
    isBotAttacker: Boolean
) {
    // 1. Constant X coordinate: bomb is STRICTLY aligned with target cell center
    val bombX = targetCenter.x

    // 2. Gravity-accelerated vertical free fall
    val startY = -60f
    val targetY = targetCenter.y
    val gravityProgress = (0.25f * progress + 0.75f * progress * progress).coerceIn(0f, 1f)
    val bombY = startY + (targetY - startY) * gravityProgress

    // 3. Perspective scaling: subtle drop from high altitude to ocean surface
    val depthScale = 1.22f - 0.22f * progress
    val bombWidth = sizePx * 0.42f * depthScale
    val bombLength = sizePx * 1.12f * depthScale
    val halfW = bombWidth / 2f
    val h = bombLength
    val tailTopY = bombY - h

    // 4. Ground Target Shadow on Ocean Surface (centered at targetCenter)
    val shadowProgress = progress.coerceIn(0f, 1f)
    val shadowRadius = sizePx * 0.40f * shadowProgress
    val shadowAlpha = (0.15f + 0.55f * shadowProgress).coerceIn(0f, 0.70f)
    drawCircle(
        color = Color(0xFF020617).copy(alpha = shadowAlpha),
        radius = shadowRadius,
        center = targetCenter
    )
    drawCircle(
        color = (if (isBotAttacker) Color(0xFFEF4444) else Color(0xFF38BDF8)).copy(alpha = shadowAlpha * 0.5f),
        radius = shadowRadius * 1.15f,
        center = targetCenter,
        style = Stroke(width = 1.5f)
    )

    // 5. Downward Air-Stream Speed Streaks (clean translucent wind shear, no smoke)
    val streakAlpha = (0.12f + 0.45f * progress).coerceIn(0f, 0.55f)
    drawLine(
        color = Color.White.copy(alpha = streakAlpha * 0.7f),
        start = Offset(bombX, tailTopY - 6f),
        end = Offset(bombX, tailTopY - 26f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = streakAlpha * 0.45f),
        start = Offset(bombX - halfW * 0.75f, tailTopY - 4f),
        end = Offset(bombX - halfW * 0.75f, tailTopY - 18f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = streakAlpha * 0.45f),
        start = Offset(bombX + halfW * 0.75f, tailTopY - 4f),
        end = Offset(bombX + halfW * 0.75f, tailTopY - 18f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )

    // 6. Aerial Bomb Stabilizer Tail Fins Assembly
    val leftFin = Path().apply {
        moveTo(bombX - halfW * 0.35f, tailTopY + h * 0.28f)
        lineTo(bombX - halfW * 1.55f, tailTopY + h * 0.06f)
        lineTo(bombX - halfW * 1.55f, tailTopY)
        lineTo(bombX - halfW * 0.3f, tailTopY)
        close()
    }
    drawPath(leftFin, Color(0xFF334155))
    drawPath(leftFin, Color(0xFF0F172A), style = Stroke(width = 1.2f))

    val rightFin = Path().apply {
        moveTo(bombX + halfW * 0.35f, tailTopY + h * 0.28f)
        lineTo(bombX + halfW * 1.55f, tailTopY + h * 0.06f)
        lineTo(bombX + halfW * 1.55f, tailTopY)
        lineTo(bombX + halfW * 0.3f, tailTopY)
        close()
    }
    drawPath(rightFin, Color(0xFF334155))
    drawPath(rightFin, Color(0xFF0F172A), style = Stroke(width = 1.2f))

    // Cylindrical tail stabilizer ring shroud
    val ringHeight = h * 0.08f
    val ringTop = tailTopY + h * 0.04f
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(bombX - halfW * 1.4f, ringTop),
        size = androidx.compose.ui.geometry.Size(halfW * 2.8f, ringHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFF475569),
        topLeft = Offset(bombX - halfW * 1.4f, ringTop),
        size = androidx.compose.ui.geometry.Size(halfW * 2.8f, ringHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
        style = Stroke(width = 1f)
    )

    // 7. Aerial Bomb Heavy Steel Ordnance Body
    val bombBodyPath = Path().apply {
        // Nose cone contact point at bottom
        moveTo(bombX, bombY)
        // Left rounded ogive nose
        cubicTo(
            bombX - halfW * 0.6f, bombY - h * 0.10f,
            bombX - halfW, bombY - h * 0.22f,
            bombX - halfW, bombY - h * 0.40f
        )
        // Left cylindrical midsection
        lineTo(bombX - halfW, bombY - h * 0.72f)
        // Left tapering aft cone
        lineTo(bombX - halfW * 0.38f, tailTopY)
        // Aft cap
        lineTo(bombX + halfW * 0.38f, tailTopY)
        // Right tapering aft cone
        lineTo(bombX + halfW, bombY - h * 0.72f)
        // Right cylindrical midsection
        lineTo(bombX + halfW, bombY - h * 0.40f)
        // Right rounded ogive nose
        cubicTo(
            bombX + halfW, bombY - h * 0.22f,
            bombX + halfW * 0.6f, bombY - h * 0.10f,
            bombX, bombY
        )
        close()
    }

    // Heavy steel ordnance gradient casing
    drawPath(
        path = bombBodyPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF334155),
                Color(0xFF475569),
                Color(0xFF1E293B),
                Color(0xFF0F172A)
            ),
            startX = bombX - halfW,
            endX = bombX + halfW
        )
    )
    drawPath(
        path = bombBodyPath,
        color = Color(0xFF020617),
        style = Stroke(width = 1.5f)
    )

    // 8. High-Explosive Ordnance Yellow Hazard Band
    val stripeY1 = bombY - h * 0.46f
    val stripeY2 = bombY - h * 0.55f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFD97706), Color(0xFFFDE047), Color(0xFFD97706)),
            startX = bombX - halfW,
            endX = bombX + halfW
        ),
        topLeft = Offset(bombX - halfW + 1f, stripeY2),
        size = androidx.compose.ui.geometry.Size(bombWidth - 2f, stripeY1 - stripeY2)
    )

    // 9. Attacker identification accent ring
    val accentColor = if (isBotAttacker) Color(0xFFEF4444) else Color(0xFF38BDF8)
    val accentStripeY = bombY - h * 0.64f
    drawRect(
        color = accentColor,
        topLeft = Offset(bombX - halfW + 1f, accentStripeY),
        size = androidx.compose.ui.geometry.Size(bombWidth - 2f, h * 0.04f)
    )

    // 10. Contact Detonation Fuze Pin at the nose tip
    drawCircle(
        color = Color(0xFF94A3B8),
        radius = 2.5f * depthScale,
        center = Offset(bombX, bombY)
    )
    drawCircle(
        color = Color(0xFFF8FAFC),
        radius = 1.2f * depthScale,
        center = Offset(bombX, bombY)
    )
}

// Backward compatibility alias
private fun DrawScope.drawFlyingProjectile(
    targetCenter: Offset,
    sizePx: Float,
    progress: Float,
    isBotAttacker: Boolean
) {
    drawVerticalFallingBomb(targetCenter, sizePx, progress, isBotAttacker)
}

private fun DrawScope.drawExplosionImpact(center: Offset, sizePx: Float, progress: Float) {
    val maxRadius = sizePx * 0.95f
    val currentRadius = maxRadius * progress

    // Expanding shockwave ring
    drawCircle(
        color = Color(0xFFFBBF24).copy(alpha = (1f - progress).coerceIn(0f, 1f)),
        radius = currentRadius * 1.35f,
        center = center,
        style = Stroke(width = 3.5f * (1f - progress))
    )

    // Fireball radial gradient
    val fireAlpha = (1f - progress * 0.75f).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFBEB).copy(alpha = fireAlpha),
                Color(0xFFF59E0B).copy(alpha = fireAlpha),
                Color(0xFFDC2626).copy(alpha = fireAlpha * 0.85f),
                Color.Transparent
            ),
            center = center,
            radius = currentRadius
        ),
        radius = currentRadius,
        center = center
    )

    // Radiating fiery shrapnel sparks
    val sparkCount = 12
    for (i in 0 until sparkCount) {
        val angle = (i * 2.0 * PI / sparkCount)
        val sparkDist = currentRadius * (0.6f + 0.5f * (i % 3))
        val sparkPos = Offset(
            (center.x + cos(angle) * sparkDist).toFloat(),
            (center.y + sin(angle) * sparkDist).toFloat()
        )
        drawCircle(
            color = Color(0xFFFDE047).copy(alpha = (1f - progress).coerceIn(0f, 1f)),
            radius = 3.5f * (1f - progress),
            center = sparkPos
        )
    }
}

private fun DrawScope.drawWaterSplashImpact(center: Offset, sizePx: Float, progress: Float) {
    val maxRadius = sizePx * 0.80f
    val currentRadius = maxRadius * progress

    // Concentric ocean ripples
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = (1f - progress).coerceIn(0f, 1f)),
        radius = currentRadius * 1.4f,
        center = center,
        style = Stroke(width = 3f * (1f - progress))
    )
    drawCircle(
        color = Color(0xFF0284C7).copy(alpha = (1f - progress * 0.7f).coerceIn(0f, 1f)),
        radius = currentRadius * 0.9f,
        center = center,
        style = Stroke(width = 2.2f * (1f - progress))
    )

    // Upward plume water splash droplets
    val splashAlpha = (1f - progress).coerceIn(0f, 1f)
    val dropCount = 8
    for (i in 0 until dropCount) {
        val angle = (i * PI / (dropCount - 1)) - PI
        val dropDist = currentRadius * (0.8f + 0.4f * (i % 2))
        val dropPos = Offset(
            (center.x + cos(angle) * dropDist).toFloat(),
            (center.y + sin(angle) * dropDist * 0.65f).toFloat() // Perspective oval
        )
        drawCircle(
            color = Color(0xFFE0F2FE).copy(alpha = splashAlpha),
            radius = 3.5f * (1f - progress * 0.5f),
            center = dropPos
        )
    }
}
