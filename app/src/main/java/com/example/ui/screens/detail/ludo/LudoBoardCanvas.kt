package com.example.ui.screens.detail.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.core.engine.ludo.LudoBoardCoordinates
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoGameState
import com.example.core.engine.ludo.LudoPlayerState
import com.example.core.engine.ludo.LudoToken
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Classic Ludo Board Quadrant Colors
private val GreenQuadrantColor = Color(0xFF34A853)
private val YellowQuadrantColor = Color(0xFFFBBC04)
private val RedQuadrantColor = Color(0xFFEA4335)
private val BlueQuadrantColor = Color(0xFF4285F4)

private val CellBorderColor = Color(0xFF222222)
private val StarColor = Color(0xFF222222)
private val TrackWhite = Color(0xFFFFFFFF)

/**
 * Standard 15x15 Ludo Board with high-fidelity 3D shaded pawn puppets,
 * smooth step-by-step hopping arcs, backward capture animations, and
 * intelligent multi-token clustering.
 */
@Composable
fun LudoBoardCanvas(
    gameState: LudoGameState,
    selectableTokenIds: List<Int>,
    animatingToken: AnimatingTokenState? = null,
    capturedAnimatingTokens: List<AnimatingCapturedState> = emptyList(),
    onTokenSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tokenPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .testTag("ludo_board_canvas")
            .pointerInput(gameState, selectableTokenIds, animatingToken, capturedAnimatingTokens) {
                detectTapGestures { tapOffset ->
                    // Prevent token selection during active animations, when no moves are available, or during bot turn
                    if (selectableTokenIds.isEmpty() || animatingToken != null || capturedAnimatingTokens.isNotEmpty() || gameState.currentPlayer.isBot) {
                        return@detectTapGestures
                    }
                    val cellSize = size.width / 15f
                    val currentP = gameState.currentPlayer

                    // Find which selectable token was tapped
                    var bestTokenId: Int? = null
                    var minDistance = Float.MAX_VALUE

                    for (tokenId in selectableTokenIds) {
                        val token = currentP.tokens.firstOrNull { it.id == tokenId } ?: continue
                        val tokenCenter = LudoPawnRenderer.getCanvasOffsetForStep(
                            color = token.color,
                            tokenId = token.id,
                            step = token.step,
                            cellSize = cellSize
                        )
                        val dist = (tapOffset - tokenCenter).getDistance()
                        // Generous tap target (at least 1.25x cell size for comfortable mobile touch)
                        if (dist <= cellSize * 1.25f && dist < minDistance) {
                            minDistance = dist
                            bestTokenId = tokenId
                        }
                    }

                    if (bestTokenId != null) {
                        onTokenSelected(bestTokenId)
                    }
                }
            }
    ) {
        val boardSize = size.width
        val cellSize = boardSize / 15f

        // 1. Draw 4 Home Yard Quadrants (6x6 each)
        drawYardQuadrant(0f, 0f, 6 * cellSize, GreenQuadrantColor, LudoColor.GREEN, cellSize)
        drawYardQuadrant(9 * cellSize, 0f, 6 * cellSize, YellowQuadrantColor, LudoColor.YELLOW, cellSize)
        drawYardQuadrant(0f, 9 * cellSize, 6 * cellSize, RedQuadrantColor, LudoColor.RED, cellSize)
        drawYardQuadrant(9 * cellSize, 9 * cellSize, 6 * cellSize, BlueQuadrantColor, LudoColor.BLUE, cellSize)

        // 2. Draw 52 Outer Track Cells
        for (i in 0 until 52) {
            val (r, c) = LudoBoardCoordinates.TRACK_COORDINATES[i]
            val cellLeft = c * cellSize
            val cellTop = r * cellSize

            // Determine background color
            val bgColor = when (i) {
                0 -> RedQuadrantColor       // Red Start
                13 -> GreenQuadrantColor    // Green Start
                26 -> YellowQuadrantColor   // Yellow Start
                39 -> BlueQuadrantColor     // Blue Start
                else -> TrackWhite
            }

            drawRect(
                color = bgColor,
                topLeft = Offset(cellLeft, cellTop),
                size = Size(cellSize, cellSize)
            )
            drawRect(
                color = CellBorderColor,
                topLeft = Offset(cellLeft, cellTop),
                size = Size(cellSize, cellSize),
                style = Stroke(width = 1.2f)
            )

            // Draw start square arrow
            when (i) {
                0 -> drawArrow(cellLeft, cellTop, cellSize, ArrowDirection.UP, Color.White)
                13 -> drawArrow(cellLeft, cellTop, cellSize, ArrowDirection.RIGHT, Color.White)
                26 -> drawArrow(cellLeft, cellTop, cellSize, ArrowDirection.DOWN, Color.White)
                39 -> drawArrow(cellLeft, cellTop, cellSize, ArrowDirection.LEFT, Color.White)
            }

            // Draw star safe icons on the 4 star safe cells
            if (i in LudoBoardCoordinates.STAR_SAFE_INDICES) {
                drawStar(cellLeft + cellSize / 2f, cellTop + cellSize / 2f, cellSize * 0.35f, StarColor)
            }
        }

        // 3. Draw Home Stretches (5 colored cells each)
        drawHomeStretchCells(LudoBoardCoordinates.RED_HOME_STRETCH, RedQuadrantColor, cellSize)
        drawHomeStretchCells(LudoBoardCoordinates.GREEN_HOME_STRETCH, GreenQuadrantColor, cellSize)
        drawHomeStretchCells(LudoBoardCoordinates.YELLOW_HOME_STRETCH, YellowQuadrantColor, cellSize)
        drawHomeStretchCells(LudoBoardCoordinates.BLUE_HOME_STRETCH, BlueQuadrantColor, cellSize)

        // 4. Draw Center 3x3 Finish Area (4 Triangles meeting at 7.5, 7.5)
        drawCenterTriangles(6 * cellSize, 6 * cellSize, 3 * cellSize)

        // 5. Draw Outer Board Border
        drawRect(
            color = CellBorderColor,
            topLeft = Offset.Zero,
            size = Size(boardSize, boardSize),
            style = Stroke(width = 3.5f)
        )

        // 6. Draw All Pawns (Static, Captured in Reverse, and Actively Hopping)
        drawBoardPawns(
            gameState = gameState,
            selectableTokenIds = selectableTokenIds,
            animatingToken = animatingToken,
            capturedAnimatingTokens = capturedAnimatingTokens,
            cellSize = cellSize,
            pulseScale = pulseScale
        )
    }
}

private fun DrawScope.drawYardQuadrant(
    left: Float,
    top: Float,
    quadrantSize: Float,
    quadrantColor: Color,
    ludoColor: LudoColor,
    cellSize: Float
) {
    drawRect(
        color = quadrantColor,
        topLeft = Offset(left, top),
        size = Size(quadrantSize, quadrantSize)
    )
    drawRect(
        color = CellBorderColor,
        topLeft = Offset(left, top),
        size = Size(quadrantSize, quadrantSize),
        style = Stroke(width = 2.0f)
    )

    val innerInset = quadrantSize * 0.16f
    val innerPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(
                    left + innerInset,
                    top + innerInset,
                    left + quadrantSize - innerInset,
                    top + quadrantSize - innerInset
                ),
                cornerRadius = CornerRadius(quadrantSize * 0.08f, quadrantSize * 0.08f)
            )
        )
    }
    drawPath(path = innerPath, color = TrackWhite, style = Fill)
    drawPath(path = innerPath, color = CellBorderColor, style = Stroke(width = 2.0f))

    val yardCenters = LudoBoardCoordinates.getYardPositions(ludoColor)
    for (centerPair in yardCenters) {
        val cx = (centerPair.second + 0.5f) * cellSize
        val cy = (centerPair.first + 0.5f) * cellSize
        val circleRadius = cellSize * 0.55f

        drawCircle(
            color = quadrantColor,
            radius = circleRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = CellBorderColor,
            radius = circleRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }
}

private fun DrawScope.drawHomeStretchCells(
    cells: List<Pair<Int, Int>>,
    color: Color,
    cellSize: Float
) {
    for ((r, c) in cells) {
        val left = c * cellSize
        val top = r * cellSize
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(cellSize, cellSize)
        )
        drawRect(
            color = CellBorderColor,
            topLeft = Offset(left, top),
            size = Size(cellSize, cellSize),
            style = Stroke(width = 1.2f)
        )
    }
}

private fun DrawScope.drawCenterTriangles(left: Float, top: Float, size: Float) {
    val center = Offset(left + size / 2f, top + size / 2f)
    val right = left + size
    val bottom = top + size

    // Yellow Top Triangle
    val yellowPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(yellowPath, YellowQuadrantColor)
    drawPath(yellowPath, CellBorderColor, style = Stroke(width = 1.5f))

    // Red Bottom Triangle
    val redPath = Path().apply {
        moveTo(left, bottom)
        lineTo(right, bottom)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(redPath, RedQuadrantColor)
    drawPath(redPath, CellBorderColor, style = Stroke(width = 1.5f))

    // Green Left Triangle
    val greenPath = Path().apply {
        moveTo(left, top)
        lineTo(left, bottom)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(greenPath, GreenQuadrantColor)
    drawPath(greenPath, CellBorderColor, style = Stroke(width = 1.5f))

    // Blue Right Triangle
    val bluePath = Path().apply {
        moveTo(right, top)
        lineTo(right, bottom)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(bluePath, BlueQuadrantColor)
    drawPath(bluePath, CellBorderColor, style = Stroke(width = 1.5f))
}

private enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

private fun DrawScope.drawArrow(
    left: Float,
    top: Float,
    cellSize: Float,
    direction: ArrowDirection,
    color: Color
) {
    val cx = left + cellSize / 2f
    val cy = top + cellSize / 2f
    val s = cellSize * 0.32f

    val path = Path()
    when (direction) {
        ArrowDirection.UP -> {
            path.moveTo(cx, cy - s)
            path.lineTo(cx - s * 0.7f, cy + s * 0.6f)
            path.lineTo(cx + s * 0.7f, cy + s * 0.6f)
            path.close()
        }
        ArrowDirection.DOWN -> {
            path.moveTo(cx, cy + s)
            path.lineTo(cx - s * 0.7f, cy - s * 0.6f)
            path.lineTo(cx + s * 0.7f, cy - s * 0.6f)
            path.close()
        }
        ArrowDirection.LEFT -> {
            path.moveTo(cx - s, cy)
            path.lineTo(cx + s * 0.6f, cy - s * 0.7f)
            path.lineTo(cx + s * 0.6f, cy + s * 0.7f)
            path.close()
        }
        ArrowDirection.RIGHT -> {
            path.moveTo(cx + s, cy)
            path.lineTo(cx - s * 0.6f, cy - s * 0.7f)
            path.lineTo(cx - s * 0.6f, cy + s * 0.7f)
            path.close()
        }
    }
    drawPath(path, color)
    drawPath(path, CellBorderColor, style = Stroke(width = 1.0f))
}

private fun DrawScope.drawStar(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color
) {
    val path = Path()
    val innerRadius = radius * 0.45f
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = (i * PI / points) - (PI / 2)
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/**
 * Renders all player pawns with 3D shaded puppets, multi-token clustering,
 * hopping arcs, and reverse capture trajectories.
 */
private fun DrawScope.drawBoardPawns(
    gameState: LudoGameState,
    selectableTokenIds: List<Int>,
    animatingToken: AnimatingTokenState?,
    capturedAnimatingTokens: List<AnimatingCapturedState>,
    cellSize: Float,
    pulseScale: Float
) {
    val currentP = gameState.currentPlayer

    // 1. Group static tokens by location for clustering
    val tokensByLocation = mutableMapOf<String, MutableList<Pair<LudoPlayerState, LudoToken>>>()

    for (player in gameState.players) {
        for (token in player.tokens) {
            // Exclude pawns that are currently actively animating (they are rendered in layer 2 and 3)
            val isActivelyMoving = animatingToken != null &&
                    animatingToken.color == player.color &&
                    animatingToken.tokenId == token.id
            val isActivelyCaptured = capturedAnimatingTokens.any {
                it.color == player.color && it.tokenId == token.id
            }

            if (!isActivelyMoving && !isActivelyCaptured) {
                val key = when {
                    token.isInYard -> "yard_${player.color}_${token.id}"
                    token.isFinished -> "finish_${player.color}"
                    token.isInHomeStretch -> "home_${player.color}_${token.homeStretchIndex}"
                    else -> "track_${token.trackIndex}"
                }
                tokensByLocation.getOrPut(key) { mutableListOf() }.add(Pair(player, token))
            }
        }
    }

    // 2. Render all static pawns
    for ((_, tokenGroup) in tokensByLocation) {
        val count = tokenGroup.size
        tokenGroup.forEachIndexed { index, (player, token) ->
            val baseCenter = LudoPawnRenderer.getCanvasOffsetForStep(
                color = token.color,
                tokenId = token.id,
                step = token.step,
                cellSize = cellSize
            )

            val offset = if (!token.isInYard) {
                LudoPawnRenderer.getMultiTokenOffset(index, count, cellSize)
            } else {
                Offset.Zero
            }

            val finalCenter = baseCenter + offset
            val isSelectable = animatingToken == null &&
                    capturedAnimatingTokens.isEmpty() &&
                    player.color == currentP.color &&
                    token.id in selectableTokenIds
            val pawnScale = if (count > 1 && !token.isInYard) 0.80f else 0.94f

            LudoPawnRenderer.drawLudoPawn(
                drawScope = this,
                center = finalCenter,
                cellSize = cellSize,
                color = player.color,
                isSelectable = isSelectable,
                pulseScale = pulseScale,
                liftPx = 0f,
                scale = pawnScale
            )
        }
    }

    // 3. Render any pawns actively returning backwards upon capture
    for (capturedAnim in capturedAnimatingTokens) {
        val reversePath = capturedAnim.reversePath
        if (reversePath.size >= 2 && capturedAnim.currentHopIndex < reversePath.size - 1) {
            val fromStep = reversePath[capturedAnim.currentHopIndex]
            val toStep = reversePath[capturedAnim.currentHopIndex + 1]

            val fromOffset = LudoPawnRenderer.getCanvasOffsetForStep(
                color = capturedAnim.color,
                tokenId = capturedAnim.tokenId,
                step = fromStep,
                cellSize = cellSize
            )
            val toOffset = LudoPawnRenderer.getCanvasOffsetForStep(
                color = capturedAnim.color,
                tokenId = capturedAnim.tokenId,
                step = toStep,
                cellSize = cellSize
            )

            val frac = capturedAnim.hopFraction.coerceIn(0f, 1f)
            val currentPos = fromOffset + (toOffset - fromOffset) * frac
            val maxHop = cellSize * 0.38f
            val liftPx = sin(frac * PI.toFloat()) * maxHop

            LudoPawnRenderer.drawLudoPawn(
                drawScope = this,
                center = currentPos,
                cellSize = cellSize,
                color = capturedAnim.color,
                isSelectable = false,
                pulseScale = 1.0f,
                liftPx = liftPx,
                scale = 0.88f
            )
        }
    }

    // 4. Render the active forward-hopping pawn (on top of all other elements)
    if (animatingToken != null) {
        val path = animatingToken.path
        if (path.size >= 2 && animatingToken.currentHopIndex < path.size - 1) {
            val fromStep = path[animatingToken.currentHopIndex]
            val toStep = path[animatingToken.currentHopIndex + 1]

            val fromOffset = LudoPawnRenderer.getCanvasOffsetForStep(
                color = animatingToken.color,
                tokenId = animatingToken.tokenId,
                step = fromStep,
                cellSize = cellSize
            )
            val toOffset = LudoPawnRenderer.getCanvasOffsetForStep(
                color = animatingToken.color,
                tokenId = animatingToken.tokenId,
                step = toStep,
                cellSize = cellSize
            )

            val frac = animatingToken.hopFraction.coerceIn(0f, 1f)
            val currentPos = fromOffset + (toOffset - fromOffset) * frac
            val maxHop = cellSize * 0.46f
            val liftPx = sin(frac * PI.toFloat()) * maxHop

            LudoPawnRenderer.drawLudoPawn(
                drawScope = this,
                center = currentPos,
                cellSize = cellSize,
                color = animatingToken.color,
                isSelectable = false,
                pulseScale = 1.0f,
                liftPx = liftPx,
                scale = 1.02f // Slightly enlarged during flight for depth
            )
        }
    }
}
