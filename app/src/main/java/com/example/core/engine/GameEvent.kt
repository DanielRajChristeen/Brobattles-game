package com.example.core.engine

import java.util.UUID

/**
 * Serializable game event sent across local controller or Bluetooth transport.
 */
sealed interface GameEvent {
    val eventId: String
    val timestamp: Long

    data class MovePlayed(
        val player: PlayerId,
        val actionType: String,
        val payload: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : GameEvent

    data class RoundStarted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : GameEvent

    data class MatchRestarted(
        val targetScore: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : GameEvent
}
