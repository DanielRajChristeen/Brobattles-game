package com.example.core.engine.tugofwar

import com.example.core.engine.Move

data class TugOfWarMove(
    val force: Float = 1.0f,
    val tapTimestamp: Long = System.currentTimeMillis()
) : Move
