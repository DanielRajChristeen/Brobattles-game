package com.example.core.engine.dotconnections

enum class DotPlayer(
    val displayName: String,
    val colorHex: Long
) {
    PLAYER_1("PLAYER 1", 0xFFF97316), // Playful Orange
    PLAYER_2("PLAYER 2", 0xFF8B5CF6); // Playful Purple

    fun opponent(): DotPlayer = if (this == PLAYER_1) PLAYER_2 else PLAYER_1
}
