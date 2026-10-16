package com.example.core.multiplayer

import org.json.JSONObject

/**
 * Wire protocol message format for local TCP socket communications between Chill Games peers.
 */
data class MultiplayerMessage(
    val type: String,
    val sessionId: String,
    val senderId: String,
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        json.put("sessionId", sessionId)
        json.put("senderId", senderId)
        json.put("payload", payload)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        const val TYPE_JOIN_REQUEST = "JOIN_REQUEST"
        const val TYPE_JOIN_ACCEPTED = "JOIN_ACCEPTED"
        const val TYPE_JOIN_REJECTED = "JOIN_REJECTED"
        const val TYPE_PLAYER_LIST = "PLAYER_LIST"
        const val TYPE_GAME_START = "GAME_START"
        const val TYPE_PLAYER_ACTION = "PLAYER_ACTION"
        const val TYPE_STATE_SYNC = "STATE_SYNC"
        const val TYPE_GAME_RESET = "GAME_RESET"
        const val TYPE_LEAVE = "LEAVE"
        const val TYPE_HOST_CLOSED = "HOST_CLOSED"
        const val TYPE_HEARTBEAT = "HEARTBEAT"

        fun fromJson(jsonStr: String): MultiplayerMessage? {
            return try {
                val json = JSONObject(jsonStr)
                MultiplayerMessage(
                    type = json.getString("type"),
                    sessionId = json.getString("sessionId"),
                    senderId = json.getString("senderId"),
                    payload = json.optString("payload", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
