package com.example.ui.screens.detail.checkers

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.engine.PlayerId
import com.example.core.engine.checkers.CheckersGameState
import com.example.core.engine.checkers.CheckersMove
import com.example.core.engine.checkers.CheckersPiece
import com.example.core.engine.checkers.CheckersSquare

private val LightSquareColor = Color(0xFFF3ECE0)
private val DarkSquareColor = Color(0xFF422F26)
private val BoardWoodFrame = Color(0xFF2B1D16)
private val SelectedSquareHighlight = Color(0xFFFBBF24) // Amber
private val LegalMoveColor = Color(0xFF10B981) // Green
private val LegalJumpColor = Color(0xFFEF4444) // Red
private val RedPiecePrimary = Color(0xFFDC2626)
private val RedPieceSecondary = Color(0xFF991B1B)
private val RedPieceHighlight = Color(0xFFF87171)
private val BlackPiecePrimary = Color(0xFF27272A)
private val BlackPieceSecondary = Color(0xFF18181B)
private val BlackPieceHighlight = Color(0xFF52525B)
private val CrownGold = Color(0xFFFBBF24)

@Composable
fun CheckersBoardView(
    state: CheckersGameState,
    selectedSquare: CheckersSquare?,
    legalMovesForSelected: List<CheckersMove>,
    onSquareClicked: (CheckersSquare) -> Unit,
    flipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(BoardWoodFrame)
            .border(4.dp, Color(0xFF1A110D), RoundedCornerShape(18.dp))
            .padding(10.dp)
            .testTag("checkers_board"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Visual Canvas rendering the 8x8 checkerboard, pieces, and movement highlights
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tileSize = size.width / 8f

                for (r in 0..7) {
                    for (c in 0..7) {
                        val visualRow = if (flipped) 7 - r else r
                        val visualCol = if (flipped) 7 - c else c
                        val sq = CheckersSquare(visualRow, visualCol)

                        val squareColor = if (sq.isDarkPlayable) DarkSquareColor else LightSquareColor
                        val left = c * tileSize
                        val top = r * tileSize

                        drawRect(
                            color = squareColor,
                            topLeft = Offset(left, top),
                            size = Size(tileSize, tileSize)
                        )

                        // Highlight last move from & to squares
                        if (state.lastMove != null && (state.lastMove.from == sq || state.lastMove.to == sq)) {
                            drawRect(
                                color = Color(0x33F59E0B),
                                topLeft = Offset(left, top),
                                size = Size(tileSize, tileSize)
                            )
                        }

                        // Highlight mandatory multi-jump square
                        if (state.mustContinueJumpingFrom == sq) {
                            drawRect(
                                color = Color(0x66F97316),
                                topLeft = Offset(left, top),
                                size = Size(tileSize, tileSize)
                            )
                        }

                        // Highlight selected square
                        if (selectedSquare == sq) {
                            drawRect(
                                color = SelectedSquareHighlight.copy(alpha = 0.45f),
                                topLeft = Offset(left, top),
                                size = Size(tileSize, tileSize)
                            )
                            drawRect(
                                color = SelectedSquareHighlight,
                                topLeft = Offset(left, top),
                                size = Size(tileSize, tileSize),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }

                        // Draw piece if present
                        val piece = state.board[sq]
                        if (piece != null) {
                            drawCheckersPiece(
                                piece = piece,
                                center = Offset(left + tileSize / 2f, top + tileSize / 2f),
                                radius = tileSize * 0.40f
                            )
                        }

                        // Draw legal move indicators on destination squares
                        val legalMove = legalMovesForSelected.firstOrNull { it.to == sq }
                        if (legalMove != null) {
                            val center = Offset(left + tileSize / 2f, top + tileSize / 2f)
                            if (legalMove.isJump) {
                                val ringRadius = tileSize * 0.36f * pulseScale
                                drawCircle(
                                    color = LegalJumpColor.copy(alpha = 0.5f),
                                    radius = ringRadius,
                                    center = center,
                                    style = Stroke(width = 3.5.dp.toPx())
                                )
                                drawCircle(
                                    color = LegalJumpColor,
                                    radius = tileSize * 0.12f,
                                    center = center,
                                    style = Fill
                                )
                            } else {
                                drawCircle(
                                    color = LegalMoveColor.copy(alpha = 0.85f),
                                    radius = tileSize * 0.15f,
                                    center = center,
                                    style = Fill
                                )
                            }
                        }
                    }
                }
            }

            // Clean 8x8 clickable touch grid with individual testTags
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0..7) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        for (c in 0..7) {
                            val visualRow = if (flipped) 7 - r else r
                            val visualCol = if (flipped) 7 - c else c
                            val sq = CheckersSquare(visualRow, visualCol)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onSquareClicked(sq) }
                                    .testTag("checkers_square_${sq.row}_${sq.col}")
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCheckersPiece(
    piece: CheckersPiece,
    center: Offset,
    radius: Float
) {
    val isRed = piece.player == PlayerId.PLAYER_1
    val baseColor = if (isRed) RedPiecePrimary else BlackPiecePrimary
    val darkColor = if (isRed) RedPieceSecondary else BlackPieceSecondary
    val highlightColor = if (isRed) RedPieceHighlight else BlackPieceHighlight

    // Drop shadow
    drawCircle(
        color = Color(0x66000000),
        radius = radius,
        center = center + Offset(2f, 4f)
    )

    // Outer rim 3D bevel gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(highlightColor, baseColor, darkColor),
            center = center - Offset(radius * 0.25f, radius * 0.25f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = center
    )

    // Outer edge border
    drawCircle(
        color = if (isRed) Color(0xFF7F1D1D) else Color(0xFF09090B),
        radius = radius,
        center = center,
        style = Stroke(width = 1.8.dp.toPx())
    )

    // Inner concentric ridge 1
    drawCircle(
        color = highlightColor.copy(alpha = 0.5f),
        radius = radius * 0.74f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Inner plateau
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor, darkColor),
            center = center,
            radius = radius * 0.7f
        ),
        radius = radius * 0.65f,
        center = center
    )

    // Inner concentric ridge 2
    drawCircle(
        color = darkColor,
        radius = radius * 0.50f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // If crowned King, draw golden royal crown
    if (piece.isKing) {
        drawCrown(center = center, size = radius * 0.75f)
    }
}

private fun DrawScope.drawCrown(center: Offset, size: Float) {
    val w = size
    val h = size * 0.75f
    val left = center.x - (w / 2f)
    val top = center.y - (h / 2f)

    val crownPath = Path().apply {
        moveTo(left, top + h)
        lineTo(left + w, top + h)
        lineTo(left + w * 0.95f, top + h * 0.35f)
        lineTo(left + w * 0.72f, top + h * 0.65f)
        lineTo(left + w * 0.50f, top + h * 0.15f)
        lineTo(left + w * 0.28f, top + h * 0.65f)
        lineTo(left + w * 0.05f, top + h * 0.35f)
        close()
    }

    // Crown shadow
    drawPath(
        path = crownPath,
        color = Color(0x66000000),
        style = Stroke(width = 2.dp.toPx())
    )

    // Gold fill
    drawPath(
        path = crownPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFEF08A), CrownGold, Color(0xFFD97706)),
            startY = top,
            endY = top + h
        ),
        style = Fill
    )

    // Crown outline
    drawPath(
        path = crownPath,
        color = Color(0xFF78350F),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Crown jewel dots on peaks
    val jewelRadius = w * 0.07f
    val jewels = listOf(
        Offset(left + w * 0.05f, top + h * 0.35f),
        Offset(left + w * 0.50f, top + h * 0.15f),
        Offset(left + w * 0.95f, top + h * 0.35f)
    )
    for (j in jewels) {
        drawCircle(color = Color.White, radius = jewelRadius, center = j)
        drawCircle(color = Color(0xFF78350F), radius = jewelRadius, center = j, style = Stroke(width = 1f))
    }
}
