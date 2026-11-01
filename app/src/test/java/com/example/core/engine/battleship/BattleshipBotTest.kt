package com.example.core.engine.battleship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleshipBotTest {

    @Test
    fun testBotNeverShootsAtAlreadyTargetedCells() {
        val bot = BattleshipBot()
        var radar = TargetingRadar()

        // Fire 50 shots and verify none repeat
        for (i in 0 until 50) {
            val move = bot.decideMove(radar)
            assertFalse("Bot must never fire at an already targeted cell", radar.hasFiredAt(move))
            radar = radar.copy(shots = radar.shots + (move to false)) // Record as miss
            bot.onShotResult(move, ShotOutcome.Miss(move))
        }
        assertEquals(50, radar.totalShotsFired)
    }

    @Test
    fun testBotTargetModePrioritizesAdjacentNeighborsAfterHit() {
        val bot = BattleshipBot()
        val hitCoord = CellCoordinate(4, 4)
        var radar = TargetingRadar(shots = mapOf(hitCoord to true))

        // Notify bot of hit at (4,4)
        bot.onShotResult(hitCoord, ShotOutcome.Hit(hitCoord, ShipType.CRUISER, isSunk = false))

        // Next move must be an adjacent orthogonal neighbor: (3,4), (5,4), (4,3), or (4,5)
        val nextMove = bot.decideMove(radar)
        val validNeighbors = listOf(
            CellCoordinate(3, 4),
            CellCoordinate(5, 4),
            CellCoordinate(4, 3),
            CellCoordinate(4, 5)
        )

        assertTrue(
            "In target mode, bot must target an adjacent orthogonal neighbor of hit ($hitCoord), got $nextMove",
            validNeighbors.contains(nextMove)
        )
    }

    @Test
    fun testBotInfersLineOrientationOnConsecutiveHits() {
        val bot = BattleshipBot()
        val hit1 = CellCoordinate(4, 4)
        val hit2 = CellCoordinate(4, 5) // Horizontal line in row 4
        var radar = TargetingRadar(shots = mapOf(hit1 to true, hit2 to true))

        bot.onShotResult(hit1, ShotOutcome.Hit(hit1, ShipType.BATTLESHIP, isSunk = false))
        bot.onShotResult(hit2, ShotOutcome.Hit(hit2, ShipType.BATTLESHIP, isSunk = false))

        val nextMove = bot.decideMove(radar)

        // Bot should extend horizontally: either (4,3) or (4,6)
        val expectedHorizontalCandidates = listOf(CellCoordinate(4, 3), CellCoordinate(4, 6))
        assertTrue(
            "Bot must extend along horizontal line orientation, got $nextMove",
            expectedHorizontalCandidates.contains(nextMove)
        )
    }

    @Test
    fun testBotRevertsToHuntModeWhenShipIsSunk() {
        val bot = BattleshipBot()
        val destroyer = ShipPlacement(ShipType.DESTROYER, CellCoordinate(1, 1), ShipOrientation.HORIZONTAL)
        val hit1 = CellCoordinate(1, 1)
        val hit2 = CellCoordinate(1, 2)

        var radar = TargetingRadar(shots = mapOf(hit1 to true, hit2 to true))
        bot.onShotResult(hit1, ShotOutcome.Hit(hit1, ShipType.DESTROYER, isSunk = false))
        // Sunk!
        bot.onShotResult(hit2, ShotOutcome.Hit(hit2, ShipType.DESTROYER, isSunk = true, sunkShip = destroyer))

        // Now that the ship is sunk, active hits is empty -> bot reverts to hunt mode
        val huntMove = bot.decideMove(radar)
        assertFalse(radar.hasFiredAt(huntMove))
        assertTrue(huntMove.row in 0..9 && huntMove.col in 0..9)
    }
}
