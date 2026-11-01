package com.example.core.engine.battleship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleshipEngineTest {

    @Test
    fun testShipTypesAndTotalLengths() {
        assertEquals(5, ShipType.CARRIER.length)
        assertEquals(4, ShipType.BATTLESHIP.length)
        assertEquals(3, ShipType.CRUISER.length)
        assertEquals(3, ShipType.SUBMARINE.length)
        assertEquals(2, ShipType.DESTROYER.length)

        val totalLength = ShipType.ALL_SHIPS.sumOf { it.length }
        assertEquals(17, totalLength)
        assertEquals(ShipType.TOTAL_FLEET_CELLS, totalLength)
    }

    @Test
    fun testValidHorizontalPlacement() {
        val valid = BattleshipEngine.isValidPlacement(
            shipType = ShipType.CARRIER, // length 5
            startRow = 0,
            startCol = 0,
            orientation = ShipOrientation.HORIZONTAL,
            existingPlacements = emptyList()
        )
        assertTrue(valid)

        // Right at the edge: col 5 + length 5 = 10 -> valid
        val edgeValid = BattleshipEngine.isValidPlacement(
            shipType = ShipType.CARRIER,
            startRow = 0,
            startCol = 5,
            orientation = ShipOrientation.HORIZONTAL,
            existingPlacements = emptyList()
        )
        assertTrue(edgeValid)

        // Exceeding grid: col 6 + length 5 = 11 -> invalid
        val outOfBounds = BattleshipEngine.isValidPlacement(
            shipType = ShipType.CARRIER,
            startRow = 0,
            startCol = 6,
            orientation = ShipOrientation.HORIZONTAL,
            existingPlacements = emptyList()
        )
        assertFalse(outOfBounds)
    }

    @Test
    fun testValidVerticalPlacement() {
        val valid = BattleshipEngine.isValidPlacement(
            shipType = ShipType.BATTLESHIP, // length 4
            startRow = 6,
            startCol = 2,
            orientation = ShipOrientation.VERTICAL,
            existingPlacements = emptyList()
        )
        assertTrue(valid)

        // Exceeding grid vertically: row 7 + length 4 = 11 -> invalid
        val outOfBounds = BattleshipEngine.isValidPlacement(
            shipType = ShipType.BATTLESHIP,
            startRow = 7,
            startCol = 2,
            orientation = ShipOrientation.VERTICAL,
            existingPlacements = emptyList()
        )
        assertFalse(outOfBounds)
    }

    @Test
    fun testPlacementOverlapRejection() {
        val carrier = ShipPlacement(
            ShipType.CARRIER,
            CellCoordinate(2, 2),
            ShipOrientation.HORIZONTAL
        ) // occupies (2,2), (2,3), (2,4), (2,5), (2,6)

        // Vertical destroyer crossing (2,4): from (1,4) to (2,4)
        val overlaps = BattleshipEngine.isValidPlacement(
            shipType = ShipType.DESTROYER,
            startRow = 1,
            startCol = 4,
            orientation = ShipOrientation.VERTICAL,
            existingPlacements = listOf(carrier)
        )
        assertFalse("Overlapping ship placement must be rejected", overlaps)

        // Non-overlapping destroyer at (3,2)
        val clear = BattleshipEngine.isValidPlacement(
            shipType = ShipType.DESTROYER,
            startRow = 3,
            startCol = 2,
            orientation = ShipOrientation.HORIZONTAL,
            existingPlacements = listOf(carrier)
        )
        assertTrue("Non-overlapping ship placement must be valid", clear)
    }

    @Test
    fun testPlaceAllFiveShipsCompletesDeployment() {
        val engine = BattleshipEngine()
        assertFalse(engine.state.p1Fleet.isDeploymentComplete)

        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER, 0, 0, ShipOrientation.HORIZONTAL))
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.BATTLESHIP, 1, 0, ShipOrientation.HORIZONTAL))
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CRUISER, 2, 0, ShipOrientation.HORIZONTAL))
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.SUBMARINE, 3, 0, ShipOrientation.HORIZONTAL))
        assertTrue(engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 4, 0, ShipOrientation.HORIZONTAL))

        assertTrue("After placing all 5 ships, deployment must be complete", engine.state.p1Fleet.isDeploymentComplete)
        assertEquals(5, engine.state.p1Fleet.placements.size)
        assertEquals(17, engine.state.p1Fleet.remainingCellsCount)
    }

    @Test
    fun testHitAndMissDetectionAndExtraShotRule() {
        val engine = BattleshipEngine()
        // Deploy P1 Destroyer at (0,0) and (0,1)
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 0, 0, ShipOrientation.HORIZONTAL)
        // Deploy P2 Destroyer at (5,5) and (5,6)
        engine.placeShip(BattleshipPlayer.PLAYER_2, ShipType.DESTROYER, 5, 5, ShipOrientation.HORIZONTAL)

        engine.setPhase(BattleshipPhase.BATTLE)
        assertEquals(BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

        // P1 fires at water (0,0) on P2's board -> MISS
        val missResult = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(0, 0))
        assertTrue(missResult is ShotOutcome.Miss)
        // Miss rule: turn passes to P2!
        assertEquals("On miss, turn must pass to opponent", BattleshipPlayer.PLAYER_2, engine.state.activePlayer)

        // P2 fires at water (1,1) -> MISS
        val p2Miss = engine.fireShot(BattleshipPlayer.PLAYER_2, CellCoordinate(1, 1))
        assertTrue(p2Miss is ShotOutcome.Miss)
        assertEquals(BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

        // P1 fires at (5,5) where P2's Destroyer is -> HIT!
        val hitResult = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(5, 5))
        assertTrue(hitResult is ShotOutcome.Hit)
        val hit = hitResult as ShotOutcome.Hit
        assertEquals(ShipType.DESTROYER, hit.shipType)
        assertFalse("1 hit of length 2 destroyer does not sink it yet", hit.isSunk)

        // Extra shot rule: P1 retains active turn!
        assertEquals("On HIT, shooter must be granted an extra shot!", BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

        // P1 fires repeated shot at (5,5) -> Rejected without penalty
        val repeated = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(5, 5))
        assertEquals(ShotOutcome.AlreadyTargeted, repeated)
        assertEquals(BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

        // P1 fires at (5,6) finishing off the Destroyer -> SUNK!
        val sunkResult = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(5, 6))
        assertTrue(sunkResult is ShotOutcome.Hit)
        val sunkHit = sunkResult as ShotOutcome.Hit
        assertTrue("Destroyer must be sunk after all cells hit", sunkHit.isSunk)
        assertEquals(ShipType.DESTROYER, sunkHit.shipType)
    }

    @Test
    fun testVictoryWhenAllShipsSunk() {
        val engine = BattleshipEngine()
        // Deploy entire P2 fleet
        val p2Fleet = listOf(
            ShipPlacement(ShipType.CARRIER, CellCoordinate(0, 0), ShipOrientation.HORIZONTAL),
            ShipPlacement(ShipType.BATTLESHIP, CellCoordinate(1, 0), ShipOrientation.HORIZONTAL),
            ShipPlacement(ShipType.CRUISER, CellCoordinate(2, 0), ShipOrientation.HORIZONTAL),
            ShipPlacement(ShipType.SUBMARINE, CellCoordinate(3, 0), ShipOrientation.HORIZONTAL),
            ShipPlacement(ShipType.DESTROYER, CellCoordinate(4, 0), ShipOrientation.HORIZONTAL)
        )
        engine.setFleet(BattleshipPlayer.PLAYER_2, p2Fleet)
        engine.setFleet(BattleshipPlayer.PLAYER_1, BattleshipEngine.generateRandomFleet())
        engine.setPhase(BattleshipPhase.BATTLE)

        // P1 sinks all 17 cells of P2
        var lastOutcome: ShotOutcome? = null
        for (placement in p2Fleet) {
            for (coord in placement.occupiedCoordinates) {
                lastOutcome = engine.fireShot(BattleshipPlayer.PLAYER_1, coord)
                assertTrue(lastOutcome is ShotOutcome.Hit)
            }
        }

        assertEquals(BattleshipPhase.GAME_OVER, engine.state.phase)
        assertEquals(BattleshipPlayer.PLAYER_1, engine.state.winner)
        assertTrue(engine.state.isGameOver)
        assertEquals(17, engine.state.p1Radar.totalHits)
        assertEquals(5, engine.state.p1Radar.sunkEnemyShips.size)
    }

    @Test
    fun testRandomFleetGenerationAlwaysValid() {
        val fleet = BattleshipEngine.generateRandomFleet()
        assertEquals(5, fleet.size)
        val types = fleet.map { it.shipType }.toSet()
        assertEquals(ShipType.ALL_SHIPS.toSet(), types)

        // Verify all 17 cells are distinct and within bounds
        val allCells = fleet.flatMap { it.occupiedCoordinates }
        assertEquals(17, allCells.size)
        assertEquals(17, allCells.toSet().size)
        for (cell in allCells) {
            assertTrue(cell.row in 0..9)
            assertTrue(cell.col in 0..9)
        }
    }

    @Test
    fun testShipRemovalUpdatesCounterAndClearsCells() {
        val engine = BattleshipEngine()
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER, 0, 0, ShipOrientation.HORIZONTAL)
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 2, 0, ShipOrientation.HORIZONTAL)
        assertEquals(2, engine.state.p1Fleet.placements.size)
        assertFalse(engine.state.p1Fleet.isDeploymentComplete)

        // Remove Carrier
        engine.removeShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER)
        assertEquals(1, engine.state.p1Fleet.placements.size)
        assertNull(engine.state.p1Fleet.getShipAt(CellCoordinate(0, 0)))
        assertNull(engine.state.p1Fleet.getShipAt(CellCoordinate(0, 4)))
        // Destroyer at (2,0) remains intact
        assertNotNull(engine.state.p1Fleet.getShipAt(CellCoordinate(2, 0)))
    }

    @Test
    fun testShipRotationInPlace() {
        val engine = BattleshipEngine()
        // Place Cruiser horizontally at (2, 2): occupies (2,2), (2,3), (2,4)
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CRUISER, 2, 2, ShipOrientation.HORIZONTAL)
        val initialPlacement = engine.state.p1Fleet.getShipAt(CellCoordinate(2, 2))
        assertNotNull(initialPlacement)
        assertEquals(ShipOrientation.HORIZONTAL, initialPlacement?.orientation)

        // Rotate in place -> should now occupy (2,2), (3,2), (4,2)
        val rotated = engine.rotateShip(BattleshipPlayer.PLAYER_1, ShipType.CRUISER)
        assertTrue("Rotating Cruiser from (2,2) vertically must succeed", rotated)

        val updatedPlacement = engine.state.p1Fleet.getShipAt(CellCoordinate(2, 2))
        assertNotNull(updatedPlacement)
        assertEquals(ShipOrientation.VERTICAL, updatedPlacement?.orientation)
        assertNotNull(engine.state.p1Fleet.getShipAt(CellCoordinate(4, 2)))
        assertNull(engine.state.p1Fleet.getShipAt(CellCoordinate(2, 4)))
    }

    @Test
    fun testShipRotationBlockedAtBoundaryPreservesPosition() {
        val engine = BattleshipEngine()
        // Place Carrier horizontally at row 8, col 0 (rows 8, cols 0..4)
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER, 8, 0, ShipOrientation.HORIZONTAL)

        // Attempting to rotate to VERTICAL: would occupy rows 8..12 which exceeds grid boundary (max 9)!
        val rotated = engine.rotateShip(BattleshipPlayer.PLAYER_1, ShipType.CARRIER)
        assertFalse("Carrier rotation exceeding vertical boundary must be rejected", rotated)

        // Position must remain preserved horizontally
        val currentPlacement = engine.state.p1Fleet.getShipAt(CellCoordinate(8, 0))
        assertNotNull(currentPlacement)
        assertEquals(ShipOrientation.HORIZONTAL, currentPlacement?.orientation)
    }

    @Test
    fun testShipRepositioning() {
        val engine = BattleshipEngine()
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.SUBMARINE, 0, 0, ShipOrientation.HORIZONTAL)
        assertNotNull(engine.state.p1Fleet.getShipAt(CellCoordinate(0, 0)))

        // Move submarine to (5, 5)
        val moved = engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.SUBMARINE, 5, 5, ShipOrientation.VERTICAL)
        assertTrue(moved)
        assertNull("Old position (0,0) must now be clear", engine.state.p1Fleet.getShipAt(CellCoordinate(0, 0)))
        assertNotNull("New position (5,5) must be occupied", engine.state.p1Fleet.getShipAt(CellCoordinate(5, 5)))
        assertEquals(1, engine.state.p1Fleet.placements.size)
    }
}
