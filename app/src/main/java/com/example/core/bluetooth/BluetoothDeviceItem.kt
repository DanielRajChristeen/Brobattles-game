package com.example.core.bluetooth

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
)
