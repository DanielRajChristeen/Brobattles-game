package com.example.ui.screens.detail.chess

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.example.core.engine.chess.ChessGameState
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessPiece
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.chess.PieceType
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulPink
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.PlayfulYellow

@Composable
fun ChessBoardView(
    state: ChessGameState,
    selectedSquare: ChessSquare?,
    legalMovesForSelected: List<ChessMove>,
    onSquareTapped: (ChessSquare) -> Unit,
    isRotated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    val isDark = colors.isDark

    // Animate last move movement
    val moveAnim = remember { Animatable(1f) }
    LaunchedEffect(state.lastMove) {
        if (state.lastMove != null) {
            moveAnim.snapTo(0f)
            moveAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    val legalTargetSquares = remember(legalMovesForSelected) {
        legalMovesForSelected.map { it.to }
    }

    val kingInCheckSquare = remember(state.inCheck, state.activeColor, state.board) {
        if (state.inCheck) state.findKing(state.activeColor) else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = if (isDark) 12.dp else 6.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
            .border(width = 2.5.dp, color = colors.border, shape = RoundedCornerShape(22.dp))
            .padding(10.dp)
            .testTag("chess_board_view")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(state, selectedSquare, legalTargetSquares, isRotated) {
                    detectTapGestures { offset ->
                        val cellSize = size.width / 8f
                        val visCol = (offset.x / cellSize).toInt().coerceIn(0, 7)
                        val visRow = (offset.y / cellSize).toInt().coerceIn(0, 7)
                        val col = if (isRotated) 7 - visCol else visCol
                        val row = if (isRotated) 7 - visRow else visRow
                        onSquareTapped(ChessSquare(row, col))
                    }
                }
        ) {
            val cellSize = size.width / 8f

            // 1. Draw 64 Board Squares
            drawBoardSquares(cellSize, isDark)

            // 2. Draw Last Move Highlight
            state.lastMove?.let { last ->
                drawSquareHighlight(last.from, cellSize, PlayfulYellow.copy(alpha = 0.28f), isRotated)
                drawSquareHighlight(last.to, cellSize, PlayfulYellow.copy(alpha = 0.38f), isRotated)
            }

            // 3. Draw Check Indicator on King's Square
            kingInCheckSquare?.let { kSquare ->
                drawSquareHighlight(kSquare, cellSize, Color(0xFFEF4444).copy(alpha = 0.45f), isRotated)
                drawCheckGlow(kSquare, cellSize, isRotated)
            }

            // 4. Draw Selected Square Highlight
            selectedSquare?.let { sel ->
                drawSquareHighlight(sel, cellSize, Color(0xFFF59E0B).copy(alpha = 0.40f), isRotated)
                drawSquareBorder(sel, cellSize, Color(0xFFF59E0B), 2.5f, isRotated)
            }

            // 5. Draw Legal Move Markers (Dots for empty, corner rings for capture)
            for (dest in legalTargetSquares) {
                val destPiece = state.pieceAt(dest)
                val isCapture = destPiece != null || (state.enPassantTarget == dest && state.pieceAt(selectedSquare ?: dest)?.type == PieceType.PAWN)
                if (isCapture) {
                    drawCaptureIndicator(dest, cellSize, isRotated)
                } else {
                    drawMoveDot(dest, cellSize, isRotated)
                }
            }

            // 6. Draw Coordinate Labels (ranks 1-8, files a-h)
            drawCoordinates(cellSize, isDark, isRotated)

            // 7. Draw Chess Pieces (with animation for last moving piece)
            val animatedMove = if (moveAnim.value < 1f) state.lastMove else null

            for (r in 0..7) {
                for (c in 0..7) {
                    val piece = state.pieceAt(r, c) ?: continue
                    val visC = if (isRotated) 7 - c else c
                    val visR = if (isRotated) 7 - r else r

                    // If this piece is currently animating its move to destination
                    if (animatedMove != null && animatedMove.to.row == r && animatedMove.to.col == c) {
                        val fromVisC = if (isRotated) 7 - animatedMove.from.col else animatedMove.from.col
                        val fromVisR = if (isRotated) 7 - animatedMove.from.row else animatedMove.from.row
                        val toVisC = if (isRotated) 7 - animatedMove.to.col else animatedMove.to.col
                        val toVisR = if (isRotated) 7 - animatedMove.to.row else animatedMove.to.row

                        val fromOffset = Offset(
                            fromVisC * cellSize + cellSize / 2f,
                            fromVisR * cellSize + cellSize / 2f
                        )
                        val toOffset = Offset(
                            toVisC * cellSize + cellSize / 2f,
                            toVisR * cellSize + cellSize / 2f
                        )
                        val progress = moveAnim.value
                        val curCenter = Offset(
                            fromOffset.x + (toOffset.x - fromOffset.x) * progress,
                            fromOffset.y + (toOffset.y - fromOffset.y) * progress
                        )
                        drawChessPiece(piece, curCenter, cellSize)
                    } else {
                        val center = Offset(visC * cellSize + cellSize / 2f, visR * cellSize + cellSize / 2f)
                        drawChessPiece(piece, center, cellSize)
                    }
                }
            }
        }
    }
}

/**
 * Draws high-contrast tournament-style checkered board.
 */
private fun DrawScope.drawBoardSquares(cellSize: Float, isDarkTheme: Boolean) {
    // Elegant tournament board palette
    // Light squares: Crisp Ivory / Warm White
    // Dark squares: Warm Slate / Muted Walnut
    val lightSquareColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFFF8FAFC)
    val darkSquareColor = if (isDarkTheme) Color(0xFF475569) else Color(0xFF94A3B8)

    for (r in 0..7) {
        for (c in 0..7) {
            val isLight = (r + c) % 2 == 0
            val color = if (isLight) lightSquareColor else darkSquareColor
            drawRect(
                color = color,
                topLeft = Offset(c * cellSize, r * cellSize),
                size = Size(cellSize, cellSize)
            )
        }
    }
}

private fun DrawScope.drawSquareHighlight(square: ChessSquare, cellSize: Float, color: Color, isRotated: Boolean = false) {
    val c = if (isRotated) 7 - square.col else square.col
    val r = if (isRotated) 7 - square.row else square.row
    drawRect(
        color = color,
        topLeft = Offset(c * cellSize, r * cellSize),
        size = Size(cellSize, cellSize)
    )
}

private fun DrawScope.drawSquareBorder(square: ChessSquare, cellSize: Float, color: Color, widthPx: Float, isRotated: Boolean = false) {
    val c = if (isRotated) 7 - square.col else square.col
    val r = if (isRotated) 7 - square.row else square.row
    drawRect(
        color = color,
        topLeft = Offset(c * cellSize + widthPx / 2f, r * cellSize + widthPx / 2f),
        size = Size(cellSize - widthPx, cellSize - widthPx),
        style = Stroke(width = widthPx)
    )
}

private fun DrawScope.drawCheckGlow(square: ChessSquare, cellSize: Float, isRotated: Boolean = false) {
    val c = if (isRotated) 7 - square.col else square.col
    val r = if (isRotated) 7 - square.row else square.row
    val center = Offset(c * cellSize + cellSize / 2f, r * cellSize + cellSize / 2f)
    drawCircle(
        color = Color(0xFFEF4444).copy(alpha = 0.5f),
        radius = cellSize * 0.45f,
        center = center
    )
}

private fun DrawScope.drawMoveDot(square: ChessSquare, cellSize: Float, isRotated: Boolean = false) {
    val c = if (isRotated) 7 - square.col else square.col
    val r = if (isRotated) 7 - square.row else square.row
    val center = Offset(c * cellSize + cellSize / 2f, r * cellSize + cellSize / 2f)
    drawCircle(
        color = PlayfulPurple.copy(alpha = 0.65f),
        radius = cellSize * 0.16f,
        center = center
    )
}

private fun DrawScope.drawCaptureIndicator(square: ChessSquare, cellSize: Float, isRotated: Boolean = false) {
    val c = if (isRotated) 7 - square.col else square.col
    val r = if (isRotated) 7 - square.row else square.row
    val left = c * cellSize
    val top = r * cellSize
    val margin = cellSize * 0.10f
    val cornerLen = cellSize * 0.24f
    val strokeWidth = 3.5f
    val color = PlayfulPink

    // 4 Corner Brackets
    // Top-Left
    drawLine(color, Offset(left + margin, top + margin), Offset(left + margin + cornerLen, top + margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left + margin, top + margin), Offset(left + margin, top + margin + cornerLen), strokeWidth, StrokeCap.Round)
    // Top-Right
    drawLine(color, Offset(left + cellSize - margin, top + margin), Offset(left + cellSize - margin - cornerLen, top + margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left + cellSize - margin, top + margin), Offset(left + cellSize - margin, top + margin + cornerLen), strokeWidth, StrokeCap.Round)
    // Bottom-Left
    drawLine(color, Offset(left + margin, top + cellSize - margin), Offset(left + margin + cornerLen, top + cellSize - margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left + margin, top + cellSize - margin), Offset(left + margin, top + cellSize - margin - cornerLen), strokeWidth, StrokeCap.Round)
    // Bottom-Right
    drawLine(color, Offset(left + cellSize - margin, top + cellSize - margin), Offset(left + cellSize - margin - cornerLen, top + cellSize - margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left + cellSize - margin, top + cellSize - margin), Offset(left + cellSize - margin, top + cellSize - margin - cornerLen), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawCoordinates(cellSize: Float, isDarkTheme: Boolean, isRotated: Boolean = false) {
    val paint = android.graphics.Paint().apply {
        textSize = cellSize * 0.22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // Files on bottom rank
    for (c in 0..7) {
        val isLight = (7 + c) % 2 == 0
        paint.color = if (isLight) {
            if (isDarkTheme) android.graphics.Color.parseColor("#475569") else android.graphics.Color.parseColor("#64748B")
        } else {
            if (isDarkTheme) android.graphics.Color.parseColor("#CBD5E1") else android.graphics.Color.parseColor("#F8FAFC")
        }
        val text = "${if (isRotated) ('h' - c) else ('a' + c)}"
        drawContext.canvas.nativeCanvas.drawText(
            text,
            c * cellSize + 4f,
            8 * cellSize - 4f,
            paint
        )
    }

    // Ranks on right edge
    for (r in 0..7) {
        val isLight = (r + 7) % 2 == 0
        paint.color = if (isLight) {
            if (isDarkTheme) android.graphics.Color.parseColor("#475569") else android.graphics.Color.parseColor("#64748B")
        } else {
            if (isDarkTheme) android.graphics.Color.parseColor("#CBD5E1") else android.graphics.Color.parseColor("#F8FAFC")
        }
        val text = "${if (isRotated) (1 + r) else (8 - r)}"
        drawContext.canvas.nativeCanvas.drawText(
            text,
            8 * cellSize - (paint.textSize * 0.75f) - 2f,
            r * cellSize + paint.textSize + 2f,
            paint
        )
    }
}

/**
 * Draws a beautiful, high-contrast chess piece with crisp contours and shading
 * ensuring 100% legibility on light and dark squares in both Light and Dark themes.
 */
private fun DrawScope.drawChessPiece(piece: ChessPiece, center: Offset, cellSize: Float) {
    val isWhite = piece.color == PieceColor.WHITE
    val size = cellSize * 0.76f

    val paint = android.graphics.Paint().apply {
        textSize = size
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // Measure vertical baseline alignment
    val fontMetrics = paint.fontMetrics
    val baselineY = center.y - (fontMetrics.descent + fontMetrics.ascent) / 2f
    val symbol = piece.unicodeSymbol

    // 1. Subtle Ground Shadow beneath piece
    paint.color = android.graphics.Color.argb(70, 0, 0, 0)
    drawContext.canvas.nativeCanvas.drawText(symbol, center.x + 1.5f, baselineY + 2.5f, paint)

    // 2. High-Contrast Stroke Outline around piece
    // White pieces get dark stroke outline so they pop on light squares.
    // Black pieces get light halo outline so they pop on dark squares.
    val strokePaint = android.graphics.Paint(paint).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = cellSize * 0.08f
        strokeJoin = android.graphics.Paint.Join.ROUND
        color = if (isWhite) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F8FAFC")
    }
    drawContext.canvas.nativeCanvas.drawText(symbol, center.x, baselineY, strokePaint)

    // 3. Solid Fill Body
    val fillPaint = android.graphics.Paint(paint).apply {
        style = android.graphics.Paint.Style.FILL
        color = if (isWhite) {
            android.graphics.Color.parseColor("#FFFFFF") // Crisp White
        } else {
            android.graphics.Color.parseColor("#0F172A") // Deep Graphite Charcoal
        }
    }
    drawContext.canvas.nativeCanvas.drawText(symbol, center.x, baselineY, fillPaint)
}
