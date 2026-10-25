package com.example.core.engine.ludo

import com.example.core.registry.GameMode
import org.json.JSONArray
import org.json.JSONObject

data class LudoPlayerState(
    val color: LudoColor,
    val name: String,
    val isBot: Boolean = false,
    val tokens: List<LudoToken> = (0..3).map { LudoToken(it, color) }
) {
    val finishedCount: Int get() = tokens.count { it.isFinished }
    val isWinner: Boolean get() = finishedCount == 4
}

data class LudoGameState(
    val mode: GameMode,
    val players: List<LudoPlayerState>,
    val currentTurnIndex: Int = 0,
    val diceRoll: Int? = null,
    val canRoll: Boolean = true,
    val selectableTokenIds: List<Int> = emptyList(),
    val statusText: String = "Roll the dice!",
    val winner: LudoColor? = null,
    val lastMoveDescription: String? = null,
    val extraTurn: Boolean = false,
    val lastCapturedColor: LudoColor? = null,
    val seqNum: Int = 0
) {
    val currentPlayer: LudoPlayerState get() = players[currentTurnIndex]
    val isGameOver: Boolean get() = winner != null
}

fun serializeLudoState(state: LudoGameState): String {
    val json = JSONObject()
    json.put("mode", state.mode.name)
    json.put("turn", state.currentTurnIndex)
    if (state.diceRoll != null) json.put("dice", state.diceRoll)
    json.put("canRoll", state.canRoll)
    json.put("status", state.statusText)
    if (state.winner != null) json.put("winner", state.winner.name)
    if (state.lastMoveDescription != null) json.put("lastMove", state.lastMoveDescription)
    json.put("extraTurn", state.extraTurn)
    if (state.lastCapturedColor != null) json.put("captured", state.lastCapturedColor.name)
    json.put("seqNum", state.seqNum)

    val selectables = JSONArray()
    state.selectableTokenIds.forEach { selectables.put(it) }
    json.put("selectable", selectables)

    val playersArray = JSONArray()
    state.players.forEach { p ->
        val pj = JSONObject()
        pj.put("color", p.color.name)
        pj.put("name", p.name)
        pj.put("isBot", p.isBot)
        val tokensArray = JSONArray()
        p.tokens.forEach { t ->
            val tj = JSONObject()
            tj.put("id", t.id)
            tj.put("step", t.step)
            tokensArray.put(tj)
        }
        pj.put("tokens", tokensArray)
        playersArray.put(pj)
    }
    json.put("players", playersArray)

    return json.toString()
}

fun deserializeLudoState(jsonString: String): LudoGameState? {
    return try {
        val json = JSONObject(jsonString)
        val mode = GameMode.valueOf(json.optString("mode", GameMode.SOLO.name))
        val turn = json.optInt("turn", 0)
        val dice = if (json.has("dice")) json.getInt("dice") else null
        val canRoll = json.optBoolean("canRoll", true)
        val status = json.optString("status", "Roll the dice!")
        val winner = if (json.has("winner")) LudoColor.fromName(json.getString("winner")) else null
        val lastMove = if (json.has("lastMove")) json.getString("lastMove") else null
        val extraTurn = json.optBoolean("extraTurn", false)
        val captured = if (json.has("captured")) LudoColor.fromName(json.getString("captured")) else null
        val seqNum = json.optInt("seqNum", 0)

        val selectableList = mutableListOf<Int>()
        val selectablesJson = json.optJSONArray("selectable")
        if (selectablesJson != null) {
            for (i in 0 until selectablesJson.length()) {
                selectableList.add(selectablesJson.getInt(i))
            }
        }

        val playersList = mutableListOf<LudoPlayerState>()
        val playersArray = json.optJSONArray("players")
        if (playersArray != null) {
            for (i in 0 until playersArray.length()) {
                val pj = playersArray.getJSONObject(i)
                val color = LudoColor.valueOf(pj.getString("color"))
                val name = pj.getString("name")
                val isBot = pj.optBoolean("isBot", false)

                val tokenList = mutableListOf<LudoToken>()
                val tokensArray = pj.optJSONArray("tokens")
                if (tokensArray != null) {
                    for (j in 0 until tokensArray.length()) {
                        val tj = tokensArray.getJSONObject(j)
                        val id = tj.getInt("id")
                        val step = tj.getInt("step")
                        tokenList.add(LudoToken(id, color, step))
                    }
                }
                playersList.add(LudoPlayerState(color, name, isBot, tokenList))
            }
        }

        LudoGameState(
            mode = mode,
            players = playersList,
            currentTurnIndex = turn,
            diceRoll = dice,
            canRoll = canRoll,
            selectableTokenIds = selectableList,
            statusText = status,
            winner = winner,
            lastMoveDescription = lastMove,
            extraTurn = extraTurn,
            lastCapturedColor = captured,
            seqNum = seqNum
        )
    } catch (_: Exception) {
        null
    }
}
