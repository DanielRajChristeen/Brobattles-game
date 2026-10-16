package com.example.core.multiplayer

/**
 * Information describing a game session hosted on the local Wi-Fi network.
 */
data class DiscoveredSession(
    val sessionId: String,
    val hostName: String,
    val gameId: String,
    val gameTitle: String,
    val hostIp: String,
    val port: Int,
    val currentPlayers: Int = 1,
    val maxPlayers: Int = 2
)

/**
 * A participant in a local multiplayer session.
 */
data class MultiplayerPlayer(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val role: String = "", // e.g., "WHITE", "BLACK", "PLAYER_1", "PLAYER_2"
    val isReady: Boolean = true
)

/**
 * Discrete states of the local multiplayer lobby lifecycle.
 */
sealed class MultiplayerLobbyState {
    object Idle : MultiplayerLobbyState()
    data class Hosting(
        val session: DiscoveredSession,
        val players: List<MultiplayerPlayer>
    ) : MultiplayerLobbyState()
    data class Discovering(
        val discoveredSessions: List<DiscoveredSession>
    ) : MultiplayerLobbyState()
    data class Connecting(
        val session: DiscoveredSession
    ) : MultiplayerLobbyState()
    data class Connected(
        val session: DiscoveredSession,
        val localPlayer: MultiplayerPlayer,
        val players: List<MultiplayerPlayer>,
        val isHost: Boolean
    ) : MultiplayerLobbyState()
    data class InGame(
        val session: DiscoveredSession,
        val localPlayer: MultiplayerPlayer,
        val players: List<MultiplayerPlayer>,
        val isHost: Boolean
    ) : MultiplayerLobbyState()
    data class Disconnected(
        val reason: String
    ) : MultiplayerLobbyState()
}

/**
 * Generic action payload sent from a client to the authoritative host.
 */
data class PlayerAction(
    val senderId: String,
    val actionType: String,
    val payload: String
)

/**
 * Event triggered when the host launches the authoritative game session.
 */
data class GameStartInfo(
    val gameId: String,
    val localRole: String,
    val players: List<MultiplayerPlayer>,
    val isHost: Boolean,
    val configPayload: String = ""
)
