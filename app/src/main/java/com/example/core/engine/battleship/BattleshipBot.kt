package com.example.core.engine.battleship

/**
 * Smart Battleship Bot utilizing classic Hunt & Target AI.
 *
 * Requirements:
 * 1. Hunt Mode:
 *    - Searches untried coordinates using a checkerboard/parity strategy (row + col) % 2 == 0.
 *    - Since the smallest ship is 2 cells, every ship must cross at least one parity square.
 *    - Avoids already fired coordinates.
 *
 * 2. Target Mode:
 *    - Triggered whenever an unsunk hit is recorded.
 *    - When only 1 hit is in the active hit cluster: fires at adjacent orthogonal neighbors (N, S, E, W).
 *    - When 2+ hits are in the active hit cluster: infers line orientation (Horizontal vs Vertical).
 *    - Extends the line in both directions to finish off the ship.
 *    - Once the ship is sunk, removes those hits from the active targeting queue.
 *    - Reverts to Hunt Mode once all active damaged ships are sunk.
 *
 * 3. Fair Play Guarantee:
 *    - The bot NEVER peeks into or reads the opponent's private ship placements.
 *    - It bases all decisions solely on its own public targeting radar (hits & misses).
 */
class BattleshipBot {

    /**
     * Set of hits on ships that have NOT yet been confirmed sunk.
     */
    private val activeHits = mutableListOf<CellCoordinate>()

    fun reset() {
        activeHits.clear()
    }

    /**
     * Reports the outcome of a bot's shot so the AI can update its targeting memory.
     */
    fun onShotResult(coord: CellCoordinate, outcome: ShotOutcome) {
        when (outcome) {
            is ShotOutcome.Hit -> {
                if (outcome.isSunk) {
                    // Ship has been sunk! Remove all coordinates belonging to this sunk ship from activeHits
                    val sunkCoords = outcome.sunkShip?.occupiedCoordinates
                    if (sunkCoords != null) {
                        activeHits.removeAll(sunkCoords)
                    } else {
                        // In case sunkShip placement object wasn't supplied directly (e.g. over network),
                        // clear the connected component of hits around this coord
                        removeConnectedCluster(coord)
                    }
                } else {
                    // Damaged but still afloat -> add to active hits for targeting
                    if (!activeHits.contains(coord)) {
                        activeHits.add(coord)
                    }
                }
            }
            is ShotOutcome.Miss -> {
                // No change to activeHits on a miss
            }
            else -> {}
        }
    }

    private fun removeConnectedCluster(anchor: CellCoordinate) {
        val toRemove = mutableSetOf<CellCoordinate>()
        val queue = ArrayDeque<CellCoordinate>()
        queue.add(anchor)
        toRemove.add(anchor)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val neighbors = getNeighbors(curr).filter { activeHits.contains(it) && !toRemove.contains(it) }
            for (nbr in neighbors) {
                toRemove.add(nbr)
                queue.add(nbr)
            }
        }
        activeHits.removeAll(toRemove)
    }

    /**
     * Decides the next coordinate to fire at based on [radar].
     * Never fires at an already targeted cell.
     */
    fun decideMove(radar: TargetingRadar): CellCoordinate {
        try {
            // If there are active unsunk hits, execute TARGET MODE
            if (activeHits.isNotEmpty()) {
                val targetMove = decideTargetMove(radar)
                if (targetMove != null) {
                    return targetMove
                }
            }

            // Otherwise, execute HUNT MODE
            return decideHuntMove(radar)
        } catch (_: Exception) {
            // Absolute fallback: pick first cell not yet fired at
            for (r in 0 until 10) {
                for (c in 0 until 10) {
                    val coord = CellCoordinate(r, c)
                    if (!radar.hasFiredAt(coord)) return coord
                }
            }
            return CellCoordinate(0, 0)
        }
    }

    /**
     * Target Mode: Finds the highest priority candidate adjacent to or collinear with active hits.
     */
    private fun decideTargetMove(radar: TargetingRadar): CellCoordinate? {
        // Group hits that are adjacent / collinear
        val cluster = activeHits.toList()

        if (cluster.size >= 2) {
            // Check if hits form a horizontal line (same row)
            val isHorizontal = cluster.all { it.row == cluster[0].row }
            // Check if hits form a vertical line (same col)
            val isVertical = cluster.all { it.col == cluster[0].col }

            if (isHorizontal) {
                val row = cluster[0].row
                val minCol = cluster.minOf { it.col }
                val maxCol = cluster.maxOf { it.col }

                // Try extending left (col - 1)
                val left = if (minCol > 0) CellCoordinate(row, minCol - 1).takeIf { !radar.hasFiredAt(it) } else null
                if (left != null) return left

                // Try extending right (col + 1)
                val right = if (maxCol < 9) CellCoordinate(row, maxCol + 1).takeIf { !radar.hasFiredAt(it) } else null
                if (right != null) return right
            } else if (isVertical) {
                val col = cluster[0].col
                val minRow = cluster.minOf { it.row }
                val maxRow = cluster.maxOf { it.row }

                // Try extending up (row - 1)
                val up = if (minRow > 0) CellCoordinate(minRow - 1, col).takeIf { !radar.hasFiredAt(it) } else null
                if (up != null) return up

                // Try extending down (row + 1)
                val down = if (maxRow < 9) CellCoordinate(maxRow + 1, col).takeIf { !radar.hasFiredAt(it) } else null
                if (down != null) return down
            }
        }

        // If only 1 hit, or if line ends were blocked, check orthogonal neighbors of all active hits
        val candidates = mutableListOf<CellCoordinate>()
        for (hit in cluster) {
            for (neighbor in getNeighbors(hit)) {
                if (!radar.hasFiredAt(neighbor) && !candidates.contains(neighbor)) {
                    candidates.add(neighbor)
                }
            }
        }

        if (candidates.isNotEmpty()) {
            // Shuffle to avoid predictable firing order across rounds
            return candidates.shuffled().first()
        }

        return null
    }

    /**
     * Hunt Mode: Searches untried cells using parity checkerboard pattern.
     */
    private fun decideHuntMove(radar: TargetingRadar): CellCoordinate {
        val untriedParity = mutableListOf<CellCoordinate>()
        val untriedAll = mutableListOf<CellCoordinate>()

        for (r in 0 until 10) {
            for (c in 0 until 10) {
                val coord = CellCoordinate(r, c)
                if (!radar.hasFiredAt(coord)) {
                    if ((r + c) % 2 == 0) {
                        untriedParity.add(coord)
                    }
                    untriedAll.add(coord)
                }
            }
        }

        // Prefer parity coordinates first
        if (untriedParity.isNotEmpty()) {
            return untriedParity.shuffled().first()
        }

        // Fallback to any remaining untried cell
        return untriedAll.shuffled().first()
    }

    private fun getNeighbors(coord: CellCoordinate): List<CellCoordinate> {
        val list = mutableListOf<CellCoordinate>()
        if (coord.row > 0) list.add(CellCoordinate(coord.row - 1, coord.col)) // North
        if (coord.row < 9) list.add(CellCoordinate(coord.row + 1, coord.col)) // South
        if (coord.col > 0) list.add(CellCoordinate(coord.row, coord.col - 1)) // West
        if (coord.col < 9) list.add(CellCoordinate(coord.row, coord.col + 1)) // East
        return list
    }
}
