package com.example.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val GAME_DETAIL = "game_detail/{gameId}"
    const val SETTINGS = "settings"
    const val STATS = "stats"

    fun gameDetail(gameId: String) = "game_detail/$gameId"
}
