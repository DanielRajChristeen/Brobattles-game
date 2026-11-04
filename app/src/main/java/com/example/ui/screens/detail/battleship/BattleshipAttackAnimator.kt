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
import androidx.compose.ui.platform.LocalDensity
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
 * Durations are tuned to provide deliberate, visually clear naval warfare animations (~6.4s total).
 */
object AttackTimingConfig {
    const val THINKING_MS = 1200L
    const val TARGET_LOCK_MS = 1000L
    const val PROJECTILE_FLIGHT_MS = 1000L
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
 * - Stage 1 (TARGET_LOCK): Highlight target cell, stationary crosshair centered on selected cell.
 * - Stage 2 (PROJECTILE_FLIGHT / BOMB DESCENT): Top-down aerial bomb falling straight down vertically into exact cell center.
 *   X coordinate is strictly constant; Y increases smoothly with gravity acceleration.
 * - Stage 3 (IMPACT): Localized fireball explosion (for Hit) or water splash (for Miss) at exact cell center.
 * - Stage 4 (RESULT_ANNOUNCEMENT): Result displayed with target cell highlighted.
 */
@Composable
fun AttackEffectOverlay(
    attackData: AttackAnimationData,
    modifier: Modifier = Modifier,
    liveCellCenterProvider: ((CellCoordinate) -> Offset)? = null
) {
    val liveCenter = liveCellCenterProvider?.invoke(attackData.targetCoord)
    val effectiveCenter = if (liveCenter != null && liveCenter != Offset.Zero) {
        liveCenter
    } else {
        attackData.targetCenterInOverlay
    }
    AttackEffectOverlay(
        targetCoord = attackData.targetCoord,
        stage = attackData.stage,
        isHit = attackData.isHit,
        isBotAttacker = attackData.isBotAttacker,
        targetCellCenter = effectiveCenter,
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
    if (stage == AttackAnimationStage.IDLE) return

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

    val density = LocalDensity.current
    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val rowLabelW = with(density) { 18.dp.toPx() }
        val gridW = size.width - rowLabelW
        val cellW = if (gridW > 10f) gridW / 10f else size.width / 10f
        val cellH = size.height / 10f

        // Exact mathematical center of the targeted cell in the 10-rows overlay coordinate space
        val exactCenter = Offset(
            x = rowLabelW + (targetCoord.col + 0.5f) * cellW,
            y = (targetCoord.row + 0.5f) * cellH
        )

        // Use exactCenter to guarantee 100% alignment with the targeted cell
        val resolvedCenter = if (targetCellCenter != Offset.Zero &&
            targetCellCenter.x >= 0f && targetCellCenter.x <= size.width &&
            targetCellCenter.y >= 0f && targetCellCenter.y <= size.height
        ) {
            targetCellCenter
        } else {
            exactCenter
        }
        val resolvedCellSize = if (cellSizePx > 10f) cellSizePx else cellW

        when (stage) {
            AttackAnimationStage.TARGET_LOCK -> {
                drawTargetCrosshair(resolvedCenter, resolvedCellSize, progress.value, isBotAttacker, isLocking = true)
            }
            AttackAnimationStage.PROJECTILE_FLIGHT -> {
                // Keep target crosshair stationary on selected cell
                drawTargetCrosshair(resolvedCenter, resolvedCellSize, 1f, isBotAttacker, isLocking = false)
                // Drop aerial bomb vertically straight down into exact target cell center
                drawVerticalFallingBomb(resolvedCenter, resolvedCellSize, progress.value, isBotAttacker)
            }
            AttackAnimationStage.IMPACT -> {
                // Target cell remains marked
                drawTargetCrosshair(resolvedCenter, resolvedCellSize, 1f, isBotAttacker, isLocking = false)
                if (isHit) {
                    drawExplosionImpact(resolvedCenter, resolvedCellSize, progress.value)
                } else {
                    drawWaterSplashImpact(resolvedCenter, resolvedCellSize, progress.value)
                }
            }
            AttackAnimationStage.RESULT_ANNOUNCEMENT -> {
                drawTargetCrosshair(resolvedCenter, resolvedCellSize, 1f, isBotAttacker, isLocking = false)
            }
            else -> {}
        }
    }
}

/**
 * Fallback grid center calculator using exact 10x10 Battleship board layout dimensions.
 * Ensures the animation never fails even before initial Compose layout measure completes.
 */
fun calculateFallbackGridCenter(coord: CellCoordinate, boardWidth: Float, boardHeight: Float, rowLabelWidthPx: Float = 48f): Offset {
    val actualLabelW = if (rowLabelWidthPx > 0f) rowLabelWidthPx else (boardWidth * 0.05f).coerceAtLeast(36f)
    val gridW = boardWidth - actualLabelW
    val cellW = (gridW / 10f).coerceAtLeast(1f)
    val cellH = (boardHeight / 10f).coerceAtLeast(1f)
    val cx = actualLabelW + (coord.col + 0.5f) * cellW
    val cy = (coord.row + 0.5f) * cellH
    return Offset(cx, cy)
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
    val safeSizePx = sizePx.coerceAtLeast(20f)
    val depthScale = (1.15f - 0.15f * progress).coerceAtLeast(0.5f)
    val bombWidth = (safeSizePx * 0.44f * depthScale).coerceAtLeast(10f)
    val bombLength = (safeSizePx * 0.94f * depthScale).coerceAtLeast(20f)
    val halfW = bombWidth / 2f
    val halfH = bombLength / 2f
    val tailTopY = bombY - halfH
    val noseBottomY = bombY + halfH

    // 4. Ground Target Shadow on Ocean Surface (centered at targetCenter)
    val shadowProgress = progress.coerceIn(0f, 1f)
    val shadowRadius = (safeSizePx * 0.40f * shadowProgress).coerceAtLeast(0f)
    val shadowAlpha = (0.15f + 0.55f * shadowProgress).coerceIn(0f, 0.70f)
    drawCircle(
        color = Color(0xFF020617).copy(alpha = shadowAlpha),
        radius = shadowRadius,
        center = targetCenter
    )
    drawCircle(
        color = (if (isBotAttacker) Color(0xFFEF4444) else Color(0xFF38BDF8)).copy(alpha = shadowAlpha * 0.5f),
        radius = (shadowRadius * 1.15f).coerceAtLeast(0f),
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
        moveTo(bombX - halfW * 0.35f, tailTopY + halfH * 0.55f)
        lineTo(bombX - halfW * 1.55f, tailTopY + halfH * 0.12f)
        lineTo(bombX - halfW * 1.55f, tailTopY)
        lineTo(bombX - halfW * 0.30f, tailTopY)
        close()
    }
    drawPath(leftFin, Color(0xFF334155))
    drawPath(leftFin, Color(0xFF0F172A), style = Stroke(width = 1.2f))

    val rightFin = Path().apply {
        moveTo(bombX + halfW * 0.35f, tailTopY + halfH * 0.55f)
        lineTo(bombX + halfW * 1.55f, tailTopY + halfH * 0.12f)
        lineTo(bombX + halfW * 1.55f, tailTopY)
        lineTo(bombX + halfW * 0.30f, tailTopY)
        close()
    }
    drawPath(rightFin, Color(0xFF334155))
    drawPath(rightFin, Color(0xFF0F172A), style = Stroke(width = 1.2f))

    // Cylindrical tail stabilizer ring shroud
    val ringHeight = (halfH * 0.16f).coerceAtLeast(1f)
    val ringTop = tailTopY + halfH * 0.08f
    val ringWidth = (halfW * 2.8f).coerceAtLeast(2f)
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(bombX - halfW * 1.4f, ringTop),
        size = androidx.compose.ui.geometry.Size(ringWidth, ringHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFF475569),
        topLeft = Offset(bombX - halfW * 1.4f, ringTop),
        size = androidx.compose.ui.geometry.Size(ringWidth, ringHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
        style = Stroke(width = 1f)
    )

    // 7. Aerial Bomb Heavy Steel Ordnance Body
    val bombBodyPath = Path().apply {
        // Nose cone contact point at bottom
        moveTo(bombX, noseBottomY)
        // Left rounded ogive nose
        cubicTo(
            bombX - halfW * 0.7f, noseBottomY - halfH * 0.40f,
            bombX - halfW, bombY + halfH * 0.20f,
            bombX - halfW, bombY - halfH * 0.10f
        )
        // Left cylindrical midsection
        lineTo(bombX - halfW, bombY - halfH * 0.45f)
        // Left tapering aft cone
        lineTo(bombX - halfW * 0.38f, tailTopY)
        // Aft cap
        lineTo(bombX + halfW * 0.38f, tailTopY)
        // Right tapering aft cone
        lineTo(bombX + halfW, bombY - halfH * 0.45f)
        // Right cylindrical midsection
        lineTo(bombX + halfW, bombY - halfH * 0.10f)
        // Right rounded ogive nose
        cubicTo(
            bombX + halfW, bombY + halfH * 0.20f,
            bombX + halfW * 0.7f, noseBottomY - halfH * 0.40f,
            bombX, noseBottomY
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
    val stripeY1 = bombY + halfH * 0.10f
    val stripeY2 = bombY - halfH * 0.10f
    val bandW = (bombWidth - 2f).coerceAtLeast(1f)
    val bandH = (stripeY1 - stripeY2).coerceAtLeast(1f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFD97706), Color(0xFFFDE047), Color(0xFFD97706)),
            startX = bombX - halfW,
            endX = bombX + halfW
        ),
        topLeft = Offset(bombX - halfW + 1f, stripeY2),
        size = androidx.compose.ui.geometry.Size(bandW, bandH)
    )

    // 9. Attacker identification accent ring
    val accentColor = if (isBotAttacker) Color(0xFFEF4444) else Color(0xFF38BDF8)
    val accentStripeY = bombY - halfH * 0.30f
    val accentH = (halfH * 0.08f).coerceAtLeast(1f)
    drawRect(
        color = accentColor,
        topLeft = Offset(bombX - halfW + 1f, accentStripeY),
        size = androidx.compose.ui.geometry.Size(bandW, accentH)
    )

    // 10. Contact Detonation Fuze Pin at the nose tip
    drawCircle(
        color = Color(0xFF94A3B8),
        radius = 2.5f * depthScale,
        center = Offset(bombX, noseBottomY)
    )
    drawCircle(
        color = Color(0xFFF8FAFC),
        radius = 1.2f * depthScale,
        center = Offset(bombX, noseBottomY)
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
    if (progress <= 0f) return
    val clampedProgress = progress.coerceIn(0.01f, 1f)
    val maxRadius = (sizePx * 0.72f).coerceAtLeast(8f)
    val currentRadius = (maxRadius * clampedProgress).coerceAtLeast(1.5f)

    // 1. Sleek expanding shockwave ring (soft amber/orange, contained and crisp)
    val strokeWidth = (2.2f * (1f - clampedProgress)).coerceAtLeast(0.5f)
    val ringRadius = (currentRadius * 1.25f).coerceAtLeast(1.5f)
    drawCircle(
        color = Color(0xFFF59E0B).copy(alpha = (0.85f * (1f - clampedProgress)).coerceIn(0f, 1f)),
        radius = ringRadius,
        center = center,
        style = Stroke(width = strokeWidth)
    )

    // 2. Focused fireball core - streamlined, crisp, radiant gradient with guaranteed positive radius
    val fireAlpha = (1f - clampedProgress * 0.75f).coerceIn(0f, 1f)
    val safeRadius = currentRadius.coerceAtLeast(1.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFBEB).copy(alpha = fireAlpha),
                Color(0xFFFBBF24).copy(alpha = fireAlpha * 0.95f),
                Color(0xFFEF4444).copy(alpha = fireAlpha * 0.75f),
                Color(0xFFDC2626).copy(alpha = 0f)
            ),
            center = center,
            radius = safeRadius
        ),
        radius = safeRadius,
        center = center
    )

    // 3. Compact glowing central flash point during initial detonation
    if (clampedProgress < 0.5f) {
        val flashAlpha = (1f - clampedProgress / 0.5f).coerceIn(0f, 1f)
        val coreRadius = (currentRadius * 0.40f).coerceAtLeast(1f)
        drawCircle(
            color = Color.White.copy(alpha = flashAlpha),
            radius = coreRadius,
            center = center
        )
    }

    // 4. Clean, focused blast sparks (reduced count from 12 to 6 for a sleek, aesthetic look)
    val sparkAlpha = (1f - clampedProgress).coerceIn(0f, 1f)
    if (sparkAlpha > 0.05f) {
        val sparkCount = 6
        for (i in 0 until sparkCount) {
            val angle = (i * 2.0 * PI / sparkCount) + 0.25
            val sparkDist = currentRadius * 0.95f
            val sparkPos = Offset(
                (center.x + cos(angle) * sparkDist).toFloat(),
                (center.y + sin(angle) * sparkDist).toFloat()
            )
            val sparkRadius = (2.2f * (1f - clampedProgress)).coerceAtLeast(0.5f)
            drawCircle(
                color = Color(0xFFFDE047).copy(alpha = sparkAlpha),
                radius = sparkRadius,
                center = sparkPos
            )
        }
    }
}

private fun DrawScope.drawWaterSplashImpact(center: Offset, sizePx: Float, progress: Float) {
    if (progress <= 0f) return
    val clampedProgress = progress.coerceIn(0.01f, 1f)
    val maxRadius = (sizePx * 0.65f).coerceAtLeast(8f)
    val currentRadius = (maxRadius * clampedProgress).coerceAtLeast(1.5f)

    // Concentric ocean ripples
    val stroke1 = (2.2f * (1f - clampedProgress)).coerceAtLeast(0.5f)
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = (0.85f * (1f - clampedProgress)).coerceIn(0f, 1f)),
        radius = (currentRadius * 1.3f).coerceAtLeast(1.5f),
        center = center,
        style = Stroke(width = stroke1)
    )
    val stroke2 = (1.5f * (1f - clampedProgress)).coerceAtLeast(0.5f)
    drawCircle(
        color = Color(0xFF0284C7).copy(alpha = (0.7f * (1f - clampedProgress * 0.8f)).coerceIn(0f, 1f)),
        radius = (currentRadius * 0.85f).coerceAtLeast(1.5f),
        center = center,
        style = Stroke(width = stroke2)
    )

    // Upward plume water splash droplets (6 droplets for a clean look)
    val splashAlpha = (1f - clampedProgress).coerceIn(0f, 1f)
    if (splashAlpha > 0.05f) {
        val dropCount = 6
        for (i in 0 until dropCount) {
            val angle = (i * PI / (dropCount - 1)) - PI
            val dropDist = currentRadius * 0.9f
            val dropPos = Offset(
                (center.x + cos(angle) * dropDist).toFloat(),
                (center.y + sin(angle) * dropDist * 0.65f).toFloat() // Perspective oval
            )
            val dropRadius = (2.5f * (1f - clampedProgress * 0.5f)).coerceAtLeast(0.5f)
            drawCircle(
                color = Color(0xFFE0F2FE).copy(alpha = splashAlpha),
                radius = dropRadius,
                center = dropPos
            )
        }
    }
}
