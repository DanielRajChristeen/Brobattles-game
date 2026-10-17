package com.example.ui.screens.detail.snakesandladders

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import com.example.core.engine.snakesandladders.Ladder
import com.example.core.engine.snakesandladders.Snake
import com.example.core.engine.snakesandladders.SnakesAndLaddersBoard
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TokenPose {
    IDLE,
    HOPPING,
    CLIMBING,
    SLIDING,
    CELEBRATING
}

object SnakesTokens {
    // 1. 🔴 Player 1: Red
    // 2. 🟣 Player 2: Purple
    // 3. 🔵 Player 3: Blue
    // 4. 🟤 Player 4: Brown
    // 5. 🟢 Player 5: Green
    val COLORS = listOf(
        Color(0xFFEF4444), // 🔴 Player 1: Red
        Color(0xFFA855F7), // 🟣 Player 2: Purple
        Color(0xFF3B82F6), // 🔵 Player 3: Blue
        Color(0xFF8D6E63), // 🟤 Player 4: Brown
        Color(0xFF22C55E)  // 🟢 Player 5: Green
    )

    val EMOJIS = listOf("🔴", "🟣", "🔵", "🟤", "🟢")
    val NAMES = listOf("Player 1", "Player 2", "Player 3", "Player 4", "Player 5")

    fun getColor(index: Int): Color = COLORS.getOrElse(index % COLORS.size) { Color(0xFFEF4444) }
    fun getColor(player: com.example.core.engine.PlayerId): Color = getColor(player.ordinal)

    fun getEmoji(index: Int): String = EMOJIS.getOrElse(index % EMOJIS.size) { "⚪" }
    fun getEmoji(player: com.example.core.engine.PlayerId): String = getEmoji(player.ordinal)

    fun getName(index: Int): String = NAMES.getOrElse(index % NAMES.size) { "Player ${index + 1}" }
    fun getName(player: com.example.core.engine.PlayerId): String = player.displayName
}

val SnakesPlayerColors = SnakesTokens.COLORS

/**
 * Distributes up to 5 player tokens occupying the same square so all tokens remain clearly distinguishable.
 */
fun getPlayerOffsetOnSquare(slotIndex: Int, totalOnSquare: Int, cellSize: Float): Offset {
    if (totalOnSquare <= 1) return Offset.Zero
    val r = cellSize * 0.22f
    return when (totalOnSquare) {
        2 -> {
            if (slotIndex == 0) Offset(-r, 0f) else Offset(r, 0f)
        }
        3 -> {
            when (slotIndex) {
                0 -> Offset(0f, -r * 0.85f)
                1 -> Offset(-r, r * 0.7f)
                else -> Offset(r, r * 0.7f)
            }
        }
        4 -> {
            when (slotIndex) {
                0 -> Offset(-r, -r * 0.8f)
                1 -> Offset(r, -r * 0.8f)
                2 -> Offset(-r, r * 0.8f)
                else -> Offset(r, r * 0.8f)
            }
        }
        else -> { // 5 players on the same square
            when (slotIndex) {
                0 -> Offset.Zero // Player 1 in center
                1 -> Offset(-r * 1.05f, -r * 0.85f)
                2 -> Offset(r * 1.05f, -r * 0.85f)
                3 -> Offset(-r * 1.05f, r * 0.85f)
                else -> Offset(r * 1.05f, r * 0.85f)
            }
        }
    }
}

data class PlayerTokenVisual(
    val playerIndex: Int = 0,
    val position: Offset,
    val pose: TokenPose = TokenPose.IDLE,
    val limbPhase: Float = 0f,
    val color: Color = SnakesTokens.getColor(playerIndex)
)

@Composable
fun SnakesAndLaddersBoardView(
    playerTokens: List<PlayerTokenVisual>? = null,
    p1VisualPos: Offset = Offset.Zero,
    p2VisualPos: Offset = Offset.Zero,
    p1Pose: TokenPose = TokenPose.IDLE,
    p2Pose: TokenPose = TokenPose.IDLE,
    p1LimbPhase: Float = 0f,
    p2LimbPhase: Float = 0f,
    activeSnakeHead: Int? = null,
    snakeWigglePhase: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("snakes_ladders_canvas")
    ) {
        val boardWidth = size.width
        val cellSize = boardWidth / 10f

        // 1. Draw Physical Board Outer Bevel & Tiles
        drawRichBoardTiles(cellSize)

        // 2. Draw Ladders with 3D Rails, Cast Shadows, and Rungs
        for (ladder in SnakesAndLaddersBoard.LADDERS) {
            drawPhysicalLadder(ladder, cellSize)
        }

        // 3. Draw Expressive Colorful Snakes
        for (snake in SnakesAndLaddersBoard.SNAKES) {
            val isWiggling = activeSnakeHead == snake.head
            val wiggle = if (isWiggling) snakeWigglePhase else 0f
            drawExpressiveSnake(snake, cellSize, wiggle)
        }

        // 4. Draw Player Stick Figure Tokens (with distinct colors for all players)
        if (playerTokens != null && playerTokens.isNotEmpty()) {
            for (token in playerTokens) {
                drawHighContrastStickFigure(
                    center = token.position,
                    pose = token.pose,
                    limbPhase = token.limbPhase,
                    primaryColor = token.color,
                    cellSize = cellSize
                )
            }
        } else {
            drawHighContrastStickFigure(
                center = p1VisualPos,
                pose = p1Pose,
                limbPhase = p1LimbPhase,
                primaryColor = SnakesTokens.getColor(0),
                cellSize = cellSize
            )
            drawHighContrastStickFigure(
                center = p2VisualPos,
                pose = p2Pose,
                limbPhase = p2LimbPhase,
                primaryColor = SnakesTokens.getColor(1),
                cellSize = cellSize
            )
        }
    }
}

/**
 * Renders the rich, colorful, physical board tiles inspired by classic board game artwork.
 */
private fun DrawScope.drawRichBoardTiles(cellSize: Float) {
    val numberPaint = android.graphics.Paint().apply {
        textSize = cellSize * 0.28f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val bannerPaint = android.graphics.Paint().apply {
        textSize = cellSize * 0.22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // Classic 5-color repeating board palette (Red, Gold, Green, Blue, Cream/White)
    val tileColors = listOf(
        Color(0xFFDC2626), // Crimson Red
        Color(0xFFF59E0B), // Warm Gold
        Color(0xFF16A34A), // Fresh Green
        Color(0xFF0284C7), // Sky Blue
        Color(0xFFF8FAFC)  // Crisp Cream
    )

    for (s in 1..100) {
        val col = SnakesAndLaddersBoard.getCol(s)
        val row = SnakesAndLaddersBoard.getRow(s)
        val left = col * cellSize
        val top = row * cellSize

        // Repeating diagonal color sequence
        val colorIndex = (col * 3 + row * 2 + s) % tileColors.size
        val cellColor = when {
            s == 100 -> Color(0xFFDC2626) // Deep Red Finish
            s == 1 -> Color(0xFFF59E0B)   // Golden Start
            else -> tileColors[colorIndex]
        }

        // Draw tile background
        drawRect(
            color = cellColor,
            topLeft = Offset(left, top),
            size = Size(cellSize, cellSize)
        )

        // Inner bevel / border for physical game-board tile feel
        drawRect(
            color = Color(0xFF1E293B).copy(alpha = 0.22f),
            topLeft = Offset(left, top),
            size = Size(cellSize, cellSize),
            style = Stroke(width = 1.2f)
        )

        // Contrast text color
        val isDarkTile = cellColor == Color(0xFFDC2626) || cellColor == Color(0xFF16A34A) || cellColor == Color(0xFF0284C7)
        numberPaint.color = if (isDarkTile) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1E293B")

        if (s == 100) {
            numberPaint.color = android.graphics.Color.WHITE
            drawContext.canvas.nativeCanvas.drawText("100", left + 4f, top + (cellSize * 0.35f), numberPaint)
            bannerPaint.color = android.graphics.Color.parseColor("#FEF08A")
            drawContext.canvas.nativeCanvas.drawText("FINISH 🏁", left + 4f, top + (cellSize * 0.75f), bannerPaint)
        } else if (s == 1) {
            numberPaint.color = android.graphics.Color.parseColor("#1E293B")
            drawContext.canvas.nativeCanvas.drawText("1", left + 4f, top + (cellSize * 0.35f), numberPaint)
            bannerPaint.color = android.graphics.Color.parseColor("#78350F")
            drawContext.canvas.nativeCanvas.drawText("START", left + 4f, top + (cellSize * 0.75f), bannerPaint)
        } else {
            drawContext.canvas.nativeCanvas.drawText(s.toString(), left + 4f, top + (cellSize * 0.32f), numberPaint)
        }
    }
}

/**
 * Draws a solid physical ladder with depth, drop shadow, dual rails, and rungs.
 */
private fun DrawScope.drawPhysicalLadder(ladder: Ladder, cellSize: Float) {
    val start = SnakesAndLaddersBoard.getSquareCenter(ladder.bottom, cellSize)
    val end = SnakesAndLaddersBoard.getSquareCenter(ladder.top, cellSize)

    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

    val perpX = (-dy / length) * (cellSize * 0.22f)
    val perpY = (dx / length) * (cellSize * 0.22f)

    val shadowOffset = Offset(3f, 5f)
    val ladderShadow = Color.Black.copy(alpha = 0.25f)
    val railWood = Color(0xFF78350F)      // Dark rich mahogany/wood
    val railHighlight = Color(0xFFB45309) // Warm highlight
    val rungColor = Color(0xFF92400E)     // Rungs

    // 1. Cast Shadow
    drawLine(
        color = ladderShadow,
        start = Offset(start.x - perpX + shadowOffset.x, start.y - perpY + shadowOffset.y),
        end = Offset(end.x - perpX + shadowOffset.x, end.y - perpY + shadowOffset.y),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ladderShadow,
        start = Offset(start.x + perpX + shadowOffset.x, start.y + perpY + shadowOffset.y),
        end = Offset(end.x + perpX + shadowOffset.x, end.y + perpY + shadowOffset.y),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )

    // 2. Rungs with shadows
    val rungCount = (length / (cellSize * 0.32f)).toInt().coerceAtLeast(3)
    for (i in 1..rungCount) {
        val t = i.toFloat() / (rungCount + 1)
        val rx = start.x + dx * t
        val ry = start.y + dy * t

        // Rung shadow
        drawLine(
            color = ladderShadow,
            start = Offset(rx - perpX + shadowOffset.x, ry - perpY + shadowOffset.y),
            end = Offset(rx + perpX + shadowOffset.x, ry + perpY + shadowOffset.y),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Solid wood rung
        drawLine(
            color = rungColor,
            start = Offset(rx - perpX, ry - perpY),
            end = Offset(rx + perpX, ry + perpY),
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
    }

    // 3. Left Rail
    drawLine(
        color = railWood,
        start = Offset(start.x - perpX, start.y - perpY),
        end = Offset(end.x - perpX, end.y - perpY),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = railHighlight,
        start = Offset(start.x - perpX - 1f, start.y - perpY - 1f),
        end = Offset(end.x - perpX - 1f, end.y - perpY - 1f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // 4. Right Rail
    drawLine(
        color = railWood,
        start = Offset(start.x + perpX, start.y + perpY),
        end = Offset(end.x + perpX, end.y + perpY),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = railHighlight,
        start = Offset(start.x + perpX - 1f, start.y + perpY - 1f),
        end = Offset(end.x + perpX - 1f, end.y + perpY - 1f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
}

/**
 * Draws a prominent, colorful cartoon snake with winding body curves, belly patterns,
 * expressive head, cartoon eyes, and flicking tongue.
 */
private fun DrawScope.drawExpressiveSnake(
    snake: Snake,
    cellSize: Float,
    wigglePhase: Float
) {
    val steps = 24
    val points = mutableListOf<Offset>()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        points.add(SnakesAndLaddersBoard.getSnakePoint(snake, t, cellSize, wigglePhase))
    }

    val shadowOffset = Offset(3f, 6f)
    val snakeShadow = Color.Black.copy(alpha = 0.28f)
    val primaryColor = Color(snake.primaryColor)
    val bellyColor = Color(snake.bellyColor)
    val spotColor = Color(snake.spotColor)

    // 1. Cast Shadow Path
    val shadowPath = Path().apply {
        moveTo(points[0].x + shadowOffset.x, points[0].y + shadowOffset.y)
        for (i in 1..steps) {
            lineTo(points[i].x + shadowOffset.x, points[i].y + shadowOffset.y)
        }
    }
    drawPath(
        path = shadowPath,
        color = snakeShadow,
        style = Stroke(width = 14f, cap = StrokeCap.Round)
    )

    // 2. Thick Snake Body (Winding Back)
    val bodyPath = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1..steps) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = bodyPath,
        color = primaryColor,
        style = Stroke(width = 15f, cap = StrokeCap.Round)
    )

    // 3. Belly Stripe / Pattern along Center Body
    drawPath(
        path = bodyPath,
        color = bellyColor,
        style = Stroke(width = 6f, cap = StrokeCap.Round)
    )

    // 4. Snake Spots / Diamonds along body
    for (i in 3 until steps step 3) {
        val pt = points[i]
        drawCircle(
            color = spotColor,
            radius = 3f,
            center = pt
        )
    }

    // 5. Friendly Cartoon Snake Head at point[0]
    val headPt = points[0]
    val neckPt = points[1]
    val angle = atan2(neckPt.y - headPt.y, neckPt.x - headPt.x) + PI.toFloat()

    val headRadius = 12f
    drawCircle(
        color = primaryColor,
        radius = headRadius,
        center = headPt
    )
    drawCircle(
        color = spotColor,
        radius = headRadius,
        center = headPt,
        style = Stroke(width = 2.5f)
    )

    // Expressive Snake Eyes
    val eyeOffsetDist = 5f
    val eyePerp = angle + (PI.toFloat() / 2f)
    val eye1 = Offset(headPt.x + cos(eyePerp) * eyeOffsetDist, headPt.y + sin(eyePerp) * eyeOffsetDist)
    val eye2 = Offset(headPt.x - cos(eyePerp) * eyeOffsetDist, headPt.y - sin(eyePerp) * eyeOffsetDist)

    drawCircle(color = Color.White, radius = 4f, center = eye1)
    drawCircle(color = Color.Black, radius = 2f, center = eye1)

    drawCircle(color = Color.White, radius = 4f, center = eye2)
    drawCircle(color = Color.Black, radius = 2f, center = eye2)

    // Red Flicking Tongue
    val tongueStart = Offset(headPt.x + cos(angle) * (headRadius - 1f), headPt.y + sin(angle) * (headRadius - 1f))
    val tongueMid = Offset(headPt.x + cos(angle) * (headRadius + 8f), headPt.y + sin(angle) * (headRadius + 8f))
    drawLine(
        color = Color.Red,
        start = tongueStart,
        end = tongueMid,
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
    // Forked tongue tip
    val tip1 = Offset(tongueMid.x + cos(angle + 0.5f) * 4f, tongueMid.y + sin(angle + 0.5f) * 4f)
    val tip2 = Offset(tongueMid.x + cos(angle - 0.5f) * 4f, tongueMid.y + sin(angle - 0.5f) * 4f)
    drawLine(color = Color.Red, start = tongueMid, end = tip1, strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(color = Color.Red, start = tongueMid, end = tip2, strokeWidth = 1.8f, cap = StrokeCap.Round)
}

/**
 * Skeletal Stick-Figure Token with High-Contrast Layered Rendering.
 * Preserves the exact Chill Games stick-figure anatomy (Head, Torso, Arms, Legs),
 * and adds a subtle high-contrast outline and drop shadow beneath each stroke.
 * This guarantees the figure is 100% readable on yellow, red, green, blue, or cream tiles.
 */
private fun DrawScope.drawHighContrastStickFigure(
    center: Offset,
    pose: TokenPose,
    limbPhase: Float,
    primaryColor: Color,
    cellSize: Float
) {
    val scale = (cellSize / 38f).coerceIn(0.75f, 1.25f)
    val headRadius = 5.2f * scale
    val coreStrokeWidth = 3.2f * scale
    val outlineStrokeWidth = coreStrokeWidth + 2.4f * scale
    val shadowStrokeWidth = coreStrokeWidth + 3.2f * scale

    val cx = center.x
    val cy = center.y

    // Pedestal contrast halo beneath feet so token pops from any background tile
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = 15f * scale,
        center = Offset(cx, cy + 8f * scale)
    )

    // Compute skeletal coordinates based on current pose
    data class SkeletonModel(
        val headCenter: Offset,
        val neck: Offset,
        val pelvis: Offset,
        val leftHand: Offset,
        val rightHand: Offset,
        val leftFoot: Offset,
        val rightFoot: Offset,
        val groundShadowOffset: Offset?,
        val groundShadowRadius: Float
    )

    val skeleton = when (pose) {
        TokenPose.CELEBRATING -> {
            val jump = sin(limbPhase * PI.toFloat()).coerceAtLeast(0f) * (8f * scale)
            val headY = cy - 14f * scale - jump
            val neckY = cy - 6f * scale - jump
            val pelvisY = cy + 2f * scale - jump
            SkeletonModel(
                headCenter = Offset(cx, headY),
                neck = Offset(cx, neckY),
                pelvis = Offset(cx, pelvisY),
                leftHand = Offset(cx - 8f * scale, neckY - 9f * scale),
                rightHand = Offset(cx + 8f * scale, neckY - 9f * scale),
                leftFoot = Offset(cx - 6f * scale, cy + 9f * scale - jump),
                rightFoot = Offset(cx + 6f * scale, cy + 9f * scale - jump),
                groundShadowOffset = Offset(cx, cy + 13f * scale),
                groundShadowRadius = 5.5f * scale
            )
        }
        TokenPose.CLIMBING -> {
            val climbCycle = sin(limbPhase * PI.toFloat() * 2f)
            val headY = cy - 12f * scale
            val neckY = cy - 5f * scale
            val pelvisY = cy + 3f * scale
            val leftArmReach = neckY - 6f * scale + (climbCycle * 4f * scale)
            val rightArmReach = neckY - 6f * scale - (climbCycle * 4f * scale)
            val leftLegStep = cy + 10f * scale - (climbCycle * 4f * scale)
            val rightLegStep = cy + 10f * scale + (climbCycle * 4f * scale)
            SkeletonModel(
                headCenter = Offset(cx, headY),
                neck = Offset(cx, neckY),
                pelvis = Offset(cx, pelvisY),
                leftHand = Offset(cx - 7f * scale, leftArmReach),
                rightHand = Offset(cx + 7f * scale, rightArmReach),
                leftFoot = Offset(cx - 5f * scale, leftLegStep),
                rightFoot = Offset(cx + 5f * scale, rightLegStep),
                groundShadowOffset = null,
                groundShadowRadius = 0f
            )
        }
        TokenPose.SLIDING -> {
            val headY = cy - 9f * scale
            val neckY = cy - 2f * scale
            val pelvisY = cy + 5f * scale
            SkeletonModel(
                headCenter = Offset(cx - 3f * scale, headY),
                neck = Offset(cx - 2f * scale, neckY),
                pelvis = Offset(cx + 2f * scale, pelvisY),
                leftHand = Offset(cx - 8f * scale, neckY - 4f * scale),
                rightHand = Offset(cx + 8f * scale, neckY - 6f * scale),
                leftFoot = Offset(cx + 9f * scale, cy + 10f * scale),
                rightFoot = Offset(cx + 6f * scale, cy + 12f * scale),
                groundShadowOffset = null,
                groundShadowRadius = 0f
            )
        }
        TokenPose.HOPPING -> {
            val hopArc = sin(limbPhase * PI.toFloat()).coerceAtLeast(0f) * (6f * scale)
            val headY = cy - 12f * scale - hopArc
            val neckY = cy - 5f * scale - hopArc
            val pelvisY = cy + 3f * scale - hopArc
            SkeletonModel(
                headCenter = Offset(cx, headY),
                neck = Offset(cx, neckY),
                pelvis = Offset(cx, pelvisY),
                leftHand = Offset(cx - 6f * scale, neckY + 6f * scale),
                rightHand = Offset(cx + 6f * scale, neckY - 2f * scale),
                leftFoot = Offset(cx - 6f * scale, cy + 11f * scale - hopArc),
                rightFoot = Offset(cx + 6f * scale, cy + 8f * scale - hopArc),
                groundShadowOffset = Offset(cx, cy + 13f * scale),
                groundShadowRadius = 6.0f * scale
            )
        }
        TokenPose.IDLE -> {
            val headY = cy - 10f * scale
            val neckY = cy - 4f * scale
            val pelvisY = cy + 4f * scale
            SkeletonModel(
                headCenter = Offset(cx, headY),
                neck = Offset(cx, neckY),
                pelvis = Offset(cx, pelvisY),
                leftHand = Offset(cx - 5f * scale, neckY + 7f * scale),
                rightHand = Offset(cx + 5f * scale, neckY + 7f * scale),
                leftFoot = Offset(cx - 4f * scale, cy + 12f * scale),
                rightFoot = Offset(cx + 4f * scale, cy + 12f * scale),
                groundShadowOffset = Offset(cx, cy + 13f * scale),
                groundShadowRadius = 7.0f * scale
            )
        }
    }

    // Ground Shadow (if applicable)
    skeleton.groundShadowOffset?.let { sPos ->
        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = skeleton.groundShadowRadius,
            center = sPos
        )
    }

    fun renderBones(color: Color, sWidth: Float, offset: Offset = Offset.Zero) {
        val stroke = Stroke(width = sWidth, cap = StrokeCap.Round)
        val hCenter = skeleton.headCenter + offset
        val n = skeleton.neck + offset
        val p = skeleton.pelvis + offset
        val lh = skeleton.leftHand + offset
        val rh = skeleton.rightHand + offset
        val lf = skeleton.leftFoot + offset
        val rf = skeleton.rightFoot + offset

        // Head
        drawCircle(color = color, radius = headRadius, center = hCenter, style = stroke)
        // Torso
        drawLine(color = color, start = n, end = p, strokeWidth = sWidth, cap = StrokeCap.Round)
        // Arms
        drawLine(color = color, start = Offset(n.x, n.y + 1f), end = lh, strokeWidth = sWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(n.x, n.y + 1f), end = rh, strokeWidth = sWidth, cap = StrokeCap.Round)
        // Legs
        drawLine(color = color, start = p, end = lf, strokeWidth = sWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = p, end = rf, strokeWidth = sWidth, cap = StrokeCap.Round)
    }

    // LAYER 1: Subtle Dark Drop-Shadow Contour (offset slightly by 1.2px)
    renderBones(
        color = Color(0xFF0F172A).copy(alpha = 0.40f),
        sWidth = shadowStrokeWidth,
        offset = Offset(1.2f, 1.8f)
    )

    // LAYER 2: Crisp Pure-White Contrast Halo (ensures readability on dark red, green, and blue tiles)
    renderBones(
        color = Color(0xFFFFFFFF),
        sWidth = outlineStrokeWidth
    )

    // LAYER 3: Core Vibrant Player Color (Orange for P1, Purple for P2)
    renderBones(
        color = primaryColor,
        sWidth = coreStrokeWidth
    )

    // LAYER 4: Subtle Bright Accent Center on Head
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = 1.6f * scale,
        center = skeleton.headCenter
    )
}
