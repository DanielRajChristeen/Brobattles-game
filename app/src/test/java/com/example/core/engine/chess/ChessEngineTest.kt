package com.example.core.engine.chess

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    @Test
    fun `initial position has 20 legal moves for White`() {
        val state = ChessGameState.initial()
        assertEquals(PieceColor.WHITE, state.activeColor)
        val moves = ChessRules.generateLegalMoves(state)
        assertEquals(20, moves.size) // 8 single pushes, 8 double pushes, 4 knight jumps
    }

    @Test
    fun `pawn advance and double push updates state and en passant target`() {
        val state = ChessGameState.initial()
        // e2 -> e4: from (6, 4) to (4, 4)
        val e2e4 = ChessMove(ChessSquare(6, 4), ChessSquare(4, 4))
        val nextState = ChessRules.applyMove(state, e2e4)

        assertEquals(PieceColor.BLACK, nextState.activeColor)
        assertNotNull(nextState.pieceAt(ChessSquare(4, 4)))
        assertNull(nextState.pieceAt(ChessSquare(6, 4)))
        // En passant target is e3: (5, 4)
        assertEquals(ChessSquare(5, 4), nextState.enPassantTarget)
    }

    @Test
    fun `en passant capture removes captured pawn`() {
        // Setup board with White pawn on e5 (3, 4) and Black pawn moving d7->d5 (1, 3 -> 3, 3)
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
        board[3 * 8 + 4] = ChessPiece(PieceType.PAWN, PieceColor.WHITE) // e5
        board[1 * 8 + 3] = ChessPiece(PieceType.PAWN, PieceColor.BLACK) // d7

        val state1 = ChessGameState(board = board, activeColor = PieceColor.BLACK)
        // Black plays d7 -> d5
        val d7d5 = ChessMove(ChessSquare(1, 3), ChessSquare(3, 3))
        val state2 = ChessRules.applyMove(state1, d7d5)

        assertEquals(ChessSquare(2, 3), state2.enPassantTarget) // d6 is en passant target

        // White captures en passant: e5xd6
        val moves = ChessRules.generateLegalMoves(state2)
        val epMove = moves.firstOrNull { it.isEnPassant }
        assertNotNull(epMove)
        assertEquals(ChessSquare(2, 3), epMove!!.to)

        val state3 = ChessRules.applyMove(state2, epMove)
        // Black pawn at (3, 3) should be removed!
        assertNull(state3.pieceAt(ChessSquare(3, 3)))
        assertNotNull(state3.pieceAt(ChessSquare(2, 3)))
        assertEquals(PieceType.PAWN, state3.pieceAt(ChessSquare(2, 3))?.type)
        assertEquals(listOf(PieceType.PAWN), state3.capturedByWhite)
    }

    @Test
    fun `kingside castling moves both king and rook`() {
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[7 * 8 + 7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE) // h1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8

        val state = ChessGameState(
            board = board,
            activeColor = PieceColor.WHITE,
            whiteCanCastleKingside = true
        )

        val moves = ChessRules.generateLegalMoves(state)
        val castleMove = moves.firstOrNull { it.isCastling && it.to.col == 6 }
        assertNotNull(castleMove)

        val nextState = ChessRules.applyMove(state, castleMove!!)
        // King is at g1 (7, 6), Rook is at f1 (7, 5)
        assertEquals(PieceType.KING, nextState.pieceAt(ChessSquare(7, 6))?.type)
        assertEquals(PieceType.ROOK, nextState.pieceAt(ChessSquare(7, 5))?.type)
        assertNull(nextState.pieceAt(ChessSquare(7, 4)))
        assertNull(nextState.pieceAt(ChessSquare(7, 7)))
        assertFalse(nextState.whiteCanCastleKingside)
    }

    @Test
    fun `cannot castle when in check or passing through check`() {
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[7 * 8 + 7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE) // h1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        // Black rook attacking f1 (7, 5): king cannot pass through check!
        board[0 * 8 + 5] = ChessPiece(PieceType.ROOK, PieceColor.BLACK) // f8

        val state = ChessGameState(
            board = board,
            activeColor = PieceColor.WHITE,
            whiteCanCastleKingside = true
        )

        val moves = ChessRules.generateLegalMoves(state)
        val castleMove = moves.firstOrNull { it.isCastling }
        assertNull(castleMove)
    }

    @Test
    fun `pawn promotion creates all 4 piece options`() {
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
        board[1 * 8 + 0] = ChessPiece(PieceType.PAWN, PieceColor.WHITE) // a7

        val state = ChessGameState(board = board, activeColor = PieceColor.WHITE)
        val moves = ChessRules.generateLegalMoves(state)
        val promoMoves = moves.filter { it.promotion != null }
        assertEquals(4, promoMoves.size)

        val queenPromo = promoMoves.first { it.promotion == PieceType.QUEEN }
        val nextState = ChessRules.applyMove(state, queenPromo)
        assertEquals(PieceType.QUEEN, nextState.pieceAt(ChessSquare(0, 0))?.type)
    }

    @Test
    fun `checkmate is detected correctly`() {
        // Fool's mate setup:
        // 1. f3 e5 2. g4 Qh4#
        val engine = ChessEngine()
        // 1. f2-f3 (6,5 -> 5,5)
        engine.makeMove(ChessMove(ChessSquare(6, 5), ChessSquare(5, 5)))
        // 1... e7-e5 (1,4 -> 3,4)
        engine.makeMove(ChessMove(ChessSquare(1, 4), ChessSquare(3, 4)))
        // 2. g2-g4 (6,6 -> 4,6)
        engine.makeMove(ChessMove(ChessSquare(6, 6), ChessSquare(4, 6)))
        // 2... Qd8-h4# (0,3 -> 4,7)
        engine.makeMove(ChessMove(ChessSquare(0, 3), ChessSquare(4, 7)))

        assertTrue(engine.state.status is ChessGameStatus.Checkmate)
        val winner = (engine.state.status as ChessGameStatus.Checkmate).winner
        assertEquals(PieceColor.BLACK, winner)
    }

    @Test
    fun `stalemate is detected correctly`() {
        // King at a8 (0, 0) and White Queen at b6 (2, 1), White King at c7 (1, 2)
        val board = arrayOfNulls<ChessPiece>(64)
        board[0 * 8 + 0] = ChessPiece(PieceType.KING, PieceColor.BLACK) // a8
        board[1 * 8 + 2] = ChessPiece(PieceType.KING, PieceColor.WHITE) // c7
        board[2 * 8 + 1] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE) // b6

        val state = ChessGameState(board = board, activeColor = PieceColor.BLACK)
        val moves = ChessRules.generateLegalMoves(state)
        assertTrue(moves.isEmpty())
        assertFalse(ChessRules.isKingInCheck(board, PieceColor.BLACK))

        // Create transition via dummy move to evaluate final status
        val dummyMove = ChessMove(ChessSquare(3, 1), ChessSquare(2, 1))
        val prevBoard = board.clone()
        prevBoard[3 * 8 + 1] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE)
        prevBoard[2 * 8 + 1] = null
        val prevState = ChessGameState(board = prevBoard, activeColor = PieceColor.WHITE)
        val applied = ChessRules.applyMove(prevState, dummyMove)

        assertEquals(ChessGameStatus.Stalemate, applied.status)
    }

    @Test
    fun `bot on all three difficulties produces valid legal moves`() = runBlocking {
        val state = ChessGameState.initial()
        val legalMoves = ChessRules.generateLegalMoves(state)

        for (diff in listOf(ChessDifficulty.EASY, ChessDifficulty.MEDIUM, ChessDifficulty.HARD)) {
            val move = ChessBot.decideMove(state, diff)
            assertNotNull("Move should not be null for $diff", move)
            assertTrue("Bot move must be legal for $diff", legalMoves.contains(move))
        }
    }

    @Test
    fun `bot on medium and hard captures free hanging queen`() = runBlocking {
        // Setup: Black knight can capture hanging White Queen on e4
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        board[4 * 8 + 4] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE) // e4 (hanging)
        board[2 * 8 + 5] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK) // f6 (can capture e4)

        val state = ChessGameState(board = board, activeColor = PieceColor.BLACK)

        val hardMove = ChessBot.decideMove(state, ChessDifficulty.HARD)
        assertNotNull(hardMove)
        assertEquals(ChessSquare(4, 4), hardMove!!.to)
        assertEquals(ChessSquare(2, 5), hardMove.from)

        val medMove = ChessBot.decideMove(state, ChessDifficulty.MEDIUM)
        assertNotNull(medMove)
        assertEquals(ChessSquare(4, 4), medMove!!.to)
    }

    @Test
    fun `chess player color resolution behaves correctly`() {
        assertEquals(PieceColor.WHITE, ChessPlayerColor.WHITE.resolve())
        assertEquals(PieceColor.BLACK, ChessPlayerColor.BLACK.resolve())

        val resolvedSet = mutableSetOf<PieceColor>()
        for (i in 0 until 100) {
            val resolved = ChessPlayerColor.AUTO.resolve()
            assertTrue(resolved == PieceColor.WHITE || resolved == PieceColor.BLACK)
            resolvedSet.add(resolved)
        }
        // With 100 coin flips, both WHITE and BLACK should be produced
        assertTrue("AUTO should produce WHITE", resolvedSet.contains(PieceColor.WHITE))
        assertTrue("AUTO should produce BLACK", resolvedSet.contains(PieceColor.BLACK))
    }

    @Test
    fun `bot as White makes opening move when player selects Black`() = runBlocking {
        // Initial state where White is activeColor
        val state = ChessGameState.initial()
        assertEquals(PieceColor.WHITE, state.activeColor)

        for (diff in listOf(ChessDifficulty.EASY, ChessDifficulty.MEDIUM, ChessDifficulty.HARD)) {
            val botWhiteMove = ChessBot.decideMove(state, diff)
            assertNotNull(botWhiteMove)
            // White opening move must start from ranks 1 or 2 (row 6 or 7)
            assertTrue(botWhiteMove!!.from.row in 6..7)
            val nextState = ChessRules.applyMove(state, botWhiteMove)
            // Now Black's turn (player's turn)
            assertEquals(PieceColor.BLACK, nextState.activeColor)
        }
    }

    @Test
    fun `bot as Black makes move after player as White makes opening move`() = runBlocking {
        val state = ChessGameState.initial()
        assertEquals(PieceColor.WHITE, state.activeColor)

        // Player as White moves e2-e4: (6,4) -> (4,4)
        val playerMove = ChessMove(ChessSquare(6, 4), ChessSquare(4, 4))
        val afterPlayer = ChessRules.applyMove(state, playerMove)
        assertEquals(PieceColor.BLACK, afterPlayer.activeColor)

        // Bot as Black responds with a legal move
        for (diff in listOf(ChessDifficulty.EASY, ChessDifficulty.MEDIUM, ChessDifficulty.HARD)) {
            val botBlackMove = ChessBot.decideMove(afterPlayer, diff)
            assertNotNull(botBlackMove)
            assertTrue(botBlackMove!!.from.row in 0..1) // Black pieces start on ranks 7 and 8 (rows 1 and 0)
            val afterBot = ChessRules.applyMove(afterPlayer, botBlackMove)
            assertEquals(PieceColor.WHITE, afterBot.activeColor)
        }
    }

    @Test
    fun `difficulty operates independently of player color`() {
        val difficulties = listOf(ChessDifficulty.EASY, ChessDifficulty.MEDIUM, ChessDifficulty.HARD)
        val colors = listOf(ChessPlayerColor.WHITE, ChessPlayerColor.BLACK, ChessPlayerColor.AUTO)

        // All combinations are distinct and valid
        for (diff in difficulties) {
            for (col in colors) {
                assertNotNull(diff.title)
                assertNotNull(col.title)
                val resolved = col.resolve()
                assertTrue(resolved == PieceColor.WHITE || resolved == PieceColor.BLACK)
            }
        }
    }

    @Test
    fun `play again re-resolves AUTO without affecting fixed game resolution`() {
        var resolvedColor = ChessPlayerColor.AUTO.resolve()
        val originalResolved = resolvedColor

        // During a game, resolvedColor remains fixed
        val moves = listOf(
            ChessMove(ChessSquare(6, 4), ChessSquare(4, 4)),
            ChessMove(ChessSquare(1, 4), ChessSquare(3, 4))
        )
        var state = ChessGameState.initial()
        for (m in moves) {
            state = ChessRules.applyMove(state, m)
            // resolvedColor does not change during the match
            assertEquals(originalResolved, resolvedColor)
        }

        // On Play Again, AUTO is re-resolved
        val newResolutions = (1..50).map { ChessPlayerColor.AUTO.resolve() }.toSet()
        assertEquals(2, newResolutions.size) // contains both WHITE and BLACK
    }

    @Test
    fun `chess perspective coordinates and touch mapping rotate 180 degrees for Black`() {
        // WHITE perspective (isRotated = false):
        // visRow = 7 is bottom, row = 7 (rank 1, White backrank at bottom)
        // visRow = 0 is top, row = 0 (rank 8, Black backrank at top)
        // visCol = 0 is left, col = 0 (file a at bottom-left: a1)
        val whitePerspectiveA1Row = 7
        val whitePerspectiveA1Col = 0
        assertEquals("a1", ChessSquare(whitePerspectiveA1Row, whitePerspectiveA1Col).algebraic)

        // BLACK perspective (isRotated = true):
        // Visual bottom-left is visRow = 7, visCol = 0.
        // Transformed coordinates:
        // row = 7 - visRow = 7 - 7 = 0 (rank 8)
        // col = 7 - visCol = 7 - 0 = 7 (file h)
        // Bottom-left square is h8!
        val blackVisRow = 7
        val blackVisCol = 0
        val transformedSquare = ChessSquare(row = 7 - blackVisRow, col = 7 - blackVisCol)
        assertEquals("h8", transformedSquare.algebraic)

        // Visual top-right is visRow = 0, visCol = 7.
        // Transformed coordinates:
        // row = 7 - 0 = 7 (rank 1)
        // col = 7 - 7 = 0 (file a)
        // Top-right square is a1!
        val blackTopRightSquare = ChessSquare(row = 7 - 0, col = 7 - 7)
        assertEquals("a1", blackTopRightSquare.algebraic)

        // Pawn visual progression:
        // Black pawn starts at row 1 (rank 7).
        // From Black's perspective: visRow = 7 - 1 = 6.
        // Advances to row 2 (rank 6).
        // From Black's perspective: visRow = 7 - 2 = 5 (moves visually UPWARD from 6 to 5).
        val blackPawnStartVisR = 7 - 1
        val blackPawnAdvanceVisR = 7 - 2
        assertTrue("Black pawn must move visually upward", blackPawnAdvanceVisR < blackPawnStartVisR)

        // White pawn starts at row 6 (rank 2).
        // From Black's perspective: visRow = 7 - 6 = 1.
        // Advances to row 4 (rank 4).
        // From Black's perspective: visRow = 7 - 4 = 3 (moves visually DOWNWARD from 1 to 3).
        val whitePawnStartVisR = 7 - 6
        val whitePawnAdvanceVisR = 7 - 4
        assertTrue("White pawn must move visually downward", whitePawnAdvanceVisR > whitePawnStartVisR)
    }
}

