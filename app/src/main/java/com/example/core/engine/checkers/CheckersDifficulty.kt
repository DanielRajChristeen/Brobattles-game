package com.example.core.engine.checkers

enum class CheckersDifficulty(val title: String, val description: String) {
    EASY("EASY", "Casual play, quick moves"),
    MEDIUM("MEDIUM", "Balanced lookahead & strategy"),
    HARD("HARD", "Deep minimax search, sharp tactics")
}
