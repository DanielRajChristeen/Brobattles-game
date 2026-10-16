package com.example.core.engine.snakesandladders

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

data class Ladder(
    val bottom: Int,
    val top: Int
)

data class Snake(
    val head: Int,
    val tail: Int,
    val primaryColor: Long,
    val bellyColor: Long,
    val spotColor: Long,
    val waveIntensity: Float = 0.65f, // curvature strength
    val flipWave: Boolean = false     // invert wave side
)

object SnakesAndLaddersBoard {
    const val TOTAL_SQUARES = 100
    const val GRID_SIZE = 10

    // Sturdy physical ladders connecting bottom to top
    val LADDERS = listOf(
        Ladder(bottom = 4, top = 14),
        Ladder(bottom = 9, top = 31),
        Ladder(bottom = 20, top = 38),
        Ladder(bottom = 28, top = 84),
        Ladder(bottom = 40, top = 59),
        Ladder(bottom = 51, top = 67),
        Ladder(bottom = 63, top = 81),
        Ladder(bottom = 71, top = 91)
    )

    // Distinct colorful snakes inspired by classic board game artwork
    val SNAKES = listOf(
        // Golden / Blue banded snake at bottom
        Snake(
            head = 17,
            tail = 7,
            primaryColor = 0xFF0284C7, // Blue
            bellyColor = 0xFFFBBF24,   // Golden yellow
            spotColor = 0xFF0369A1,
            waveIntensity = 0.6f,
            flipWave = false
        ),
        // Coral red viper in lower-middle
        Snake(
            head = 54,
            tail = 34,
            primaryColor = 0xFFDC2626, // Red
            bellyColor = 0xFFFEF08A,   // Soft yellow
            spotColor = 0xFFB91C1C,
            waveIntensity = 0.7f,
            flipWave = true
        ),
        // Large emerald green python
        Snake(
            head = 62,
            tail = 19,
            primaryColor = 0xFF16A34A, // Green
            bellyColor = 0xFF86EFAC,   // Mint
            spotColor = 0xFF15803D,
            waveIntensity = 0.8f,
            flipWave = false
        ),
        // Playful short snake
        Snake(
            head = 64,
            tail = 60,
            primaryColor = 0xFFD97706, // Amber
            bellyColor = 0xFFFEF3C7,
            spotColor = 0xFFB45309,
            waveIntensity = 0.5f,
            flipWave = true
        ),
        // Long sky-blue & white diamond snake
        Snake(
            head = 87,
            tail = 24,
            primaryColor = 0xFF2563EB, // Royal Blue
            bellyColor = 0xFFE0F2FE,   // Ice White
            spotColor = 0xFF1D4ED8,
            waveIntensity = 0.9f,
            flipWave = false
        ),
        // Spotted golden snake
        Snake(
            head = 93,
            tail = 73,
            primaryColor = 0xFFEAB308, // Gold
            bellyColor = 0xFFFEF9C3,
            spotColor = 0xFFCA8A04,
            waveIntensity = 0.7f,
            flipWave = true
        ),
        // Crimson striped snake
        Snake(
            head = 95,
            tail = 75,
            primaryColor = 0xFFE11D48, // Rose Red
            bellyColor = 0xFFFDE047,   // Bright Yellow
            spotColor = 0xFFBE123C,
            waveIntensity = 0.75f,
            flipWave = false
        ),
        // Emerald master serpent at top
        Snake(
            head = 99,
            tail = 78,
            primaryColor = 0xFF059669, // Forest Emerald
            bellyColor = 0xFFA7F3D0,
            spotColor = 0xFF047857,
            waveIntensity = 0.65f,
            flipWave = true
        )
    )

    val LADDER_MAP: Map<Int, Int> = LADDERS.associate { it.bottom to it.top }
    val SNAKE_MAP: Map<Int, Int> = SNAKES.associate { it.head to it.tail }

    /**
     * Converts a 1-based square (1..100) to grid column (0..9, from left).
     */
    fun getCol(square: Int): Int {
        val s = square.coerceIn(1, TOTAL_SQUARES)
        val idx = s - 1
        val rowFromBottom = idx / GRID_SIZE
        return if (rowFromBottom % 2 == 0) {
            idx % GRID_SIZE
        } else {
            GRID_SIZE - 1 - (idx % GRID_SIZE)
        }
    }

    /**
     * Converts a 1-based square (1..100) to grid row (0..9, from top).
     */
    fun getRow(square: Int): Int {
        val s = square.coerceIn(1, TOTAL_SQUARES)
        val idx = s - 1
        val rowFromBottom = idx / GRID_SIZE
        return (GRID_SIZE - 1) - rowFromBottom
    }

    /**
     * Calculates the center pixel coordinate of a square given cellSize.
     */
    fun getSquareCenter(square: Int, cellSize: Float): Offset {
        val col = getCol(square)
        val row = getRow(square)
        return Offset(col * cellSize + cellSize / 2f, row * cellSize + cellSize / 2f)
    }

    /**
     * Interpolates along the straight geometric path of a ladder from bottom (t=0) to top (t=1).
     */
    fun getLadderPoint(ladder: Ladder, t: Float, cellSize: Float): Offset {
        val start = getSquareCenter(ladder.bottom, cellSize)
        val end = getSquareCenter(ladder.top, cellSize)
        val clampedT = t.coerceIn(0f, 1f)
        return Offset(
            start.x + (end.x - start.x) * clampedT,
            start.y + (end.y - start.y) * clampedT
        )
    }

    /**
     * Evaluates a smooth cubic Bezier curve point along a snake from head (t=0) to tail (t=1).
     */
    fun getSnakePoint(snake: Snake, t: Float, cellSize: Float, wigglePhase: Float = 0f): Offset {
        val head = getSquareCenter(snake.head, cellSize)
        val tail = getSquareCenter(snake.tail, cellSize)

        val dx = tail.x - head.x
        val dy = tail.y - head.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val sign = if (snake.flipWave) -1f else 1f
        val nx = (-dy / dist) * sign
        val ny = (dx / dist) * sign

        val wave = cellSize * snake.waveIntensity
        val wiggle = if (wigglePhase != 0f) kotlin.math.sin(wigglePhase) * (cellSize * 0.15f) else 0f

        val cp1 = Offset(head.x + dx * 0.33f + nx * (wave + wiggle), head.y + dy * 0.33f + ny * (wave + wiggle))
        val cp2 = Offset(head.x + dx * 0.66f - nx * (wave - wiggle), head.y + dy * 0.66f - ny * (wave - wiggle))

        val u = 1f - t.coerceIn(0f, 1f)
        val tt = t.coerceIn(0f, 1f)

        val x = u * u * u * head.x + 3 * u * u * tt * cp1.x + 3 * u * tt * tt * cp2.x + tt * tt * tt * tail.x
        val y = u * u * u * head.y + 3 * u * u * tt * cp1.y + 3 * u * tt * tt * cp2.y + tt * tt * tt * tail.y

        return Offset(x, y)
    }
}
