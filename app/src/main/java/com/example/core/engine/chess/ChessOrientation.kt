package com.example.core.engine.chess

import com.example.core.registry.GameMode

/**
 * Helper object providing authoritative rules and coordinate mappings for
 * chessboard orientation and multiplayer color assignments.
 */
object ChessBoardOrientation {

    /**
     * Determines whether the chessboard should be rendered rotated (180 degrees, Black at bottom).
     *
     * - Same-Device 2-Player mode (LOCAL_1V1):
     *   The orientation automatically follows the active player's turn.
     *   When White's turn is active -> White at bottom (isRotated = false).
     *   When Black's turn is active -> Black at bottom (isRotated = true).
     *   The board flips on each turn change between White and Black.
     *
     * - Nearby Multiplayer (BLUETOOTH_1V1):
     *   Each device orients the board according to its local player's assigned color.
     *   Local player is White -> White at bottom (isRotated = false).
     *   Local player is Black -> Black at bottom (isRotated = true).
     *   Remote opponent's pieces appear at the top on both phones.
     *
     * - VS Bot (SOLO):
     *   Board is oriented to the human player's assigned color.
     */
    fun isBoardRotated(
        selectedMode: GameMode,
        activeColor: PieceColor,
        localColor: PieceColor,
        humanPlayerColor: PieceColor = PieceColor.WHITE
    ): Boolean {
        return when (selectedMode) {
            GameMode.LOCAL_1V1 -> activeColor == PieceColor.BLACK
            GameMode.BLUETOOTH_1V1 -> localColor == PieceColor.BLACK
            GameMode.SOLO -> humanPlayerColor == PieceColor.BLACK
        }
    }

    /**
     * Resolves the assigned colors for both host and joining peer in Nearby multiplayer.
     * Host chooses either "WHITE" or "BLACK".
     *
     * If host chooses WHITE:
     * - Host plays White (localColor = WHITE, opponentColor = BLACK).
     * - Joiner plays Black (localColor = BLACK, opponentColor = WHITE).
     *
     * If host chooses BLACK:
     * - Host plays Black (localColor = BLACK, opponentColor = WHITE).
     * - Joiner plays White (localColor = WHITE, opponentColor = BLACK).
     */
    fun resolveNearbyColors(hostColorPreference: String, isHost: Boolean): Pair<PieceColor, PieceColor> {
        val hostColor = if (hostColorPreference.equals("BLACK", ignoreCase = true)) PieceColor.BLACK else PieceColor.WHITE
        val joinerColor = if (hostColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        val localColor = if (isHost) hostColor else joinerColor
        val opponentColor = if (isHost) joinerColor else hostColor
        return Pair(localColor, opponentColor)
    }

    /**
     * Converts visual display coordinates (0..7) on screen to underlying board model coordinates (0..7).
     * In standard orientation (isRotated = false), visual rank 0..7 matches board row 0..7.
     * In rotated orientation (isRotated = true), visual rank 0..7 maps to board row (7 - visRow).
     */
    fun visualToBoardSquare(visRow: Int, visCol: Int, isRotated: Boolean): ChessSquare {
        val row = if (isRotated) 7 - visRow.coerceIn(0, 7) else visRow.coerceIn(0, 7)
        val col = if (isRotated) 7 - visCol.coerceIn(0, 7) else visCol.coerceIn(0, 7)
        return ChessSquare(row, col)
    }

    /**
     * Converts underlying board model coordinates (0..7) to visual display coordinates (0..7).
     */
    fun boardToVisualCoordinates(square: ChessSquare, isRotated: Boolean): Pair<Int, Int> {
        val visRow = if (isRotated) 7 - square.row else square.row
        val visCol = if (isRotated) 7 - square.col else square.col
        return Pair(visRow, visCol)
    }
}
