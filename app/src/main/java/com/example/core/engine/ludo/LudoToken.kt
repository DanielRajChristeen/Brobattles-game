package com.example.core.engine.ludo

/**
 * Represents a single Ludo token.
 *
 * Steps progression from start:
 * - step = -1: In yard (not yet entered play)
 * - step = 0: On player's starting cell
 * - step in 0..51: Navigating the 52-cell common outer track
 * - step in 52..56: Entering player's 5-cell private home stretch
 * - step = 57: Reached Home Center (Finished)
 */
data class LudoToken(
    val id: Int, // 0..3
    val color: LudoColor,
    val step: Int = -1
) {
    val isInYard: Boolean get() = step == -1
    val isOnTrack: Boolean get() = step in 0..51
    val isInHomeStretch: Boolean get() = step in 52..56
    val isFinished: Boolean get() = step >= 57

    /**
     * Track index (0..51) on the common outer track if this token is currently on it.
     */
    val trackIndex: Int?
        get() = if (isOnTrack) (color.startIndex + step) % 52 else null

    /**
     * Home stretch index (0..4) if this token is currently in the home stretch.
     */
    val homeStretchIndex: Int?
        get() = if (isInHomeStretch) step - 52 else null

    companion object {
        const val STEP_YARD = -1
        const val STEP_START = 0
        const val STEP_LAST_TRACK = 51
        const val STEP_FIRST_HOME_STRETCH = 52
        const val STEP_LAST_HOME_STRETCH = 56
        const val STEP_FINISH = 57
    }
}
