package com.example.core.registry

object GameRegistry {

    val XO = GameDefinition(
        id = "xo",
        title = "Tic Tac Toe",
        subtitle = "Wanna battle?",
        description = "Tic Tac Toe with 3 marks per player! Move your marks strategically across exactly 3, 5, or 10 rounds.",
        emoji = "❌⭕",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = listOf(3, 5, 10),
        defaultTargetScore = 5,
        accentHex = 0xFF8B5CF6, // Playful Purple
        secondaryAccentHex = 0xFFEC4899 // Bubblegum Pink
    )

    val TUG_OF_WAR = GameDefinition(
        id = "tug_of_war",
        title = "Tug of War",
        subtitle = "Tap like crazy!",
        description = "Tap faster than your opponent! Pull the rope over before your thumbs give out.",
        emoji = "🤼",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = listOf(1, 3, 5),
        defaultTargetScore = 1,
        accentHex = 0xFFF97316, // Warm Orange
        secondaryAccentHex = 0xFFFBBF24 // Sunny Yellow
    )

    val SNAKES_AND_LADDERS = GameDefinition(
        id = "snakes_and_ladders",
        title = "Snakes & Ladders",
        subtitle = "Roll the dice!",
        description = "Roll. Climb. Don't get bitten! The classic race filled with sudden twists.",
        emoji = "🐍🪜",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = emptyList(),
        defaultTargetScore = 1,
        accentHex = 0xFF10B981, // Emerald Green
        secondaryAccentHex = 0xFF84CC16 // Bright Lime
    )

    val CHESS = GameDefinition(
        id = "chess",
        title = "Chess",
        subtitle = "Think you can beat me?",
        description = "Your brain vs mine. Simple, swift checkmates with someone sitting next to you.",
        emoji = "♟️",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = emptyList(),
        defaultTargetScore = 1,
        accentHex = 0xFFD97706, // Warm Caramel
        secondaryAccentHex = 0xFF78350F // Coffee Wood
    )

    val ARCHERY = GameDefinition(
        id = "archery",
        title = "Archery",
        subtitle = "Draw. Aim. Release!",
        description = "Stick-figure archery duel! Hit different body zones, land headshots, and out-duel your rival.",
        emoji = "🏹",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = listOf(3, 5, 10),
        defaultTargetScore = 5,
        accentHex = 0xFF0D9488, // Archer Jade Teal
        secondaryAccentHex = 0xFFF59E0B // Golden Bow Amber
    )

    val DOT_CONNECTIONS = GameDefinition(
        id = "dot_connections",
        title = "Dot Connections",
        subtitle = "Box them in!",
        description = "Connect neighboring dots, complete squares to claim them, and take extra turns. Most boxes wins!",
        emoji = "🔲",
        statusTag = "LET'S GO! 🚀",
        isAvailable = true,
        supportedModes = listOf(GameMode.SOLO, GameMode.LOCAL_1V1, GameMode.BLUETOOTH_1V1),
        targetScoreOptions = emptyList(),
        defaultTargetScore = 1,
        accentHex = 0xFFF97316, // Playful Orange
        secondaryAccentHex = 0xFF8B5CF6 // Playful Purple
    )

    val games: List<GameDefinition> = listOf(
        XO,
        TUG_OF_WAR,
        SNAKES_AND_LADDERS,
        CHESS,
        ARCHERY,
        DOT_CONNECTIONS
    )

    fun getGameById(id: String): GameDefinition? = games.firstOrNull { it.id.equals(id, ignoreCase = true) }
}
