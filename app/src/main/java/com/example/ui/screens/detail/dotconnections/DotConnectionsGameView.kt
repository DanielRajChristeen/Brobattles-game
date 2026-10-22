package com.example.ui.screens.detail.dotconnections

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotConnectionsBot
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotDifficulty
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotMoveResult
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.PlayfulYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DotConnectionsGameView(
    selectedMode: GameMode,
    difficulty: DotDifficulty = DotDifficulty.MEDIUM,
    boardSize: DotBoardSize = DotBoardSize.MEDIUM,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val colors = ChillTheme.colors

    val engine = remember(boardSize) { DotConnectionsEngine(boardSize) }
    var gameState by remember(boardSize) { mutableStateOf(engine.state) }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayer = if (isHost) DotPlayer.PLAYER_1 else DotPlayer.PLAYER_2

    var isBotThinking by remember { mutableStateOf(false) }
    var showExtraTurnBanner by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var gameSessionId by remember { mutableIntStateOf(0) }

    fun vibrate(isBig: Boolean = false) {
        if (settings.vibrationEnabled) {
            val effect = if (isBig) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
            view.performHapticFeedback(effect)
        }
    }

    fun restartGame() {
        engine.resetGame(boardSize)
        gameState = engine.state
        isBotThinking = false
        showExtraTurnBanner = false
        matchRecorded = false
        gameSessionId++
        vibrate()
    }

    fun handleUserMove(edge: DotEdge, sendNetwork: Boolean = true) {
        if (isBotThinking || gameState.isGameOver) return
        if (selectedMode == GameMode.SOLO && gameState.activePlayer != DotPlayer.PLAYER_1) return
        if (isNearbyMode && gameState.activePlayer != localPlayer) return

        val result = engine.makeMove(edge, gameState.activePlayer)
        if (result is DotMoveResult.Success) {
            gameState = result.newState
            if (result.extraTurn) {
                showExtraTurnBanner = true
                vibrate(isBig = true)
            } else {
                showExtraTurnBanner = false
                vibrate(isBig = false)
            }
            if (sendNetwork && isNearbyMode) {
                multiplayerManager.sendAction("DOT_EDGE", "${edge.orientation.name},${edge.row},${edge.col}")
            }
        }
    }

    // Listen for incoming actions in Nearby Mode for Dot Connections
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "DOT_EDGE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size == 3) {
                            val ori = runCatching { com.example.core.engine.dotconnections.EdgeOrientation.valueOf(parts[0]) }.getOrNull() ?: return@collect
                            val r = parts[1].toIntOrNull() ?: return@collect
                            val c = parts[2].toIntOrNull() ?: return@collect
                            handleUserMove(DotEdge(ori, r, c), sendNetwork = false)
                        }
                    }
                    "DOT_REMATCH" -> {
                        engine.resetGame(boardSize)
                        gameState = engine.state
                        isBotThinking = false
                        showExtraTurnBanner = false
                        matchRecorded = false
                        gameSessionId++
                        vibrate()
                    }
                }
            }
        }
    }

    // Autonomous Bot in VS BOT mode: continues playing consecutively as long as it has turns
    LaunchedEffect(gameState.activePlayer, gameState.isGameOver, selectedMode, gameSessionId) {
        if (selectedMode != GameMode.SOLO) return@LaunchedEffect

        while (gameState.activePlayer == DotPlayer.PLAYER_2 && !gameState.isGameOver) {
            isBotThinking = true
            // Natural thinking delay (420ms)
            delay(420)
            val botMove = DotConnectionsBot.decideMove(gameState, difficulty)
            if (botMove != null) {
                val result = engine.makeMove(botMove, DotPlayer.PLAYER_2)
                if (result is DotMoveResult.Success) {
                    gameState = result.newState
                    if (result.extraTurn) {
                        showExtraTurnBanner = true
                        vibrate(isBig = true)
                        // Brief pause after completing a box so player can see the square animation
                        // before the bot makes its next consecutive extra-turn move!
                        delay(400)
                    } else {
                        showExtraTurnBanner = false
                        vibrate(isBig = false)
                    }
                } else {
                    break
                }
            } else {
                break
            }
        }
        isBotThinking = false
    }

    // Record Match to Room DB on game completion
    LaunchedEffect(gameState.isGameOver) {
        if (gameState.isGameOver && !matchRecorded) {
            matchRecorded = true
            val (winnerName, p1Score, p2Score) = when {
                gameState.winner == DotPlayer.PLAYER_1 -> Triple("PLAYER_1", gameState.p1Score, gameState.p2Score)
                gameState.winner == DotPlayer.PLAYER_2 -> Triple("PLAYER_2", gameState.p1Score, gameState.p2Score)
                else -> Triple("DRAW", gameState.p1Score, gameState.p2Score)
            }

            viewModel.repository.recordMatch(
                gameId = "dot_connections",
                gameTitle = "Dot Connections",
                mode = selectedMode.name,
                player1Score = p1Score,
                player2Score = p2Score,
                winner = winnerName,
                durationSeconds = 90
            )
        }
    }

    BackHandler {
        onExit()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Controls Bar: Back, Title / Size, Help, Restart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, colors.border, CircleShape)
                    .bouncyClickable(onClick = onExit)
                    .testTag("dot_connections_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit to Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DOT CONNECTIONS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${boardSize.dots}×${boardSize.dots} GRID • ${boardSize.squaresCount} BOXES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, colors.border, CircleShape)
                        .bouncyClickable { showHelpDialog = true }
                        .testTag("dot_connections_help_button"),
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
                        .bouncyClickable { restartGame() }
                        .testTag("dot_connections_restart_button"),
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

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Compact Score HUD
        val p1Name = when {
            selectedMode == GameMode.SOLO -> "YOU"
            isNearbyMode -> if (localPlayer == DotPlayer.PLAYER_1) "YOU" else "OPPONENT"
            else -> "PLAYER 1"
        }
        val p2Name = when {
            selectedMode == GameMode.SOLO -> "BOT (${difficulty.title})"
            isNearbyMode -> if (localPlayer == DotPlayer.PLAYER_2) "YOU" else "OPPONENT"
            else -> "PLAYER 2"
        }
        val isP1Turn = gameState.activePlayer == DotPlayer.PLAYER_1 && !gameState.isGameOver
        val isP2Turn = gameState.activePlayer == DotPlayer.PLAYER_2 && !gameState.isGameOver

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player 1 Card
            ScoreCard(
                name = p1Name,
                score = gameState.p1Score,
                color = PlayfulOrange,
                isTurn = isP1Turn,
                modifier = Modifier.weight(1f)
            )

            // VS Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Player 2 Card
            ScoreCard(
                name = p2Name,
                score = gameState.p2Score,
                color = PlayfulPurple,
                isTurn = isP2Turn,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Status Pill
        when {
            gameState.isGameOver -> {
                val winnerText = when {
                    gameState.winner == DotPlayer.PLAYER_1 -> if (selectedMode == GameMode.SOLO) "YOU WON! 🏆" else "PLAYER 1 WON! 🏆"
                    gameState.winner == DotPlayer.PLAYER_2 -> if (selectedMode == GameMode.SOLO) "BOT WON! 🤖" else "PLAYER 2 WON! 🏆"
                    else -> "IT'S A DRAW! 🤝"
                }
                PlayfulPill(
                    text = winnerText,
                    backgroundColor = PlayfulYellow.copy(alpha = 0.22f),
                    textColor = Color(0xFFD97706)
                )
            }
            showExtraTurnBanner && gameState.activePlayer == DotPlayer.PLAYER_2 -> {
                PlayfulPill(
                    text = "BOT COMPLETED BOX! EXTRA TURN 🤖",
                    backgroundColor = PlayfulPurple.copy(alpha = 0.18f),
                    textColor = PlayfulPurple
                )
            }
            showExtraTurnBanner -> {
                PlayfulPill(
                    text = "BOX COMPLETED! EXTRA TURN! 🎁",
                    backgroundColor = PlayfulGreen.copy(alpha = 0.18f),
                    textColor = PlayfulGreen
                )
            }
            isBotThinking -> {
                PlayfulPill(
                    text = "BOT IS THINKING... 🤖",
                    backgroundColor = PlayfulPurple.copy(alpha = 0.15f),
                    textColor = PlayfulPurple
                )
            }
            selectedMode == GameMode.SOLO -> {
                PlayfulPill(
                    text = if (isP1Turn) "YOUR TURN • CONNECT 2 DOTS" else "BOT'S TURN",
                    backgroundColor = if (isP1Turn) PlayfulOrange.copy(alpha = 0.14f) else PlayfulPurple.copy(alpha = 0.14f),
                    textColor = if (isP1Turn) PlayfulOrange else PlayfulPurple
                )
            }
            isNearbyMode -> {
                val isMyTurn = gameState.activePlayer == localPlayer
                PlayfulPill(
                    text = if (isMyTurn) "YOUR TURN • CONNECT 2 DOTS" else "OPPONENT'S TURN 📱",
                    backgroundColor = if (isMyTurn) PlayfulOrange.copy(alpha = 0.14f) else PlayfulPurple.copy(alpha = 0.14f),
                    textColor = if (isMyTurn) PlayfulOrange else PlayfulPurple
                )
            }
            else -> {
                PlayfulPill(
                    text = if (isP1Turn) "PLAYER 1'S TURN (ORANGE)" else "PLAYER 2'S TURN (PURPLE)",
                    backgroundColor = if (isP1Turn) PlayfulOrange.copy(alpha = 0.14f) else PlayfulPurple.copy(alpha = 0.14f),
                    textColor = if (isP1Turn) PlayfulOrange else PlayfulPurple
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Central Game Board
        val isUserTurn = !gameState.isGameOver && !isBotThinking &&
            (selectedMode != GameMode.SOLO || gameState.activePlayer == DotPlayer.PLAYER_1)

        DotConnectionsBoardView(
            state = gameState,
            onEdgeSelected = { handleUserMove(it) },
            isInteractive = isUserTurn,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Text(text = "🔲", fontSize = 42.sp)
            },
            title = {
                Text(
                    text = "How to Play",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Connect two neighboring dots with a horizontal or vertical line.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "2. Complete the 4th side of any square to claim that box and earn an EXTRA TURN!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlayfulGreen
                    )
                    Text(
                        text = "3. If you don't complete a box, the turn passes to your opponent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "4. Claim the most boxes when the board is filled to win!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It!", fontWeight = FontWeight.Black)
                }
            }
        )
    }

    // End Game Dialog
    if (gameState.isGameOver) {
        val didPlayer1Win = gameState.winner == DotPlayer.PLAYER_1
        val isDraw = gameState.isDraw

        val titleText = when {
            isDraw -> "🤝 IT'S A DRAW!"
            selectedMode == GameMode.SOLO && didPlayer1Win -> "🏆 YOU WIN!"
            selectedMode == GameMode.SOLO -> "🤖 BOT WINS!"
            isNearbyMode && gameState.winner == localPlayer -> "🏆 YOU WIN!"
            isNearbyMode -> "🏆 OPPONENT WINS!"
            didPlayer1Win -> "🏆 PLAYER 1 WINS!"
            else -> "🏆 PLAYER 2 WINS!"
        }

        val subtitleText = when {
            isDraw -> "Equal squares claimed! Perfectly matched duel (${gameState.p1Score} — ${gameState.p2Score})."
            selectedMode == GameMode.SOLO && didPlayer1Win -> "Masterful boxing strategy! You out-connected the bot (${gameState.p1Score} — ${gameState.p2Score})."
            selectedMode == GameMode.SOLO -> "The bot secured more boxes this round (${gameState.p2Score} — ${gameState.p1Score}). Ready for revenge?"
            else -> "Great match! Final score: ${gameState.p1Score} — ${gameState.p2Score}."
        }

        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Text(
                    text = if (isDraw) "🤝" else if (didPlayer1Win) "🏆" else "🤖",
                    fontSize = 44.sp
                )
            },
            title = {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val p1DialogLabel = when {
                                selectedMode == GameMode.SOLO -> "YOU"
                                isNearbyMode -> if (localPlayer == DotPlayer.PLAYER_1) "YOU" else "OPPONENT"
                                else -> "P1"
                            }
                            Text(
                                text = p1DialogLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PlayfulOrange
                            )
                            Text(
                                text = "${gameState.p1Score}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "—",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val p2DialogLabel = when {
                                selectedMode == GameMode.SOLO -> "BOT"
                                isNearbyMode -> if (localPlayer == DotPlayer.PLAYER_2) "YOU" else "OPPONENT"
                                else -> "P2"
                            }
                            Text(
                                text = p2DialogLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PlayfulPurple
                            )
                            Text(
                                text = "${gameState.p2Score}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "Play Again 🔄",
                    backgroundColor = colors.accent,
                    onClick = {
                        restartGame()
                        if (isNearbyMode) {
                            multiplayerManager.sendAction("DOT_REMATCH", "")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { onChangeMode() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Change Mode",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun ScoreCard(
    name: String,
    score: Int,
    color: Color,
    isTurn: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isTurn) color.copy(alpha = 0.14f) else colors.surfaceVariant)
            .border(
                width = if (isTurn) 2.5.dp else 1.dp,
                color = if (isTurn) color else colors.border,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (isTurn) color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isTurn) {
                    Text(
                        text = "TURN 🎯",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = color
                    )
                }
            }

            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
