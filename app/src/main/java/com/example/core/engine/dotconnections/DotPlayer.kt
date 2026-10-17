package com.example.core.engine.dotconnections

import androidx.compose.ui.graphics.Color

enum class DotPlayer(
    val displayName: String,
    val colorHex: Long,
    val emoji: String,
    val symbol: String
) {
    PLAYER_1("Player 1", 0xFFEF4444L, "🔴", "P1"), // 🔴 Red
    PLAYER_2("Player 2", 0xFFA855F7L, "🟣", "P2"), // 🟣 Purple
    PLAYER_3("Player 3", 0xFF3B82F6L, "🔵", "P3"), // 🔵 Blue
    PLAYER_4("Player 4", 0xFF8D6E63L, "🟤", "P4"), // 🟤 Brown
    PLAYER_5("Player 5", 0xFF22C55EL, "🟢", "P5"); // 🟢 Green

    val composeColor: Color get() = Color(colorHex)

    val colorName: String
        get() = when (this) {
            PLAYER_1 -> "Red"
            PLAYER_2 -> "Purple"
            PLAYER_3 -> "Blue"
            PLAYER_4 -> "Brown"
            PLAYER_5 -> "Green"
        }

    fun opponent(): DotPlayer = if (this == PLAYER_1) PLAYER_2 else PLAYER_1

    fun next(playerCount: Int): DotPlayer {
        val count = playerCount.coerceIn(2, 5)
        val nextIndex = (this.ordinal + 1) % count
        return entries[nextIndex]
    }

    companion object {
        val COLOR_NAMES = listOf("Red", "Purple", "Blue", "Brown", "Green")
        val COLORS = listOf(
            Color(0xFFEF4444), // 🔴 Player 1: Red
            Color(0xFFA855F7), // 🟣 Player 2: Purple
            Color(0xFF3B82F6), // 🔵 Player 3: Blue
            Color(0xFF8D6E63), // 🟤 Player 4: Brown
            Color(0xFF22C55E)  // 🟢 Player 5: Green
        )

        val EMOJIS = listOf("🔴", "🟣", "🔵", "🟤", "🟢")
        val NAMES = listOf("Player 1", "Player 2", "Player 3", "Player 4", "Player 5")

        fun fromOrdinal(ordinal: Int): DotPlayer =
            entries.getOrElse(ordinal.coerceIn(0, entries.size - 1)) { PLAYER_1 }

        fun getColorName(index: Int): String = COLOR_NAMES.getOrElse(index % COLOR_NAMES.size) { "Unknown" }

        fun activePlayers(count: Int): List<DotPlayer> =
            entries.take(count.coerceIn(2, 5))

        fun getColor(player: DotPlayer): Color = player.composeColor
        fun getColor(index: Int): Color = COLORS.getOrElse(index % COLORS.size) { Color(0xFFEF4444) }
        fun getEmoji(player: DotPlayer): String = player.emoji
        fun getEmoji(index: Int): String = EMOJIS.getOrElse(index % EMOJIS.size) { "⚪" }
        fun getName(player: DotPlayer): String = player.displayName
        fun getName(index: Int): String = NAMES.getOrElse(index % NAMES.size) { "Player ${index + 1}" }
    }
}
