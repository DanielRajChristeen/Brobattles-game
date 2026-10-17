package com.example.core.multiplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.snakesandladders.SnakesAndLaddersBoard
import com.example.core.engine.snakesandladders.SnakesAndLaddersGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersMove
import com.example.core.engine.snakesandladders.deserializeSnakesState
import com.example.core.engine.snakesandladders.serializeSnakesState
import com.example.ui.screens.detail.snakesandladders.SnakesTokens
import com.example.ui.screens.detail.snakesandladders.getPlayerOffsetOnSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnakesAndLaddersMultiplayerTest {

    @Test
    fun testTokenColorsAndOrder() {
        // Requirement:
        // 1. 🔴 Player 1
        // 2. 🟣 Player 2
        // 3. 🔵 Player 3
        // 4. 🟤 Player 4
        // 5. 🟢 Player 5
        assertEquals(5, SnakesTokens.COLORS.size)
        assertEquals(5, SnakesTokens.EMOJIS.size)

        assertEquals(Color(0xFFEF4444), SnakesTokens.getColor(0))
        assertEquals("🔴", SnakesTokens.getEmoji(0))

        assertEquals(Color(0xFFA855F7), SnakesTokens.getColor(1))
        assertEquals("🟣", SnakesTokens.getEmoji(1))

        assertEquals(Color(0xFF3B82F6), SnakesTokens.getColor(2))
        assertEquals("🔵", SnakesTokens.getEmoji(2))

        assertEquals(Color(0xFF8D6E63), SnakesTokens.getColor(3))
        assertEquals("🟤", SnakesTokens.getEmoji(3))

        assertEquals(Color(0xFF22C55E), SnakesTokens.getColor(4))
        assertEquals("🟢", SnakesTokens.getEmoji(4))
    }

    @Test
    fun testDistinguishableOffsetsOnSameSquare() {
        val cellSize = 50f

        // When 5 players occupy the same square, all 5 must have distinct offsets
        val offsets5 = (0 until 5).map { getPlayerOffsetOnSquare(it, 5, cellSize) }
        val uniqueOffsets = offsets5.toSet()
        assertEquals("All 5 player tokens on the same square must have unique offsets", 5, uniqueOffsets.size)

        // Player 1 is centered
        assertEquals(Offset.Zero, offsets5[0])
        // Others are spread around
        for (i in 1..4) {
            assertNotEquals(Offset.Zero, offsets5[i])
        }
    }

    @Test
    fun testTurnRotationThroughAllFivePlayers() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        assertEquals(5, engine.playerCount)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Turn 1: Player 1 rolls 2 (not extra turn) -> next is Player 2
        var res = engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)
        assertTrue(res is MoveResult.Success)
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)

        // Turn 2: Player 2 rolls 3 -> next is Player 3
        res = engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_2)
        assertTrue(res is MoveResult.Success)
        assertEquals(PlayerId.PLAYER_3, engine.state.activePlayer)

        // Turn 3: Player 3 rolls 4 -> next is Player 4
        res = engine.makeMove(SnakesAndLaddersMove(4), PlayerId.PLAYER_3)
        assertTrue(res is MoveResult.Success)
        assertEquals(PlayerId.PLAYER_4, engine.state.activePlayer)

        // Turn 4: Player 4 rolls 5 -> next is Player 5
        res = engine.makeMove(SnakesAndLaddersMove(5), PlayerId.PLAYER_4)
        assertTrue(res is MoveResult.Success)
        assertEquals(PlayerId.PLAYER_5, engine.state.activePlayer)

        // Turn 5: Player 5 rolls 2 -> wraps back to Player 1!
        res = engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_5)
        assertTrue(res is MoveResult.Success)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)
    }

    @Test
    fun testInactivePlayersCannotRollOutOfTurn() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Player 2 attempts to move out of turn
        assertFalse(engine.validateMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_2))
        val invalidRes = engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_2)
        assertTrue(invalidRes is MoveResult.Invalid)

        // State remains unchanged and Player 1 is still active
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)
        assertEquals(1, engine.state.p2Position)
    }

    @Test
    fun testExtraTurnRule() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Player 1 rolls 6 (or 1) -> extra turn granted!
        val res = engine.makeMove(SnakesAndLaddersMove(6), PlayerId.PLAYER_1)
        assertTrue(res is MoveResult.Success)
        assertTrue(engine.state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_1, engine.state.activePlayer)

        // Player 1 rolls again: rolls 2 -> extra turn finishes, next is Player 2
        val res2 = engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1)
        assertTrue(res2 is MoveResult.Success)
        assertFalse(engine.state.extraTurnGranted)
        assertEquals(PlayerId.PLAYER_2, engine.state.activePlayer)
    }

    @Test
    fun testFivePlayerStateSerializationAndDeserialization() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_1) // P1 at 3
        engine.makeMove(SnakesAndLaddersMove(3), PlayerId.PLAYER_2) // P2 lands on 4 -> ladder to 14!
        engine.makeMove(SnakesAndLaddersMove(4), PlayerId.PLAYER_3) // P3 at 5
        engine.makeMove(SnakesAndLaddersMove(5), PlayerId.PLAYER_4) // P4 at 6
        engine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_5) // P5 at 3

        val state = engine.state
        assertEquals(5, state.playerCount)
        assertEquals(3, state.p1Position)
        assertEquals(14, state.p2Position)
        assertEquals(5, state.p3Position)
        assertEquals(6, state.p4Position)
        assertEquals(3, state.p5Position)

        val json = serializeSnakesState(state)
        val deserialized = deserializeSnakesState(json)

        assertNotNull(deserialized)
        assertEquals(5, deserialized!!.playerCount)
        assertEquals(state.playerPositions, deserialized.playerPositions)
        assertEquals(state.activePlayer, deserialized.activePlayer)
        assertEquals(state.lastDiceRoll, deserialized.lastDiceRoll)
        assertEquals(state.lastMovePlayer, deserialized.lastMovePlayer)
        assertEquals(state.status, deserialized.status)
    }

    @Test
    fun testAuthoritativeHostSyncWithClientEngines() {
        // Host (P1) and 4 Clients (P2, P3, P4, P5)
        val hostEngine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)
        val clientEngines = (2..5).map { SnakesAndLaddersGameEngine(initialPlayerCount = 5) }

        // Host makes move for P1 (roll 4 -> ladder 4 to 14)
        val moveP1 = SnakesAndLaddersMove(3) // 1 + 3 = 4 -> 14
        val hostMoveRes = hostEngine.makeMove(moveP1, PlayerId.PLAYER_1)
        assertTrue(hostMoveRes is MoveResult.Success)

        // Host serializes and broadcasts SNAKES_SYNC
        val syncPayload = serializeSnakesState(hostEngine.state, 3)
        for (client in clientEngines) {
            val synced = deserializeSnakesState(syncPayload)
            assertNotNull(synced)
            client.syncState(synced!!)
            assertEquals(14, client.state.p1Position)
            assertEquals(PlayerId.PLAYER_2, client.state.activePlayer)
        }

        // Client 1 (PLAYER_2) requests roll from host -> host rolls 2
        val hostMoveP2 = hostEngine.makeMove(SnakesAndLaddersMove(2), PlayerId.PLAYER_2)
        assertTrue(hostMoveP2 is MoveResult.Success)
        val syncPayload2 = serializeSnakesState(hostEngine.state, 2)

        for (client in clientEngines) {
            val synced2 = deserializeSnakesState(syncPayload2)
            assertNotNull(synced2)
            client.syncState(synced2!!)
            assertEquals(3, client.state.p2Position)
            assertEquals(PlayerId.PLAYER_3, client.state.activePlayer)
        }
    }

    @Test
    fun testWinningAt100AndResetRematchPreservesFivePlayers() {
        val engine = SnakesAndLaddersGameEngine(initialPlayerCount = 5)

        // Simulate Player 3 reaching 100
        val targetMove = SnakesAndLaddersMove(5) // from near 95
        // Manually place P3 near 100 via state sync
        val nearWinState = engine.state.copy(
            playerPositions = listOf(1, 1, 95, 1, 1),
            activePlayer = PlayerId.PLAYER_3
        )
        engine.syncState(nearWinState)

        val winRes = engine.makeMove(targetMove, PlayerId.PLAYER_3)
        assertTrue(winRes is MoveResult.Success)
        assertEquals(100, engine.state.p3Position)
        assertTrue(engine.state.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_3, (engine.state.status as GameStatus.MatchWon).matchWinner)

        // Rematch resets game: target score preserved, 5 players back at square 1
        val resetState = engine.resetGame(1)
        assertEquals(5, resetState.playerCount)
        assertEquals(listOf(1, 1, 1, 1, 1), resetState.playerPositions)
        assertEquals(PlayerId.PLAYER_1, resetState.activePlayer)
        assertTrue(resetState.status is GameStatus.InProgress)
    }
}
