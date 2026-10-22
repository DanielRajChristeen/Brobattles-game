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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.random.Random

/**
 * Authoritative local Wi-Fi / hotspot session manager for Chill Games.
 * Facilitates zero-configuration peer-to-peer play over local Wi-Fi LANs without
 * Internet, cloud accounts, or third-party servers.
 */
class LocalWifiMultiplayerManager(
    private val context: Context,
    private val scope: CoroutineScope
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

    private val localPlayerId = UUID.randomUUID().toString()

    // Host networking state
    private var hostServerSocket: ServerSocket? = null
    private var hostAcceptJob: Job? = null
    private val clientSockets = mutableListOf<Socket>()
    private val clientWriters = mutableListOf<BufferedWriter>()
    private val hostConnectedPlayers = mutableListOf<MultiplayerPlayer>()

    // Client networking state
    private var clientSocket: Socket? = null
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
        hostName: String = localDeviceName
    ) {
        disconnect()

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

        val maxPlayers = if (gameId.equals("snakes_and_ladders", ignoreCase = true)) 5 else 2

        val session = DiscoveredSession(
            sessionId = UUID.randomUUID().toString(),
            hostName = hostName,
            gameId = gameId,
            gameTitle = gameTitle,
            hostIp = localIp,
            port = serverSocket.localPort,
            currentPlayers = 1,
            maxPlayers = maxPlayers
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
            var clientWriterRef: BufferedWriter? = null
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                clientWriterRef = writer

                // Wait for initial JOIN_REQUEST
                val firstLine = reader.readLine() ?: return@launch
                val joinMsg = MultiplayerMessage.fromJson(firstLine)

                if (joinMsg?.type == MultiplayerMessage.TYPE_JOIN_REQUEST) {
                    val clientSenderId = joinMsg.senderId
                    val rawName = if (joinMsg.payload.isNotBlank()) joinMsg.payload else "Player"

                    var assignedPlayer: MultiplayerPlayer? = null
                    var isFull = false
                    var currentPlayersSnapshot: List<MultiplayerPlayer> = emptyList()

                    synchronized(hostConnectedPlayers) {
                        val existingIndex = hostConnectedPlayers.indexOfFirst { it.id == clientSenderId }
                        if (existingIndex >= 0) {
                            val existing = hostConnectedPlayers[existingIndex]
                            assignedPlayer = existing
                        } else if (hostConnectedPlayers.size >= session.maxPlayers) {
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
                            payload = "Room is full (max ${session.maxPlayers} players)"
                        )
                        sendRawToWriter(writer, rejectMsg)
                        try { socket.close() } catch (ignored: Exception) {}
                        return@launch
                    }

                    synchronized(clientSockets) {
                        clientSockets.add(socket)
                        clientWriters.add(writer)
                    }

                    val playersJsonArr = org.json.JSONArray().apply {
                        currentPlayersSnapshot.forEach { p ->
                            put(JSONObject().apply {
                                put("id", p.id)
                                put("name", p.name)
                                put("isHost", p.isHost)
                                put("role", p.role)
                            })
                        }
                    }

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
                            put("maxPlayers", session.maxPlayers)
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

                    val updatedSession = session.copy(currentPlayers = currentPlayersSnapshot.size)
                    _lobbyState.value = MultiplayerLobbyState.Connected(
                        session = updatedSession,
                        localPlayer = hostPlayer,
                        players = currentPlayersSnapshot,
                        isHost = true
                    )
                    discovery.startAnnouncing(updatedSession, scope)

                    // Read loop for this connected client
                    var line: String? = reader.readLine()
                    while (line != null && isActive) {
                        val msg = MultiplayerMessage.fromJson(line)
                        if (msg != null) {
                            handleMessageFromClient(msg)
                        }
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client socket handler ended: ${e.message}")
            } finally {
                synchronized(clientSockets) {
                    clientSockets.remove(socket)
                    clientWriterRef?.let { clientWriters.remove(it) }
                }
                try { socket.close() } catch (ignored: Exception) {}
                val state = _lobbyState.value
                if (state is MultiplayerLobbyState.Connected || state is MultiplayerLobbyState.InGame) {
                    synchronized(clientSockets) {
                        if (clientSockets.isEmpty()) {
                            onPeerDisconnected("Opponent disconnected")
                        }
                    }
                }
            }
        }
    }

    /**
     * Start discovering sessions hosted by nearby devices on this Wi-Fi network.
     */
    fun startDiscovery() {
        disconnect()

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
                _lobbyState.value = MultiplayerLobbyState.Discovering(sessions.toList())
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
     * Connect to an existing discovered session.
     */
    fun connectToSession(
        session: DiscoveredSession,
        playerName: String = localDeviceName
    ) {
        discovery.stopDiscovery()
        _lobbyState.value = MultiplayerLobbyState.Connecting(session)

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
                        onPeerDisconnected("Lost connection to host")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to session: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _lobbyState.value = MultiplayerLobbyState.Disconnected("Could not connect to host at ${session.hostIp}")
                }
            }
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
     * Called by Host when both players are connected and the Host taps Start Game.
     */
    fun launchGame(extraConfigPayload: String = "") {
        val currentState = _lobbyState.value
        if (currentState is MultiplayerLobbyState.Connected && currentState.isHost) {
            val playersJsonArr = org.json.JSONArray().apply {
                currentState.players.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("isHost", p.isHost)
                        put("role", p.role)
                    })
                }
            }
            val startPayload = JSONObject().apply {
                put("config", extraConfigPayload)
                put("players", playersJsonArr)
            }.toString()

            val msg = MultiplayerMessage(
                type = MultiplayerMessage.TYPE_GAME_START,
                sessionId = currentState.session.sessionId,
                senderId = localPlayerId,
                payload = startPayload
            )
            broadcastToClients(msg)

            val startInfo = GameStartInfo(
                gameId = currentState.session.gameId,
                localRole = "PLAYER_1",
                players = currentState.players,
                isHost = true,
                configPayload = extraConfigPayload
            )
            _gameStartEvent.tryEmit(startInfo)

            _lobbyState.value = MultiplayerLobbyState.InGame(
                session = currentState.session,
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
        val sessionId = when (currentState) {
            is MultiplayerLobbyState.InGame -> currentState.session.sessionId
            is MultiplayerLobbyState.Connected -> currentState.session.sessionId
            else -> return
        }
        val isHost = (currentState as? MultiplayerLobbyState.InGame)?.isHost ?: true

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
                onPeerDisconnected(msg.payload.ifBlank { "Session is full (max players reached)" })
            }
            MultiplayerMessage.TYPE_PLAYER_LIST -> {
                try {
                    val arr = org.json.JSONArray(msg.payload)
                    val updatedPlayers = mutableListOf<MultiplayerPlayer>()
                    for (i in 0 until arr.length()) {
                        val pObj = arr.getJSONObject(i)
                        updatedPlayers.add(
                            MultiplayerPlayer(
                                id = pObj.getString("id"),
                                name = pObj.getString("name"),
                                isHost = pObj.getBoolean("isHost"),
                                role = pObj.getString("role")
                            )
                        )
                    }
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
                val hostName = try {
                    JSONObject(msg.payload).optString("hostName", session.hostName)
                } catch (e: Exception) {
                    session.hostName
                }
                val actualGameId = try {
                    JSONObject(msg.payload).optString("gameId", session.gameId)
                } catch (e: Exception) {
                    session.gameId
                }
                val actualGameTitle = try {
                    JSONObject(msg.payload).optString("gameTitle", session.gameTitle)
                } catch (e: Exception) {
                    session.gameTitle
                }
                val hostColor = try {
                    JSONObject(msg.payload).optString("chessHostColor", "WHITE")
                } catch (e: Exception) {
                    "WHITE"
                }
                _chessHostColor.value = hostColor

                val assignedRole = try {
                    JSONObject(msg.payload).optString("role", "PLAYER_2")
                } catch (e: Exception) {
                    "PLAYER_2"
                }
                val maxPlayers = try {
                    JSONObject(msg.payload).optInt("maxPlayers", session.maxPlayers)
                } catch (e: Exception) {
                    session.maxPlayers
                }

                val playersList = mutableListOf<MultiplayerPlayer>()
                try {
                    val pArr = JSONObject(msg.payload).optJSONArray("players")
                    if (pArr != null) {
                        for (i in 0 until pArr.length()) {
                            val pObj = pArr.getJSONObject(i)
                            playersList.add(
                                MultiplayerPlayer(
                                    id = pObj.getString("id"),
                                    name = pObj.getString("name"),
                                    isHost = pObj.getBoolean("isHost"),
                                    role = pObj.getString("role")
                                )
                            )
                        }
                    }
                } catch (e: Exception) {}

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
                            val pArr = json.getJSONArray("players")
                            val pList = mutableListOf<MultiplayerPlayer>()
                            for (i in 0 until pArr.length()) {
                                val pObj = pArr.getJSONObject(i)
                                pList.add(
                                    MultiplayerPlayer(
                                        id = pObj.getString("id"),
                                        name = pObj.getString("name"),
                                        isHost = pObj.getBoolean("isHost"),
                                        role = pObj.getString("role")
                                    )
                                )
                            }
                            parsedPlayers = pList
                        }
                    }
                } catch (e: Exception) {}

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

                val updatedSession = session.copy(sessionId = msg.sessionId, currentPlayers = finalPlayers.size)
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
                onPeerDisconnected("Host closed the room")
            }
        }
    }

    private fun broadcastToClients(msg: MultiplayerMessage) {
        scope.launch(Dispatchers.IO) {
            synchronized(clientSockets) {
                clientWriters.forEach { writer ->
                    sendRawToWriter(writer, msg)
                }
            }
        }
    }

    private fun sendRawToWriter(writer: BufferedWriter, msg: MultiplayerMessage) {
        try {
            writer.write(msg.toJson())
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send message: ${e.message}")
        }
    }

    private fun onPeerDisconnected(reason: String) {
        scope.launch(Dispatchers.Main) {
            val current = _lobbyState.value
            if (current is MultiplayerLobbyState.Connected || current is MultiplayerLobbyState.InGame) {
                _lobbyState.value = MultiplayerLobbyState.Disconnected(reason)
            }
        }
    }

    fun disconnect() {
        discovery.stopAnnouncing()
        discovery.stopDiscovery()

        hostAcceptJob?.cancel()
        hostAcceptJob = null

        try { hostServerSocket?.close() } catch (ignored: Exception) {}
        hostServerSocket = null

        synchronized(clientSockets) {
            clientSockets.forEach { try { it.close() } catch (ignored: Exception) {} }
            clientSockets.clear()
            clientWriters.clear()
        }

        clientReaderJob?.cancel()
        clientReaderJob = null

        try { clientSocket?.close() } catch (ignored: Exception) {}
        clientSocket = null
        clientWriter = null

        _lobbyState.value = MultiplayerLobbyState.Idle
        _chessHostColor.value = "WHITE"
    }

    companion object {
        private const val TAG = "WifiMultiplayer"
        const val DEFAULT_GAME_PORT = 8890
    }
}
