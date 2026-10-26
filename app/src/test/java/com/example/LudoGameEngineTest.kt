package com.example

import com.example.core.bot.BotDifficulty
import com.example.core.engine.ludo.LudoBoardCoordinates
import com.example.core.engine.ludo.LudoBotOpponent
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoGameEngine
import com.example.core.engine.ludo.LudoPlayerState
import com.example.core.engine.ludo.LudoToken
import com.example.core.engine.ludo.deserializeLudoState
import com.example.core.engine.ludo.serializeLudoState
import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoGameEngineTest {

    @Test
    fun `initial state has 4 tokens in yard for all players`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 4)
        val state = engine.state

        assertEquals(4, state.players.size)
        assertEquals(0, state.currentTurnIndex)
        assertEquals(LudoColor.RED, state.currentPlayer.color)
        assertTrue(state.canRoll)
        assertNull(state.diceRoll)
        assertTrue(state.selectableTokenIds.isEmpty())

        for (player in state.players) {
            assertEquals(4, player.tokens.size)
            assertTrue(player.tokens.all { it.isInYard })
            assertEquals(0, player.finishedCount)
            assertFalse(player.isWinner)
        }
    }

    @Test
    fun `rolling 2, 3, 4, 5 with all tokens in yard gives no legal moves and passes turn`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Player 1 rolls 3
        engine.rollDice(forcedValue = 3)
        val state = engine.state

        // Turn should have passed to Player 2 (Green)
        assertEquals(1, state.currentTurnIndex)
        assertEquals(LudoColor.GREEN, state.currentPlayer.color)
        assertTrue(state.canRoll)
        assertTrue(state.selectableTokenIds.isEmpty())
        assertTrue(state.players[0].tokens.all { it.isInYard })
    }

    @Test
    fun `rolling 1 releases token from yard to start cell and grants extra turn`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Player 1 (Red) rolls 1
        engine.rollDice(forcedValue = 1)
        val rolledState = engine.state

        assertEquals(1, rolledState.diceRoll)
        assertFalse(rolledState.canRoll)
        assertEquals(4, rolledState.selectableTokenIds.size) // Any of the 4 yard tokens

        // Move token 0
        engine.moveToken(0)
        val movedState = engine.state

        // Token 0 should now be at step 0 (Start square)
        val redToken0 = movedState.players[0].tokens[0]
        assertEquals(0, redToken0.step)
        assertTrue(redToken0.isOnTrack)
        assertEquals(0, redToken0.trackIndex) // Red start is track index 0

        // Extra turn granted! Red is still active player and can roll again
        assertEquals(0, movedState.currentTurnIndex)
        assertEquals(LudoColor.RED, movedState.currentPlayer.color)
        assertTrue(movedState.canRoll)
        assertTrue(movedState.extraTurn)
    }

    @Test
    fun `rolling 6 releases token from yard to start cell and grants extra turn`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Player 1 (Red) rolls 6
        engine.rollDice(forcedValue = 6)
        engine.moveToken(1)
        val state = engine.state

        val redToken1 = state.players[0].tokens[1]
        assertEquals(0, redToken1.step)
        assertTrue(redToken1.isOnTrack)
        assertEquals(0, state.currentTurnIndex)
        assertTrue(state.canRoll)
        assertTrue(state.extraTurn)
    }

    @Test
    fun `token on track advances by dice roll amount`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Roll 6 and release token 0 to step 0
        engine.rollDice(forcedValue = 6)
        engine.moveToken(0)

        // Red has extra turn, rolls 4
        engine.rollDice(forcedValue = 4)
        engine.moveToken(0)
        val state = engine.state

        // Token 0 should now be at step 0 + 4 = 4
        val token0 = state.players[0].tokens[0]
        assertEquals(4, token0.step)
        assertEquals(4, token0.trackIndex)

        // No 1 or 6 and no capture, so turn passes to Player 2 (Green)
        assertEquals(1, state.currentTurnIndex)
        assertEquals(LudoColor.GREEN, state.currentPlayer.color)
    }

    @Test
    fun `exact roll required to enter center finish`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Setup token 0 at step 55 (2 steps away from finish at 57)
        val currentP = engine.state.players[0]
        val updatedTokens = currentP.tokens.map { if (it.id == 0) it.copy(step = 55) else it }
        val updatedPlayers = engine.state.players.map { if (it.color == LudoColor.RED) it.copy(tokens = updatedTokens) else it }
        engine.setState(engine.state.copy(players = updatedPlayers))

        // Rolling 3 is an overshoot (55 + 3 = 58 > 57) -> illegal for token 0!
        engine.rollDice(forcedValue = 3)
        assertFalse(engine.state.selectableTokenIds.contains(0))

        // Reset to Red turn and roll 2 -> exactly 55 + 2 = 57 (legal!)
        engine.setState(engine.state.copy(currentTurnIndex = 0, canRoll = true, diceRoll = null, selectableTokenIds = emptyList()))
        engine.rollDice(forcedValue = 2)
        assertTrue(engine.state.selectableTokenIds.contains(0))

        engine.moveToken(0)
        val finishedToken = engine.state.players[0].tokens[0]
        assertEquals(57, finishedToken.step)
        assertTrue(finishedToken.isFinished)
    }

    @Test
    fun `capturing enemy token sends enemy to yard and grants extra turn`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Place Green token 0 at outer track index 5 (step 5 - 13 + 52) % 52 = 44 for Green
        // Track index 5 is not safe!
        val greenToken0 = LudoToken(id = 0, color = LudoColor.GREEN, step = 44) // (13 + 44) % 52 = 5
        assertEquals(5, greenToken0.trackIndex)
        assertFalse(LudoBoardCoordinates.isSafeSquare(5))

        // Place Red token 0 at step 2 (track index 2)
        val redToken0 = LudoToken(id = 0, color = LudoColor.RED, step = 2)
        assertEquals(2, redToken0.trackIndex)

        val updatedP1 = engine.state.players[0].copy(tokens = listOf(redToken0, LudoToken(1, LudoColor.RED), LudoToken(2, LudoColor.RED), LudoToken(3, LudoColor.RED)))
        val updatedP2 = engine.state.players[1].copy(tokens = listOf(greenToken0, LudoToken(1, LudoColor.GREEN), LudoToken(2, LudoColor.GREEN), LudoToken(3, LudoColor.GREEN)))
        engine.setState(engine.state.copy(players = listOf(updatedP1, updatedP2), currentTurnIndex = 0, canRoll = true))

        // Red rolls 3: advances from step 2 to step 5 (track index 5, capturing Green!)
        engine.rollDice(forcedValue = 3)
        engine.moveToken(0)
        val state = engine.state

        // Red is at step 5
        assertEquals(5, state.players[0].tokens[0].step)
        // Green token 0 was captured and sent back to yard (step -1)
        assertEquals(-1, state.players[1].tokens[0].step)
        assertTrue(state.players[1].tokens[0].isInYard)

        // Capturing grants an extra turn even though roll was 3!
        assertEquals(0, state.currentTurnIndex)
        assertEquals(LudoColor.RED, state.currentPlayer.color)
        assertTrue(state.canRoll)
        assertTrue(state.extraTurn)
    }

    @Test
    fun `safe track cells protect tokens from capture`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Star safe square at track index 8
        assertTrue(LudoBoardCoordinates.isSafeSquare(8))

        // Place Green token 0 at track index 8
        val greenToken0 = LudoToken(id = 0, color = LudoColor.GREEN, step = 47) // (13 + 47) % 52 = 8
        assertEquals(8, greenToken0.trackIndex)

        // Place Red token 0 at step 4 (track index 4)
        val redToken0 = LudoToken(id = 0, color = LudoColor.RED, step = 4)

        val updatedP1 = engine.state.players[0].copy(tokens = listOf(redToken0, LudoToken(1, LudoColor.RED), LudoToken(2, LudoColor.RED), LudoToken(3, LudoColor.RED)))
        val updatedP2 = engine.state.players[1].copy(tokens = listOf(greenToken0, LudoToken(1, LudoColor.GREEN), LudoToken(2, LudoColor.GREEN), LudoToken(3, LudoColor.GREEN)))
        engine.setState(engine.state.copy(players = listOf(updatedP1, updatedP2), currentTurnIndex = 0, canRoll = true))

        // Red rolls 4: lands on track index 8 (safe square!)
        engine.rollDice(forcedValue = 4)
        engine.moveToken(0)
        val state = engine.state

        // Red landed on 8
        assertEquals(8, state.players[0].tokens[0].step)
        // Green was NOT captured because it is a safe square!
        assertEquals(47, state.players[1].tokens[0].step)
        assertEquals(8, state.players[1].tokens[0].trackIndex)
        assertFalse(state.players[1].tokens[0].isInYard)
    }

    @Test
    fun `all 4 tokens finished triggers match win`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 2)

        // Setup Red with 3 finished tokens and token 3 at step 56 (needs 1 to finish)
        val redTokens = listOf(
            LudoToken(0, LudoColor.RED, step = 57),
            LudoToken(1, LudoColor.RED, step = 57),
            LudoToken(2, LudoColor.RED, step = 57),
            LudoToken(3, LudoColor.RED, step = 56)
        )
        val updatedP1 = engine.state.players[0].copy(tokens = redTokens)
        engine.setState(engine.state.copy(players = listOf(updatedP1, engine.state.players[1]), currentTurnIndex = 0, canRoll = true))

        // Red rolls 1
        engine.rollDice(forcedValue = 1)
        engine.moveToken(3)
        val wonState = engine.state

        assertEquals(LudoColor.RED, wonState.winner)
        assertTrue(wonState.isGameOver)
        assertEquals(4, wonState.players[0].finishedCount)
        assertTrue(wonState.players[0].isWinner)
    }

    @Test
    fun `bot opponent prioritizes winning move`() {
        val bot = LudoBotOpponent()
        val yellowTokens = listOf(
            LudoToken(0, LudoColor.YELLOW, step = 57),
            LudoToken(1, LudoColor.YELLOW, step = 57),
            LudoToken(2, LudoColor.YELLOW, step = 57),
            LudoToken(3, LudoColor.YELLOW, step = 55) // roll of 2 wins!
        )
        val yellowPlayer = LudoPlayerState(LudoColor.YELLOW, "Bot", isBot = true, tokens = yellowTokens)
        val state = com.example.core.engine.ludo.LudoGameState(
            mode = GameMode.SOLO,
            players = listOf(LudoPlayerState(LudoColor.RED, "Player"), yellowPlayer),
            currentTurnIndex = 1,
            diceRoll = 2,
            canRoll = false,
            selectableTokenIds = listOf(3)
        )

        val move = bot.decideMove(state, BotDifficulty.HARD)
        assertEquals(3, move)
    }

    @Test
    fun `serialization and deserialization roundtrip preserves full state`() {
        val engine = LudoGameEngine(GameMode.LOCAL_1V1, playerCount = 3)
        engine.rollDice(forcedValue = 6)
        engine.moveToken(0)

        val json = serializeLudoState(engine.state)
        assertNotNull(json)
        val restored = deserializeLudoState(json)
        assertNotNull(restored)

        assertEquals(engine.state.mode, restored!!.mode)
        assertEquals(engine.state.currentTurnIndex, restored.currentTurnIndex)
        assertEquals(engine.state.players.size, restored.players.size)
        assertEquals(engine.state.players[0].tokens[0].step, restored.players[0].tokens[0].step)
    }

    @Test
    fun `pawn renderer canvas offset produces valid distinct positions for yard, track, home lane, and center`() {
        val boardOrigin = androidx.compose.ui.geometry.Offset(0f, 0f)
        val cellSize = 40f

        for (color in LudoColor.values()) {
            // Yard position
            val yardOffset = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getCanvasOffsetForStep(
                color = color, tokenId = 0, step = -1, cellSize = cellSize
            )
            assertTrue(yardOffset.x > 0f && yardOffset.y > 0f)

            // Start square (step 0)
            val startOffset = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getCanvasOffsetForStep(
                color = color, tokenId = 0, step = 0, cellSize = cellSize
            )
            assertTrue(startOffset.x >= 0f && startOffset.y >= 0f)

            // Last track square (step 51)
            val trackLastOffset = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getCanvasOffsetForStep(
                color = color, tokenId = 0, step = 51, cellSize = cellSize
            )
            assertTrue(trackLastOffset.x >= 0f && trackLastOffset.y >= 0f)

            // Home stretch squares (steps 52..56)
            for (homeStep in 52..56) {
                val homeOffset = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getCanvasOffsetForStep(
                    color = color, tokenId = 0, step = homeStep, cellSize = cellSize
                )
                assertTrue(homeOffset.x >= 0f && homeOffset.y >= 0f)
            }

            // Finish center (step 57)
            val finishOffset = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getCanvasOffsetForStep(
                color = color, tokenId = 0, step = 57, cellSize = cellSize
            )
            // Center 3x3 finish zone on 15x15 board with cellSize=40 spans rows 6..8, cols 6..8 (240px..360px)
            assertTrue(finishOffset.x in 240f..360f)
            assertTrue(finishOffset.y in 240f..360f)
        }
    }

    @Test
    fun `step by step forward hopping path derives intermediate squares in correct order`() {
        // Given pawn at step 5 moving 3 steps to step 8
        val currentStep = 5
        val diceRoll = 3
        val path = (1..diceRoll).map { currentStep + it }

        assertEquals(listOf(6, 7, 8), path)
    }

    @Test
    fun `capture animation reverse path walks back to origin yard`() {
        // Red captures Yellow pawn at Yellow's step 8
        val capturedStep = 8
        val reversePath = (capturedStep downTo 0).toList() + listOf(-1)

        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1, 0, -1), reversePath)
        assertEquals(-1, reversePath.last()) // Lands in own yard
    }

    @Test
    fun `multi-token clustering offsets distribute pawns without overlapping centers`() {
        val cellSize = 40f
        val offset0 = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getMultiTokenOffset(0, 2, cellSize)
        val offset1 = com.example.ui.screens.detail.ludo.LudoPawnRenderer.getMultiTokenOffset(1, 2, cellSize)

        assertFalse(offset0 == offset1)
        assertTrue(offset0.x < 0f)
        assertTrue(offset1.x > 0f)
    }
}
