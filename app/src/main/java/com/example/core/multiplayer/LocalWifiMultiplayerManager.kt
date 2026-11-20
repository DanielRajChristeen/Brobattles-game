package com.example.core.multiplayer

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Authoritative local Wi-Fi / hotspot session manager for Chill Games.
 * Facilitates zero-configuration peer-to-peer play over local Wi-Fi LANs without
 * Internet, cloud accounts, or third-party servers.
 */
class LocalWifiMultiplayerManager(
    private val context: Context,
    private val scope: CoroutineScope,
    playerIdOverride: String? = null
) {
    private val discovery = WifiLanDiscovery(context)

    private val _lobbyState = MutableStateFlow<MultiplayerLobbyState>(MultiplayerLobbyState.Idle)
    val lobbyState: StateFlow<MultiplayerLobbyState> = _lobbyState.asStateFlow()

    private val _incomingActions = MutableSharedFlow<PlayerAction>(extraBufferCapacity = 64)
    val incomingActions: SharedFlow<PlayerAction> = _incomingActions.asSharedFlow()

    private val _gameStartEvent = MutableSharedFlow<GameStartInfo>(extraBufferCapacity = 4)
    val gameStartEvent: SharedFlow<GameStartInfo> = _gameStartEvent.asSharedFlow()

    // Chess color selection for Nearby multiplayer (default: White for host)
    private val _chessHostColor = MutableStateFlow<String>("WHITE")
    val chessHostColor: StateFlow<String> = _chessHostColor.asStateFlow()

    fun setChessHostColor(color: String) {
        val normalized = if (color.equals("BLACK", ignoreCase = true)) "BLACK" else "WHITE"
        _chessHostColor.value = normalized
        val currentState = _lobbyState.value
        if (currentState is MultiplayerLobbyState.Connected && currentState.isHost) {
            sendAction("CHESS_COLOR_ASSIGNMENT", normalized)
        }
    }

    // Reconnection & Pause grace period (e.g. 10s countdown on screen lock or brief network drop)
    private val _isConnectionPaused = MutableStateFlow<Boolean>(false)
    val isConnectionPaused: StateFlow<Boolean> = _isConnectionPaused.asStateFlow()

    private val _pauseRemainingSeconds = MutableStateFlow<Int?>(null)
    val pauseRemainingSeconds: StateFlow<Int?> = _pauseRemainingSeconds.asStateFlow()

    private val _pauseReason = MutableStateFlow<String>("")
    val pauseReason: StateFlow<String> = _pauseReason.asStateFlow()

    // Host live config preview for connected guests before game start
    private val _hostConfigPreview = MutableStateFlow<String>("")
    val hostConfigPreview: StateFlow<String> = _hostConfigPreview.asStateFlow()

    // Role & Rejoin state flows
    private val _isSessionHost = MutableStateFlow(false)
    val isSessionHost: StateFlow<Boolean> = _isSessionHost.asStateFlow()

    private val _pendingRejoinRequests = MutableStateFlow<List<RejoinRequest>>(emptyList())
    val pendingRejoinRequests: StateFlow<List<RejoinRequest>> = _pendingRejoinRequests.asStateFlow()

    private val _rejoinRequestStatus = MutableStateFlow<RejoinRequestStatus>(RejoinRequestStatus.Idle)
    val rejoinRequestStatus: StateFlow<RejoinRequestStatus> = _rejoinRequestStatus.asStateFlow()

    private val _lastMatchSession = MutableStateFlow<DiscoveredSession?>(null)
    val lastMatchSession: StateFlow<DiscoveredSession?> = _lastMatchSession.asStateFlow()

    private val _pendingRejoinStateSync = MutableStateFlow<Pair<String, String>?>(null)
    val pendingRejoinStateSync: StateFlow<Pair<String, String>?> = _pendingRejoinStateSync.asStateFlow()

    @Volatile
    private var hostStateSnapshotProvider: (() -> Pair<String, String>?)? = null

    private var graceJob: Job? = null
    private var lastInGameSession: DiscoveredSession? = null
    private var lastPlayerName: String = ""

    private data class ClientConnection(
        val playerId: String,
        val socket: Socket,
        val writer: BufferedWriter
    )

    private data class PendingRejoinEntry(
        val request: RejoinRequest,
        val player: MultiplayerPlayer,
        val socket: Socket,
        val writer: BufferedWriter
    )

    private val connectionLock = Any()
    private val activeClientConnections = LinkedHashMap<String, ClientConnection>()
    private val pendingRejoinEntries = LinkedHashMap<String, PendingRejoinEntry>()

    @Volatile
    private var activeMatchSession: DiscoveredSession? = null
    @Volatile
    private var activeMatchHostPlayer: MultiplayerPlayer? = null
    @Volatile
    private var activeGameConfigPayload: String = ""
    private val activeMatchRoster = mutableListOf<MultiplayerPlayer>()

    private val localPlayerId: String = playerIdOverride ?: resolveOrCreateStablePlayerId(context)

    val currentPlayerId: String
        get() = localPlayerId

    fun registerHostStateSnapshotProvider(provider: (() -> Pair<String, String>?)?) {
        hostStateSnapshotProvider = provider
    }

    fun consumePendingRejoinState(gameId: String): String? {
        val pending = _pendingRejoinStateSync.value ?: return null
        if (!pending.first.equals(gameId, ignoreCase = true)) return null
        _pendingRejoinStateSync.value = null
        return pending.second
    }

    fun clearRejoinStatus() {
        _rejoinRequestStatus.value = RejoinRequestStatus.Idle
    }

    fun broadcastHostConfigPreview(configJson: String) {
        _hostConfigPreview.value = configJson
        val currentState = _lobbyState.value
        if (currentState is MultiplayerLobbyState.Connected && currentState.isHost) {
            sendAction("HOST_CONFIG_PREVIEW", configJson)
        }
    }

    fun updateHostRoomCapacity(newMaxPlayers: Int) {
        val current = _lobbyState.value
        if (current is MultiplayerLobbyState.Hosting) {
            val updated = current.session.copy(maxPlayers = newMaxPlayers)
            _lobbyState.value = current.copy(session = updated)
            discovery.startAnnouncing(updated, scope)
        } else if (current is MultiplayerLobbyState.Connected && current.isHost) {
            val updated = current.session.copy(maxPlayers = newMaxPlayers)
            _lobbyState.value = current.copy(session = updated)
            discovery.startAnnouncing(updated, scope)
        }
    }

    fun notifyConnectionResumed() {
        scope.launch(Dispatchers.Main) {
            graceJob?.cancel()
            graceJob = null
            _isConnectionPaused.value = false
            _pauseRemainingSeconds.value = null
            _pauseReason.value = ""
            sendAction("WIFI_RESUME_GAME", "RESUMED")
        }
    }

    fun exitLobbyFromGrace() {
        graceJob?.cancel()
        graceJob = null
        _isConnectionPaused.value = false
        _pauseRemainingSeconds.value = null
        _pauseReason.value = ""
        // Preserve lastMatchSession for client so they can still request rejoin from Join Lobby
        disconnect(clearLastMatchSession = _isSessionHost.value)
    }

    // Host networking state
    private var hostServerSocket: ServerSocket? = null
    private var hostAcceptJob: Job? = null
    private val hostConnectedPlayers = mutableListOf<MultiplayerPlayer>()

    // Client networking state
    @Volatile
    private var clientSocket: Socket? = null
    @Volatile
    private var clientWriter: BufferedWriter? = null
    private var clientReaderJob: Job? = null

    @Volatile
    var customUsername: String = ""

    val localDeviceName: String
        get() {
            if (customUsername.isNotBlank()) return customUsername.trim()
            val model = Build.MODEL ?: "Phone"
            return if (model.length > 12) model.take(12) else model
        }

    fun getLocalIpAddress(): String {
        return discovery.getLocalIpAddress()
    }

    /**
     * Start hosting an authoritative game session on this device.
     */
    fun startHosting(
        gameId: String,
        gameTitle: String,
        hostName: String = localDeviceName,
        maxPlayersOverride: Int? = null
    ) {
        disconnect(clearLastMatchSession = true)
        _isSessionHost.value = true

        val localIp = discovery.getLocalIpAddress()
        val serverSocket = try {
            ServerSocket(DEFAULT_GAME_PORT).apply {
                reuseAddress = true
            }
        } catch (e: Exception) {
            // Port might be in use, allocate an open dynamic port
            ServerSocket(0)
        }
        hostServerSocket = serverSocket

        val maxPlayers = maxPlayersOverride ?: when {
            gameId.equals("snakes_and_ladders", ignoreCase = true) -> 5
            gameId.equals("ludo", ignoreCase = true) -> 4
            else -> 2
        }

        val session = DiscoveredSession(
            sessionId = UUID.randomUUID().toString(),
            hostName = hostName,
            gameId = gameId,
            gameTitle = gameTitle,
            hostIp = localIp,
            port = serverSocket.localPort,
            currentPlayers = 1,
            maxPlayers = maxPlayers,
            isInProgress = false
        )

        val hostPlayer = MultiplayerPlayer(
            id = localPlayerId,
            name = hostName,
            isHost = true,
            role = "PLAYER_1"
        )

        synchronized(hostConnectedPlayers) {
            hostConnectedPlayers.clear()
            hostConnectedPlayers.add(hostPlayer)
            activeMatchRoster.clear()
        }

        _lobbyState.value = MultiplayerLobbyState.Hosting(
            session = session,
            players = listOf(hostPlayer)
        )

        // Broadcast presence over mDNS and UDP beacon
        discovery.startAnnouncing(session, scope)

        // Accept loop for incoming client connections
        hostAcceptJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !serverSocket.isClosed) {
                    val socket = serverSocket.accept()
                    socket.tcpNoDelay = true
                    socket.soTimeout = 0
                    handleAcceptedClient(socket, session, hostPlayer)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Host accept loop finished or interrupted: ${e.message}")
                }
            }
        }
    }

    private fun handleAcceptedClient(
        socket: Socket,
        session: DiscoveredSession,
        hostPlayer: MultiplayerPlayer
    ) {
        scope.launch(Dispatchers.IO) {
            var associatedPlayerId: String? = null
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                val firstLine = reader.readLine() ?: return@launch
                val firstMsg = MultiplayerMessage.fromJson(firstLine) ?: return@launch

                val currentMatchSession = activeMatchSession
                if (currentMatchSession != null) {
                    handleIncomingMatchRejoin(
                        socket = socket,
                        reader = reader,
                        writer = writer,
                        firstMsg = firstMsg,
                        matchSession = currentMatchSession,
                        hostPlayer = activeMatchHostPlayer ?: hostPlayer
                    )
                    return@launch
                }

                // Pre-game lobby: reject stray REJOIN_REQUEST cleanly
                if (firstMsg.type == MultiplayerMessage.TYPE_REJOIN_REQUEST) {
                    val rejectMsg = MultiplayerMessage(
                        type = MultiplayerMessage.TYPE_REJOIN_REJECTED,
                        sessionId = session.sessionId,
                        senderId = localPlayerId,
                        payload = "No active match is running in this room"
                    )
                    sendRawToWriter(writer, rejectMsg)
                    try { socket.close() } catch (_: Exception) {}
                    return@launch
                }

                if (firstMsg.type != MultiplayerMessage.TYPE_JOIN_REQUEST) {
                    try { socket.close() } catch (_: Exception) {}
                    return@launch
                }

                val clientSenderId = firstMsg.senderId
                associatedPlayerId = clientSenderId
                val rawName = if (firstMsg.payload.isNotBlank()) firstMsg.payload else "Player"

                var assignedPlayer: MultiplayerPlayer? = null
                var isFull = false
                var currentPlayersSnapshot: List<MultiplayerPlayer> = emptyList()

                val currentMaxPlayers = when (val st = _lobbyState.value) {
                    is MultiplayerLobbyState.Hosting -> st.session.maxPlayers
                    is MultiplayerLobbyState.Connected -> st.session.maxPlayers
                    else -> session.maxPlayers
                }

                synchronized(hostConnectedPlayers) {
                    val existingIndex = hostConnectedPlayers.indexOfFirst { it.id == clientSenderId }
                    if (existingIndex >= 0) {
                        val existing = hostConnectedPlayers[existingIndex]
                        assignedPlayer = existing
                    } else if (hostConnectedPlayers.size >= currentMaxPlayers) {
                        isFull = true
                    } else {
                        val nextIndex = hostConnectedPlayers.size + 1
                        val newRole = "PLAYER_$nextIndex"
                        val displayName = if (rawName != "Player") rawName else "Player $nextIndex"
                        val newPlayer = MultiplayerPlayer(
                            id = clientSenderId,
                            name = displayName,
                            isHost = false,
                            role = newRole
                        )
                        hostConnectedPlayers.add(newPlayer)
                        assignedPlayer = newPlayer
                    }
                    currentPlayersSnapshot = hostConnectedPlayers.toList()
                }

                if (isFull || assignedPlayer == null) {
                    val rejectMsg = MultiplayerMessage(
                        type = MultiplayerMessage.TYPE_JOIN_REJECTED,
                        sessionId = session.sessionId,
                        senderId = localPlayerId,
                        payload = "Room is full (max $currentMaxPlayers players)"
                    )
                    sendRawToWriter(writer, rejectMsg)
                    try { socket.close() } catch (_: Exception) {}
                    return@launch
                }

                var previousSocket: Socket? = null
                synchronized(connectionLock) {
                    previousSocket = activeClientConnections[clientSenderId]?.socket
                    activeClientConnections[clientSenderId] = ClientConnection(clientSenderId, socket, writer)
                }
                if (previousSocket != null && previousSocket !== socket) {
                    try { previousSocket?.close() } catch (_: Exception) {}
                }

                val playersJsonArr = encodePlayersJson(currentPlayersSnapshot)

                // Acknowledge join with assigned role and full player list
                val acceptMsg = MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_JOIN_ACCEPTED,
                    sessionId = session.sessionId,
                    senderId = localPlayerId,
                    payload = JSONObject().apply {
                        put("role", assignedPlayer.role)
                        put("gameId", session.gameId)
                        put("gameTitle", session.gameTitle)
                        put("hostName", hostPlayer.name)
                        put("chessHostColor", _chessHostColor.value)
                        put("maxPlayers", currentMaxPlayers)
                        put("players", playersJsonArr)
                    }.toString()
                )
                sendRawToWriter(writer, acceptMsg)

                // Broadcast updated player list to all connected clients
                val playerListMsg = MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_PLAYER_LIST,
                    sessionId = session.sessionId,
                    senderId = localPlayerId,
                    payload = playersJsonArr.toString()
                )
                broadcastToClients(playerListMsg)

                val updatedSession = session.copy(
                    currentPlayers = currentPlayersSnapshot.size,
                    maxPlayers = currentMaxPlayers
                )
                if (_lobbyState.value !is MultiplayerLobbyState.InGame) {
                    _lobbyState.value = MultiplayerLobbyState.Connected(
                        session = updatedSession,
                        localPlayer = hostPlayer,
                        players = currentPlayersSnapshot,
                        isHost = true
                    )
                }
                discovery.startAnnouncing(updatedSession, scope)
                if (_isConnectionPaused.value) {
                    notifyConnectionResumed()
                }

                // Read loop for this connected client
                var line: String? = reader.readLine()
                while (line != null && isActive) {
                    val msg = MultiplayerMessage.fromJson(line)
                    if (msg != null) {
                        handleMessageFromClient(msg)
                    }
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client socket handler ended: ${e.message}")
            } finally {
                val playerId = associatedPlayerId
                var wasActiveConnection = false
                var remainingClients = 0
                synchronized(connectionLock) {
                    if (playerId != null && activeClientConnections[playerId]?.socket === socket) {
                        activeClientConnections.remove(playerId)
                        wasActiveConnection = true
                    }
                    remainingClients = activeClientConnections.size
                }
                try { socket.close() } catch (_: Exception) {}
                if (wasActiveConnection) {
                    val state = _lobbyState.value
                    if (state is MultiplayerLobbyState.Connected || state is MultiplayerLobbyState.InGame) {
                        if (remainingClients == 0) {
                            onPeerDisconnected("Opponent disconnected")
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles incoming rejoin (or join-while-in-progress) connections on the Host while a match is active.
     * Validates room session ID & original participant identity, deduplicates pending requests,
     * publishes to [pendingRejoinRequests] in real time, and transitions seamlessly into gameplay
     * once the Host accepts.
     */
    private suspend fun handleIncomingMatchRejoin(
        socket: Socket,
        reader: BufferedReader,
        writer: BufferedWriter,
        firstMsg: MultiplayerMessage,
        matchSession: DiscoveredSession,
        hostPlayer: MultiplayerPlayer
    ) {
        if (firstMsg.type != MultiplayerMessage.TYPE_REJOIN_REQUEST &&
            firstMsg.type != MultiplayerMessage.TYPE_JOIN_REQUEST
        ) {
            try { socket.close() } catch (_: Exception) {}
            return
        }

        val sessionMatches = firstMsg.sessionId == matchSession.sessionId ||
            firstMsg.sessionId.startsWith("direct_")
        if (!sessionMatches) {
            sendRawToWriter(
                writer,
                MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_REJOIN_REJECTED,
                    sessionId = matchSession.sessionId,
                    senderId = localPlayerId,
                    payload = "Invalid room session"
                )
            )
            try { socket.close() } catch (_: Exception) {}
            return
        }

        val requesterId = firstMsg.senderId
        val originalPlayer: MultiplayerPlayer? = synchronized(hostConnectedPlayers) {
            activeMatchRoster.firstOrNull { !it.isHost && it.id == requesterId }
        }

        if (originalPlayer == null) {
            sendRawToWriter(
                writer,
                MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_REJOIN_REJECTED,
                    sessionId = matchSession.sessionId,
                    senderId = localPlayerId,
                    payload = "Match already in progress — only original players can rejoin"
                )
            )
            try { socket.close() } catch (_: Exception) {}
            return
        }

        val displayName = firstMsg.payload.trim().ifBlank { originalPlayer.name }
        val updatedPlayer = originalPlayer.copy(name = displayName)
        val request = RejoinRequest(
            playerId = originalPlayer.id,
            playerName = displayName,
            role = originalPlayer.role,
            requestedAt = System.currentTimeMillis()
        )
        val entry = PendingRejoinEntry(
            request = request,
            player = updatedPlayer,
            socket = socket,
            writer = writer
        )

        var supersededPendingSocket: Socket? = null
        synchronized(connectionLock) {
            supersededPendingSocket = pendingRejoinEntries[requesterId]?.socket
            pendingRejoinEntries[requesterId] = entry
            publishPendingRejoinRequestsLocked()
        }
        if (supersededPendingSocket != null && supersededPendingSocket !== socket) {
            try { supersededPendingSocket?.close() } catch (_: Exception) {}
        }

        // Notify requesting player immediately that Host has received the rejoin request
        sendRawToWriter(
            writer,
            MultiplayerMessage(
                type = MultiplayerMessage.TYPE_REJOIN_PENDING,
                sessionId = matchSession.sessionId,
                senderId = localPlayerId,
                payload = JSONObject().apply {
                    put("sessionId", matchSession.sessionId)
                    put("hostName", hostPlayer.name)
                    put("gameId", matchSession.gameId)
                    put("gameTitle", matchSession.gameTitle)
                    put("role", originalPlayer.role)
                }.toString()
            )
        )

        // Single reader loop: monitors cancellation while pending, then seamlessly handles
        // live gameplay actions once Host accepts the request.
        try {
            var line: String? = reader.readLine()
            while (line != null && scope.isActive) {
                val msg = MultiplayerMessage.fromJson(line)
                if (msg != null) {
                    val isAcceptedSocket = synchronized(connectionLock) {
                        activeClientConnections[requesterId]?.socket === socket
                    }
                    if (isAcceptedSocket) {
                        handleMessageFromClient(msg)
                    } else if (msg.type == MultiplayerMessage.TYPE_LEAVE) {
                        // Requester cancelled their pending rejoin request
                        break
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Rejoin client socket ended for $requesterId: ${e.message}")
        } finally {
            var wasActive = false
            var remainingClients = 0
            synchronized(connectionLock) {
                if (pendingRejoinEntries[requesterId]?.socket === socket) {
                    pendingRejoinEntries.remove(requesterId)
                    publishPendingRejoinRequestsLocked()
                }
                if (activeClientConnections[requesterId]?.socket === socket) {
                    activeClientConnections.remove(requesterId)
                    wasActive = true
                }
                remainingClients = activeClientConnections.size
            }
            try { socket.close() } catch (_: Exception) {}
            if (wasActive && activeMatchSession != null && remainingClients == 0) {
                val state = _lobbyState.value
                if (state is MultiplayerLobbyState.InGame || state is MultiplayerLobbyState.Connected) {
                    onPeerDisconnected("Opponent disconnected")
                }
            }
        }
    }

    /**
     * Called by the Host to approve an incoming rejoin request.
     * Reconnects the original player into the existing room, restores their identity & role,
     * sends the authoritative live match state snapshot, and resumes active gameplay.
     */
    fun acceptRejoinRequest(playerId: String) {
        val matchSession = activeMatchSession ?: return
        val matchHost = activeMatchHostPlayer ?: return

        val entry: PendingRejoinEntry
        var previousActiveSocket: Socket? = null
        synchronized(connectionLock) {
            val pending = pendingRejoinEntries.remove(playerId) ?: return
            entry = pending
            previousActiveSocket = activeClientConnections[playerId]?.socket
            activeClientConnections[playerId] = ClientConnection(playerId, pending.socket, pending.writer)
            publishPendingRejoinRequestsLocked()
        }

        if (previousActiveSocket != null && previousActiveSocket !== entry.socket) {
            try { previousActiveSocket?.close() } catch (_: Exception) {}
        }

        val rosterSnapshot: List<MultiplayerPlayer> = synchronized(hostConnectedPlayers) {
            val idx = activeMatchRoster.indexOfFirst { it.id == playerId }
            if (idx >= 0) {
                activeMatchRoster[idx] = entry.player
            }
            val connIdx = hostConnectedPlayers.indexOfFirst { it.id == playerId }
            if (connIdx >= 0) {
                hostConnectedPlayers[connIdx] = entry.player
            }
            activeMatchRoster.toList()
        }

        // Capture live authoritative game state from the Host's active game view
        val snapshotPair = try {
            hostStateSnapshotProvider?.invoke()
        } catch (e: Exception) {
            Log.w(TAG, "Host snapshot capture failed: ${e.message}")
            null
        }

        val updatedSession = matchSession.copy(
            currentPlayers = rosterSnapshot.size,
            isInProgress = true
        )
        activeMatchSession = updatedSession

        val acceptPayload = JSONObject().apply {
            put("role", entry.player.role)
            put("gameId", updatedSession.gameId)
            put("gameTitle", updatedSession.gameTitle)
            put("hostName", matchHost.name)
            put("chessHostColor", _chessHostColor.value)
            put("maxPlayers", updatedSession.maxPlayers)
            put("config", activeGameConfigPayload)
            put("players", encodePlayersJson(rosterSnapshot))
            if (snapshotPair != null) {
                put("stateGameId", snapshotPair.first)
                put("stateSnapshot", snapshotPair.second)
            }
        }.toString()

        val acceptMsg = MultiplayerMessage(
            type = MultiplayerMessage.TYPE_REJOIN_ACCEPTED,
            sessionId = updatedSession.sessionId,
            senderId = localPlayerId,
            payload = acceptPayload
        )

        // Restore Host lobby state & dismiss reconnect pause immediately
        graceJob?.cancel()
        graceJob = null
        _isConnectionPaused.value = false
        _pauseRemainingSeconds.value = null
        _pauseReason.value = ""
        _lobbyState.value = MultiplayerLobbyState.InGame(
            session = updatedSession,
            localPlayer = matchHost,
            players = rosterSnapshot,
            isHost = true
        )

        scope.launch(Dispatchers.IO) {
            sendRawToWriter(entry.writer, acceptMsg)
        }
    }

    /**
     * Called by the Host to decline an incoming rejoin request.
     * Notifies the requesting player and closes their pending socket without affecting the ongoing match.
     */
    fun rejectRejoinRequest(
        playerId: String,
        reason: String = "Host declined your rejoin request"
    ) {
        val sessionId = activeMatchSession?.sessionId ?: ""
        val entry = synchronized(connectionLock) {
            val removed = pendingRejoinEntries.remove(playerId)
            publishPendingRejoinRequestsLocked()
            removed
        } ?: return

        scope.launch(Dispatchers.IO) {
            sendRawToWriter(
                entry.writer,
                MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_REJOIN_REJECTED,
                    sessionId = sessionId,
                    senderId = localPlayerId,
                    payload = reason
                )
            )
            try { entry.socket.close() } catch (_: Exception) {}
        }
    }

    private fun publishPendingRejoinRequestsLocked() {
        _pendingRejoinRequests.value = pendingRejoinEntries.values.map { it.request }
    }

    /**
     * Start discovering sessions hosted by nearby devices on this Wi-Fi network.
     */
    fun startDiscovery() {
        disconnect(clearLastMatchSession = false)

        val sessions = mutableListOf<DiscoveredSession>()
        _lobbyState.value = MultiplayerLobbyState.Discovering(emptyList())

        discovery.startDiscovery(scope) { newSession ->
            scope.launch(Dispatchers.Main) {
                val existingIndex = sessions.indexOfFirst {
                    it.sessionId == newSession.sessionId || (it.hostIp == newSession.hostIp && it.port == newSession.port)
                }
                if (existingIndex >= 0) {
                    sessions[existingIndex] = newSession
                } else {
                    sessions.add(newSession)
                }
                if (_lobbyState.value is MultiplayerLobbyState.Discovering) {
                    _lobbyState.value = MultiplayerLobbyState.Discovering(sessions.toList())
                }
            }
        }
    }

    fun stopDiscovery() {
        discovery.stopDiscovery()
        if (_lobbyState.value is MultiplayerLobbyState.Discovering) {
            _lobbyState.value = MultiplayerLobbyState.Idle
        }
    }

    /**
     * Connect to an existing discovered session (pre-game lobby join, or routes cleanly to rejoin if match is in progress).
     */
    fun connectToSession(
        session: DiscoveredSession,
        playerName: String = localDeviceName
    ) {
        if (session.isInProgress) {
            requestRejoin(session, playerName)
            return
        }

        discovery.stopDiscovery()
        closeClientSocketQuietly()
        _isSessionHost.value = false
        _rejoinRequestStatus.value = RejoinRequestStatus.Idle
        _lobbyState.value = MultiplayerLobbyState.Connecting(session)
        lastInGameSession = session
        lastPlayerName = playerName

        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(session.hostIp, session.port), 6000)
                socket.tcpNoDelay = true

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                clientSocket = socket
                clientWriter = writer

                // Send JOIN_REQUEST
                val joinMsg = MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_JOIN_REQUEST,
                    sessionId = session.sessionId,
                    senderId = localPlayerId,
                    payload = playerName
                )
                sendRawToWriter(writer, joinMsg)

                // Read server responses
                startClientReaderLoop(socket, reader, session, playerName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to session: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _lobbyState.value = MultiplayerLobbyState.Disconnected("Could not connect to host at ${session.hostIp}")
                }
            }
        }
    }

    /**
     * Sends a rejoin request from a disconnected player to the Host of an ongoing match.
     */
    fun requestRejoin(
        session: DiscoveredSession? = _lastMatchSession.value ?: lastInGameSession,
        playerName: String = lastPlayerName.ifBlank { localDeviceName }
    ) {
        val targetSession = session
        if (targetSession == null) {
            _rejoinRequestStatus.value = RejoinRequestStatus.Rejected("No active match session to rejoin")
            return
        }

        discovery.stopDiscovery()
        closeClientSocketQuietly()
        graceJob?.cancel()
        graceJob = null
        _isSessionHost.value = false
        lastInGameSession = targetSession
        lastPlayerName = playerName
        _lastMatchSession.value = targetSession
        _rejoinRequestStatus.value = RejoinRequestStatus.Pending(targetSession)

        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetSession.hostIp, targetSession.port), 6000)
                socket.tcpNoDelay = true

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                clientSocket = socket
                clientWriter = writer

                val rejoinMsg = MultiplayerMessage(
                    type = MultiplayerMessage.TYPE_REJOIN_REQUEST,
                    sessionId = targetSession.sessionId,
                    senderId = localPlayerId,
                    payload = playerName
                )
                sendRawToWriter(writer, rejoinMsg)

                startClientReaderLoop(socket, reader, targetSession, playerName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send rejoin request: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _rejoinRequestStatus.value = RejoinRequestStatus.Rejected(
                        "Could not reach host — the room may have closed"
                    )
                }
            }
        }
    }

    private fun startClientReaderLoop(
        socket: Socket,
        reader: BufferedReader,
        session: DiscoveredSession,
        playerName: String
    ) {
        clientReaderJob?.cancel()
        clientReaderJob = scope.launch(Dispatchers.IO) {
            try {
                var line: String? = reader.readLine()
                while (line != null && isActive) {
                    val msg = MultiplayerMessage.fromJson(line)
                    if (msg != null) {
                        handleMessageFromServer(msg, session, playerName)
                    }
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client reader stream ended: ${e.message}")
            } finally {
                val isCurrentSocket = (clientSocket === socket)
                if (isCurrentSocket) {
                    clientSocket = null
                    clientWriter = null
                    try { socket.close() } catch (_: Exception) {}
                    if (_rejoinRequestStatus.value is RejoinRequestStatus.Pending) {
                        _rejoinRequestStatus.value = RejoinRequestStatus.Rejected("Lost connection to host while waiting for approval")
                    } else {
                        onPeerDisconnected("Lost connection to host")
                    }
                } else {
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun closeClientSocketQuietly() {
        val oldSocket = clientSocket
        clientSocket = null
        clientWriter = null
        clientReaderJob?.cancel()
        clientReaderJob = null
        if (oldSocket != null) {
            try { oldSocket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Connect directly by entering the host's IP and port (e.g., when broadcast is filtered on hotspot).
     */
    fun connectDirect(
        hostIp: String,
        port: Int = DEFAULT_GAME_PORT,
        gameId: String = "xo",
        gameTitle: String = "Chill Game",
        playerName: String = localDeviceName
    ) {
        val directSession = DiscoveredSession(
            sessionId = "direct_${System.currentTimeMillis()}",
            hostName = "Direct Host",
            gameId = gameId,
            gameTitle = gameTitle,
            hostIp = hostIp.trim(),
            port = port,
            currentPlayers = 1,
            maxPlayers = 2
        )
        connectToSession(directSession, playerName)
    }

    /**
     * Called by Host when players are connected and the Host taps Start Game.
     */
    fun launchGame(extraConfigPayload: String = "") {
        val currentState = _lobbyState.value
        if (currentState is MultiplayerLobbyState.Connected && currentState.isHost) {
            val activeSession = currentState.session.copy(isInProgress = true)
            activeMatchSession = activeSession
            activeMatchHostPlayer = currentState.localPlayer
            activeGameConfigPayload = extraConfigPayload
            synchronized(hostConnectedPlayers) {
                activeMatchRoster.clear()
                activeMatchRoster.addAll(currentState.players)
            }

            // Continue announcing room with isInProgress = true so disconnected players can find & rejoin
            discovery.startAnnouncing(activeSession, scope)

            val playersJsonArr = encodePlayersJson(currentState.players)
            val startPayload = JSONObject().apply {
                put("config", extraConfigPayload)
                put("players", playersJsonArr)
                put("chessHostColor", _chessHostColor.value)
            }.toString()

            val msg = MultiplayerMessage(
                type = MultiplayerMessage.TYPE_GAME_START,
                sessionId = activeSession.sessionId,
                senderId = localPlayerId,
                payload = startPayload
            )
            broadcastToClients(msg)

            val startInfo = GameStartInfo(
                gameId = activeSession.gameId,
                localRole = "PLAYER_1",
                players = currentState.players,
                isHost = true,
                configPayload = extraConfigPayload
            )
            _gameStartEvent.tryEmit(startInfo)

            _lobbyState.value = MultiplayerLobbyState.InGame(
                session = activeSession,
                localPlayer = currentState.localPlayer,
                players = currentState.players,
                isHost = true
            )
        }
    }

    /**
     * Send a game action (e.g. Move, Roll, Tap) to the other player.
     */
    fun sendAction(actionType: String, payload: String) {
        val currentState = _lobbyState.value
        val (sessionId, isHost) = when (currentState) {
            is MultiplayerLobbyState.InGame -> currentState.session.sessionId to currentState.isHost
            is MultiplayerLobbyState.Connected -> currentState.session.sessionId to currentState.isHost
            else -> return
        }

        val jsonPayload = JSONObject().apply {
            put("actionType", actionType)
            put("actionData", payload)
        }.toString()

        val msg = MultiplayerMessage(
            type = MultiplayerMessage.TYPE_PLAYER_ACTION,
            sessionId = sessionId,
            senderId = localPlayerId,
            payload = jsonPayload
        )

        scope.launch(Dispatchers.IO) {
            if (isHost) {
                broadcastToClients(msg)
            } else {
                clientWriter?.let { sendRawToWriter(it, msg) }
            }
        }
    }

    /**
     * Send a match restart / next round event to synchronize both devices.
     */
    fun sendReset(extraPayload: String = "") {
        val currentState = _lobbyState.value
        val (sessionId, isHost) = when (currentState) {
            is MultiplayerLobbyState.InGame -> currentState.session.sessionId to currentState.isHost
            is MultiplayerLobbyState.Connected -> currentState.session.sessionId to currentState.isHost
            else -> return
        }

        val msg = MultiplayerMessage(
            type = MultiplayerMessage.TYPE_GAME_RESET,
            sessionId = sessionId,
            senderId = localPlayerId,
            payload = extraPayload
        )

        scope.launch(Dispatchers.IO) {
            if (isHost) {
                broadcastToClients(msg)
            } else {
                clientWriter?.let { sendRawToWriter(it, msg) }
            }
        }
    }

    private fun handleMessageFromClient(msg: MultiplayerMessage) {
        when (msg.type) {
            MultiplayerMessage.TYPE_PLAYER_ACTION -> {
                try {
                    val json = JSONObject(msg.payload)
                    val actionType = json.getString("actionType")
                    val actionData = json.getString("actionData")
                    if (actionType == "WIFI_RESUME_GAME") {
                        _isConnectionPaused.value = false
                        _pauseRemainingSeconds.value = null
                        graceJob?.cancel()
                        graceJob = null
                    }
                    _incomingActions.tryEmit(PlayerAction(msg.senderId, actionType, actionData))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing client action payload: ${e.message}")
                }
            }
            MultiplayerMessage.TYPE_GAME_RESET -> {
                _incomingActions.tryEmit(PlayerAction(msg.senderId, "GAME_RESET", msg.payload))
            }
            MultiplayerMessage.TYPE_LEAVE -> {
                onPeerDisconnected("Client left the game")
            }
        }
    }

    private fun handleMessageFromServer(
        msg: MultiplayerMessage,
        session: DiscoveredSession,
        localPlayerName: String
    ) {
        when (msg.type) {
            MultiplayerMessage.TYPE_JOIN_REJECTED -> {
                closeClientSocketQuietly()
                onPeerDisconnected(msg.payload.ifBlank { "Session is full (max players reached)" })
            }
            MultiplayerMessage.TYPE_REJOIN_PENDING -> {
                val updatedSession = try {
                    val json = JSONObject(msg.payload)
                    session.copy(
                        sessionId = json.optString("sessionId", msg.sessionId),
                        hostName = json.optString("hostName", session.hostName),
                        gameId = json.optString("gameId", session.gameId),
                        gameTitle = json.optString("gameTitle", session.gameTitle),
                        isInProgress = true
                    )
                } catch (_: Exception) {
                    session.copy(sessionId = msg.sessionId, isInProgress = true)
                }
                graceJob?.cancel()
                graceJob = null
                lastInGameSession = updatedSession
                _lastMatchSession.value = updatedSession
                _rejoinRequestStatus.value = RejoinRequestStatus.Pending(updatedSession)
            }
            MultiplayerMessage.TYPE_REJOIN_ACCEPTED -> {
                val payloadJson = try { JSONObject(msg.payload) } catch (_: Exception) { JSONObject() }
                val hostName = payloadJson.optString("hostName", session.hostName)
                val actualGameId = payloadJson.optString("gameId", session.gameId)
                val actualGameTitle = payloadJson.optString("gameTitle", session.gameTitle)
                val hostColor = payloadJson.optString("chessHostColor", _chessHostColor.value)
                val assignedRole = payloadJson.optString("role", "PLAYER_2")
                val maxPlayers = payloadJson.optInt("maxPlayers", session.maxPlayers)
                val configPayload = payloadJson.optString("config", "")
                val stateGameId = payloadJson.optString("stateGameId", actualGameId)
                val stateSnapshot = payloadJson.optString("stateSnapshot", "")

                _chessHostColor.value = hostColor

                val playersList = parsePlayersJson(payloadJson.optJSONArray("players"))
                val localPlayer = playersList.firstOrNull { it.id == localPlayerId }
                    ?: MultiplayerPlayer(
                        id = localPlayerId,
                        name = localPlayerName,
                        isHost = false,
                        role = assignedRole
                    )
                val hostPlayer = MultiplayerPlayer(
                    id = msg.senderId,
                    name = hostName,
                    isHost = true,
                    role = "PLAYER_1"
                )
                val finalPlayers = if (playersList.isNotEmpty()) playersList else listOf(hostPlayer, localPlayer)

                val updatedSession = session.copy(
                    sessionId = msg.sessionId,
                    hostName = hostName,
                    gameId = actualGameId,
                    gameTitle = actualGameTitle,
                    currentPlayers = finalPlayers.size,
                    maxPlayers = maxPlayers,
                    isInProgress = true
                )

                // Store sticky state snapshot for newly mounted views AND emit action for already mounted views
                if (stateSnapshot.isNotBlank()) {
                    _pendingRejoinStateSync.value = stateGameId to stateSnapshot
                    _incomingActions.tryEmit(
                        PlayerAction(
                            senderId = msg.senderId,
                            actionType = MultiplayerStateSnapshot.ACTION_REJOIN_STATE_SYNC,
                            payload = stateSnapshot
                        )
                    )
                }

                graceJob?.cancel()
                graceJob = null
                _isConnectionPaused.value = false
                _pauseRemainingSeconds.value = null
                _pauseReason.value = ""
                lastInGameSession = updatedSession
                _lastMatchSession.value = updatedSession
                _rejoinRequestStatus.value = RejoinRequestStatus.Accepted(updatedSession)

                val startInfo = GameStartInfo(
                    gameId = updatedSession.gameId,
                    localRole = localPlayer.role,
                    players = finalPlayers,
                    isHost = false,
                    configPayload = configPayload
                )
                _gameStartEvent.tryEmit(startInfo)

                _lobbyState.value = MultiplayerLobbyState.InGame(
                    session = updatedSession,
                    localPlayer = localPlayer,
                    players = finalPlayers,
                    isHost = false
                )
            }
            MultiplayerMessage.TYPE_REJOIN_REJECTED -> {
                val reason = msg.payload.ifBlank { "Host declined your rejoin request" }
                closeClientSocketQuietly()
                _rejoinRequestStatus.value = RejoinRequestStatus.Rejected(reason)
                val current = _lobbyState.value
                if (current is MultiplayerLobbyState.Connecting ||
                    current is MultiplayerLobbyState.Connected ||
                    current is MultiplayerLobbyState.InGame
                ) {
                    _isConnectionPaused.value = false
                    _pauseRemainingSeconds.value = null
                    _lobbyState.value = MultiplayerLobbyState.Disconnected(reason)
                }
            }
            MultiplayerMessage.TYPE_PLAYER_LIST -> {
                try {
                    val updatedPlayers = parsePlayersJson(JSONArray(msg.payload))
                    val current = _lobbyState.value
                    if (current is MultiplayerLobbyState.Connected) {
                        _lobbyState.value = current.copy(
                            session = current.session.copy(currentPlayers = updatedPlayers.size),
                            players = updatedPlayers
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing player list: ${e.message}")
                }
            }
            MultiplayerMessage.TYPE_JOIN_ACCEPTED -> {
                val payloadJson = try { JSONObject(msg.payload) } catch (_: Exception) { JSONObject() }
                val hostName = payloadJson.optString("hostName", session.hostName)
                val actualGameId = payloadJson.optString("gameId", session.gameId)
                val actualGameTitle = payloadJson.optString("gameTitle", session.gameTitle)
                val hostColor = payloadJson.optString("chessHostColor", "WHITE")
                _chessHostColor.value = hostColor

                val assignedRole = payloadJson.optString("role", "PLAYER_2")
                val maxPlayers = payloadJson.optInt("maxPlayers", session.maxPlayers)
                val playersList = parsePlayersJson(payloadJson.optJSONArray("players"))

                val localPlayer = MultiplayerPlayer(
                    id = localPlayerId,
                    name = localPlayerName,
                    isHost = false,
                    role = assignedRole
                )
                val hostPlayer = MultiplayerPlayer(
                    id = msg.senderId,
                    name = hostName,
                    isHost = true,
                    role = "PLAYER_1"
                )

                val finalPlayers = if (playersList.isNotEmpty()) playersList else listOf(hostPlayer, localPlayer)

                val updatedSession = session.copy(
                    sessionId = msg.sessionId,
                    hostName = hostName,
                    gameId = actualGameId,
                    gameTitle = actualGameTitle,
                    currentPlayers = finalPlayers.size,
                    maxPlayers = maxPlayers
                )
                lastInGameSession = updatedSession

                _lobbyState.value = MultiplayerLobbyState.Connected(
                    session = updatedSession,
                    localPlayer = localPlayer,
                    players = finalPlayers,
                    isHost = false
                )
            }
            MultiplayerMessage.TYPE_GAME_START -> {
                var configPayload = msg.payload
                var parsedPlayers: List<MultiplayerPlayer>? = null
                try {
                    if (msg.payload.trimStart().startsWith("{")) {
                        val json = JSONObject(msg.payload)
                        if (json.has("config")) {
                            configPayload = json.getString("config")
                        }
                        val color = json.optString("chessHostColor", "")
                        if (color.isNotBlank()) {
                            _chessHostColor.value = color
                        }
                        if (json.has("players")) {
                            parsedPlayers = parsePlayersJson(json.getJSONArray("players"))
                        }
                    }
                } catch (_: Exception) {}

                val currentState = _lobbyState.value
                val existingPlayers = (currentState as? MultiplayerLobbyState.Connected)?.players ?: emptyList()
                val finalPlayers = parsedPlayers ?: (if (existingPlayers.isNotEmpty()) existingPlayers else {
                    val hostP = MultiplayerPlayer(id = msg.senderId, name = session.hostName, isHost = true, role = "PLAYER_1")
                    val localP = MultiplayerPlayer(id = localPlayerId, name = localPlayerName, isHost = false, role = "PLAYER_2")
                    listOf(hostP, localP)
                })

                val localPlayer = (currentState as? MultiplayerLobbyState.Connected)?.localPlayer
                    ?: finalPlayers.firstOrNull { it.id == localPlayerId }
                    ?: MultiplayerPlayer(
                        id = localPlayerId,
                        name = localPlayerName,
                        isHost = false,
                        role = "PLAYER_2"
                    )

                val updatedSession = session.copy(
                    sessionId = msg.sessionId,
                    currentPlayers = finalPlayers.size,
                    isInProgress = true
                )
                lastInGameSession = updatedSession
                _lastMatchSession.value = updatedSession

                val startInfo = GameStartInfo(
                    gameId = updatedSession.gameId,
                    localRole = localPlayer.role,
                    players = finalPlayers,
                    isHost = false,
                    configPayload = configPayload
                )
                _gameStartEvent.tryEmit(startInfo)

                _lobbyState.value = MultiplayerLobbyState.InGame(
                    session = updatedSession,
                    localPlayer = localPlayer,
                    players = finalPlayers,
                    isHost = false
                )
            }
            MultiplayerMessage.TYPE_PLAYER_ACTION -> {
                try {
                    val json = JSONObject(msg.payload)
                    val actionType = json.getString("actionType")
                    val actionData = json.getString("actionData")
                    if (actionType == "CHESS_COLOR_ASSIGNMENT") {
                        _chessHostColor.value = actionData
                    } else if (actionType == "HOST_CONFIG_PREVIEW") {
                        _hostConfigPreview.value = actionData
                    } else if (actionType == "WIFI_RESUME_GAME") {
                        _isConnectionPaused.value = false
                        _pauseRemainingSeconds.value = null
                        graceJob?.cancel()
                        graceJob = null
                    }
                    _incomingActions.tryEmit(PlayerAction(msg.senderId, actionType, actionData))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing server action payload: ${e.message}")
                }
            }
            MultiplayerMessage.TYPE_GAME_RESET -> {
                _incomingActions.tryEmit(PlayerAction(msg.senderId, "GAME_RESET", msg.payload))
            }
            MultiplayerMessage.TYPE_HOST_CLOSED,
            MultiplayerMessage.TYPE_LEAVE -> {
                lastInGameSession = null
                _lastMatchSession.value = null
                if (_rejoinRequestStatus.value is RejoinRequestStatus.Pending) {
                    _rejoinRequestStatus.value = RejoinRequestStatus.Rejected("Host closed the room")
                }
                closeClientSocketQuietly()
                onPeerDisconnected("Host closed the room", hostClosed = true)
            }
        }
    }

    private fun encodePlayersJson(players: List<MultiplayerPlayer>): JSONArray {
        return JSONArray().apply {
            players.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("isHost", p.isHost)
                    put("role", p.role)
                })
            }
        }
    }

    private fun parsePlayersJson(arr: JSONArray?): List<MultiplayerPlayer> {
        if (arr == null) return emptyList()
        val list = mutableListOf<MultiplayerPlayer>()
        for (i in 0 until arr.length()) {
            val pObj = arr.getJSONObject(i)
            list.add(
                MultiplayerPlayer(
                    id = pObj.getString("id"),
                    name = pObj.getString("name"),
                    isHost = pObj.getBoolean("isHost"),
                    role = pObj.getString("role")
                )
            )
        }
        return list
    }

    private fun broadcastToClients(msg: MultiplayerMessage) {
        scope.launch(Dispatchers.IO) {
            val writers = synchronized(connectionLock) {
                activeClientConnections.values.map { it.writer }
            }
            writers.forEach { writer ->
                sendRawToWriter(writer, msg)
            }
        }
    }

    private fun sendRawToWriter(writer: BufferedWriter, msg: MultiplayerMessage) {
        try {
            synchronized(writer) {
                writer.write(msg.toJson())
                writer.newLine()
                writer.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send message: ${e.message}")
        }
    }

    private fun onPeerDisconnected(reason: String, hostClosed: Boolean = false) {
        scope.launch(Dispatchers.Main) {
            val current = _lobbyState.value
            if (current is MultiplayerLobbyState.InGame) {
                if (hostClosed) {
                    graceJob?.cancel()
                    graceJob = null
                    _isConnectionPaused.value = false
                    _pauseRemainingSeconds.value = null
                    _pauseReason.value = reason
                    _lobbyState.value = MultiplayerLobbyState.Disconnected(reason)
                    return@launch
                }

                if (_isConnectionPaused.value) return@launch

                _isConnectionPaused.value = true
                _pauseReason.value = reason
                _pauseRemainingSeconds.value = 10

                if (!current.isHost) {
                    lastInGameSession = current.session
                    _lastMatchSession.value = current.session
                    _rejoinRequestStatus.value = RejoinRequestStatus.Idle
                }

                graceJob?.cancel()
                graceJob = scope.launch(Dispatchers.Main) {
                    for (sec in 10 downTo 1) {
                        _pauseRemainingSeconds.value = sec
                        kotlinx.coroutines.delay(1000)
                        if (!_isConnectionPaused.value) {
                            return@launch
                        }
                    }
                    // If Host currently has pending rejoin request(s), stay paused waiting for Host's decision
                    if (current.isHost && _pendingRejoinRequests.value.isNotEmpty()) {
                        _pauseRemainingSeconds.value = 0
                        return@launch
                    }
                    _pauseRemainingSeconds.value = 0
                    _isConnectionPaused.value = false
                    _lobbyState.value = MultiplayerLobbyState.Disconnected(
                        if (current.isHost) {
                            "Opponent disconnected (waiting for rejoin or exit to lobby)"
                        } else {
                            "Disconnected from host. Tap Request Rejoin to reconnect."
                        }
                    )
                }
            } else if (current is MultiplayerLobbyState.Connected || current is MultiplayerLobbyState.Connecting) {
                _lobbyState.value = MultiplayerLobbyState.Disconnected(reason)
            }
        }
    }

    fun disconnect(clearLastMatchSession: Boolean = false) {
        graceJob?.cancel()
        graceJob = null
        _isConnectionPaused.value = false
        _pauseRemainingSeconds.value = null
        _pauseReason.value = ""

        val wasHost = _isSessionHost.value
        val hostSessionId = activeMatchSession?.sessionId
            ?: (_lobbyState.value as? MultiplayerLobbyState.Hosting)?.session?.sessionId
            ?: (_lobbyState.value as? MultiplayerLobbyState.Connected)?.session?.sessionId
            ?: (_lobbyState.value as? MultiplayerLobbyState.InGame)?.session?.sessionId

        // Notify any connected or pending-rejoin clients that Host closed the room
        val socketsToClose = mutableListOf<Socket>()
        val writersToNotify = mutableListOf<BufferedWriter>()
        synchronized(connectionLock) {
            activeClientConnections.values.forEach { conn ->
                writersToNotify.add(conn.writer)
                socketsToClose.add(conn.socket)
            }
            pendingRejoinEntries.values.forEach { entry ->
                writersToNotify.add(entry.writer)
                socketsToClose.add(entry.socket)
            }
            activeClientConnections.clear()
            pendingRejoinEntries.clear()
            publishPendingRejoinRequestsLocked()
        }

        if (wasHost && writersToNotify.isNotEmpty() && hostSessionId != null) {
            val closedMsg = MultiplayerMessage(
                type = MultiplayerMessage.TYPE_HOST_CLOSED,
                sessionId = hostSessionId,
                senderId = localPlayerId,
                payload = "Host closed the room"
            )
            scope.launch(Dispatchers.IO) {
                writersToNotify.forEach { w -> sendRawToWriter(w, closedMsg) }
                socketsToClose.forEach { s -> try { s.close() } catch (_: Exception) {} }
            }
        } else {
            socketsToClose.forEach { s -> try { s.close() } catch (_: Exception) {} }
        }

        discovery.stopAnnouncing()
        discovery.stopDiscovery()

        hostAcceptJob?.cancel()
        hostAcceptJob = null

        try { hostServerSocket?.close() } catch (_: Exception) {}
        hostServerSocket = null

        closeClientSocketQuietly()

        activeMatchSession = null
        activeMatchHostPlayer = null
        activeGameConfigPayload = ""
        synchronized(hostConnectedPlayers) {
            hostConnectedPlayers.clear()
            activeMatchRoster.clear()
        }
        _isSessionHost.value = false
        _rejoinRequestStatus.value = RejoinRequestStatus.Idle
        _pendingRejoinStateSync.value = null
        if (clearLastMatchSession) {
            lastInGameSession = null
            _lastMatchSession.value = null
        }

        _lobbyState.value = MultiplayerLobbyState.Idle
        _chessHostColor.value = "WHITE"
    }

    companion object {
        private const val TAG = "WifiMultiplayer"
        private const val PREFS_NAME = "chill_multiplayer_prefs"
        private const val KEY_STABLE_PLAYER_ID = "stable_player_id"
        const val DEFAULT_GAME_PORT = 8890

        private fun resolveOrCreateStablePlayerId(context: Context): String {
            return try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val existing = prefs.getString(KEY_STABLE_PLAYER_ID, null)
                if (!existing.isNullOrBlank()) {
                    existing
                } else {
                    val created = UUID.randomUUID().toString()
                    prefs.edit().putString(KEY_STABLE_PLAYER_ID, created).apply()
                    created
                }
            } catch (_: Exception) {
                UUID.randomUUID().toString()
            }
        }
    }
}
