package com.example.core.engine.chess

import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessOrientationTest {

    // ------------------------------------------------------------------------
    // REQUIREMENT 1: Same-Device 2-Player Mode — Automatic Turn-Based Flip
    // ------------------------------------------------------------------------

    @Test
    fun `same-device 2-player mode orients board with White at bottom when White's turn`() {
        val isRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.LOCAL_1V1,
            activeColor = PieceColor.WHITE,
            localColor = PieceColor.WHITE
        )
        // isRotated == false means White at bottom, Black at top
        assertFalse("White's turn must place White at bottom (isRotated=false)", isRotated)
    }

    @Test
    fun `same-device 2-player mode flips board with Black at bottom when Black's turn`() {
        val isRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.LOCAL_1V1,
            activeColor = PieceColor.BLACK,
            localColor = PieceColor.WHITE
        )
        // isRotated == true means Black at bottom, White at top
        assertTrue("Black's turn must place Black at bottom (isRotated=true)", isRotated)
    }

    @Test
    fun `same-device 2-player mode flips orientation across move cycle while preserving board state`() {
        val engine = ChessEngine()
        val initialState = engine.state

        // Move 0: Game starts with White's turn
        assertEquals(PieceColor.WHITE, engine.state.activeColor)
        assertFalse(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.LOCAL_1V1,
                activeColor = engine.state.activeColor,
                localColor = PieceColor.WHITE
            )
        )

        // Move 1: White plays e2 -> e4: (6, 4) to (4, 4)
        val e2e4 = ChessMove(ChessSquare(6, 4), ChessSquare(4, 4))
        assertTrue(engine.makeMove(e2e4))

        // Turn changes to Black -> board flips to Black at bottom
        assertEquals(PieceColor.BLACK, engine.state.activeColor)
        assertTrue(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.LOCAL_1V1,
                activeColor = engine.state.activeColor,
                localColor = PieceColor.WHITE
            )
        )
        // Underlying board state is correctly updated
        assertNotNull(engine.state.pieceAt(ChessSquare(4, 4)))
        assertEquals(PieceType.PAWN, engine.state.pieceAt(ChessSquare(4, 4))?.type)
        assertEquals(PieceColor.WHITE, engine.state.pieceAt(ChessSquare(4, 4))?.color)

        // Move 2: Black plays e7 -> e5: (1, 4) to (3, 4)
        val e7e5 = ChessMove(ChessSquare(1, 4), ChessSquare(3, 4))
        assertTrue(engine.makeMove(e7e5))

        // Turn changes back to White -> board flips back to White at bottom
        assertEquals(PieceColor.WHITE, engine.state.activeColor)
        assertFalse(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.LOCAL_1V1,
                activeColor = engine.state.activeColor,
                localColor = PieceColor.WHITE
            )
        )

        // Move 3: White plays Nf3: (7, 6) to (5, 5)
        val nf3 = ChessMove(ChessSquare(7, 6), ChessSquare(5, 5))
        assertTrue(engine.makeMove(nf3))

        // Turn changes to Black -> board flips again to Black at bottom
        assertEquals(PieceColor.BLACK, engine.state.activeColor)
        assertTrue(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.LOCAL_1V1,
                activeColor = engine.state.activeColor,
                localColor = PieceColor.WHITE
            )
        )
    }

    @Test
    fun `board orientation does not repeatedly flip when turn is refreshed without move`() {
        val state = ChessGameState.initial()
        val rot1 = ChessBoardOrientation.isBoardRotated(GameMode.LOCAL_1V1, state.activeColor, PieceColor.WHITE)
        val rot2 = ChessBoardOrientation.isBoardRotated(GameMode.LOCAL_1V1, state.activeColor, PieceColor.WHITE)
        assertEquals(rot1, rot2)
        assertFalse(rot1)
    }

    // ------------------------------------------------------------------------
    // REQUIREMENT 2 & 3: Nearby Multiplayer — Color Selection and Perspective
    // ------------------------------------------------------------------------

    @Test
    fun `host chooses White - Phone A (Host) sees White at bottom, Phone B (Joiner) sees Black at bottom`() {
        val hostPreference = "WHITE"

        // Phone A is Host
        val (phoneALocalColor, phoneAOpponentColor) = ChessBoardOrientation.resolveNearbyColors(hostPreference, isHost = true)
        assertEquals(PieceColor.WHITE, phoneALocalColor)
        assertEquals(PieceColor.BLACK, phoneAOpponentColor)
        val phoneABoardRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.BLUETOOTH_1V1,
            activeColor = PieceColor.WHITE,
            localColor = phoneALocalColor
        )
        assertFalse("Phone A (Host = White) must see White at bottom (isRotated=false)", phoneABoardRotated)

        // Phone B is Joiner
        val (phoneBLocalColor, phoneBOpponentColor) = ChessBoardOrientation.resolveNearbyColors(hostPreference, isHost = false)
        assertEquals(PieceColor.BLACK, phoneBLocalColor)
        assertEquals(PieceColor.WHITE, phoneBOpponentColor)
        val phoneBBoardRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.BLUETOOTH_1V1,
            activeColor = PieceColor.WHITE,
            localColor = phoneBLocalColor
        )
        assertTrue("Phone B (Joiner = Black) must see Black at bottom (isRotated=true)", phoneBBoardRotated)
    }

    @Test
    fun `host chooses Black - Phone A (Host) sees Black at bottom, Phone B (Joiner) sees White at bottom`() {
        val hostPreference = "BLACK"

        // Phone A is Host
        val (phoneALocalColor, phoneAOpponentColor) = ChessBoardOrientation.resolveNearbyColors(hostPreference, isHost = true)
        assertEquals(PieceColor.BLACK, phoneALocalColor)
        assertEquals(PieceColor.WHITE, phoneAOpponentColor)
        val phoneABoardRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.BLUETOOTH_1V1,
            activeColor = PieceColor.WHITE,
            localColor = phoneALocalColor
        )
        assertTrue("Phone A (Host = Black) must see Black at bottom (isRotated=true)", phoneABoardRotated)

        // Phone B is Joiner
        val (phoneBLocalColor, phoneBOpponentColor) = ChessBoardOrientation.resolveNearbyColors(hostPreference, isHost = false)
        assertEquals(PieceColor.WHITE, phoneBLocalColor)
        assertEquals(PieceColor.BLACK, phoneBOpponentColor)
        val phoneBBoardRotated = ChessBoardOrientation.isBoardRotated(
            selectedMode = GameMode.BLUETOOTH_1V1,
            activeColor = PieceColor.WHITE,
            localColor = phoneBLocalColor
        )
        assertFalse("Phone B (Joiner = White) must see White at bottom (isRotated=false)", phoneBBoardRotated)
    }

    @Test
    fun `reverse hosting direction - Phone B hosts and Phone A joins`() {
        // Sub-case 1: Phone B hosts and chooses White
        val (bHostWhite, aJoinBlack) = ChessBoardOrientation.resolveNearbyColors("WHITE", isHost = true) to
            ChessBoardOrientation.resolveNearbyColors("WHITE", isHost = false)
        assertEquals(PieceColor.WHITE, bHostWhite.first)
        assertEquals(PieceColor.BLACK, aJoinBlack.first)
        assertFalse(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, bHostWhite.first))
        assertTrue(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, aJoinBlack.first))

        // Sub-case 2: Phone B hosts and chooses Black
        val (bHostBlack, aJoinWhite) = ChessBoardOrientation.resolveNearbyColors("BLACK", isHost = true) to
            ChessBoardOrientation.resolveNearbyColors("BLACK", isHost = false)
        assertEquals(PieceColor.BLACK, bHostBlack.first)
        assertEquals(PieceColor.WHITE, aJoinWhite.first)
        assertTrue(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, bHostBlack.first))
        assertFalse(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, aJoinWhite.first))
    }

    @Test
    fun `nearby multiplayer board orientation remains constant to local player across turns`() {
        val hostLocalColor = PieceColor.WHITE
        val clientLocalColor = PieceColor.BLACK

        // Turn 1: White's turn
        assertFalse(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, hostLocalColor))
        assertTrue(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.WHITE, clientLocalColor))

        // Turn 2: Black's turn (Nearby boards must NOT flip turn-by-turn; they stay anchored to player's color!)
        assertFalse(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.BLACK, hostLocalColor))
        assertTrue(ChessBoardOrientation.isBoardRotated(GameMode.BLUETOOTH_1V1, PieceColor.BLACK, clientLocalColor))
    }

    // ------------------------------------------------------------------------
    // COORDINATE TRANSLATION AND PIECE MAPPING
    // ------------------------------------------------------------------------

    @Test
    fun `visual to board coordinate mapping is accurate for normal orientation`() {
        // e2 pawn: rank 6, col 4
        val e2 = ChessBoardOrientation.visualToBoardSquare(visRow = 6, visCol = 4, isRotated = false)
        assertEquals(ChessSquare(6, 4), e2)

        // a1 corner: visual (7, 0) -> board (7, 0)
        val a1 = ChessBoardOrientation.visualToBoardSquare(visRow = 7, visCol = 0, isRotated = false)
        assertEquals(ChessSquare(7, 0), a1)

        // h8 corner: visual (0, 7) -> board (0, 7)
        val h8 = ChessBoardOrientation.visualToBoardSquare(visRow = 0, visCol = 7, isRotated = false)
        assertEquals(ChessSquare(0, 7), h8)
    }

    @Test
    fun `visual to board coordinate mapping is accurate for rotated orientation`() {
        // When rotated (Black's view):
        // Black's King is at e8: board row 0, col 4.
        // Visually, Black King appears on bottom rank: visRow = 7, visCol = 3 (since files are h..a).
        val mappedKing = ChessBoardOrientation.visualToBoardSquare(visRow = 7, visCol = 3, isRotated = true)
        assertEquals(ChessSquare(0, 4), mappedKing)

        // Black's a7 pawn: board row 1, col 0.
        // Visually, visRow = 6, visCol = 7 (rightmost col on screen).
        val mappedA7 = ChessBoardOrientation.visualToBoardSquare(visRow = 6, visCol = 7, isRotated = true)
        assertEquals(ChessSquare(1, 0), mappedA7)

        // Round-trip bijection check for all 64 squares
        for (r in 0..7) {
            for (c in 0..7) {
                val orig = ChessSquare(r, c)
                val (visR, visC) = ChessBoardOrientation.boardToVisualCoordinates(orig, isRotated = true)
                val restored = ChessBoardOrientation.visualToBoardSquare(visR, visC, isRotated = true)
                assertEquals(orig, restored)
            }
        }
    }

    // ------------------------------------------------------------------------
    // REQUIREMENT 4: VS Bot Behavior Preservation
    // ------------------------------------------------------------------------

    @Test
    fun `vs bot mode preserves human player assigned color orientation`() {
        // Player chose White: White at bottom
        assertFalse(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.SOLO,
                activeColor = PieceColor.WHITE,
                localColor = PieceColor.WHITE,
                humanPlayerColor = PieceColor.WHITE
            )
        )

        // Player chose Black: Black at bottom
        assertTrue(
            ChessBoardOrientation.isBoardRotated(
                selectedMode = GameMode.SOLO,
                activeColor = PieceColor.WHITE,
                localColor = PieceColor.BLACK,
                humanPlayerColor = PieceColor.BLACK
            )
        )
    }
}
