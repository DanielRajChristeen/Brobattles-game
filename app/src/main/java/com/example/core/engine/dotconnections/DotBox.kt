package com.example.core.engine.dotconnections

data class DotBox(
    val row: Int,
    val col: Int,
    val owner: DotPlayer? = null,
    val completedAtTimestamp: Long = 0L
)
