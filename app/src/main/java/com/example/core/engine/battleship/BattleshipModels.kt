package com.example.core.engine.battleship

/**
 * Standard 5 naval ships of classic Battleship.
 * Total 17 occupied grid cells across the fleet.
 */
enum class ShipType(
    val title: String,
    val length: Int,
    val code: Char,
    val emoji: String
) {
    CARRIER("Aircraft Carrier", 5, 'A', "🚢"),
    BATTLESHIP("Battleship", 4, 'B', "🛳️"),
    CRUISER("Cruiser", 3, 'C', "🛥️"),
    SUBMARINE("Submarine", 3, 'S', "🌊"),
    DESTROYER("Destroyer", 2, 'D', "🚤");

    companion object {
        val ALL_SHIPS = listOf(CARRIER, BATTLESHIP, CRUISER, SUBMARINE, DESTROYER)
        const val TOTAL_FLEET_CELLS = 17 // 5 + 4 + 3 + 3 + 2
    }
}

enum class ShipOrientation {
    HORIZONTAL,
    VERTICAL
}

data class CellCoordinate(
    val row: Int,
    val col: Int
) {
    init {
        require(row in 0..9 && col in 0..9) { "Coordinates must be within 0..9 grid (got row=$row, col=$col)" }
    }

    /**
     * Human-friendly naval coordinate format, e.g. row 0, col 0 -> "A1"; row 9, col 9 -> "J10"
     */
    val label: String
        get() {
            val colLetter = ('A' + col)
            val rowNumber = row + 1
            return "$colLetter$rowNumber"
        }

    companion object {
        const val GRID_SIZE = 10

        fun fromIndex(index: Int): CellCoordinate {
            require(index in 0 until GRID_SIZE * GRID_SIZE)
            return CellCoordinate(row = index / GRID_SIZE, col = index % GRID_SIZE)
        }

        fun ofOrNull(row: Int, col: Int): CellCoordinate? {
            return if (row in 0 until GRID_SIZE && col in 0 until GRID_SIZE) {
                CellCoordinate(row, col)
            } else {
                null
            }
        }

        fun parse(label: String): CellCoordinate? {
            val trimmed = label.trim().uppercase()
            if (trimmed.length < 2 || trimmed.length > 3) return null
            val colChar = trimmed[0]
            if (colChar !in 'A'..'J') return null
            val col = colChar - 'A'
            val rowNum = trimmed.substring(1).toIntOrNull() ?: return null
            if (rowNum !in 1..10) return null
            return CellCoordinate(row = rowNum - 1, col = col)
        }
    }
}

/**
 * Represents a single placed ship on the 10x10 grid.
 */
data class ShipPlacement(
    val shipType: ShipType,
    val start: CellCoordinate,
    val orientation: ShipOrientation
) {
    val occupiedCoordinates: List<CellCoordinate> = List(shipType.length) { offset ->
        when (orientation) {
            ShipOrientation.HORIZONTAL -> CellCoordinate(start.row, start.col + offset)
            ShipOrientation.VERTICAL -> CellCoordinate(start.row + offset, start.col)
        }
    }

    fun contains(coord: CellCoordinate): Boolean = occupiedCoordinates.contains(coord)
}

enum class BattleshipPlayer {
    PLAYER_1,
    PLAYER_2;

    fun opponent(): BattleshipPlayer = if (this == PLAYER_1) PLAYER_2 else PLAYER_1
}

sealed class ShotOutcome {
    data class Hit(
        val coordinate: CellCoordinate,
        val shipType: ShipType,
        val isSunk: Boolean,
        val sunkShip: ShipPlacement? = null
    ) : ShotOutcome()

    data class Miss(val coordinate: CellCoordinate) : ShotOutcome()
    object AlreadyTargeted : ShotOutcome()
    object InvalidMove : ShotOutcome()
}

/**
 * State of a single player's board / fleet.
 */
data class PlayerFleetState(
    val player: BattleshipPlayer,
    val placements: List<ShipPlacement> = emptyList(),
    val incomingShots: Map<CellCoordinate, Boolean> = emptyMap(), // true = HIT, false = MISS
    val isDeploymentComplete: Boolean = false
) {
    val hitsTaken: Int
        get() = incomingShots.count { it.value }

    val isAllSunk: Boolean
        get() = isDeploymentComplete && placements.isNotEmpty() && placements.all { isShipSunk(it) }

    fun isShipSunk(placement: ShipPlacement): Boolean {
        return placement.occupiedCoordinates.all { incomingShots[it] == true }
    }

    fun getShipAt(coord: CellCoordinate): ShipPlacement? {
        return placements.firstOrNull { it.contains(coord) }
    }

    val sunkShips: List<ShipPlacement>
        get() = placements.filter { isShipSunk(it) }

    val remainingShipsCount: Int
        get() = placements.count { !isShipSunk(it) }

    val remainingCellsCount: Int
        get() = ShipType.TOTAL_FLEET_CELLS - hitsTaken
}

/**
 * Targeting radar history tracking shots fired by a player at the enemy.
 */
data class TargetingRadar(
    val shots: Map<CellCoordinate, Boolean> = emptyMap(), // true = HIT, false = MISS
    val sunkEnemyShips: List<ShipType> = emptyList(),
    val lastShotOutcome: ShotOutcome? = null
) {
    val totalShotsFired: Int
        get() = shots.size

    val totalHits: Int
        get() = shots.count { it.value }

    val totalMisses: Int
        get() = shots.count { !it.value }

    val accuracyPercentage: Int
        get() = if (totalShotsFired == 0) 0 else ((totalHits * 100) / totalShotsFired)

    fun hasFiredAt(coord: CellCoordinate): Boolean = shots.containsKey(coord)
}

enum class BattleshipPhase {
    DEPLOYMENT_P1,
    DEPLOYMENT_HANDOVER_TO_P2, // Local 1v1 privacy handover
    DEPLOYMENT_P2,
    WAITING_FOR_REMOTE_DEPLOYMENT, // WiFi Play: waiting for opponent to finish deploying
    BATTLE,
    COMBAT_HANDOVER, // Local 1v1 pass-the-phone after a MISS
    GAME_OVER
}

/**
 * Global game state for Battleship.
 */
data class BattleshipState(
    val phase: BattleshipPhase = BattleshipPhase.DEPLOYMENT_P1,
    val p1Fleet: PlayerFleetState = PlayerFleetState(BattleshipPlayer.PLAYER_1),
    val p2Fleet: PlayerFleetState = PlayerFleetState(BattleshipPlayer.PLAYER_2),
    val p1Radar: TargetingRadar = TargetingRadar(),
    val p2Radar: TargetingRadar = TargetingRadar(),
    val activePlayer: BattleshipPlayer = BattleshipPlayer.PLAYER_1,
    val winner: BattleshipPlayer? = null,
    val turnNumber: Int = 1,
    val latestAnnouncement: String = ""
) {
    val isGameOver: Boolean
        get() = phase == BattleshipPhase.GAME_OVER || winner != null

    fun radarFor(player: BattleshipPlayer): TargetingRadar =
        if (player == BattleshipPlayer.PLAYER_1) p1Radar else p2Radar

    fun fleetFor(player: BattleshipPlayer): PlayerFleetState =
        if (player == BattleshipPlayer.PLAYER_1) p1Fleet else p2Fleet
}
