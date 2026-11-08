package com.example.core.engine.battleship

import androidx.compose.ui.geometry.Offset
import com.example.core.multiplayer.battleship.AttackRequestPayload
import com.example.core.multiplayer.battleship.BattleshipAttackEvent
import com.example.core.multiplayer.battleship.BattleshipMultiplayerProtocol
import com.example.ui.screens.detail.battleship.AttackAnimationData
import com.example.ui.screens.detail.battleship.AttackAnimationStage
import com.example.ui.screens.detail.battleship.AttackTimingConfig
import com.example.ui.screens.detail.battleship.BoardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BattleshipWiFiPlayTest {

    private val mandatoryCoordinates = listOf(
        Pair("A1", CellCoordinate(0, 0)),
        Pair("E5", CellCoordinate(4, 4)),
        Pair("J10", CellCoordinate(9, 9)),
        Pair("A10", CellCoordinate(9, 0)),
        Pair("J1", CellCoordinate(0, 9)),
        Pair("H8", CellCoordinate(7, 7))
    )

    @Test
    fun testFleetSerializationAndDeserialization() {
        val fleet = BattleshipEngine.generateRandomFleet()
        assertEquals(5, fleet.size)

        val json = BattleshipMultiplayerProtocol.serializeFleet(fleet)
        assertNotNull(json)
        assertTrue(json.contains("CARRIER"))
        assertTrue(json.contains("BATTLESHIP"))
        assertTrue(json.contains("CRUISER"))
        assertTrue(json.contains("SUBMARINE"))
        assertTrue(json.contains("DESTROYER"))

        val restored = BattleshipMultiplayerProtocol.deserializeFleet(json)
        assertNotNull(restored)
        assertEquals(5, restored!!.size)

        for (shipType in ShipType.ALL_SHIPS) {
            val original = fleet.first { it.shipType == shipType }
            val deserialized = restored.first { it.shipType == shipType }
            assertEquals(original.start, deserialized.start)
            assertEquals(original.orientation, deserialized.orientation)
            assertEquals(original.occupiedCoordinates, deserialized.occupiedCoordinates)
        }
    }

    @Test
    fun testFleetPrivacyInAttackEventSerialization() {
        // Strict specification requirement:
        // Never transmit unhit opponent ship coordinates or ship names in attack event updates.
        val event = BattleshipAttackEvent(
            attackId = UUID.randomUUID().toString(),
            sequenceNumber = 42L,
            attacker = BattleshipPlayer.PLAYER_1,
            coordinate = CellCoordinate(4, 4), // E5
            isHit = true,
            isSunk = true, // Generic confirmation
            isGameOver = false,
            winner = null,
            nextActivePlayer = BattleshipPlayer.PLAYER_1 // Extra shot!
        )

        val json = BattleshipMultiplayerProtocol.serializeAttackEvent(event)
        // Verify JSON contains strictly generic outcomes, no ship names
        assertFalse("JSON must never expose ship names", json.contains("Aircraft Carrier"))
        assertFalse("JSON must never expose ship names", json.contains("Carrier"))
        assertFalse("JSON must never expose ship names", json.contains("Submarine"))
        assertTrue(json.contains("\"isHit\":true"))
        assertTrue(json.contains("\"isSunk\":true"))

        val deserialized = BattleshipMultiplayerProtocol.deserializeAttackEvent(json)
        assertNotNull(deserialized)
        assertEquals(event.attackId, deserialized!!.attackId)
        assertEquals(event.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(event.attacker, deserialized.attacker)
        assertEquals(event.coordinate, deserialized.coordinate)
        assertEquals(event.isHit, deserialized.isHit)
        assertEquals(event.isSunk, deserialized.isSunk)
        assertEquals(event.isGameOver, deserialized.isGameOver)
        assertNull(deserialized.winner)
        assertEquals(BattleshipPlayer.PLAYER_1, deserialized.nextActivePlayer)
    }

    @Test
    fun testAttackRequestSerializationRoundtrip() {
        val attackId = UUID.randomUUID().toString()
        val seq = 7L
        val coord = CellCoordinate(7, 7) // H8

        val json = BattleshipMultiplayerProtocol.serializeAttackRequest(attackId, seq, coord)
        val deserialized = BattleshipMultiplayerProtocol.deserializeAttackRequest(json)

        assertNotNull(deserialized)
        assertEquals(attackId, deserialized!!.attackId)
        assertEquals(seq, deserialized.sequenceNumber)
        assertEquals(coord, deserialized.coordinate)
    }

    @Test
    fun testHostAuthoritativeCombatFlow() {
        val hostEngine = BattleshipEngine()
        val guestEngine = BattleshipEngine()

        // 1. Both deploy 5 ships
        val p1Fleet = BattleshipEngine.generateRandomFleet()
        val p2Fleet = BattleshipEngine.generateRandomFleet()

        // Host holds authoritative copy of both fleets
        hostEngine.setFleet(BattleshipPlayer.PLAYER_1, p1Fleet)
        hostEngine.setFleet(BattleshipPlayer.PLAYER_2, p2Fleet)
        hostEngine.setPhase(BattleshipPhase.BATTLE)

        // Guest holds only own fleet (P2 fleet) locally; P1 fleet is fog-of-war
        guestEngine.setFleet(BattleshipPlayer.PLAYER_2, p2Fleet)
        guestEngine.setPhase(BattleshipPhase.BATTLE)

        // 2. Host (Player 1) fires first at a coordinate
        val target1 = p2Fleet.first().occupiedCoordinates.first() // Guaranteed HIT
        assertEquals(BattleshipPlayer.PLAYER_1, hostEngine.state.activePlayer)

        val outcome1 = hostEngine.fireShot(BattleshipPlayer.PLAYER_1, target1)
        assertTrue(outcome1 is ShotOutcome.Hit)

        val event1 = BattleshipAttackEvent(
            attackId = UUID.randomUUID().toString(),
            sequenceNumber = 1L,
            attacker = BattleshipPlayer.PLAYER_1,
            coordinate = target1,
            isHit = true,
            isSunk = (outcome1 as ShotOutcome.Hit).isSunk,
            isGameOver = hostEngine.state.isGameOver,
            winner = hostEngine.state.winner,
            nextActivePlayer = hostEngine.state.activePlayer
        )

        // Guest applies authoritative event from Host
        guestEngine.applyAuthoritativeAttackEvent(event1)

        // Verify state synchronization
        assertEquals(hostEngine.state.activePlayer, guestEngine.state.activePlayer)
        assertEquals(hostEngine.state.phase, guestEngine.state.phase)
        assertEquals(hostEngine.state.p1Radar.shots[target1], guestEngine.state.p1Radar.shots[target1])
        assertEquals(hostEngine.state.p2Fleet.incomingShots[target1], guestEngine.state.p2Fleet.incomingShots[target1])
        assertEquals(BattleshipPlayer.PLAYER_1, hostEngine.state.activePlayer) // Extra shot!
    }

    @Test
    fun testAuthoritativeTurnSwitchOnMiss() {
        val hostEngine = BattleshipEngine()
        val guestEngine = BattleshipEngine()

        // Place a single Destroyer for P2 at (0, 0) and (0, 1)
        hostEngine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 5, 5, ShipOrientation.HORIZONTAL)
        hostEngine.placeShip(BattleshipPlayer.PLAYER_2, ShipType.DESTROYER, 0, 0, ShipOrientation.HORIZONTAL)
        hostEngine.setPhase(BattleshipPhase.BATTLE)

        guestEngine.placeShip(BattleshipPlayer.PLAYER_2, ShipType.DESTROYER, 0, 0, ShipOrientation.HORIZONTAL)
        guestEngine.setPhase(BattleshipPhase.BATTLE)

        // Player 1 fires at (9, 9) -> MISS
        val missCoord = CellCoordinate(9, 9)
        val outcome = hostEngine.fireShot(BattleshipPlayer.PLAYER_1, missCoord)
        assertTrue(outcome is ShotOutcome.Miss)
        assertEquals(BattleshipPlayer.PLAYER_2, hostEngine.state.activePlayer) // Turn passes to P2!

        val event = BattleshipAttackEvent(
            attackId = UUID.randomUUID().toString(),
            sequenceNumber = 1L,
            attacker = BattleshipPlayer.PLAYER_1,
            coordinate = missCoord,
            isHit = false,
            isSunk = false,
            isGameOver = false,
            winner = null,
            nextActivePlayer = BattleshipPlayer.PLAYER_2
        )

        guestEngine.applyAuthoritativeAttackEvent(event)

        assertEquals(BattleshipPlayer.PLAYER_2, guestEngine.state.activePlayer)
        assertFalse(guestEngine.state.p1Radar.shots[missCoord]!!)
    }

    @Test
    fun testSynchronizedBombDropAnimationDataAlignmentAcrossMandatoryCoordinates() {
        // Test mandatory coordinates: A1, E5, J10, A10, J1, H8
        for ((label, coord) in mandatoryCoordinates) {
            val attackId = UUID.randomUUID().toString()
            val cellSize = 44f
            val radarCenter = Offset(18f + (coord.col + 0.5f) * cellSize, (coord.row + 0.5f) * cellSize)
            val fleetCenter = Offset(18f + (coord.col + 0.5f) * cellSize, (coord.row + 0.5f) * cellSize)

            // Authoritative Host Event
            val event = BattleshipAttackEvent(
                attackId = attackId,
                sequenceNumber = 10L,
                attacker = BattleshipPlayer.PLAYER_1,
                coordinate = coord,
                isHit = true,
                isSunk = false,
                isGameOver = false,
                winner = null,
                nextActivePlayer = BattleshipPlayer.PLAYER_1
            )

            // Attacker Device Animation Data (Player 1 on RADAR)
            val attackerAnim = AttackAnimationData(
                attacker = event.attacker,
                isBotAttacker = false,
                targetCoord = event.coordinate,
                targetBoard = BoardType.RADAR,
                targetCenterInOverlay = radarCenter,
                cellSizePx = cellSize,
                isHit = event.isHit,
                isSunk = event.isSunk,
                stage = AttackAnimationStage.PROJECTILE_FLIGHT,
                bannerTitle = "⚔️ YOUR ATTACK"
            )

            // Defender Device Animation Data (Player 2 on MY FLEET)
            val defenderAnim = AttackAnimationData(
                attacker = event.attacker,
                isBotAttacker = true,
                targetCoord = event.coordinate,
                targetBoard = BoardType.MY_FLEET,
                targetCenterInOverlay = fleetCenter,
                cellSizePx = cellSize,
                isHit = event.isHit,
                isSunk = event.isSunk,
                stage = AttackAnimationStage.PROJECTILE_FLIGHT,
                bannerTitle = "⚠️ OPPONENT ATTACKING YOU!"
            )

            // Both animations must target the exact same coordinate and result
            assertEquals("Attacker and defender must share target coordinate $label", attackerAnim.targetCoord, defenderAnim.targetCoord)
            assertEquals(coord, attackerAnim.targetCoord)
            assertEquals(coord, defenderAnim.targetCoord)
            assertEquals(radarCenter, attackerAnim.targetCenterInOverlay)
            assertEquals(fleetCenter, defenderAnim.targetCenterInOverlay)
            assertEquals(event.isHit, attackerAnim.isHit)
            assertEquals(event.isHit, defenderAnim.isHit)
        }
    }

    @Test
    fun testOutOfTurnAndRepeatedShotRejection() {
        val engine = BattleshipEngine()
        engine.placeShip(BattleshipPlayer.PLAYER_1, ShipType.DESTROYER, 2, 2, ShipOrientation.HORIZONTAL)
        engine.placeShip(BattleshipPlayer.PLAYER_2, ShipType.DESTROYER, 5, 5, ShipOrientation.HORIZONTAL)
        engine.setPhase(BattleshipPhase.BATTLE)

        // Active player is PLAYER_1
        assertEquals(BattleshipPlayer.PLAYER_1, engine.state.activePlayer)

        // 1. Out-of-turn attack attempt by PLAYER_2 must be rejected
        val illegalMove = engine.fireShot(BattleshipPlayer.PLAYER_2, CellCoordinate(2, 2))
        assertEquals(ShotOutcome.InvalidMove, illegalMove)

        // 2. Valid shot by active player
        val validShot = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(5, 5))
        assertTrue(validShot is ShotOutcome.Hit)

        // 3. Repeated shot at the same coordinate must be rejected
        val repeatedShot = engine.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(5, 5))
        assertEquals(ShotOutcome.AlreadyTargeted, repeatedShot)
    }
}
