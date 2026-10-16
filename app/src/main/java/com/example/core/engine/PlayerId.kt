package com.example.core.engine

enum class PlayerId {
    PLAYER_1,
    PLAYER_2,
    PLAYER_3,
    PLAYER_4,
    PLAYER_5;

    fun other(): PlayerId = if (this == PLAYER_1) PLAYER_2 else PLAYER_1

    val displayName: String
        get() = when (this) {
            PLAYER_1 -> "Player 1"
            PLAYER_2 -> "Player 2"
            PLAYER_3 -> "Player 3"
            PLAYER_4 -> "Player 4"
            PLAYER_5 -> "Player 5"
        }
}
