package com.example.core.bluetooth

import org.json.JSONObject

/**
 * Lightweight frame format for direct peer-to-peer RFCOMM transport over Bluetooth.
 */
data class BluetoothMessage(
    val type: String,
    val gameId: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        json.put("gameId", gameId)
        json.put("payload", payload)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        const val TYPE_HANDSHAKE_HOST = "HANDSHAKE_HOST"
        const val TYPE_HANDSHAKE_CLIENT = "HANDSHAKE_CLIENT"
        const val TYPE_GAME_EVENT = "GAME_EVENT"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
        const val TYPE_RESTART = "RESTART"
        const val TYPE_DISCONNECT = "DISCONNECT"

        fun fromJson(jsonStr: String): BluetoothMessage? {
            return try {
                val json = JSONObject(jsonStr)
                BluetoothMessage(
                    type = json.getString("type"),
                    gameId = json.getString("gameId"),
                    payload = json.optString("payload", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
