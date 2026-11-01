package com.example.core.engine.battleship

/**
 * Authoritative game engine for classic Battleship.
 *
 * Rules:
 * - 10x10 grid (A1 to J10).
 * - 5 standard ships: Carrier (5), Battleship (4), Cruiser (3), Submarine (3), Destroyer (2).
 * - Ships can be placed horizontally or vertically, non-overlapping, strictly within grid.
 * - Extra-shot rule: Any HIT or SUNK grants the firing player another shot immediately.
 * - A MISS passes the turn to the opponent.
 * - Repeated shots at already-targeted coordinates are rejected without consuming a turn.
 * - Game ends when all 5 enemy ships (17 cells) are sunk.
 */
class BattleshipEngine {

    var state: BattleshipState = BattleshipState()
        private set

    fun resetGame() {
        state = BattleshipState()
    }

    // ==========================================
    // Ship Placement Validation & Management
    // ==========================================

    companion object {
        const val GRID_SIZE = 10

        /**
         * Checks whether a proposed ship placement is valid within grid boundaries
         * and does not overlap with any already placed ships.
         */
        fun isValidPlacement(
            shipType: ShipType,
            startRow: Int,
            startCol: Int,
            orientation: ShipOrientation,
            existingPlacements: List<ShipPlacement>
        ): Boolean {
            val length = shipType.length

            // Boundary checks
            when (orientation) {
                ShipOrientation.HORIZONTAL -> {
                    if (startRow !in 0 until GRID_SIZE) return false
                    if (startCol !in 0 until GRID_SIZE) return false
                    if (startCol + length > GRID_SIZE) return false
                }
                ShipOrientation.VERTICAL -> {
                    if (startRow !in 0 until GRID_SIZE) return false
                    if (startCol !in 0 until GRID_SIZE) return false
                    if (startRow + length > GRID_SIZE) return false
                }
            }

            val proposedCoords = List(length) { offset ->
                when (orientation) {
                    ShipOrientation.HORIZONTAL -> CellCoordinate(startRow, startCol + offset)
                    ShipOrientation.VERTICAL -> CellCoordinate(startRow + offset, startCol)
                }
            }

            // Overlap check (excluding the ship being re-positioned if it's in existingPlacements)
            val otherPlacements = existingPlacements.filter { it.shipType != shipType }
            val occupiedSet = otherPlacements.flatMap { it.occupiedCoordinates }.toSet()

            return proposedCoords.none { occupiedSet.contains(it) }
        }

        /**
         * Helper to auto-generate a valid random fleet layout (used for Bot deployment or quick deploy).
         */
        fun generateRandomFleet(): List<ShipPlacement> {
            val placements = mutableListOf<ShipPlacement>()
            val random = java.util.Random()

            for (shipType in ShipType.ALL_SHIPS) {
                var placed = false
                var attempts = 0
                while (!placed && attempts < 500) {
                    attempts++
                    val orientation = if (random.nextBoolean()) ShipOrientation.HORIZONTAL else ShipOrientation.VERTICAL
                    val maxRow = if (orientation == ShipOrientation.VERTICAL) GRID_SIZE - shipType.length else GRID_SIZE - 1
                    val maxCol = if (orientation == ShipOrientation.HORIZONTAL) GRID_SIZE - shipType.length else GRID_SIZE - 1

                    val r = random.nextInt(maxRow + 1)
                    val c = random.nextInt(maxCol + 1)

                    if (isValidPlacement(shipType, r, c, orientation, placements)) {
                        placements.add(ShipPlacement(shipType, CellCoordinate(r, c), orientation))
                        placed = true
                    }
                }
            }
            return placements
        }
    }

    /**
     * Attempts to place a ship for the specified player.
     * Replaces any existing placement of the same ship type.
     */
    fun placeShip(
        player: BattleshipPlayer,
        shipType: ShipType,
        startRow: Int,
        startCol: Int,
        orientation: ShipOrientation
    ): Boolean {
        val currentFleet = state.fleetFor(player)
        if (!isValidPlacement(shipType, startRow, startCol, orientation, currentFleet.placements)) {
            return false
        }

        val newPlacement = ShipPlacement(shipType, CellCoordinate(startRow, startCol), orientation)
        val updatedPlacements = currentFleet.placements.filter { it.shipType != shipType } + newPlacement
        val isComplete = updatedPlacements.map { it.shipType }.containsAll(ShipType.ALL_SHIPS)

        val updatedFleet = currentFleet.copy(
            placements = updatedPlacements,
            isDeploymentComplete = isComplete
        )

        state = if (player == BattleshipPlayer.PLAYER_1) {
            state.copy(p1Fleet = updatedFleet)
        } else {
            state.copy(p2Fleet = updatedFleet)
        }
        return true
    }

    /**
     * Removes a placed ship for the specified player so it can be re-positioned.
     */
    fun removeShip(player: BattleshipPlayer, shipType: ShipType) {
        val currentFleet = state.fleetFor(player)
        val updatedPlacements = currentFleet.placements.filter { it.shipType != shipType }
        val updatedFleet = currentFleet.copy(
            placements = updatedPlacements,
            isDeploymentComplete = false
        )
        state = if (player == BattleshipPlayer.PLAYER_1) {
            state.copy(p1Fleet = updatedFleet)
        } else {
            state.copy(p2Fleet = updatedFleet)
        }
    }

    /**
     * Attempts to rotate an existing placed ship in place.
     * Returns true if the rotation is valid and was applied, false otherwise.
     */
    fun rotateShip(player: BattleshipPlayer, shipType: ShipType): Boolean {
        val currentFleet = state.fleetFor(player)
        val placement = currentFleet.placements.firstOrNull { it.shipType == shipType } ?: return false
        val newOrientation = if (placement.orientation == ShipOrientation.HORIZONTAL) {
            ShipOrientation.VERTICAL
        } else {
            ShipOrientation.HORIZONTAL
        }
        return placeShip(player, shipType, placement.start.row, placement.start.col, newOrientation)
    }

    /**
     * Sets the complete fleet directly (used when setting bot fleet or restoring placements).
     */
    fun setFleet(player: BattleshipPlayer, placements: List<ShipPlacement>): Boolean {
        if (placements.size != ShipType.ALL_SHIPS.size) return false
        val currentFleet = state.fleetFor(player)
        val updatedFleet = currentFleet.copy(
            placements = placements,
            isDeploymentComplete = true
        )
        state = if (player == BattleshipPlayer.PLAYER_1) {
            state.copy(p1Fleet = updatedFleet)
        } else {
            state.copy(p2Fleet = updatedFleet)
        }
        return true
    }

    fun setPhase(phase: BattleshipPhase) {
        state = state.copy(phase = phase)
    }

    fun setActivePlayerForTesting(player: BattleshipPlayer) {
        state = state.copy(activePlayer = player)
    }

    // ==========================================
    // Combat Firing Logic
    // ==========================================

    /**
     * Executes a shot by [firingPlayer] at [coord].
     *
     * Returns:
     * - [ShotOutcome.AlreadyTargeted] if coordinate was previously targeted.
     * - [ShotOutcome.InvalidMove] if coordinate is out of bounds or game is not in BATTLE phase.
     * - [ShotOutcome.Hit] if an enemy ship was hit. If that hit sunk the ship, [ShotOutcome.Hit.isSunk] is true.
     * - [ShotOutcome.Miss] if water was struck.
     */
    fun fireShot(firingPlayer: BattleshipPlayer, coord: CellCoordinate): ShotOutcome {
        if (state.phase != BattleshipPhase.BATTLE) {
            return ShotOutcome.InvalidMove
        }
        if (state.activePlayer != firingPlayer) {
            return ShotOutcome.InvalidMove
        }

        val enemyPlayer = firingPlayer.opponent()
        val radar = state.radarFor(firingPlayer)
        val enemyFleet = state.fleetFor(enemyPlayer)

        if (radar.hasFiredAt(coord)) {
            return ShotOutcome.AlreadyTargeted
        }

        val targetShip = enemyFleet.getShipAt(coord)
        val isHit = targetShip != null

        // Update enemy fleet incoming shots
        val updatedIncoming = enemyFleet.incomingShots + (coord to isHit)
        val updatedEnemyFleet = enemyFleet.copy(incomingShots = updatedIncoming)

        // Determine if target ship is now sunk
        val isSunk = if (targetShip != null) {
            updatedEnemyFleet.isShipSunk(targetShip)
        } else false

        val sunkShipPlacement = if (isSunk) targetShip else null
        val updatedSunkList = if (isSunk && targetShip != null) {
            radar.sunkEnemyShips + targetShip.shipType
        } else {
            radar.sunkEnemyShips
        }

        val outcome: ShotOutcome = if (isHit && targetShip != null) {
            ShotOutcome.Hit(
                coordinate = coord,
                shipType = targetShip.shipType,
                isSunk = isSunk,
                sunkShip = sunkShipPlacement
            )
        } else {
            ShotOutcome.Miss(coord)
        }

        // Update radar
        val updatedRadar = radar.copy(
            shots = radar.shots + (coord to isHit),
            sunkEnemyShips = updatedSunkList,
            lastShotOutcome = outcome
        )

        // Check if all enemy ships are sunk
        val allSunk = updatedEnemyFleet.isAllSunk

        val newPhase = if (allSunk) {
            BattleshipPhase.GAME_OVER
        } else {
            BattleshipPhase.BATTLE
        }

        val winner = if (allSunk) firingPlayer else null

        // Extra shot rule: If HIT, firing player retains turn. If MISS, turn passes to opponent!
        val nextActivePlayer = if (allSunk) {
            firingPlayer
        } else if (isHit) {
            firingPlayer // Extra shot!
        } else {
            enemyPlayer // Turn passes on miss
        }

        val announcement = when {
            allSunk -> "Victory! All enemy vessels have been destroyed!"
            isSunk && targetShip != null -> "Direct hit! ${targetShip.shipType.title} SUNK! Take another shot!"
            isHit -> "Direct hit! Extra shot awarded!"
            else -> "Splash! Shot missed. Turn passes to opponent."
        }

        state = if (firingPlayer == BattleshipPlayer.PLAYER_1) {
            state.copy(
                p1Radar = updatedRadar,
                p2Fleet = updatedEnemyFleet,
                phase = newPhase,
                activePlayer = nextActivePlayer,
                winner = winner,
                turnNumber = state.turnNumber + 1,
                latestAnnouncement = announcement
            )
        } else {
            state.copy(
                p2Radar = updatedRadar,
                p1Fleet = updatedEnemyFleet,
                phase = newPhase,
                activePlayer = nextActivePlayer,
                winner = winner,
                turnNumber = state.turnNumber + 1,
                latestAnnouncement = announcement
            )
        }

        return outcome
    }

    /**
     * Applies a remote shot result into our local state in WiFi Play mode.
     * When remote fires at us, or when we receive the authoritative result from the host.
     */
    fun applyRemoteIncomingShot(firingPlayer: BattleshipPlayer, coord: CellCoordinate, isHit: Boolean, sunkType: ShipType?): ShotOutcome {
        val enemyPlayer = firingPlayer.opponent()
        val radar = state.radarFor(firingPlayer)
        val enemyFleet = state.fleetFor(enemyPlayer)

        val updatedIncoming = enemyFleet.incomingShots + (coord to isHit)
        val updatedEnemyFleet = enemyFleet.copy(incomingShots = updatedIncoming)

        val isSunk = sunkType != null
        val updatedSunk = if (isSunk && sunkType != null && !radar.sunkEnemyShips.contains(sunkType)) {
            radar.sunkEnemyShips + sunkType
        } else {
            radar.sunkEnemyShips
        }

        val outcome = if (isHit) {
            ShotOutcome.Hit(
                coordinate = coord,
                shipType = sunkType ?: ShipType.DESTROYER,
                isSunk = isSunk,
                sunkShip = null
            )
        } else {
            ShotOutcome.Miss(coord)
        }

        val updatedRadar = radar.copy(
            shots = radar.shots + (coord to isHit),
            sunkEnemyShips = updatedSunk,
            lastShotOutcome = outcome
        )

        val allSunk = updatedRadar.totalHits >= ShipType.TOTAL_FLEET_CELLS
        val newPhase = if (allSunk) BattleshipPhase.GAME_OVER else BattleshipPhase.BATTLE
        val winner = if (allSunk) firingPlayer else null
        val nextActive = if (allSunk) firingPlayer else if (isHit) firingPlayer else enemyPlayer

        state = if (firingPlayer == BattleshipPlayer.PLAYER_1) {
            state.copy(
                p1Radar = updatedRadar,
                p2Fleet = updatedEnemyFleet,
                phase = newPhase,
                activePlayer = nextActive,
                winner = winner,
                turnNumber = state.turnNumber + 1
            )
        } else {
            state.copy(
                p2Radar = updatedRadar,
                p1Fleet = updatedEnemyFleet,
                phase = newPhase,
                activePlayer = nextActive,
                winner = winner,
                turnNumber = state.turnNumber + 1
            )
        }

        return outcome
    }
}
