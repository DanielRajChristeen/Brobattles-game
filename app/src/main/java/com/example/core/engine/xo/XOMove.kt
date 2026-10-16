package com.example.core.engine.xo

import com.example.core.engine.Move

data class XOMove(val toIndex: Int, val fromIndex: Int? = null) : Move {
    constructor(index: Int) : this(toIndex = index, fromIndex = null)

    init {
        require(toIndex in 0..8) { "XO Move toIndex must be in range 0..8, was $toIndex" }
        if (fromIndex != null) {
            require(fromIndex in 0..8) { "XO Move fromIndex must be in range 0..8, was $fromIndex" }
            require(fromIndex != toIndex) { "fromIndex and toIndex cannot be identical" }
        }
    }

    val index: Int get() = toIndex
    val row: Int get() = toIndex / 3
    val col: Int get() = toIndex % 3
}
