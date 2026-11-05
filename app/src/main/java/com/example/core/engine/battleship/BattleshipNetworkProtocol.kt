package com.example.core.engine.battleship

import org.json.JSONArray
import org.json.JSONObject

/**
 * Message protocol definitions and payload serialization for Battleship WiFi Play.
 * Follows the BroBattles local TCP socket wire protocol.
 */
object BattleshipNetworkProtocol {

    // Action types
    const val ACTION_FLEET_SUBMITTED = "BATTLESHIP_FLEET_SUBMITTED"
    const val ACTION_BATTLE_START = "BATTLESHIP_BATTLE_START"
    const val ACTION_ATTACK_REQUEST = "BATTLESHIP_ATTACK_REQUEST"
    const val ACTION_ATTACK_EVENT = "BATTLESHIP_ATTACK_EVENT"
    const val ACTION_REMATCH_REQUEST = "BATTLESHIP_REMATCH_REQUEST"
    const val ACTION_REMATCH_ACCEPTED = "BATTLESHIP_REMATCH_ACCEPTED"
    const val ACTION_STATE_RESYNC = "BATTLESHIP_STATE_RESYNC"

    /**
     * Serializes a player's placed fleet into JSON format for transmission to the authoritative host.
     */
    fun serializeFleet(placements: List<ShipPlacement>): String {
        val array = JSONArray()
        placements.forEach { p ->
            val obj = JSONObject().apply {
                put("type", p.shipType.name)
                put("r", p.start.row)
                put("c", p.start.col)
                put("o", p.orientation.name)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Deserializes a fleet JSON string into a list of ShipPlacement instances.
     */
    fun deserializeFleet(jsonStr: String): List<ShipPlacement>? {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ShipPlacement>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val shipType = ShipType.valueOf(obj.getString("type"))
                val row = obj.getInt("r")
                val col = obj.getInt("c")
                val orientation = ShipOrientation.valueOf(obj.getString("o"))
                list.add(ShipPlacement(shipType, CellCoordinate(row, col), orientation))
            }
            if (list.size == ShipType.ALL_SHIPS.size) list else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Payload sent from Client to Host when requesting an attack at a grid coordinate.
     */
    data class AttackRequest(
        val attackId: String,
        val row: Int,
        val col: Int,
        val senderRole: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("attackId", attackId)
                put("row", row)
                put("col", col)
                put("senderRole", senderRole)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): AttackRequest? {
                return try {
                    val json = JSONObject(jsonStr)
                    AttackRequest(
                        attackId = json.getString("attackId"),
                        row = json.getInt("row"),
                        col = json.getInt("col"),
                        senderRole = json.getString("senderRole")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Authoritative attack outcome event broadcast by the Host to both players.
     * Contains only public attack information: coordinate, hit/miss, whether a ship was sunk
     * (without revealing ship name or hidden fleet positions), next turn, and game outcome.
     */
    data class AuthoritativeAttackEvent(
        val attackId: String,
        val attackerRole: String, // "PLAYER_1" (Host) or "PLAYER_2" (Client)
        val row: Int,
        val col: Int,
        val isHit: Boolean,
        val isSunk: Boolean,
        val nextTurnRole: String, // "PLAYER_1" or "PLAYER_2"
        val isGameOver: Boolean,
        val winnerRole: String? = null // "PLAYER_1" or "PLAYER_2" or null
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("attackId", attackId)
                put("attackerRole", attackerRole)
                put("row", row)
                put("col", col)
                put("isHit", isHit)
                put("isSunk", isSunk)
                put("nextTurnRole", nextTurnRole)
                put("isGameOver", isGameOver)
                if (winnerRole != null) put("winnerRole", winnerRole)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): AuthoritativeAttackEvent? {
                return try {
                    val json = JSONObject(jsonStr)
                    AuthoritativeAttackEvent(
                        attackId = json.getString("attackId"),
                        attackerRole = json.getString("attackerRole"),
                        row = json.getInt("row"),
                        col = json.getInt("col"),
                        isHit = json.getBoolean("isHit"),
                        isSunk = json.getBoolean("isSunk"),
                        nextTurnRole = json.getString("nextTurnRole"),
                        isGameOver = json.getBoolean("isGameOver"),
                        winnerRole = if (json.has("winnerRole")) json.getString("winnerRole") else null
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
