package com.example.core.registry

data class GameDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val emoji: String,
    val statusTag: String,
    val isAvailable: Boolean,
    val supportedModes: List<GameMode> = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
    val targetScoreOptions: List<Int> = listOf(3, 5, 10),
    val defaultTargetScore: Int = 5,
    val accentHex: Long = 0xFF8B5CF6,
    val secondaryAccentHex: Long = 0xFFEC4899,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 2
)
