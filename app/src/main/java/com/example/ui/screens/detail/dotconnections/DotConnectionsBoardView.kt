package com.example.ui.screens.detail.dotconnections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotConnectionsGameState
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun DotConnectionsBoardView(
    state: DotConnectionsGameState,
    onEdgeSelected: (DotEdge) -> Unit,
    isInteractive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    val isDark = colors.isDark

    // Track active selection or preview from user gestures
    var selectedDot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var previewEdge by remember { mutableStateOf<DotEdge?>(null) }

    // Line drawing animation for the most recent move
    val lineAnim = remember { Animatable(1f) }
    LaunchedEffect(state.placedEdges.size, state.lastMoveEdge) {
        if (state.lastMoveEdge != null) {
            lineAnim.snapTo(0f)
            lineAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    // Square completion pulse animation
    val boxPulseAnim = remember { Animatable(1f) }
    LaunchedEffect(state.lastCompletedBoxes) {
        if (state.lastCompletedBoxes.isNotEmpty()) {
            boxPulseAnim.snapTo(1.15f)
            boxPulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    val dotsCount = state.boardSize.dots

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = if (isDark) 12.dp else 6.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
            .border(width = 2.5.dp, color = colors.border, shape = RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("dot_connections_board")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(state.placedEdges, isInteractive, dotsCount) {
                    if (!isInteractive) return@pointerInput

                    detectTapGestures(
                        onPress = { offset ->
                            val boardPadding = 18f
                            val drawAreaSize = size.width - 2 * boardPadding
                            val spacing = drawAreaSize / (dotsCount - 1)

                            // 1. Direct Edge Detection (generous touch corridor around each edge)
                            val edgeThreshold = spacing * 0.42f
                            val touchedEdge = findNearestEdge(offset, dotsCount, boardPadding, spacing, edgeThreshold)
                            if (touchedEdge != null && !state.isEdgeOccupied(touchedEdge)) {
                                previewEdge = touchedEdge
                                tryAwaitRelease()
                                previewEdge = null
                                onEdgeSelected(touchedEdge)
                                selectedDot = null
                                return@detectTapGestures
                            }

                            // 2. Dot-based selection
                            val dotThreshold = spacing * 0.38f
                            val touchedDot = findNearestDot(offset, dotsCount, boardPadding, spacing, dotThreshold)
                            if (touchedDot != null) {
                                val prevDot = selectedDot
                                if (prevDot != null) {
                                    // Check if adjacent to prevDot
                                    val rowDiff = abs(prevDot.first - touchedDot.first)
                                    val colDiff = abs(prevDot.second - touchedDot.second)
                                    if (rowDiff + colDiff == 1) {
                                        val candidateEdge = getEdgeBetweenDots(prevDot, touchedDot)
                                        if (candidateEdge != null && !state.isEdgeOccupied(candidateEdge)) {
                                            onEdgeSelected(candidateEdge)
                                            selectedDot = null
                                            previewEdge = null
                                            return@detectTapGestures
                                        }
                                    }
                                }
                                selectedDot = touchedDot
                            } else {
                                selectedDot = null
                            }
                        }
                    )
                }
                .pointerInput(state.placedEdges, isInteractive, dotsCount) {
                    if (!isInteractive) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            val boardPadding = 18f
                            val drawAreaSize = size.width - 2 * boardPadding
                            val spacing = drawAreaSize / (dotsCount - 1)
                            val dotThreshold = spacing * 0.40f
                            val dot = findNearestDot(offset, dotsCount, boardPadding, spacing, dotThreshold)
                            if (dot != null) {
                                selectedDot = dot
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val curSelected = selectedDot ?: return@detectDragGestures
                            val boardPadding = 18f
                            val drawAreaSize = size.width - 2 * boardPadding
                            val spacing = drawAreaSize / (dotsCount - 1)

                            // Check if drag position is nearing an adjacent dot
                            val targetDot = findNearestDot(change.position, dotsCount, boardPadding, spacing, spacing * 0.45f)
                            if (targetDot != null && targetDot != curSelected) {
                                val rowDiff = abs(curSelected.first - targetDot.first)
                                val colDiff = abs(curSelected.second - targetDot.second)
                                if (rowDiff + colDiff == 1) {
                                    val edge = getEdgeBetweenDots(curSelected, targetDot)
                                    if (edge != null && !state.isEdgeOccupied(edge)) {
                                        previewEdge = edge
                                    } else {
                                        previewEdge = null
                                    }
                                } else {
                                    previewEdge = null
                                }
                            } else {
                                previewEdge = null
                            }
                        },
                        onDragEnd = {
                            val activePreview = previewEdge
                            if (activePreview != null && !state.isEdgeOccupied(activePreview)) {
                                onEdgeSelected(activePreview)
                            }
                            selectedDot = null
                            previewEdge = null
                        },
                        onDragCancel = {
                            selectedDot = null
                            previewEdge = null
                        }
                    )
                }
        ) {
            val boardPadding = 18f
            val drawAreaSize = size.width - 2 * boardPadding
            val spacing = drawAreaSize / (dotsCount - 1)
            val strokeWidth = max(5.5f, size.width * 0.016f)
            val dotRadius = max(6.5f, size.width * 0.022f)

            // 1. Draw Claimed Squares (soft background fill + pulse on newly completed)
            val boxesPerSide = dotsCount - 1
            for (r in 0 until boxesPerSide) {
                for (c in 0 until boxesPerSide) {
                    val boxOwner = state.getBoxOwner(r, c) ?: continue
                    val isNewlyCompleted = state.lastCompletedBoxes.contains(Pair(r, c))
                    val pulseScale = if (isNewlyCompleted) boxPulseAnim.value else 1f

                    val boxLeft = boardPadding + c * spacing
                    val boxTop = boardPadding + r * spacing
                    val boxSize = spacing

                    val fillColor = if (boxOwner == DotPlayer.PLAYER_1) {
                        PlayfulOrange.copy(alpha = 0.22f)
                    } else {
                        PlayfulPurple.copy(alpha = 0.22f)
                    }

                    // Scaled pulse around box center
                    val centerX = boxLeft + boxSize / 2f
                    val centerY = boxTop + boxSize / 2f
                    val scaledSize = boxSize * pulseScale
                    val drawLeft = centerX - scaledSize / 2f
                    val drawTop = centerY - scaledSize / 2f

                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(drawLeft + 3f, drawTop + 3f),
                        size = Size(scaledSize - 6f, scaledSize - 6f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Draw subtle owner badge / initial in center of box
                    val paint = android.graphics.Paint().apply {
                        textSize = spacing * 0.32f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        color = if (boxOwner == DotPlayer.PLAYER_1) {
                            android.graphics.Color.parseColor("#F97316")
                        } else {
                            android.graphics.Color.parseColor("#8B5CF6")
                        }
                    }
                    val fontMetrics = paint.fontMetrics
                    val textY = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f
                    val symbol = if (boxOwner == DotPlayer.PLAYER_1) "P1" else "P2"
                    drawContext.canvas.nativeCanvas.drawText(symbol, centerX, textY, paint)
                }
            }

            // 2. Draw Candidate / Hover Preview Edge
            previewEdge?.let { edge ->
                val previewColor = if (state.activePlayer == DotPlayer.PLAYER_1) {
                    PlayfulOrange.copy(alpha = 0.45f)
                } else {
                    PlayfulPurple.copy(alpha = 0.45f)
                }
                drawEdgeLine(edge, boardPadding, spacing, previewColor, strokeWidth * 0.85f, progress = 1f)
            }

            // 3. Draw Placed Edges
            for ((edge, placement) in state.placedEdges) {
                val lineColor = if (placement.owner == DotPlayer.PLAYER_1) PlayfulOrange else PlayfulPurple
                val isRecent = state.lastMoveEdge == edge
                val progress = if (isRecent) lineAnim.value else 1f
                drawEdgeLine(edge, boardPadding, spacing, lineColor, strokeWidth, progress)
            }

            // 4. Draw Matrix of Dots
            val dotColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF334155)
            val dotGlowColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFCBD5E1)

            for (r in 0 until dotsCount) {
                for (c in 0 until dotsCount) {
                    val dotCenter = Offset(
                        boardPadding + c * spacing,
                        boardPadding + r * spacing
                    )

                    // Highlight currently selected dot
                    val isSelected = selectedDot?.first == r && selectedDot?.second == c
                    if (isSelected) {
                        drawCircle(
                            color = (if (state.activePlayer == DotPlayer.PLAYER_1) PlayfulOrange else PlayfulPurple).copy(alpha = 0.35f),
                            radius = dotRadius * 2.2f,
                            center = dotCenter
                        )
                    }

                    // Shadow / contrast ring beneath dot
                    drawCircle(
                        color = dotGlowColor,
                        radius = dotRadius + 1.5f,
                        center = dotCenter
                    )

                    // Solid dot
                    drawCircle(
                        color = if (isSelected) {
                            if (state.activePlayer == DotPlayer.PLAYER_1) PlayfulOrange else PlayfulPurple
                        } else dotColor,
                        radius = dotRadius,
                        center = dotCenter
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawEdgeLine(
    edge: DotEdge,
    boardPadding: Float,
    spacing: Float,
    color: Color,
    strokeWidth: Float,
    progress: Float
) {
    val (start, end) = when (edge.orientation) {
        EdgeOrientation.HORIZONTAL -> {
            val startX = boardPadding + edge.col * spacing
            val endX = boardPadding + (edge.col + 1) * spacing
            val y = boardPadding + edge.row * spacing
            Pair(Offset(startX, y), Offset(endX, y))
        }
        EdgeOrientation.VERTICAL -> {
            val x = boardPadding + edge.col * spacing
            val startY = boardPadding + edge.row * spacing
            val endY = boardPadding + (edge.row + 1) * spacing
            Pair(Offset(x, startY), Offset(x, endY))
        }
    }

    val currentEnd = Offset(
        start.x + (end.x - start.x) * progress,
        start.y + (end.y - start.y) * progress
    )

    drawLine(
        color = color,
        start = start,
        end = currentEnd,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * Finds the nearest dot within a given threshold distance.
 */
private fun findNearestDot(
    pos: Offset,
    dotsCount: Int,
    boardPadding: Float,
    spacing: Float,
    threshold: Float
): Pair<Int, Int>? {
    var bestDot: Pair<Int, Int>? = null
    var minDistance = threshold

    for (r in 0 until dotsCount) {
        for (c in 0 until dotsCount) {
            val dotX = boardPadding + c * spacing
            val dotY = boardPadding + r * spacing
            val dist = hypot(pos.x - dotX, pos.y - dotY)
            if (dist < minDistance) {
                minDistance = dist
                bestDot = Pair(r, c)
            }
        }
    }
    return bestDot
}

/**
 * Finds the nearest edge within a generous rectangular corridor.
 */
private fun findNearestEdge(
    pos: Offset,
    dotsCount: Int,
    boardPadding: Float,
    spacing: Float,
    threshold: Float
): DotEdge? {
    var bestEdge: DotEdge? = null
    var minDistance = threshold

    // Check all horizontal edges
    for (r in 0 until dotsCount) {
        for (c in 0 until (dotsCount - 1)) {
            val x1 = boardPadding + c * spacing
            val x2 = boardPadding + (c + 1) * spacing
            val y = boardPadding + r * spacing

            if (pos.x in (x1 - threshold * 0.5f)..(x2 + threshold * 0.5f)) {
                val distY = abs(pos.y - y)
                if (distY < minDistance) {
                    minDistance = distY
                    bestEdge = DotEdge(EdgeOrientation.HORIZONTAL, r, c)
                }
            }
        }
    }

    // Check all vertical edges
    for (r in 0 until (dotsCount - 1)) {
        for (c in 0 until dotsCount) {
            val x = boardPadding + c * spacing
            val y1 = boardPadding + r * spacing
            val y2 = boardPadding + (r + 1) * spacing

            if (pos.y in (y1 - threshold * 0.5f)..(y2 + threshold * 0.5f)) {
                val distX = abs(pos.x - x)
                if (distX < minDistance) {
                    minDistance = distX
                    bestEdge = DotEdge(EdgeOrientation.VERTICAL, r, c)
                }
            }
        }
    }

    return bestEdge
}

/**
 * Creates the valid edge connecting two adjacent dots, or null if not adjacent.
 */
private fun getEdgeBetweenDots(dotA: Pair<Int, Int>, dotB: Pair<Int, Int>): DotEdge? {
    val (r1, c1) = dotA
    val (r2, c2) = dotB

    return when {
        r1 == r2 && abs(c1 - c2) == 1 -> {
            DotEdge(EdgeOrientation.HORIZONTAL, r1, min(c1, c2))
        }
        c1 == c2 && abs(r1 - r2) == 1 -> {
            DotEdge(EdgeOrientation.VERTICAL, min(r1, r2), c1)
        }
        else -> null
    }
}
