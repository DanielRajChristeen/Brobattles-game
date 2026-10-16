package com.example.core.registry

enum class GameMode(
    val title: String,
    val description: String,
    val emoji: String,
    val iconName: String
) {
    SOLO(
        title = "Play the bot",
        description = "Think you can win?",
        emoji = "🤖",
        iconName = "smart_toy"
    ),
    LOCAL_1V1(
        title = "Pass the phone",
        description = "Two players on this device",
        emoji = "👥",
        iconName = "people"
    ),
    BLUETOOTH_1V1(
        title = "Battle a friend",
        description = "Connect with nearby phone 📱📱",
        emoji = "📱📱",
        iconName = "bluetooth"
    )
}
