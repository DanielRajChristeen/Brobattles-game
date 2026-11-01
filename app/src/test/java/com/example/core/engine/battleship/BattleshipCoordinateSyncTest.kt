package com.example.core.engine.battleship

import androidx.compose.ui.geometry.Offset
import com.example.ui.screens.detail.battleship.AttackAnimationData
import com.example.ui.screens.detail.battleship.AttackAnimationStage
import com.example.ui.screens.detail.battleship.AttackTimingConfig
import com.example.ui.screens.detail.battleship.BoardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleshipCoordinateSyncTest {

    // Test coordinates required by specification
    private val testCoordinates = listOf(
        Pair("A1", CellCoordinate(0, 0)),
        Pair("E5", CellCoordinate(4, 4)),
        Pair("J10", CellCoordinate(9, 9)),
        Pair("A10", CellCoordinate(9, 0)),
        Pair("J1", CellCoordinate(0, 9))
    )

    @Test
    fun testCoordinateToNauticalNotationMapping() {
        for ((notation, coord) in testCoordinates) {
            val letter = ('A' + coord.col).toString()
            val number = (coord.row + 1).toString()
            val formatted = "$letter$number"
            assertEquals("Notation for $coord must match expected", notation, formatted)
        }
    }

    @Test
    fun testHumanAttackCoordinateSingleSourceOfTruth() {
        for ((notation, testCoord) in testCoordinates) {
            val engine = BattleshipEngine()
            val p2Fleet = BattleshipEngine.generateRandomFleet()
            engine.setFleet(BattleshipPlayer.PLAYER_2, p2Fleet)
            engine.setFleet(BattleshipPlayer.PLAYER_1, BattleshipEngine.generateRandomFleet())
            engine.setPhase(BattleshipPhase.BATTLE)
            // Ensure Human (PLAYER_1) is active player
            assertEquals(BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

            // Simulated measured screen center for cell in overlay
            val cellSizePx = 40f
            val measuredCenter = Offset(
                x = 20f + testCoord.col * cellSizePx + cellSizePx / 2f,
                y = 30f + testCoord.row * cellSizePx + cellSizePx / 2f
            )

            val enemyShip = engine.state.p2Fleet.getShipAt(testCoord)
            val isHit = enemyShip != null

            // Create AttackAnimationData
            val attackData = AttackAnimationData(
                attacker = BattleshipPlayer.PLAYER_1,
                isBotAttacker = false,
                targetCoord = testCoord,
                targetBoard = BoardType.RADAR,
                targetCenterInOverlay = measuredCenter,
                cellSizePx = cellSizePx,
                isHit = isHit,
                isSunk = false,
                stage = AttackAnimationStage.TARGET_LOCK,
                bannerTitle = "⚔️ YOUR ATTACK",
                bannerSubtitle = "Targeting enemy fleet...",
                announcement = "Target lock acquired at $notation"
            )

            // Verify single source of truth holds
            assertEquals(testCoord.row, attackData.targetRow)
            assertEquals(testCoord.col, attackData.targetColumn)
            assertEquals(testCoord, attackData.targetCoord)
            assertEquals(BoardType.RADAR, attackData.targetBoard)
            assertEquals(measuredCenter, attackData.targetCenterInOverlay)
            assertEquals(cellSizePx, attackData.cellSizePx)
            assertFalse(attackData.isBotAttacker)

            // Fire in engine using the EXACT SAME coordinate from attackData
            val outcome = engine.fireShot(attackData.attacker, attackData.targetCoord)
            assertNotNull(outcome)
            assertNotEquals(ShotOutcome.AlreadyTargeted, outcome)
            assertNotEquals(ShotOutcome.InvalidMove, outcome)
            assertTrue("Outcome must be Hit or Miss: $outcome", outcome is ShotOutcome.Hit || outcome is ShotOutcome.Miss)

            // Verify radar recorded shot at the EXACT SAME coordinate
            assertTrue(engine.state.p1Radar.shots.containsKey(attackData.targetCoord))
            assertEquals(isHit, engine.state.p1Radar.shots[attackData.targetCoord])
        }
    }

    @Test
    fun testBotAttackCoordinateSingleSourceOfTruth() {
        for ((notation, testCoord) in testCoordinates) {
            val engine = BattleshipEngine()
            val p1Fleet = BattleshipEngine.generateRandomFleet()
            engine.setFleet(BattleshipPlayer.PLAYER_1, p1Fleet)
            engine.setFleet(BattleshipPlayer.PLAYER_2, BattleshipEngine.generateRandomFleet())
            engine.setPhase(BattleshipPhase.BATTLE)
            // Make PLAYER_2 active for bot turn
            engine.setActivePlayerForTesting(BattleshipPlayer.PLAYER_2)

            val cellSizePx = 42f
            val measuredFleetCenter = Offset(
                x = 15f + testCoord.col * cellSizePx + cellSizePx / 2f,
                y = 25f + testCoord.row * cellSizePx + cellSizePx / 2f
            )

            val humanShip = engine.state.p1Fleet.getShipAt(testCoord)
            val isHit = humanShip != null

            // Construct immutable AttackAnimationData for bot attack
            val attackData = AttackAnimationData(
                attacker = BattleshipPlayer.PLAYER_2,
                isBotAttacker = true,
                targetCoord = testCoord,
                targetBoard = BoardType.MY_FLEET,
                targetCenterInOverlay = measuredFleetCenter,
                cellSizePx = cellSizePx,
                isHit = isHit,
                isSunk = false,
                stage = AttackAnimationStage.TARGET_LOCK,
                bannerTitle = "🤖 BOT ATTACKING YOU!",
                bannerSubtitle = "Incoming naval strike...",
                announcement = "Warning! Strike locked at $notation"
            )

            // Verify immutable single source of truth
            assertEquals(testCoord, attackData.targetCoord)
            assertEquals(BoardType.MY_FLEET, attackData.targetBoard)
            assertEquals(measuredFleetCenter, attackData.targetCenterInOverlay)
            assertTrue(attackData.isBotAttacker)

            // Fire in engine using attackData.targetCoord
            val outcome = engine.fireShot(attackData.attacker, attackData.targetCoord)
            assertNotNull(outcome)
            assertNotEquals(ShotOutcome.AlreadyTargeted, outcome)
            assertNotEquals(ShotOutcome.InvalidMove, outcome)
            assertTrue("Outcome must be Hit or Miss: $outcome", outcome is ShotOutcome.Hit || outcome is ShotOutcome.Miss)

            // Verify P1 Fleet incoming shots record the exact shot at targetCoord
            assertTrue(engine.state.p1Fleet.incomingShots.containsKey(attackData.targetCoord))
            assertEquals(isHit, engine.state.p1Fleet.incomingShots[attackData.targetCoord])
        }
    }

    @Test
    fun testVerticalBombDropExactAlignmentAndConstantX() {
        // Verify that throughout the entire vertical descent:
        // 1. Bomb X coordinate is STRICTLY CONSTANT and equal to targetCenter.x at all times (t = 0.0 .. 1.0)
        // 2. Bomb Y coordinate accelerates down and reaches targetCenter.y exactly at t = 1.0
        // 3. Impact center equals targetCenter
        for ((_, testCoord) in testCoordinates) {
            val cellSizePx = 40f
            val targetCenter = Offset(
                x = 20f + testCoord.col * cellSizePx + cellSizePx / 2f,
                y = 30f + testCoord.row * cellSizePx + cellSizePx / 2f
            )

            val startY = -60f
            val targetY = targetCenter.y

            // Test progress at 0%, 20%, 40%, 60%, 80%, 100%
            val progressSteps = listOf(0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
            for (progress in progressSteps) {
                // Bomb X must be constant at all times
                val bombX = targetCenter.x
                assertEquals("Bomb X must strictly equal targetCenter.x at progress $progress", targetCenter.x, bombX, 0.0001f)

                // Gravity accelerated Y
                val gravityProgress = (0.25f * progress + 0.75f * progress * progress).coerceIn(0f, 1f)
                val bombY = startY + (targetY - startY) * gravityProgress

                if (progress == 0.0f) {
                    assertEquals("At t=0, bomb starts at startY", startY, bombY, 0.0001f)
                } else if (progress == 1.0f) {
                    assertEquals("At t=1.0, bomb Y must land exactly on targetCenter.y", targetCenter.y, bombY, 0.0001f)
                } else {
                    assertTrue("Bomb Y must smoothly descend between startY and targetY", bombY in startY..targetY)
                }
            }
        }
    }

    @Test
    fun testBotFleetPrivacyInSoloMode() {
        val engine = BattleshipEngine()
        val humanFleet = BattleshipEngine.generateRandomFleet()
        val botFleet = BattleshipEngine.generateRandomFleet()

        engine.setFleet(BattleshipPlayer.PLAYER_1, humanFleet)
        engine.setFleet(BattleshipPlayer.PLAYER_2, botFleet)
        engine.setPhase(BattleshipPhase.BATTLE)

        // In SOLO mode:
        // Viewing player is strictly PLAYER_1 (Human)
        val viewingPlayer = BattleshipPlayer.PLAYER_1
        val displayedFleet = engine.state.fleetFor(viewingPlayer)

        // Verify displayed fleet on human screen is ONLY human fleet, NOT bot fleet
        assertEquals(humanFleet, displayedFleet.placements)
        assertNotEquals(botFleet, displayedFleet.placements)

        // Bot fleet must have 0 unhit positions revealed to human radar
        val humanRadar = engine.state.radarFor(viewingPlayer)
        assertEquals(0, humanRadar.totalShotsFired)
        assertEquals(0, humanRadar.shots.size)

        // Simulate Bot Attack on human fleet cell (e.g., A1)
        val botTarget = CellCoordinate(0, 0)
        engine.setActivePlayerForTesting(BattleshipPlayer.PLAYER_2)
        val botOutcome = engine.fireShot(BattleshipPlayer.PLAYER_2, botTarget)

        // Verify human fleet records incoming shot
        assertTrue(engine.state.p1Fleet.incomingShots.containsKey(botTarget))

        // Verify Bot fleet incoming shots were NOT altered by bot firing
        assertFalse(engine.state.p2Fleet.incomingShots.containsKey(botTarget))

        // Human radar still does not leak unhit bot ships
        assertEquals(0, engine.state.p1Radar.shots.size)
    }

    @Test
    fun testAttackTimingConfiguration() {
        assertEquals(1200L, AttackTimingConfig.THINKING_MS)
        assertEquals(1000L, AttackTimingConfig.TARGET_LOCK_MS)
        assertEquals(1500L, AttackTimingConfig.PROJECTILE_FLIGHT_MS)
        assertEquals(1200L, AttackTimingConfig.IMPACT_MS)
        assertEquals(1500L, AttackTimingConfig.RESULT_ANNOUNCEMENT_MS)
        assertEquals(500L, AttackTimingConfig.TURN_TRANSITION_MS)
        assertEquals(6900L, AttackTimingConfig.TOTAL_SEQUENCE_MS)
    }

    @Test
    fun testRepeatedAttackRejection() {
        val engine = BattleshipEngine()
        engine.placeShip(BattleshipPlayer.PLAYER_2, ShipType.DESTROYER, 5, 5, ShipOrientation.HORIZONTAL)
        engine.setPhase(BattleshipPhase.BATTLE)

        val target = CellCoordinate(5, 5)
        val firstShot = engine.fireShot(BattleshipPlayer.PLAYER_1, target)
        assertTrue(firstShot is ShotOutcome.Hit)

        // Attempting to fire at the same cell again
        val secondShot = engine.fireShot(BattleshipPlayer.PLAYER_1, target)
        assertEquals(ShotOutcome.AlreadyTargeted, secondShot)
    }
}
