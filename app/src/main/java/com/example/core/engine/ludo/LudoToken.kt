package com.example.core.engine.ludo

/**
 * Represents a single Ludo soldier (pawn).
 *
 * Steps progression from start:
 * - step = -1: In yard (not yet entered play)
 * - step = 0: On player's starting cell
 * - step in 0..50: Navigating the 51-cell common outer track up to the home-lane entry point
 * - step in 51..55: Entering player's 5-cell private home stretch
 * - step = 56: Reached Home Center (Finished)
 */
data class LudoToken(
    val id: Int, // 0..3
    val color: LudoColor,
    val step: Int = -1
) {
    val isInYard: Boolean get() = step == -1
    val isOnTrack: Boolean get() = step in 0..50
    val isInHomeStretch: Boolean get() = step in 51..55
    val isFinished: Boolean get() = step >= 56

    /**
     * Track index (0..51) on the common outer track if this soldier is currently on it.
     */
    val trackIndex: Int?
        get() = if (isOnTrack) (color.startIndex + step) % 52 else null

    /**
     * Home stretch index (0..4) if this soldier is currently in the home stretch.
     */
    val homeStretchIndex: Int?
        get() = if (isInHomeStretch) step - 51 else null

    companion object {
        const val STEP_YARD = -1
        const val STEP_START = 0
        const val STEP_LAST_TRACK = 50 // Exactly 50 steps from start (step 0 to 50 = 51 squares on track)
        const val STEP_FIRST_HOME_STRETCH = 51 // 1st home stretch cell
        const val STEP_LAST_HOME_STRETCH = 55  // 5th home stretch cell
        const val STEP_FINISH = 56             // Home Center
    }
}
