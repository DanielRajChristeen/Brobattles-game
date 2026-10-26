package com.example.ui.screens.detail.ludo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.core.engine.ludo.LudoBoardCoordinates
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoToken

/**
 * High-quality 3D shaded Ludo pawn puppet rendering and board geometry coordinate helpers.
 *
 * Characteristics of the Ludo pawn:
 * - Spherical head with 3D spherical specular highlight
 * - Narrow neck collar connecting head to torso
 * - Flared conical torso with directional shading
 * - Rounded flared pedestal base
 * - Ground contact shadow that responds to hop elevation
 * - Pulsing glowing selection ring and subtle vertical hover bounce
 */
object LudoPawnRenderer {

    private val PawnOutlineColor = Color(0xFF1E293B)

    /**
     * Draws a 3D-shaded Ludo puppet/pawn on the canvas.
     *
     * @param center The ground center point of the board square.
     * @param cellSize The dimension of one grid square on the 15x15 board.
     * @param color The Ludo player color (Red, Green, Yellow, Blue).
     * @param isSelectable Whether this pawn is legal to move and currently selectable.
     * @param pulseScale Pulsing scale factor for the selection aura.
     * @param liftPx Vertical hop displacement (pixels above the board surface).
     * @param scale Overall size multiplier (used for multi-token clustering).
     */
    fun drawLudoPawn(
        drawScope: DrawScope,
        center: Offset,
        cellSize: Float,
        color: LudoColor,
        isSelectable: Boolean,
        pulseScale: Float = 1.0f,
        liftPx: Float = 0f,
        scale: Float = 1.0f
    ) {
        with(drawScope) {
            val primaryColor = Color(color.primaryColorHex)
            val lightColor = Color(color.lightColorHex)
            val darkColor = Color(color.darkColorHex)

            val baseWidth = cellSize * 0.64f * scale
            val baseHeight = baseWidth * 0.38f
            val totalHeight = cellSize * 0.92f * scale
            val groundY = center.y + cellSize * 0.22f
            val cx = center.x

            val maxHop = cellSize * 0.45f
            val liftFactor = (liftPx / maxHop).coerceIn(0f, 1f)

            // 1. Ground Contact Shadow (anchored to the board square)
            val shadowWidth = baseWidth * (1f - liftFactor * 0.35f)
            val shadowHeight = baseHeight * (1f - liftFactor * 0.35f)
            val shadowAlpha = (0.38f * (1f - liftFactor * 0.45f)).coerceIn(0.08f, 0.38f)

            drawOval(
                color = Color.Black.copy(alpha = shadowAlpha),
                topLeft = Offset(cx - shadowWidth / 2f, groundY - shadowHeight / 2f),
                size = Size(shadowWidth, shadowHeight)
            )

            // 2. Selection Ring / Glowing Aura (drawn on ground beneath selectable pawns)
            if (isSelectable) {
                val auraWidth = baseWidth * 1.35f * pulseScale
                val auraHeight = baseHeight * 1.35f * pulseScale
                drawOval(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(cx - auraWidth / 2f, groundY - auraHeight / 2f),
                    size = Size(auraWidth, auraHeight),
                    style = Stroke(width = 3.2f)
                )
                val outerAuraWidth = baseWidth * 1.55f * pulseScale
                val outerAuraHeight = baseHeight * 1.55f * pulseScale
                drawOval(
                    color = primaryColor.copy(alpha = 0.65f),
                    topLeft = Offset(cx - outerAuraWidth / 2f, groundY - outerAuraHeight / 2f),
                    size = Size(outerAuraWidth, outerAuraHeight),
                    style = Stroke(width = 2.0f)
                )
            }

            // The pawn moves upward by liftPx
            val pawnY = groundY - liftPx

            // 3. Flared Pedestal Base (Skirt)
            val baseRectTop = pawnY - baseHeight / 2f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(lightColor, primaryColor, darkColor),
                    center = Offset(cx - baseWidth * 0.22f, pawnY - baseHeight * 0.22f),
                    radius = baseWidth * 0.75f
                ),
                topLeft = Offset(cx - baseWidth / 2f, baseRectTop),
                size = Size(baseWidth, baseHeight)
            )
            drawOval(
                color = PawnOutlineColor,
                topLeft = Offset(cx - baseWidth / 2f, baseRectTop),
                size = Size(baseWidth, baseHeight),
                style = Stroke(width = 1.4f)
            )

            // 4. Conical Flared Torso / Body
            val torsoBottomWidth = baseWidth * 0.72f
            val torsoBottomY = pawnY - baseHeight * 0.38f
            val neckY = pawnY - totalHeight * 0.54f
            val neckWidth = baseWidth * 0.32f

            val torsoPath = Path().apply {
                moveTo(cx - neckWidth / 2f, neckY)
                // Left curve outward to base
                cubicTo(
                    cx - neckWidth * 0.60f, neckY + (torsoBottomY - neckY) * 0.40f,
                    cx - torsoBottomWidth * 0.58f, torsoBottomY - (torsoBottomY - neckY) * 0.18f,
                    cx - torsoBottomWidth / 2f, torsoBottomY
                )
                // Bottom line
                lineTo(cx + torsoBottomWidth / 2f, torsoBottomY)
                // Right curve inward to neck
                cubicTo(
                    cx + torsoBottomWidth * 0.58f, torsoBottomY - (torsoBottomY - neckY) * 0.18f,
                    cx + neckWidth * 0.60f, neckY + (torsoBottomY - neckY) * 0.40f,
                    cx + neckWidth / 2f, neckY
                )
                close()
            }

            drawPath(
                path = torsoPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        darkColor,
                        lightColor.copy(alpha = 0.95f),
                        primaryColor,
                        darkColor
                    ),
                    startX = cx - baseWidth * 0.5f,
                    endX = cx + baseWidth * 0.5f
                )
            )
            drawPath(
                path = torsoPath,
                color = PawnOutlineColor,
                style = Stroke(width = 1.4f)
            )

            // 5. Neck Collar Ring
            val collarWidth = neckWidth * 1.30f
            val collarHeight = baseWidth * 0.18f
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(lightColor, darkColor),
                    startY = neckY - collarHeight / 2f,
                    endY = neckY + collarHeight / 2f
                ),
                topLeft = Offset(cx - collarWidth / 2f, neckY - collarHeight / 2f),
                size = Size(collarWidth, collarHeight)
            )
            drawOval(
                color = PawnOutlineColor,
                topLeft = Offset(cx - collarWidth / 2f, neckY - collarHeight / 2f),
                size = Size(collarWidth, collarHeight),
                style = Stroke(width = 1.2f)
            )

            // 6. Spherical 3D Head
            val headRadius = baseWidth * 0.33f
            val headCenter = Offset(cx, neckY - headRadius * 0.78f)

            // 3D Spherical Radial Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        lightColor,
                        primaryColor,
                        darkColor
                    ),
                    center = Offset(headCenter.x - headRadius * 0.32f, headCenter.y - headRadius * 0.32f),
                    radius = headRadius * 1.35f
                ),
                radius = headRadius,
                center = headCenter
            )

            // Specular Glint Dot on top-left of head
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = headRadius * 0.28f,
                center = Offset(headCenter.x - headRadius * 0.35f, headCenter.y - headRadius * 0.35f)
            )

            // Head Outline
            drawCircle(
                color = PawnOutlineColor,
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = 1.4f)
            )
        }
    }

    /**
     * Calculates the exact pixel center on the board canvas for any logical step.
     */
    fun getCanvasOffsetForStep(
        color: LudoColor,
        tokenId: Int,
        step: Int,
        cellSize: Float
    ): Offset {
        val (r, c) = when {
            step <= LudoToken.STEP_YARD -> LudoBoardCoordinates.getYardPositions(color)[tokenId]
            step in 0..LudoToken.STEP_LAST_TRACK -> {
                val trackIdx = (color.startIndex + step) % 52
                val (row, col) = LudoBoardCoordinates.TRACK_COORDINATES[trackIdx]
                Pair(row.toFloat(), col.toFloat())
            }
            step in LudoToken.STEP_FIRST_HOME_STRETCH..LudoToken.STEP_LAST_HOME_STRETCH -> {
                val stretchIdx = step - LudoToken.STEP_FIRST_HOME_STRETCH
                val (row, col) = LudoBoardCoordinates.getHomeStretch(color)[stretchIdx]
                Pair(row.toFloat(), col.toFloat())
            }
            else -> LudoBoardCoordinates.getFinishCenter(color)
        }
        return Offset(
            x = (c + 0.5f) * cellSize,
            y = (r + 0.5f) * cellSize
        )
    }

    /**
     * Calculates clustering offset when multiple tokens share a board square.
     */
    fun getMultiTokenOffset(
        groupIndex: Int,
        groupSize: Int,
        cellSize: Float
    ): Offset {
        if (groupSize <= 1) return Offset.Zero
        val spread = cellSize * 0.16f
        return when (groupSize) {
            2 -> when (groupIndex) {
                0 -> Offset(-spread, 0f)
                else -> Offset(spread, 0f)
            }
            3 -> when (groupIndex) {
                0 -> Offset(-spread * 0.9f, -spread * 0.7f)
                1 -> Offset(spread * 0.9f, -spread * 0.7f)
                else -> Offset(0f, spread * 0.9f)
            }
            else -> when (groupIndex % 4) {
                0 -> Offset(-spread, -spread)
                1 -> Offset(spread, -spread)
                2 -> Offset(-spread, spread)
                else -> Offset(spread, spread)
            }
        }
    }
}

/**
 * State representing a pawn actively hopping forward step-by-step.
 */
data class AnimatingTokenState(
    val color: LudoColor,
    val tokenId: Int,
    val path: List<Int>,
    val currentHopIndex: Int,
    val hopFraction: Float
)

/**
 * State representing an enemy pawn actively travelling backwards to its yard upon capture.
 */
data class AnimatingCapturedState(
    val color: LudoColor,
    val tokenId: Int,
    val reversePath: List<Int>,
    val currentHopIndex: Int,
    val hopFraction: Float
)
