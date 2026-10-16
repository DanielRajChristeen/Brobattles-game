package com.example.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.engine.GameEvent
import com.example.core.engine.PlayerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class AndroidBluetoothTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : BluetoothTransport {

    companion object {
        private const val TAG = "ChillBtTransport"
        private const val SERVICE_NAME = "ChillGamesP2P"
        val APP_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    override val incomingEvents: SharedFlow<GameEvent> = _incomingEvents.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    override val discoveredDevices: StateFlow<List<BluetoothDeviceItem>> = _discoveredDevices.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var isReceiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { dev ->
                        val name = dev.name ?: "Nearby Device"
                        val address = dev.address
                        val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
                        val currentList = _discoveredDevices.value.toMutableList()
                        if (currentList.none { it.address == address }) {
                            currentList.add(BluetoothDeviceItem(name, address, isBonded))
                            _discoveredDevices.value = currentList
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_connectionState.value is BluetoothConnectionState.Discovering) {
                        Log.d(TAG, "Discovery finished")
                    }
                }
            }
        }
    }

    override fun isBluetoothSupported(): Boolean = adapter != null

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    override fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val advertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            scan && connect && advertise
        } else {
            val bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            val btAdmin = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            bt && btAdmin
        }
    }

    @SuppressLint("MissingPermission")
    override fun startHosting(gameId: String) {
        disconnect()
        if (!isBluetoothSupported()) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth is not supported on this device")
            return
        }
        if (!isBluetoothEnabled()) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth is turned off. Please enable it.")
            return
        }
        if (!hasRequiredPermissions()) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth permissions are missing.")
            return
        }

        val deviceName = try {
            adapter?.name ?: "Host Device"
        } catch (e: Exception) {
            "Host Device"
        }

        _connectionState.value = BluetoothConnectionState.Advertising(deviceName)

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                val socket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null

                if (socket != null) {
                    onSocketConnected(socket, BluetoothRole.HOST, gameId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hosting failed or cancelled", e)
                if (_connectionState.value is BluetoothConnectionState.Advertising) {
                    _connectionState.value = BluetoothConnectionState.Error("Failed to host: ${e.message ?: "Unknown"}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (!isBluetoothSupported() || !isBluetoothEnabled() || !hasRequiredPermissions()) {
            _connectionState.value = BluetoothConnectionState.Error("Cannot discover: Bluetooth disabled or missing permissions")
            return
        }

        _discoveredDevices.value = emptyList()
        // Add already bonded devices first
        try {
            val bonded = adapter?.bondedDevices.orEmpty().map {
                BluetoothDeviceItem(it.name ?: "Paired Device", it.address, isBonded = true)
            }
            _discoveredDevices.value = bonded
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading bonded devices", e)
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        }

        try {
            adapter?.cancelDiscovery()
            adapter?.startDiscovery()
            _connectionState.value = BluetoothConnectionState.Discovering
        } catch (e: Exception) {
            Log.w(TAG, "Error starting discovery", e)
            _connectionState.value = BluetoothConnectionState.Error("Failed to start discovery: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        try {
            adapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Failed canceling discovery", e)
        }
        unregisterDiscoveryReceiver()
        if (_connectionState.value is BluetoothConnectionState.Discovering) {
            _connectionState.value = BluetoothConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToHost(device: BluetoothDeviceItem, gameId: String) {
        disconnect()
        if (!isBluetoothSupported() || !isBluetoothEnabled() || !hasRequiredPermissions()) {
            _connectionState.value = BluetoothConnectionState.Error("Cannot connect: Bluetooth disabled or missing permissions")
            return
        }

        _connectionState.value = BluetoothConnectionState.Connecting(device.name)
        stopDiscovery()

        connectJob = scope.launch(Dispatchers.IO) {
            try {
                val remoteDevice = adapter?.getRemoteDevice(device.address)
                if (remoteDevice == null) {
                    _connectionState.value = BluetoothConnectionState.Error("Target device not found")
                    return@launch
                }
                val socket = remoteDevice.createRfcommSocketToServiceRecord(APP_UUID)
                adapter?.cancelDiscovery()
                socket.connect()
                onSocketConnected(socket, BluetoothRole.CLIENT, gameId)
            } catch (e: Exception) {
                Log.w(TAG, "Connection failed", e)
                _connectionState.value = BluetoothConnectionState.Error("Connection failed: ${e.message ?: "Could not reach host"}")
            }
        }
    }

    private suspend fun onSocketConnected(socket: BluetoothSocket, role: BluetoothRole, gameId: String) {
        connectedSocket = socket
        writer = PrintWriter(socket.outputStream, true)
        reader = BufferedReader(InputStreamReader(socket.inputStream))

        val remoteName = try {
            socket.remoteDevice.name ?: "Opponent Phone"
        } catch (e: Exception) {
            "Opponent Phone"
        }

        _connectionState.value = BluetoothConnectionState.Connected(role, remoteName)

        // Send initial handshake
        val handshakeType = if (role == BluetoothRole.HOST) {
            BluetoothMessage.TYPE_HANDSHAKE_HOST
        } else {
            BluetoothMessage.TYPE_HANDSHAKE_CLIENT
        }
        val handshake = BluetoothMessage(handshakeType, gameId, payload = "ready")
        sendRawMessage(handshake)

        // Start reading loop
        startReadingLoop()
    }

    private fun startReadingLoop() {
        readJob = scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    val msg = BluetoothMessage.fromJson(line) ?: continue
                    handleIncomingMessage(msg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Socket read terminated", e)
            } finally {
                handleDisconnectInternal("Opponent disconnected or connection lost")
            }
        }
    }

    private suspend fun handleIncomingMessage(msg: BluetoothMessage) {
        when (msg.type) {
            BluetoothMessage.TYPE_GAME_EVENT -> {
                val event = parseGameEventFromPayload(msg.payload)
                if (event != null) {
                    _incomingEvents.emit(event)
                }
            }
            BluetoothMessage.TYPE_RESTART -> {
                val targetScore = msg.payload.toIntOrNull() ?: 5
                _incomingEvents.emit(GameEvent.MatchRestarted(targetScore = targetScore))
            }
            BluetoothMessage.TYPE_DISCONNECT -> {
                handleDisconnectInternal("Opponent left the game")
            }
        }
    }

    override suspend fun sendEvent(event: GameEvent): Boolean = withContext(Dispatchers.IO) {
        val payload = serializeGameEvent(event)
        val msg = BluetoothMessage(
            type = BluetoothMessage.TYPE_GAME_EVENT,
            gameId = "game",
            payload = payload
        )
        sendRawMessage(msg)
    }

    private fun sendRawMessage(msg: BluetoothMessage): Boolean {
        return try {
            val json = msg.toJson()
            writer?.println(json)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send Bluetooth message", e)
            false
        }
    }

    private fun handleDisconnectInternal(reason: String) {
        cleanupSockets()
        if (_connectionState.value is BluetoothConnectionState.Connected) {
            _connectionState.value = BluetoothConnectionState.ConnectionLost(reason)
        } else {
            _connectionState.value = BluetoothConnectionState.Disconnected
        }
    }

    override fun disconnect() {
        acceptJob?.cancel()
        connectJob?.cancel()
        readJob?.cancel()
        unregisterDiscoveryReceiver()
        cleanupSockets()
        _connectionState.value = BluetoothConnectionState.Disconnected
    }

    private fun cleanupSockets() {
        try {
            writer?.close()
            reader?.close()
            connectedSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing sockets", e)
        } finally {
            writer = null
            reader = null
            connectedSocket = null
            serverSocket = null
        }
    }

    private fun unregisterDiscoveryReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Receiver unregister error", e)
            }
            isReceiverRegistered = false
        }
    }

    private fun serializeGameEvent(event: GameEvent): String {
        val json = JSONObject()
        json.put("eventId", event.eventId)
        json.put("timestamp", event.timestamp)
        when (event) {
            is GameEvent.MovePlayed -> {
                json.put("type", "MOVE")
                json.put("player", event.player.name)
                json.put("actionType", event.actionType)
                json.put("payload", event.payload)
            }
            is GameEvent.RoundStarted -> {
                json.put("type", "ROUND_START")
            }
            is GameEvent.MatchRestarted -> {
                json.put("type", "MATCH_RESTART")
                json.put("targetScore", event.targetScore)
            }
        }
        return json.toString()
    }

    private fun parseGameEventFromPayload(payload: String): GameEvent? {
        return try {
            val json = JSONObject(payload)
            val eventId = json.optString("eventId", UUID.randomUUID().toString())
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            when (json.getString("type")) {
                "MOVE" -> {
                    val player = PlayerId.valueOf(json.getString("player"))
                    val actionType = json.getString("actionType")
                    val movePayload = json.optString("payload", "")
                    GameEvent.MovePlayed(player, actionType, movePayload, eventId, timestamp)
                }
                "ROUND_START" -> GameEvent.RoundStarted(eventId, timestamp)
                "MATCH_RESTART" -> {
                    val target = json.optInt("targetScore", 5)
                    GameEvent.MatchRestarted(target, eventId, timestamp)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse game event JSON", e)
            null
        }
    }
}
