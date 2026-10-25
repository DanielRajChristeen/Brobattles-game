package com.example.core.engine.ludo

/**
 * Coordinate mapping and board geometry for the standard 15x15 Ludo board.
 * Coordinates are represented as (row, col) from 0..14.
 */
object LudoBoardCoordinates {

    const val BOARD_GRID_SIZE = 15

    /**
     * The 52 track squares in clockwise order starting from Red's start square at (13, 6).
     */
    val TRACK_COORDINATES: List<Pair<Int, Int>> = listOf(
        Pair(13, 6),  // 0: RED START
        Pair(12, 6),  // 1
        Pair(11, 6),  // 2
        Pair(10, 6),  // 3
        Pair(9, 6),   // 4
        Pair(8, 5),   // 5
        Pair(8, 4),   // 6
        Pair(8, 3),   // 7
        Pair(8, 2),   // 8: STAR SAFE SQUARE
        Pair(8, 1),   // 9
        Pair(8, 0),   // 10
        Pair(7, 0),   // 11
        Pair(6, 0),   // 12
        Pair(6, 1),   // 13: GREEN START
        Pair(6, 2),   // 14
        Pair(6, 3),   // 15
        Pair(6, 4),   // 16
        Pair(6, 5),   // 17
        Pair(5, 6),   // 18
        Pair(4, 6),   // 19
        Pair(3, 6),   // 20
        Pair(2, 6),   // 21: STAR SAFE SQUARE
        Pair(1, 6),   // 22
        Pair(0, 6),   // 23
        Pair(0, 7),   // 24
        Pair(0, 8),   // 25
        Pair(1, 8),   // 26: YELLOW START
        Pair(2, 8),   // 27
        Pair(3, 8),   // 28
        Pair(4, 8),   // 29
        Pair(5, 8),   // 30
        Pair(6, 9),   // 31
        Pair(6, 10),  // 32
        Pair(6, 11),  // 33
        Pair(6, 12),  // 34: STAR SAFE SQUARE
        Pair(6, 13),  // 35
        Pair(6, 14),  // 36
        Pair(7, 14),  // 37
        Pair(8, 14),  // 38
        Pair(8, 13),  // 39: BLUE START
        Pair(8, 12),  // 40
        Pair(8, 11),  // 41
        Pair(8, 10),  // 42
        Pair(8, 9),   // 43
        Pair(9, 8),   // 44
        Pair(10, 8),  // 45
        Pair(11, 8),  // 46
        Pair(12, 8),  // 47: STAR SAFE SQUARE
        Pair(13, 8),  // 48
        Pair(14, 8),  // 49
        Pair(14, 7),  // 50
        Pair(14, 6)   // 51
    )

    /**
     * Home stretch coordinates for each color (5 squares each leading to center).
     */
    val RED_HOME_STRETCH = listOf(
        Pair(13, 7), Pair(12, 7), Pair(11, 7), Pair(10, 7), Pair(9, 7)
    )

    val GREEN_HOME_STRETCH = listOf(
        Pair(7, 1), Pair(7, 2), Pair(7, 3), Pair(7, 4), Pair(7, 5)
    )

    val YELLOW_HOME_STRETCH = listOf(
        Pair(1, 7), Pair(2, 7), Pair(3, 7), Pair(4, 7), Pair(5, 7)
    )

    val BLUE_HOME_STRETCH = listOf(
        Pair(7, 13), Pair(7, 12), Pair(7, 11), Pair(7, 10), Pair(7, 9)
    )

    fun getHomeStretch(color: LudoColor): List<Pair<Int, Int>> = when (color) {
        LudoColor.RED -> RED_HOME_STRETCH
        LudoColor.GREEN -> GREEN_HOME_STRETCH
        LudoColor.YELLOW -> YELLOW_HOME_STRETCH
        LudoColor.BLUE -> BLUE_HOME_STRETCH
    }

    /**
     * 4 Home finish center coordinates (inside 3x3 finish triangle).
     */
    fun getFinishCenter(color: LudoColor): Pair<Float, Float> = when (color) {
        LudoColor.RED -> Pair(8.0f, 7.0f)
        LudoColor.GREEN -> Pair(7.0f, 6.0f)
        LudoColor.YELLOW -> Pair(6.0f, 7.0f)
        LudoColor.BLUE -> Pair(7.0f, 8.0f)
    }

    /**
     * 4 Yard base circle centers for each color.
     */
    fun getYardPositions(color: LudoColor): List<Pair<Float, Float>> = when (color) {
        LudoColor.GREEN -> listOf(Pair(1.7f, 1.7f), Pair(1.7f, 3.7f), Pair(3.7f, 1.7f), Pair(3.7f, 3.7f))
        LudoColor.YELLOW -> listOf(Pair(1.7f, 10.7f), Pair(1.7f, 12.7f), Pair(3.7f, 10.7f), Pair(3.7f, 12.7f))
        LudoColor.RED -> listOf(Pair(10.7f, 1.7f), Pair(10.7f, 3.7f), Pair(12.7f, 1.7f), Pair(12.7f, 3.7f))
        LudoColor.BLUE -> listOf(Pair(10.7f, 10.7f), Pair(10.7f, 12.7f), Pair(12.7f, 10.7f), Pair(12.7f, 12.7f))
    }

    /**
     * Set of 8 safe square indices on the outer track.
     * Contains the 4 player starting squares (0, 13, 26, 39) and the 4 star safe squares (8, 21, 34, 47).
     */
    val SAFE_TRACK_INDICES = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /**
     * Only the 4 star safe squares.
     */
    val STAR_SAFE_INDICES = setOf(8, 21, 34, 47)

    fun isSafeSquare(trackIndex: Int): Boolean = trackIndex in SAFE_TRACK_INDICES

    /**
     * Calculates the (row, col) grid coordinate for a token.
     */
    fun getTokenCoordinate(token: LudoToken): Pair<Float, Float> {
        return when {
            token.isInYard -> getYardPositions(token.color)[token.id]
            token.isOnTrack -> {
                val idx = (token.color.startIndex + token.step) % 52
                val (r, c) = TRACK_COORDINATES[idx]
                Pair(r.toFloat(), c.toFloat())
            }
            token.isInHomeStretch -> {
                val stretchIdx = token.step - 52
                val (r, c) = getHomeStretch(token.color)[stretchIdx]
                Pair(r.toFloat(), c.toFloat())
            }
            token.isFinished -> getFinishCenter(token.color)
            else -> getYardPositions(token.color)[token.id]
        }
    }
}
