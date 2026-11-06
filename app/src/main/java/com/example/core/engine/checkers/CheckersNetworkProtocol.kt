package com.example.core.engine.checkers

import org.json.JSONObject

/**
 * Message protocol definitions and payload serialization for Checkers WiFi Play.
 * Follows the BroBattles local TCP socket wire protocol with strict host-authoritative validation.
 */
object CheckersNetworkProtocol {

    // Action types
    const val ACTION_MOVE_REQUEST = "CHECKERS_MOVE_REQUEST"
    const val ACTION_MOVE_ACCEPTED = "CHECKERS_MOVE_ACCEPTED"
    const val ACTION_MOVE_REJECTED = "CHECKERS_MOVE_REJECTED"
    const val ACTION_STATE_SYNC = "CHECKERS_STATE_SYNC"
    const val ACTION_REMATCH_REQUEST = "CHECKERS_REMATCH_REQUEST"
    const val ACTION_REMATCH_ACCEPTED = "CHECKERS_REMATCH_ACCEPTED"
    const val ACTION_REMATCH_DECLINED = "CHECKERS_REMATCH_DECLINED"
    const val ACTION_SESSION_EXIT = "CHECKERS_SESSION_EXIT"

    // Legacy action fallback
    const val ACTION_LEGACY_MOVE = "CHECKERS_MOVE"
    const val ACTION_LEGACY_SYNC = "CHECKERS_SYNC"
    const val ACTION_LEGACY_REMATCH = "CHECKERS_REMATCH"

    /**
     * Payload sent from a player to the authoritative Host requesting a move.
     */
    data class MoveRequest(
        val moveId: String,
        val seqNum: Int,
        val senderRole: String, // "PLAYER_1" or "PLAYER_2"
        val fromRow: Int,
        val fromCol: Int,
        val toRow: Int,
        val toCol: Int
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("moveId", moveId)
                put("seqNum", seqNum)
                put("senderRole", senderRole)
                put("fromRow", fromRow)
                put("fromCol", fromCol)
                put("toRow", toRow)
                put("toCol", toCol)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): MoveRequest? {
                return try {
                    val json = JSONObject(jsonStr)
                    MoveRequest(
                        moveId = json.getString("moveId"),
                        seqNum = json.getInt("seqNum"),
                        senderRole = json.getString("senderRole"),
                        fromRow = json.getInt("fromRow"),
                        fromCol = json.getInt("fromCol"),
                        toRow = json.getInt("toRow"),
                        toCol = json.getInt("toCol")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Authoritative move accepted event broadcast by Host to both devices.
     * Contains public move details and full serialized authoritative state.
     */
    data class MoveAccepted(
        val moveId: String,
        val seqNum: Int,
        val fromRow: Int,
        val fromCol: Int,
        val toRow: Int,
        val toCol: Int,
        val capturedRow: Int? = null,
        val capturedCol: Int? = null,
        val isKingPromotion: Boolean = false,
        val multiJumpAvailable: Boolean = false,
        val mustJumpRow: Int? = null,
        val mustJumpCol: Int? = null,
        val nextPlayer: String, // "PLAYER_1" or "PLAYER_2"
        val isGameOver: Boolean = false,
        val winnerRole: String? = null, // "PLAYER_1", "PLAYER_2", or "DRAW"
        val gameStateJson: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("moveId", moveId)
                put("seqNum", seqNum)
                put("fromRow", fromRow)
                put("fromCol", fromCol)
                put("toRow", toRow)
                put("toCol", toCol)
                if (capturedRow != null && capturedCol != null) {
                    put("capturedRow", capturedRow)
                    put("capturedCol", capturedCol)
                }
                put("isKingPromotion", isKingPromotion)
                put("multiJumpAvailable", multiJumpAvailable)
                if (mustJumpRow != null && mustJumpCol != null) {
                    put("mustJumpRow", mustJumpRow)
                    put("mustJumpCol", mustJumpCol)
                }
                put("nextPlayer", nextPlayer)
                put("isGameOver", isGameOver)
                if (winnerRole != null) {
                    put("winnerRole", winnerRole)
                }
                put("gameStateJson", gameStateJson)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): MoveAccepted? {
                return try {
                    val json = JSONObject(jsonStr)
                    MoveAccepted(
                        moveId = json.getString("moveId"),
                        seqNum = json.getInt("seqNum"),
                        fromRow = json.getInt("fromRow"),
                        fromCol = json.getInt("fromCol"),
                        toRow = json.getInt("toRow"),
                        toCol = json.getInt("toCol"),
                        capturedRow = if (json.has("capturedRow")) json.getInt("capturedRow") else null,
                        capturedCol = if (json.has("capturedCol")) json.getInt("capturedCol") else null,
                        isKingPromotion = json.optBoolean("isKingPromotion", false),
                        multiJumpAvailable = json.optBoolean("multiJumpAvailable", false),
                        mustJumpRow = if (json.has("mustJumpRow")) json.getInt("mustJumpRow") else null,
                        mustJumpCol = if (json.has("mustJumpCol")) json.getInt("mustJumpCol") else null,
                        nextPlayer = json.getString("nextPlayer"),
                        isGameOver = json.optBoolean("isGameOver", false),
                        winnerRole = if (json.has("winnerRole")) json.getString("winnerRole") else null,
                        gameStateJson = json.getString("gameStateJson")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Rejection notification sent by Host if a move request is invalid, stale, duplicate, or out of turn.
     */
    data class MoveRejected(
        val moveId: String,
        val seqNum: Int,
        val reason: String,
        val gameStateJson: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("moveId", moveId)
                put("seqNum", seqNum)
                put("reason", reason)
                put("gameStateJson", gameStateJson)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): MoveRejected? {
                return try {
                    val json = JSONObject(jsonStr)
                    MoveRejected(
                        moveId = json.getString("moveId"),
                        seqNum = json.getInt("seqNum"),
                        reason = json.getString("reason"),
                        gameStateJson = json.getString("gameStateJson")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Resynchronization payload containing sequence number and authoritative state.
     */
    data class StateSync(
        val seqNum: Int,
        val gameStateJson: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("seqNum", seqNum)
                put("gameStateJson", gameStateJson)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): StateSync? {
                return try {
                    val json = JSONObject(jsonStr)
                    StateSync(
                        seqNum = json.getInt("seqNum"),
                        gameStateJson = json.getString("gameStateJson")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
