package com.example.core.engine.ludo

/**
 * Standard Ludo player colors and their corresponding track properties.
 * Turn order: RED -> GREEN -> YELLOW -> BLUE.
 */
enum class LudoColor(
    val displayName: String,
    val tokenEmoji: String,
    val primaryColorHex: Long,
    val lightColorHex: Long,
    val darkColorHex: Long,
    val startIndex: Int,        // Index on the 52-cell outer track
    val safeStarIndex: Int     // 8th cell after start (star safe square)
) {
    RED(
        displayName = "Red",
        tokenEmoji = "🔴",
        primaryColorHex = 0xFFE53935, // Vibrant Red
        lightColorHex = 0xFFFFCDD2,
        darkColorHex = 0xFFB71C1C,
        startIndex = 0,               // (13, 6)
        safeStarIndex = 8             // (8, 2)
    ),
    GREEN(
        displayName = "Green",
        tokenEmoji = "🟢",
        primaryColorHex = 0xFF43A047, // Vivid Green
        lightColorHex = 0xFFC8E6C9,
        darkColorHex = 0xFF1B5E20,
        startIndex = 13,              // (6, 1)
        safeStarIndex = 21            // (2, 6)
    ),
    YELLOW(
        displayName = "Yellow",
        tokenEmoji = "🟡",
        primaryColorHex = 0xFFFDD835, // Golden Yellow
        lightColorHex = 0xFFFFF9C4,
        darkColorHex = 0xFFF57F17,
        startIndex = 26,              // (1, 8)
        safeStarIndex = 34            // (6, 12)
    ),
    BLUE(
        displayName = "Blue",
        tokenEmoji = "🔵",
        primaryColorHex = 0xFF1E88E5, // Royal Blue
        lightColorHex = 0xFFBBDEFB,
        darkColorHex = 0xFF0D47A1,
        startIndex = 39,              // (8, 13)
        safeStarIndex = 47            // (12, 8)
    );

    companion object {
        fun fromName(name: String?): LudoColor? = values().firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true)
        }
    }
}
