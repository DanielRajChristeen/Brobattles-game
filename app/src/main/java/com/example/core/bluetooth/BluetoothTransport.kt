package com.example.core.bluetooth

import com.example.core.engine.GameEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport contract abstracting hardware Bluetooth communication from game logic.
 */
interface BluetoothTransport {
    val connectionState: StateFlow<BluetoothConnectionState>
    val incomingEvents: SharedFlow<GameEvent>
    val discoveredDevices: StateFlow<List<BluetoothDeviceItem>>

    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun hasRequiredPermissions(): Boolean

    fun startHosting(gameId: String)
    fun startDiscovery()
    fun stopDiscovery()
    fun connectToHost(device: BluetoothDeviceItem, gameId: String)
    suspend fun sendEvent(event: GameEvent): Boolean
    fun disconnect()
}
