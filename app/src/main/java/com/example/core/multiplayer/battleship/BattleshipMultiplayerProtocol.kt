package com.example.core.multiplayer.battleship

import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.CellCoordinate
import com.example.core.engine.battleship.ShipOrientation
import com.example.core.engine.battleship.ShipPlacement
import com.example.core.engine.battleship.ShipType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured wire protocol messages for Battleship WiFi Play over local Wi-Fi / Hotspot LAN.
 * Designed to conform with Chill Games' host-authoritative peer-to-peer networking architecture.
 */
object BattleshipMultiplayerProtocol {

    // Logical Action Types
    const val ACTION_FLEET_SUBMITTED = "BATTLESHIP_FLEET_SUBMITTED"
    const val ACTION_BATTLE_START = "BATTLESHIP_BATTLE_START"
    const val ACTION_ATTACK_REQUEST = "BATTLESHIP_ATTACK_REQUEST"
    const val ACTION_ATTACK_EVENT = "BATTLESHIP_ATTACK_EVENT"
    const val ACTION_REMATCH_REQUEST = "BATTLESHIP_REMATCH_REQUEST"
    const val ACTION_REMATCH_ACCEPTED = "BATTLESHIP_REMATCH_ACCEPTED"
    const val ACTION_STATE_RESYNC = "BATTLESHIP_STATE_RESYNC"

    /**
     * Serializes 5 deployed ship placements into a JSON string for transmission to the host.
     */
    fun serializeFleet(placements: List<ShipPlacement>): String {
        val jsonArr = JSONArray()
        for (p in placements) {
            val obj = JSONObject()
            obj.put("ship", p.shipType.name)
            obj.put("row", p.start.row)
            obj.put("col", p.start.col)
            obj.put("orientation", p.orientation.name)
            jsonArr.put(obj)
        }
        return jsonArr.toString()
    }

    /**
     * Deserializes a fleet JSON string back into a list of ship placements.
     */
    fun deserializeFleet(jsonStr: String): List<ShipPlacement>? {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ShipPlacement>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ship = ShipType.valueOf(obj.getString("ship"))
                val row = obj.getInt("row")
                val col = obj.getInt("col")
                val orientation = ShipOrientation.valueOf(obj.getString("orientation"))
                list.add(ShipPlacement(ship, CellCoordinate(row, col), orientation))
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Serializes an attack request from client to host.
     */
    fun serializeAttackRequest(
        attackId: String,
        sequenceNumber: Long,
        coordinate: CellCoordinate
    ): String {
        val obj = JSONObject()
        obj.put("attackId", attackId)
        obj.put("sequence", sequenceNumber)
        obj.put("row", coordinate.row)
        obj.put("col", coordinate.col)
        return obj.toString()
    }

    /**
     * Deserializes an attack request.
     */
    fun deserializeAttackRequest(jsonStr: String): AttackRequestPayload? {
        return try {
            val obj = JSONObject(jsonStr)
            AttackRequestPayload(
                attackId = obj.getString("attackId"),
                sequenceNumber = obj.optLong("sequence", 0L),
                coordinate = CellCoordinate(obj.getInt("row"), obj.getInt("col"))
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Serializes an authoritative attack event broadcasted by the host to both devices.
     * Note: Fleet privacy is strictly enforced. Never contains ship name or hidden coordinates!
     */
    fun serializeAttackEvent(event: BattleshipAttackEvent): String {
        val obj = JSONObject()
        obj.put("attackId", event.attackId)
        obj.put("sequence", event.sequenceNumber)
        obj.put("attacker", event.attacker.name)
        obj.put("row", event.coordinate.row)
        obj.put("col", event.coordinate.col)
        obj.put("isHit", event.isHit)
        obj.put("isSunk", event.isSunk)
        obj.put("isGameOver", event.isGameOver)
        obj.put("winner", event.winner?.name ?: "")
        obj.put("nextActivePlayer", event.nextActivePlayer.name)
        return obj.toString()
    }

    /**
     * Deserializes an authoritative attack event.
     */
    fun deserializeAttackEvent(jsonStr: String): BattleshipAttackEvent? {
        return try {
            val obj = JSONObject(jsonStr)
            val winnerStr = obj.optString("winner", "")
            val winner = if (winnerStr.isNotBlank()) BattleshipPlayer.valueOf(winnerStr) else null
            BattleshipAttackEvent(
                attackId = obj.getString("attackId"),
                sequenceNumber = obj.optLong("sequence", 0L),
                attacker = BattleshipPlayer.valueOf(obj.getString("attacker")),
                coordinate = CellCoordinate(obj.getInt("row"), obj.getInt("col")),
                isHit = obj.getBoolean("isHit"),
                isSunk = obj.getBoolean("isSunk"),
                isGameOver = obj.getBoolean("isGameOver"),
                winner = winner,
                nextActivePlayer = BattleshipPlayer.valueOf(obj.getString("nextActivePlayer"))
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Payload sent from a player to the host requesting a combat shot.
 */
data class AttackRequestPayload(
    val attackId: String,
    val sequenceNumber: Long,
    val coordinate: CellCoordinate
)

/**
 * Authoritative combat outcome event created by the host and executed in lockstep on both devices.
 */
data class BattleshipAttackEvent(
    val attackId: String,
    val sequenceNumber: Long,
    val attacker: BattleshipPlayer,
    val coordinate: CellCoordinate,
    val isHit: Boolean,
    val isSunk: Boolean,
    val isGameOver: Boolean,
    val winner: BattleshipPlayer?,
    val nextActivePlayer: BattleshipPlayer
)
