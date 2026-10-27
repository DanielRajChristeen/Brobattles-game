package com.example.core.engine.checkers

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
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
class CheckersEngineTest {

    @Test
    fun testInitialBoardSetup() {
        val engine = CheckersEngine()
        val state = engine.state

        assertEquals(12, state.redPiecesRemaining)
        assertEquals(12, state.blackPiecesRemaining)
        assertEquals(0, state.redKings)
        assertEquals(0, state.blackKings)
        assertEquals(PlayerId.PLAYER_1, state.activePlayer)
        assertEquals(GameStatus.InProgress, state.status)

        // Verify all 24 pieces are placed on valid dark playable squares
        assertEquals(24, state.board.size)
        assertTrue(state.board.keys.all { it.isDarkPlayable })

        // Red is on rows 5..7
        assertTrue(state.board.filter { it.value.player == PlayerId.PLAYER_1 }.keys.all { it.row in 5..7 })

        // Black is on rows 0..2
        assertTrue(state.board.filter { it.value.player == PlayerId.PLAYER_2 }.keys.all { it.row in 0..2 })
    }

    @Test
    fun testInitialLegalMoves() {
        val engine = CheckersEngine()
        val legalMoves = engine.getLegalMoves()

        // Red pieces in row 5 can each move forward into row 4
        // Row 5 dark squares are cols 0, 2, 4, 6
        // Col 0 can move to (4, 1) -> 1 move
        // Col 2 can move to (4, 1) and (4, 3) -> 2 moves
        // Col 4 can move to (4, 3) and (4, 5) -> 2 moves
        // Col 6 can move to (4, 5) and (4, 7) -> 2 moves
        // Total = 7 legal moves for Red
        assertEquals(7, legalMoves.size)
        assertTrue(legalMoves.all { !it.isJump })
        assertTrue(legalMoves.all { it.from.row == 5 && it.to.row == 4 })
    }

    @Test
    fun testRegularMoveExecution() {
        val engine = CheckersEngine()
        val move = CheckersMove(CheckersSquare(5, 0), CheckersSquare(4, 1))
        val result = engine.makeMove(move)

        assertTrue(result.success)
        assertFalse(result.isCapture)
        assertFalse(result.isKingPromotion)
        assertFalse(result.multiJumpAvailable)

        val newState = engine.state
        assertEquals(PlayerId.PLAYER_2, newState.activePlayer)
        assertNull(newState.board[CheckersSquare(5, 0)])
        assertNotNull(newState.board[CheckersSquare(4, 1)])
        assertEquals(PlayerId.PLAYER_1, newState.board[CheckersSquare(4, 1)]?.player)
    }

    @Test
    fun testMandatoryCaptureRule() {
        // Setup custom state where Red can make a capture and a non-capture move
        val redPiece = CheckersPiece(1, PlayerId.PLAYER_1, isKing = false)
        val blackPiece = CheckersPiece(2, PlayerId.PLAYER_2, isKing = false)
        val nonCapturingRed = CheckersPiece(3, PlayerId.PLAYER_1, isKing = false)

        val board = mapOf(
            CheckersSquare(5, 2) to redPiece,
            CheckersSquare(4, 3) to blackPiece,
            // CheckersSquare(3, 4) is empty for jump destination!
            CheckersSquare(7, 0) to nonCapturingRed
        )

        val state = CheckersGameState(
            board = board,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress
        )

        val legalMoves = CheckersRules.getLegalMoves(state)

        // Mandatory Capture: only the jump move (5,2)->(3,4) must be legal!
        assertEquals(1, legalMoves.size)
        val jump = legalMoves.first()
        assertTrue(jump.isJump)
        assertEquals(CheckersSquare(5, 2), jump.from)
        assertEquals(CheckersSquare(3, 4), jump.to)
        assertEquals(CheckersSquare(4, 3), jump.capturedSquare)
    }

    @Test
    fun testMultiJumpExecution() {
        // Setup state where a piece has a double jump available
        val redPiece = CheckersPiece(1, PlayerId.PLAYER_1, isKing = false)
        val black1 = CheckersPiece(2, PlayerId.PLAYER_2, isKing = false)
        val black2 = CheckersPiece(3, PlayerId.PLAYER_2, isKing = false)

        val board = mapOf(
            CheckersSquare(5, 0) to redPiece,
            CheckersSquare(4, 1) to black1,
            // (3, 2) is landing square of 1st jump
            CheckersSquare(2, 3) to black2
            // (1, 4) is landing square of 2nd jump
        )

        val engine = CheckersEngine()
        engine.syncState(
            CheckersGameState(
                board = board,
                activePlayer = PlayerId.PLAYER_1,
                status = GameStatus.InProgress
            )
        )

        // Execute 1st jump
        val res1 = engine.makeMove(CheckersMove(CheckersSquare(5, 0), CheckersSquare(3, 2)))
        assertTrue(res1.success)
        assertTrue(res1.isCapture)
        assertTrue(res1.multiJumpAvailable) // Must continue jumping!

        val stateAfterJump1 = engine.state
        assertEquals(PlayerId.PLAYER_1, stateAfterJump1.activePlayer) // Turn does not end!
        assertEquals(CheckersSquare(3, 2), stateAfterJump1.mustContinueJumpingFrom)

        // Execute 2nd jump
        val res2 = engine.makeMove(CheckersMove(CheckersSquare(3, 2), CheckersSquare(1, 4)))
        assertTrue(res2.success)
        assertTrue(res2.isCapture)
        assertFalse(res2.multiJumpAvailable) // No more jumps

        val stateAfterJump2 = engine.state
        assertEquals(PlayerId.PLAYER_2, stateAfterJump2.activePlayer) // Turn now ends
        assertNull(stateAfterJump2.mustContinueJumpingFrom)
        assertEquals(0, stateAfterJump2.blackPiecesRemaining)
        assertTrue(stateAfterJump2.status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (stateAfterJump2.status as GameStatus.MatchWon).matchWinner)
    }

    @Test
    fun testKingPromotionAndBackwardMovement() {
        // Red piece moving to row 0 promotes to King
        val redPiece = CheckersPiece(1, PlayerId.PLAYER_1, isKing = false)
        val board = mapOf(CheckersSquare(1, 2) to redPiece)

        val engine = CheckersEngine()
        engine.syncState(
            CheckersGameState(
                board = board,
                activePlayer = PlayerId.PLAYER_1,
                status = GameStatus.InProgress
            )
        )

        val res = engine.makeMove(CheckersMove(CheckersSquare(1, 2), CheckersSquare(0, 3)))
        assertTrue(res.success)
        assertTrue(res.isKingPromotion)

        val state = engine.state
        val crownedPiece = state.board[CheckersSquare(0, 3)]
        assertNotNull(crownedPiece)
        assertTrue(crownedPiece!!.isKing)

        // Verify that a King can move backwards
        val kingBoard = mapOf(CheckersSquare(4, 3) to CheckersPiece(1, PlayerId.PLAYER_1, isKing = true))
        val kingState = CheckersGameState(
            board = kingBoard,
            activePlayer = PlayerId.PLAYER_1,
            status = GameStatus.InProgress
        )

        val kingLegal = CheckersRules.getLegalMoves(kingState)
        // Can move in all 4 diagonal directions: (3, 2), (3, 4), (5, 2), (5, 4)
        assertEquals(4, kingLegal.size)
        assertTrue(kingLegal.any { it.to == CheckersSquare(5, 2) }) // Backward move for Red
        assertTrue(kingLegal.any { it.to == CheckersSquare(5, 4) }) // Backward move for Red
    }

    @Test
    fun testWinConditionWhenNoLegalMoves() {
        // Black piece is completely trapped in the corner
        val blackPiece = CheckersPiece(1, PlayerId.PLAYER_2, isKing = false)
        val red1 = CheckersPiece(2, PlayerId.PLAYER_1, isKing = true)
        val red2 = CheckersPiece(3, PlayerId.PLAYER_1, isKing = true)

        // Black at (0, 7) has only forward move to (1, 6), blocked by Red
        // And jump over (1, 6) would land at (2, 5), blocked by another Red piece
        val board = mapOf(
            CheckersSquare(0, 7) to blackPiece,
            CheckersSquare(1, 6) to red1,
            CheckersSquare(2, 5) to red2
        )

        val state = CheckersGameState(
            board = board,
            activePlayer = PlayerId.PLAYER_2,
            status = GameStatus.InProgress
        )

        val status = CheckersRules.evaluateGameStatus(state)
        assertTrue(status is GameStatus.MatchWon)
        assertEquals(PlayerId.PLAYER_1, (status as GameStatus.MatchWon).matchWinner)
    }

    @Test
    fun testStateSerializationAndDeserialization() {
        val engine = CheckersEngine()
        val originalState = engine.state

        val serialized = serializeCheckersState(originalState)
        val restored = deserializeCheckersState(serialized)

        assertNotNull(restored)
        assertEquals(originalState.activePlayer, restored!!.activePlayer)
        assertEquals(originalState.redPiecesRemaining, restored.redPiecesRemaining)
        assertEquals(originalState.blackPiecesRemaining, restored.blackPiecesRemaining)
        assertEquals(originalState.board.size, restored.board.size)
    }

    @Test
    fun testBotMoveDecided() {
        val engine = CheckersEngine()
        // Switch to Black's turn
        engine.makeMove(CheckersMove(CheckersSquare(5, 0), CheckersSquare(4, 1)))

        val botMove = CheckersBot.decideMove(engine.state, CheckersDifficulty.EASY, PlayerId.PLAYER_2)
        assertNotNull(botMove)
        assertTrue(engine.validateMove(botMove!!))
    }

    @Test
    fun testCheckersSoundSynthesisAndVibrations() {
        // Test coin tap tone generation
        val coinNotes = listOf(480.0 to 20, 640.0 to 35)
        val coinPcm = com.example.core.audio.CheckersSoundManager.generateToneSequence(coinNotes)
        assertTrue(coinPcm.isNotEmpty())
        assertTrue(coinPcm.any { it != 0.toByte() })

        // Test king crowned regal fanfare
        val kingNotes = listOf(523.25 to 60, 659.25 to 60, 783.99 to 80, 1046.50 to 160)
        val kingPcm = com.example.core.audio.CheckersSoundManager.generateToneSequence(kingNotes)
        assertTrue(kingPcm.isNotEmpty())
        assertTrue(kingPcm.any { it != 0.toByte() })

        // Test vibration patterns
        val kingVibe = com.example.core.audio.CheckersSoundManager.KING_CROWN_VIBRATION_PATTERN
        val winVibe = com.example.core.audio.CheckersSoundManager.VICTORY_VIBRATION_PATTERN
        val defeatVibe = com.example.core.audio.CheckersSoundManager.DEFEAT_VIBRATION_PATTERN

        assertTrue(kingVibe.isNotEmpty())
        assertTrue(winVibe.isNotEmpty())
        assertTrue(defeatVibe.isNotEmpty())
        assertFalse(winVibe.contentEquals(defeatVibe))
    }
}
