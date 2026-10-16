package com.example.core.engine.dotconnections

import kotlin.random.Random

object DotConnectionsBot {

    fun decideMove(
        state: DotConnectionsGameState,
        difficulty: DotDifficulty
    ): DotEdge? {
        if (state.isGameOver) return null

        val availableEdges = DotConnectionsEngine.getAllPossibleEdges(state.boardSize)
            .filter { !state.isEdgeOccupied(it) }

        if (availableEdges.isEmpty()) return null

        // Categorize available moves
        val doubleCaptures = mutableListOf<DotEdge>()
        val singleCaptures = mutableListOf<DotEdge>()
        val safeEdges = mutableListOf<DotEdge>()
        val sacrificeEdges = mutableListOf<DotEdge>()

        for (edge in availableEdges) {
            val adjacent = edge.getAdjacentBoxCoordinates(state.boardSize)
            var completes = 0
            var createsThirdSide = false

            for ((r, c) in adjacent) {
                // If box already owned, skip
                if (state.getBoxOwner(r, c) != null) continue

                val currentSides = state.countDrawnSides(r, c)
                if (currentSides == 3) {
                    completes++
                } else if (currentSides == 2) {
                    createsThirdSide = true
                }
            }

            when {
                completes == 2 -> doubleCaptures.add(edge)
                completes == 1 -> singleCaptures.add(edge)
                !createsThirdSide -> safeEdges.add(edge)
                else -> sacrificeEdges.add(edge)
            }
        }

        return when (difficulty) {
            DotDifficulty.EASY -> decideEasy(
                doubleCaptures,
                singleCaptures,
                safeEdges,
                sacrificeEdges,
                availableEdges
            )
            DotDifficulty.MEDIUM -> decideMedium(
                doubleCaptures,
                singleCaptures,
                safeEdges,
                sacrificeEdges,
                availableEdges
            )
            DotDifficulty.HARD -> decideHard(
                state,
                doubleCaptures,
                singleCaptures,
                safeEdges,
                sacrificeEdges,
                availableEdges
            )
        }
    }

    private fun decideEasy(
        doubleCaptures: List<DotEdge>,
        singleCaptures: List<DotEdge>,
        safeEdges: List<DotEdge>,
        sacrificeEdges: List<DotEdge>,
        all: List<DotEdge>
    ): DotEdge {
        // 80% takes box if available
        if (Random.nextFloat() < 0.80f) {
            if (doubleCaptures.isNotEmpty()) return doubleCaptures.random()
            if (singleCaptures.isNotEmpty()) return singleCaptures.random()
        }

        // 70% prefers safe moves
        if (safeEdges.isNotEmpty() && Random.nextFloat() < 0.70f) {
            return safeEdges.random()
        }

        return all.random()
    }

    private fun decideMedium(
        doubleCaptures: List<DotEdge>,
        singleCaptures: List<DotEdge>,
        safeEdges: List<DotEdge>,
        sacrificeEdges: List<DotEdge>,
        all: List<DotEdge>
    ): DotEdge {
        // Always take box completions! Double capture first
        if (doubleCaptures.isNotEmpty()) return doubleCaptures.random()
        if (singleCaptures.isNotEmpty()) return singleCaptures.random()

        // Always choose a safe edge if any exist (never give opponent free boxes)
        if (safeEdges.isNotEmpty()) {
            return safeEdges.random()
        }

        // If forced to sacrifice, pick from sacrifices
        return if (sacrificeEdges.isNotEmpty()) sacrificeEdges.random() else all.random()
    }

    private fun decideHard(
        state: DotConnectionsGameState,
        doubleCaptures: List<DotEdge>,
        singleCaptures: List<DotEdge>,
        safeEdges: List<DotEdge>,
        sacrificeEdges: List<DotEdge>,
        all: List<DotEdge>
    ): DotEdge {
        // Priority 1: Double captures (grab 2 boxes immediately)
        if (doubleCaptures.isNotEmpty()) return doubleCaptures.random()

        // Priority 2: Single captures
        if (singleCaptures.isNotEmpty()) {
            return singleCaptures.random()
        }

        // Priority 3: Safe moves.
        // Heuristic: Prefer safe edges on perimeter or edges that do not connect to already occupied lines
        if (safeEdges.isNotEmpty()) {
            // Sort safe edges by how few neighbors are drawn (keep game open)
            val bestSafe = safeEdges.minByOrNull { edge ->
                val (r, c) = when (edge.orientation) {
                    EdgeOrientation.HORIZONTAL -> Pair(edge.row, edge.col)
                    EdgeOrientation.VERTICAL -> Pair(edge.row, edge.col)
                }
                edge.getAdjacentBoxCoordinates(state.boardSize)
                    .sumOf { (br, bc) -> state.countDrawnSides(br, bc) }
            }
            return bestSafe ?: safeEdges.random()
        }

        // Priority 4: When forced to sacrifice, minimize the chain given away!
        // Calculate the damage of each sacrifice edge
        if (sacrificeEdges.isNotEmpty()) {
            val minDamageEdge = sacrificeEdges.minByOrNull { edge ->
                estimateSacrificeChainLength(state, edge)
            }
            return minDamageEdge ?: sacrificeEdges.random()
        }

        return all.random()
    }

    /**
     * Estimates how many boxes the opponent can take if this sacrifice edge is given.
     */
    private fun estimateSacrificeChainLength(state: DotConnectionsGameState, edge: DotEdge): Int {
        val adjacent = edge.getAdjacentBoxCoordinates(state.boardSize)
        var vulnerableBoxes = 0
        for ((r, c) in adjacent) {
            if (state.getBoxOwner(r, c) == null && state.countDrawnSides(r, c) == 2) {
                vulnerableBoxes++
            }
        }
        return vulnerableBoxes
    }
}
