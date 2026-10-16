package com.example.core.bluetooth

sealed interface BluetoothConnectionState {
    data object Disconnected : BluetoothConnectionState
    data object Initializing : BluetoothConnectionState
    data class Advertising(val deviceName: String) : BluetoothConnectionState
    data object Discovering : BluetoothConnectionState
    data class Connecting(val remoteDeviceName: String) : BluetoothConnectionState
    data class Connected(val role: BluetoothRole, val remoteDeviceName: String) : BluetoothConnectionState
    data class ConnectionLost(val reason: String, val canReconnect: Boolean = true) : BluetoothConnectionState
    data class Error(val message: String, val canRetry: Boolean = true) : BluetoothConnectionState

    val isConnected: Boolean
        get() = this is Connected

    val isBusy: Boolean
        get() = this is Advertising || this is Discovering || this is Connecting
}
