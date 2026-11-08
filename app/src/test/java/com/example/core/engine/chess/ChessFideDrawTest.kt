package com.example.core.engine.chess

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessFideDrawTest {

    /**
     * Helper to create a move from algebraic coordinates (e.g. "g1", "f3").
     */
    private fun move(from: String, to: String): ChessMove {
        val colMap = mapOf('a' to 0, 'b' to 1, 'c' to 2, 'd' to 3, 'e' to 4, 'f' to 5, 'g' to 6, 'h' to 7)
        val fromSquare = ChessSquare(8 - (from[1] - '0'), colMap[from[0]]!!)
        val toSquare = ChessSquare(8 - (to[1] - '0'), colMap[to[0]]!!)
        return ChessMove(fromSquare, toSquare)
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Knight cycles (Threefold repetition claimable)
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 1 - knight cycles threefold repetition is claimable and terminates game`() {
        val engine = ChessEngine()
        assertEquals(1, engine.state.currentPositionRepetitions)
        assertFalse(engine.state.isThreefoldRepetitionClaimable)

        // 1. Nf3 Nf6
        assertTrue(engine.makeMove(move("g1", "f3")))
        assertTrue(engine.makeMove(move("g8", "f6")))
        // 2. Ng1 Ng8 (Back to initial position for 2nd time)
        assertTrue(engine.makeMove(move("f3", "g1")))
        assertTrue(engine.makeMove(move("f6", "g8")))
        assertEquals(2, engine.state.currentPositionRepetitions)
        assertFalse(engine.state.isThreefoldRepetitionClaimable)

        // 3. Nf3 Nf6
        assertTrue(engine.makeMove(move("g1", "f3")))
        assertTrue(engine.makeMove(move("g8", "f6")))
        // 4. Ng1 Ng8 (Back to initial position for 3rd time!)
        assertTrue(engine.makeMove(move("f3", "g1")))
        assertTrue(engine.makeMove(move("f6", "g8")))

        assertEquals(3, engine.state.currentPositionRepetitions)
        assertTrue(engine.state.isThreefoldRepetitionClaimable)
        assertTrue(engine.state.isDrawClaimable)
        assertFalse("Game should still be InProgress until claimed", engine.state.status.isGameOver)

        // Human claims draw
        val claimed = engine.claimThreefoldRepetitionDraw()
        assertTrue("Claiming threefold repetition draw should succeed", claimed)
        assertEquals(ChessGameStatus.DrawByThreefoldRepetition, engine.state.status)
        assertTrue(engine.state.status.isGameOver)
        assertTrue(engine.state.status.isDraw)

        // No moves allowed once game is over
        val moveAfter = engine.makeMove(move("e2", "e4"))
        assertFalse("Further moves must be rejected after draw claim", moveAfter)
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Fivefold repetition (Automatic termination)
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 2 - fivefold repetition terminates automatically without claim`() {
        val engine = ChessEngine()

        // Loop knight cycles 4 times (yielding 5 occurrences total of the initial position)
        for (cycle in 1..4) {
            assertTrue(engine.makeMove(move("g1", "f3")))
            assertTrue(engine.makeMove(move("g8", "f6")))
            assertTrue(engine.makeMove(move("f3", "g1")))
            assertTrue(engine.makeMove(move("f6", "g8")))
        }

        // On the 5th occurrence of the exact position, game must automatically end
        assertEquals(5, engine.state.currentPositionRepetitions)
        assertEquals(ChessGameStatus.DrawByFivefoldRepetition, engine.state.status)
        assertTrue(engine.state.status.isGameOver)
        assertTrue(engine.state.status.isDraw)

        // Attempting to move fails
        assertFalse(engine.makeMove(move("e2", "e4")))
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Rook cycles
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 3 - rook cycles repetition in endgame position`() {
        val board = arrayOfNulls<ChessPiece>(64)
        // White King on e1, Rook on a1; Black King on e8, Rook on a8
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[7 * 8 + 0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE) // a1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        board[0 * 8 + 0] = ChessPiece(PieceType.ROOK, PieceColor.BLACK) // a8

        // Castling rights already lost
        val startState = ChessGameState(
            board = board,
            activeColor = PieceColor.WHITE,
            whiteCanCastleKingside = false,
            whiteCanCastleQueenside = false,
            blackCanCastleKingside = false,
            blackCanCastleQueenside = false
        )
        val engine = ChessEngine(startState)
        assertEquals(1, engine.state.currentPositionRepetitions)

        // Move Rooks: Ra1-b1, Ra8-b8, Rb1-a1, Rb8-a8 (Occurrence 2)
        assertTrue(engine.makeMove(move("a1", "b1")))
        assertTrue(engine.makeMove(move("a8", "b8")))
        assertTrue(engine.makeMove(move("b1", "a1")))
        assertTrue(engine.makeMove(move("b8", "a8")))
        assertEquals(2, engine.state.currentPositionRepetitions)

        // Move Rooks again (Occurrence 3)
        assertTrue(engine.makeMove(move("a1", "b1")))
        assertTrue(engine.makeMove(move("a8", "b8")))
        assertTrue(engine.makeMove(move("b1", "a1")))
        assertTrue(engine.makeMove(move("b8", "a8")))
        assertEquals(3, engine.state.currentPositionRepetitions)
        assertTrue(engine.state.isThreefoldRepetitionClaimable)

        assertTrue(engine.claimThreefoldRepetitionDraw())
        assertEquals(ChessGameStatus.DrawByThreefoldRepetition, engine.state.status)
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Castling rights loss differentiating positions
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 4 - castling right differences prevent false repetition counts`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move("e2", "e4")))
        assertTrue(engine.makeMove(move("e7", "e5")))
        val keyAfterE4E5 = engine.state.canonicalPositionKey
        assertTrue("Key should contain castling rights KQkq", keyAfterE4E5.contains("KQkq"))

        // White moves King: e1 -> e2 (Castling rights for White lost!)
        assertTrue(engine.makeMove(move("e1", "e2")))
        assertFalse(engine.state.whiteCanCastleKingside)
        assertFalse(engine.state.whiteCanCastleQueenside)

        // Black moves King: e8 -> e7 (Castling rights for Black lost!)
        assertTrue(engine.makeMove(move("e8", "e7")))
        assertFalse(engine.state.blackCanCastleKingside)
        assertFalse(engine.state.blackCanCastleQueenside)

        // White moves King back: e2 -> e1
        assertTrue(engine.makeMove(move("e2", "e1")))

        // Black moves King back: e7 -> e8
        assertTrue(engine.makeMove(move("e7", "e8")))

        // Pieces are in identical board positions as after 1. e4 e5,
        // but castling rights are gone!
        val currentKey = engine.state.canonicalPositionKey
        assertNotEquals("Position must not match earlier position due to lost castling rights", keyAfterE4E5, currentKey)
        val castlingPart = currentKey.split(" ")[2]
        assertEquals("Castling rights must be '-' since all castling rights were lost", "-", castlingPart)
    }

    // -------------------------------------------------------------------------
    // Scenario 5: En passant availability difference (legal vs no legal ep pawn)
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 5 - en passant is only part of canonical key when capture is legal`() {
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        board[3 * 8 + 4] = ChessPiece(PieceType.PAWN, PieceColor.WHITE) // e5 (row 3, col 4)
        board[1 * 8 + 3] = ChessPiece(PieceType.PAWN, PieceColor.BLACK) // d7 (row 1, col 3)

        val state = ChessGameState(board = board, activeColor = PieceColor.BLACK)
        // Black plays d7 -> d5
        val next = ChessRules.applyMove(state, move("d7", "d5"))
        // White pawn on e5 CAN legally capture en passant on d6!
        assertEquals("d6", next.enPassantTarget?.algebraic)
        assertTrue("Key must include d6 when White can legally capture en passant", next.canonicalPositionKey.endsWith("d6"))

        // Now contrast with case where Black plays a7 -> a5, but White has NO pawn on b5
        val board2 = arrayOfNulls<ChessPiece>(64)
        board2[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
        board2[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
        board2[1 * 8 + 0] = ChessPiece(PieceType.PAWN, PieceColor.BLACK) // a7

        val state2 = ChessGameState(board = board2, activeColor = PieceColor.BLACK)
        val next2 = ChessRules.applyMove(state2, move("a7", "a5"))
        // enPassantTarget on board is a6, but under FIDE rules, NO opposing pawn can capture it!
        assertTrue("Effective en passant should be '-' because no white pawn can capture on a6", next2.canonicalPositionKey.endsWith("-"))
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Move undo restoring exact repetition count
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 6 - move undo correctly restores prior repetition count`() {
        val engine = ChessEngine()
        assertEquals(1, engine.state.currentPositionRepetitions)

        // 1. Nf3 Nf6 2. Ng1 Ng8 (Repetition 2)
        engine.makeMove(move("g1", "f3"))
        engine.makeMove(move("g8", "f6"))
        engine.makeMove(move("f3", "g1"))
        engine.makeMove(move("f6", "g8"))
        assertEquals(2, engine.state.currentPositionRepetitions)

        // 3. Nf3 Nf6 4. Ng1 Ng8 (Repetition 3)
        engine.makeMove(move("g1", "f3"))
        engine.makeMove(move("g8", "f6"))
        engine.makeMove(move("f3", "g1"))
        engine.makeMove(move("f6", "g8"))
        assertEquals(3, engine.state.currentPositionRepetitions)
        assertTrue(engine.state.isThreefoldRepetitionClaimable)

        // Undo Ng8
        assertTrue(engine.canUndo())
        engine.undoMove()
        // Now it's Black to move, Black knight is on f6
        assertFalse(engine.state.isThreefoldRepetitionClaimable)

        // Undo Ng1, Nf6, Nf3 (Back to Repetition 2)
        engine.undoMove()
        engine.undoMove()
        engine.undoMove()
        assertEquals(2, engine.state.currentPositionRepetitions)
        assertFalse(engine.state.isThreefoldRepetitionClaimable)

        // Make moves again to reach Repetition 3
        engine.makeMove(move("g1", "f3"))
        engine.makeMove(move("g8", "f6"))
        engine.makeMove(move("f3", "g1"))
        engine.makeMove(move("f6", "g8"))
        assertEquals(3, engine.state.currentPositionRepetitions)
        assertTrue(engine.state.isThreefoldRepetitionClaimable)
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Restart resetting history and repetition counts
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 7 - resetGame clears all history and resets repetitions to 1`() {
        val engine = ChessEngine()
        engine.makeMove(move("g1", "f3"))
        engine.makeMove(move("g8", "f6"))
        engine.makeMove(move("f3", "g1"))
        engine.makeMove(move("f6", "g8"))
        assertEquals(2, engine.state.currentPositionRepetitions)

        engine.resetGame()
        assertEquals(1, engine.state.currentPositionRepetitions)
        assertEquals(1, engine.state.positionHistory.size)
        assertEquals(ChessGameStatus.InProgress, engine.state.status)
        assertFalse(engine.state.isThreefoldRepetitionClaimable)
        assertFalse(engine.canUndo())
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Bot avoids repetitive movement when winning or equal
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 8 - bot avoids repeating position when alternatives exist`() = runBlocking {
        // Setup board where White has extra Rook: White is winning
        val board = arrayOfNulls<ChessPiece>(64)
        board[7 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e1
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        board[7 * 8 + 0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE) // a1
        board[7 * 8 + 1] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE) // b1

        val state = ChessGameState(
            board = board,
            activeColor = PieceColor.WHITE,
            whiteCanCastleKingside = false,
            whiteCanCastleQueenside = false,
            blackCanCastleKingside = false,
            blackCanCastleQueenside = false
        )
        val initialKey = state.canonicalPositionKey
        // History already contains initialKey twice
        val stateWithHistory = state.copy(positionHistory = listOf(initialKey, initialKey))

        // White Knight on b1 can move to c3, d2, a3, etc.
        // If White plays Nc3, then later moves back to b1, that would create 3rd repetition.
        // Bot search must penalize moves that lead to repetition when winning!
        val botMove = ChessBot.decideMove(stateWithHistory, ChessDifficulty.HARD)
        assertNotNull(botMove)
        val next = ChessRules.applyMove(stateWithHistory, botMove!!)
        assertFalse("Winning bot should not accept an immediate threefold draw", next.currentPositionRepetitions >= 3)
    }

    // -------------------------------------------------------------------------
    // Scenario 9: Multiplayer draw claim synchronization
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 9 - multiplayer draw claim synchronization`() {
        val hostEngine = ChessEngine()
        val clientEngine = ChessEngine()

        // Both play 3-fold repetition
        val moves = listOf(
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8")
        )
        for (m in moves) {
            hostEngine.makeMove(m)
            clientEngine.makeMove(m)
        }

        assertTrue(hostEngine.state.isThreefoldRepetitionClaimable)
        assertTrue(clientEngine.state.isThreefoldRepetitionClaimable)

        // Host claims draw
        val claimed = hostEngine.claimThreefoldRepetitionDraw()
        assertTrue(claimed)
        assertEquals(ChessGameStatus.DrawByThreefoldRepetition, hostEngine.state.status)

        // Remote client receives "CHESS_CLAIM_DRAW" action and claims draw
        val clientClaimed = clientEngine.claimThreefoldRepetitionDraw()
        assertTrue(clientClaimed)
        assertEquals(ChessGameStatus.DrawByThreefoldRepetition, clientEngine.state.status)

        // Both players have identical game over draw status
        assertEquals(hostEngine.state.status, clientEngine.state.status)
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Checkmate takes precedence over 75-move draw and 5-fold repetition
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 10 - checkmate takes precedence over 75-move draw on move 150`() {
        val board = arrayOfNulls<ChessPiece>(64)
        board[2 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.WHITE) // e6 protects e7 and covers d7/f7
        board[0 * 8 + 4] = ChessPiece(PieceType.KING, PieceColor.BLACK) // e8
        board[1 * 8 + 0] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE) // a7

        // halfmoveClock is 149 (next move makes it 150)
        val state = ChessGameState(
            board = board,
            activeColor = PieceColor.WHITE,
            halfmoveClock = 149
        )

        // White plays Queen a7 -> e7 checkmate!
        val checkmateMove = move("a7", "e7")
        val next = ChessRules.applyMove(state, checkmateMove)

        assertEquals("Checkmate must take precedence over 75-move rule", ChessGameStatus.Checkmate(winner = PieceColor.WHITE), next.status)
        assertTrue(next.status.isGameOver)
    }
}
