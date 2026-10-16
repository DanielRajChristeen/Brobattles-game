package com.example.ui.screens.detail.chess

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.chess.ChessBoardOrientation
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessEngine
import com.example.core.engine.chess.ChessGameStatus
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessPiece
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.chess.PieceType
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPink
import com.example.ui.theme.PlayfulPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChessGameView(
    selectedMode: GameMode,
    difficulty: ChessDifficulty,
    playerColorPreference: ChessPlayerColor = ChessPlayerColor.AUTO,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val colors = ChillTheme.colors

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.Connected)?.isHost ?: true)
    val chessHostColor by multiplayerManager.chessHostColor.collectAsState()
    val (localColor, opponentColor) = ChessBoardOrientation.resolveNearbyColors(chessHostColor, isHost)

    val engine = remember { ChessEngine() }
    var gameState by remember { mutableStateOf(engine.state) }
    var selectedSquare by remember { mutableStateOf<ChessSquare?>(null) }
    var isBotThinking by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var gameSessionId by remember { mutableStateOf(0) }

    // Resolved color for this specific match (remains fixed during entire match)
    var resolvedPlayerColor by remember(playerColorPreference) { mutableStateOf(playerColorPreference.resolve()) }

    // Promotion dialog state
    var pendingPromotionFromTo by remember { mutableStateOf<Pair<ChessSquare, ChessSquare>?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    fun vibrate() {
        if (settings.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val legalMovesForSelected = remember(gameState, selectedSquare) {
        if (selectedSquare != null) {
            engine.getLegalMovesForSquare(selectedSquare!!)
        } else {
            emptyList()
        }
    }

    // Trigger Bot Move whenever it is the Bot's turn
    LaunchedEffect(gameState.activeColor, gameState.status, resolvedPlayerColor, selectedMode, gameSessionId) {
        val isBotTurn = selectedMode == GameMode.SOLO &&
            gameState.activeColor != resolvedPlayerColor &&
            gameState.status is ChessGameStatus.InProgress

        if (isBotTurn) {
            // First show "BOT'S TURN ♟️" so player can clearly see turn indicator transition
            isBotThinking = false
            delay(350)
            // Then show "BOT IS THINKING... ♟️"
            isBotThinking = true
            delay(500)
            val botMove = com.example.core.engine.chess.ChessBot.decideMove(gameState, difficulty)
            if (botMove != null) {
                val success = engine.makeMove(botMove)
                if (success) {
                    gameState = engine.state
                    vibrate()
                }
            }
            isBotThinking = false
        }
    }

    // Listen for incoming actions in Nearby Mode for Chess
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "CHESS_MOVE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 4) {
                            val from = ChessSquare(parts[0].toInt(), parts[1].toInt())
                            val to = ChessSquare(parts[2].toInt(), parts[3].toInt())
                            val promoStr = parts.getOrNull(4)
                            val promo = if (!promoStr.isNullOrBlank()) {
                                runCatching { PieceType.valueOf(promoStr) }.getOrNull()
                            } else null
                            val move = ChessMove(from, to, promo)
                            if (engine.makeMove(move)) {
                                gameState = engine.state
                                selectedSquare = null
                                vibrate()
                            }
                        }
                    }
                    "CHESS_REMATCH" -> {
                        engine.resetGame()
                        gameState = engine.state
                        selectedSquare = null
                        pendingPromotionFromTo = null
                        matchRecorded = false
                        vibrate()
                    }
                }
            }
        }
    }

    // Record Match to Room DB on game completion
    LaunchedEffect(gameState.status) {
        val status = gameState.status
        if (status.isGameOver && !matchRecorded) {
            matchRecorded = true
            val (winnerName, p1Score, p2Score) = when (status) {
                is ChessGameStatus.Checkmate -> {
                    if (selectedMode == GameMode.SOLO) {
                        if (status.winner == resolvedPlayerColor) Triple("PLAYER_1", 1, 0)
                        else Triple("PLAYER_2", 0, 1)
                    } else if (isNearbyMode) {
                        val hostColor = if (isHost) localColor else opponentColor
                        if (status.winner == hostColor) Triple("PLAYER_1", 1, 0)
                        else Triple("PLAYER_2", 0, 1)
                    } else {
                        if (status.winner == PieceColor.WHITE) Triple("PLAYER_1", 1, 0)
                        else Triple("PLAYER_2", 0, 1)
                    }
                }
                else -> Triple("DRAW", 0, 0)
            }
            viewModel.repository.recordMatch(
                gameId = "chess",
                gameTitle = "Chess",
                mode = selectedMode.name,
                player1Score = p1Score,
                player2Score = p2Score,
                winner = winnerName,
                durationSeconds = 120
            )
        }
    }

    fun handleSquareTap(square: ChessSquare) {
        if (isBotThinking || gameState.status.isGameOver) return
        // In Solo mode, player can only interact when it's their resolved color's turn
        if (selectedMode == GameMode.SOLO && gameState.activeColor != resolvedPlayerColor) return
        // In Nearby mode, player can only interact when it's their assigned color's turn
        if (isNearbyMode && gameState.activeColor != localColor) return

        val activeColor = gameState.activeColor
        val tappedPiece = gameState.pieceAt(square)

        if (selectedSquare == null) {
            // Select piece if it belongs to active player
            if (tappedPiece != null && tappedPiece.color == activeColor) {
                selectedSquare = square
                vibrate()
            }
        } else {
            val from = selectedSquare!!
            if (from == square) {
                // Deselect on second tap
                selectedSquare = null
                return
            }

            // Switch selection to another friendly piece
            if (tappedPiece != null && tappedPiece.color == activeColor) {
                selectedSquare = square
                vibrate()
                return
            }

            // Check if this move is legal
            val matchingMoves = legalMovesForSelected.filter { it.to == square }
            if (matchingMoves.isNotEmpty()) {
                val movingPiece = gameState.pieceAt(from)
                val isPawnPromotion = movingPiece?.type == PieceType.PAWN &&
                    ((movingPiece.color == PieceColor.WHITE && square.row == 0) ||
                     (movingPiece.color == PieceColor.BLACK && square.row == 7))

                if (isPawnPromotion) {
                    pendingPromotionFromTo = from to square
                } else {
                    val move = matchingMoves.first()
                    if (engine.makeMove(move)) {
                        gameState = engine.state
                        selectedSquare = null
                        vibrate()
                        if (isNearbyMode) {
                            multiplayerManager.sendAction(
                                "CHESS_MOVE",
                                "${move.from.row},${move.from.col},${move.to.row},${move.to.col},${move.promotion?.name ?: ""}"
                            )
                        }
                    }
                }
            } else {
                selectedSquare = null
            }
        }
    }

    fun handlePromotionSelected(pieceType: PieceType) {
        val fromTo = pendingPromotionFromTo ?: return
        pendingPromotionFromTo = null

        val promoMove = engine.currentLegalMoves.firstOrNull {
            it.from == fromTo.first && it.to == fromTo.second && it.promotion == pieceType
        } ?: ChessMove(from = fromTo.first, to = fromTo.second, promotion = pieceType)

        if (engine.makeMove(promoMove)) {
            gameState = engine.state
            selectedSquare = null
            vibrate()
            if (isNearbyMode) {
                multiplayerManager.sendAction(
                    "CHESS_MOVE",
                    "${promoMove.from.row},${promoMove.from.col},${promoMove.to.row},${promoMove.to.col},${promoMove.promotion?.name ?: ""}"
                )
            }
        }
    }

    fun restartGame() {
        // AUTO is re-randomized for every new game / play again
        resolvedPlayerColor = playerColorPreference.resolve()
        engine.resetGame()
        gameState = engine.state
        selectedSquare = null
        isBotThinking = false
        matchRecorded = false
        gameSessionId++
        vibrate()
        if (isNearbyMode) {
            multiplayerManager.sendAction("CHESS_REMATCH", "")
        }
    }

    // Determine card titles & thinking states based on resolvedPlayerColor
    val blackPlayerName: String
    val blackIsThinking: Boolean
    val whitePlayerName: String
    val whiteIsThinking: Boolean

    if (isNearbyMode) {
        if (localColor == PieceColor.WHITE) {
            whitePlayerName = "YOU ⚪"
            whiteIsThinking = false
            blackPlayerName = "OPPONENT ⚫"
            blackIsThinking = false
        } else {
            whitePlayerName = "OPPONENT ⚪"
            whiteIsThinking = false
            blackPlayerName = "YOU ⚫"
            blackIsThinking = false
        }
    } else if (selectedMode == GameMode.SOLO) {
        if (resolvedPlayerColor == PieceColor.BLACK) {
            blackPlayerName = "YOU 👤 ⚫"
            blackIsThinking = false
            whitePlayerName = "BOT 🤖 ⚪ (${difficulty.title})"
            whiteIsThinking = isBotThinking
        } else {
            blackPlayerName = "BOT 🤖 ⚫ (${difficulty.title})"
            blackIsThinking = isBotThinking
            whitePlayerName = "YOU 👤 ⚪"
            whiteIsThinking = false
        }
    } else {
        blackPlayerName = "PLAYER 2 ⚫"
        blackIsThinking = false
        whitePlayerName = "PLAYER 1 ⚪"
        whiteIsThinking = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, colors.border, CircleShape)
                        .bouncyClickable(onClick = onChangeMode)
                        .testTag("chess_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Mode Title Pill
                PlayfulPill(
                    text = when (selectedMode) {
                        GameMode.SOLO -> "🤖 VS BOT (${difficulty.title})"
                        GameMode.LOCAL_1V1 -> "👥 2 PLAYERS"
                        GameMode.BLUETOOTH_1V1 -> "📱 NEARBY"
                    },
                    backgroundColor = colors.accent.copy(alpha = 0.14f),
                    textColor = colors.accent
                )

                // Actions: Help & Restart
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, colors.border, CircleShape)
                            .bouncyClickable { showHelpDialog = true }
                            .testTag("chess_help_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, colors.border, CircleShape)
                            .bouncyClickable(onClick = { restartGame() })
                            .testTag("chess_restart_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isRotated = ChessBoardOrientation.isBoardRotated(
                selectedMode = selectedMode,
                activeColor = gameState.activeColor,
                localColor = localColor,
                humanPlayerColor = resolvedPlayerColor
            )

            // 2. Top Player HUD (Opponent)
            val topColor = if (isRotated) PieceColor.WHITE else PieceColor.BLACK
            val topName = if (isRotated) whitePlayerName else blackPlayerName
            val topIsTurn = gameState.activeColor == topColor
            val topIsThinking = if (isRotated) whiteIsThinking else blackIsThinking
            val topCaptured = if (isRotated) gameState.capturedByWhite else gameState.capturedByBlack

            PlayerInfoCard(
                playerName = topName,
                playerColor = topColor,
                isTurn = topIsTurn,
                isThinking = topIsThinking,
                capturedPieces = topCaptured,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Central Physical Chess Board
            ChessBoardView(
                state = gameState,
                selectedSquare = selectedSquare,
                legalMovesForSelected = legalMovesForSelected,
                onSquareTapped = { handleSquareTap(it) },
                isRotated = isRotated
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Bottom Player HUD (Active Player)
            val bottomColor = if (isRotated) PieceColor.BLACK else PieceColor.WHITE
            val bottomName = if (isRotated) blackPlayerName else whitePlayerName
            val bottomIsTurn = gameState.activeColor == bottomColor
            val bottomIsThinking = if (isRotated) blackIsThinking else whiteIsThinking
            val bottomCaptured = if (isRotated) gameState.capturedByBlack else gameState.capturedByWhite

            PlayerInfoCard(
                playerName = bottomName,
                playerColor = bottomColor,
                isTurn = bottomIsTurn,
                isThinking = bottomIsThinking,
                capturedPieces = bottomCaptured,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Bottom Status Strip
            when {
                gameState.inCheck && !gameState.status.isGameOver -> {
                    PlayfulPill(
                        text = "CHECK! ⚠️",
                        backgroundColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                        textColor = Color(0xFFEF4444)
                    )
                }
                isBotThinking -> {
                    PlayfulPill(
                        text = "BOT IS THINKING... ♟️",
                        backgroundColor = PlayfulPurple.copy(alpha = 0.12f),
                        textColor = PlayfulPurple
                    )
                }
                selectedMode == GameMode.SOLO -> {
                    if (gameState.activeColor == resolvedPlayerColor) {
                        PlayfulPill(
                            text = "YOUR TURN ♟️",
                            backgroundColor = PlayfulGreen.copy(alpha = 0.12f),
                            textColor = PlayfulGreen
                        )
                    } else {
                        PlayfulPill(
                            text = "BOT'S TURN ♟️",
                            backgroundColor = PlayfulOrange.copy(alpha = 0.12f),
                            textColor = PlayfulOrange
                        )
                    }
                }
                isNearbyMode -> {
                    if (gameState.activeColor == localColor) {
                        PlayfulPill(
                            text = "YOUR TURN ♟️",
                            backgroundColor = PlayfulGreen.copy(alpha = 0.12f),
                            textColor = PlayfulGreen
                        )
                    } else {
                        PlayfulPill(
                            text = "OPPONENT'S TURN ♟️",
                            backgroundColor = PlayfulOrange.copy(alpha = 0.12f),
                            textColor = PlayfulOrange
                        )
                    }
                }
                gameState.activeColor == PieceColor.WHITE -> {
                    PlayfulPill(
                        text = "WHITE'S TURN ♟️",
                        backgroundColor = PlayfulGreen.copy(alpha = 0.12f),
                        textColor = PlayfulGreen
                    )
                }
                else -> {
                    PlayfulPill(
                        text = "BLACK'S TURN ♟️",
                        backgroundColor = PlayfulOrange.copy(alpha = 0.12f),
                        textColor = PlayfulOrange
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Pawn Promotion Modal Dialog
        if (pendingPromotionFromTo != null) {
            AlertDialog(
                onDismissRequest = { pendingPromotionFromTo = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Pawn Promotion 👑",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val promoPieces = listOf(
                            PieceType.QUEEN to "♕ Queen",
                            PieceType.ROOK to "♖ Rook",
                            PieceType.BISHOP to "♗ Bishop",
                            PieceType.KNIGHT to "♘ Knight"
                        )
                        for ((type, label) in promoPieces) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceVariant)
                                    .border(1.5.dp, colors.border, RoundedCornerShape(14.dp))
                                    .bouncyClickable { handlePromotionSelected(type) }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = ChessPiece(type, gameState.activeColor).unicodeSymbol,
                                    fontSize = 32.sp
                                )
                                Text(
                                    text = label.substringAfter(" "),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Game Over Dialog
        if (gameState.status.isGameOver) {
            val status = gameState.status
            val isCheckmate = status is ChessGameStatus.Checkmate
            val didPlayerWin = isCheckmate && (status as ChessGameStatus.Checkmate).winner == resolvedPlayerColor

            val titleText = when {
                isCheckmate && selectedMode == GameMode.SOLO && didPlayerWin -> "🏆 YOU WIN!"
                isCheckmate && selectedMode == GameMode.SOLO -> "♟️ BOT WINS!"
                isCheckmate && isNearbyMode && (status as ChessGameStatus.Checkmate).winner == localColor -> "🏆 YOU WIN!"
                isCheckmate && isNearbyMode -> "♟️ OPPONENT WINS!"
                isCheckmate -> "🏆 ${if ((status as ChessGameStatus.Checkmate).winner == PieceColor.WHITE) "PLAYER 1" else "PLAYER 2"} WINS!"
                status is ChessGameStatus.Stalemate -> "🤝 STALEMATE"
                status is ChessGameStatus.DrawByFiftyMoves -> "🤝 50-MOVE DRAW"
                else -> "🤝 DRAW (Insufficient Material)"
            }

            val subtitleText = when {
                isCheckmate -> "Checkmate! Fantastic game."
                status is ChessGameStatus.Stalemate -> "No legal moves left. The match is drawn."
                else -> "Neither side has mating material. Peace on the board."
            }

            AlertDialog(
                onDismissRequest = {},
                shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                icon = {
                    Text(
                        text = if (isCheckmate) "🏆" else "🤝",
                        fontSize = 44.sp
                    )
                },
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    ChunkyButton(
                        text = "PLAY AGAIN 🔄",
                        onClick = { restartGame() },
                        backgroundColor = colors.accent,
                        modifier = Modifier.fillMaxWidth().testTag("chess_play_again_button")
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().testTag("chess_exit_button")
                    ) {
                        Text("Home 🏠", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Help Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "How to Play Chess ♟️",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "• Tap a piece to highlight all valid moves.\n" +
                                   "• Tap a highlighted circle to move, or corner brackets to capture.\n" +
                                   "• Castling: Tap King, then destination (g1/c1 for White).\n" +
                                   "• En Passant & Pawn Promotion are fully supported.\n" +
                                   "• Win by delivering checkmate to the opponent's king!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bot Difficulties:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent
                        )
                        Text(
                            text = "• EASY: Beginner-friendly with casual moves.\n" +
                                   "• MEDIUM: Tactical, balanced & punishes hanging pieces.\n" +
                                   "• HARD: Deep alpha-beta search with quiescence captures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Got it! ♟️",
                        onClick = { showHelpDialog = false },
                        backgroundColor = colors.accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
    }
}

@Composable
private fun PlayerInfoCard(
    playerName: String,
    playerColor: PieceColor,
    isTurn: Boolean,
    isThinking: Boolean,
    capturedPieces: List<PieceType>,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    val isWhite = playerColor == PieceColor.WHITE

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isTurn) colors.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(
                width = if (isTurn) 2.dp else 1.5.dp,
                color = if (isTurn) colors.accent else colors.border,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Piece Color Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isWhite) Color.White else Color(0xFF0F172A))
                        .border(1.5.dp, colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isWhite) "♔" else "♚",
                        fontSize = 18.sp,
                        color = if (isWhite) Color(0xFF0F172A) else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = playerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Captured Pieces String
                    if (capturedPieces.isNotEmpty()) {
                        val capString = capturedPieces.joinToString(" ") { type ->
                            when (type) {
                                PieceType.PAWN -> "♙"
                                PieceType.KNIGHT -> "♘"
                                PieceType.BISHOP -> "♗"
                                PieceType.ROOK -> "♖"
                                PieceType.QUEEN -> "♕"
                                PieceType.KING -> ""
                            }
                        }
                        Text(
                            text = capString,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Turn Badge
            if (isTurn) {
                PlayfulPill(
                    text = if (isThinking) "THINKING... ♟️" else "TURN",
                    backgroundColor = if (isThinking) PlayfulPurple.copy(alpha = 0.15f) else PlayfulGreen.copy(alpha = 0.15f),
                    textColor = if (isThinking) PlayfulPurple else PlayfulGreen
                )
            }
        }
    }
}
