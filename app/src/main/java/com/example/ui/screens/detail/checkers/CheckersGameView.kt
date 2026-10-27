package com.example.ui.screens.detail.checkers

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.CheckersSoundManager
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.checkers.CheckersBot
import com.example.core.engine.checkers.CheckersDifficulty
import com.example.core.engine.checkers.CheckersEngine
import com.example.core.engine.checkers.CheckersGameState
import com.example.core.engine.checkers.CheckersMove
import com.example.core.engine.checkers.CheckersRules
import com.example.core.engine.checkers.CheckersSquare
import com.example.core.engine.checkers.deserializeCheckersState
import com.example.core.engine.checkers.serializeCheckersState
import com.example.core.multiplayer.MultiplayerLobbyState
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
fun CheckersGameView(
    selectedMode: GameMode,
    difficulty: CheckersDifficulty = CheckersDifficulty.MEDIUM,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val colors = ChillTheme.colors

    val soundManager = remember(context) { CheckersSoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) {
            soundManager.stopAllSounds()
        }
    }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)

    // Local player in Nearby: Host is Red (Player 1), Client is Black (Player 2)
    val localPlayer = when (selectedMode) {
        GameMode.BLUETOOTH_1V1 -> if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
        else -> PlayerId.PLAYER_1
    }

    val engine = remember { CheckersEngine() }
    var gameState by remember { mutableStateOf(engine.state) }
    var selectedSquare by remember { mutableStateOf<CheckersSquare?>(null) }
    var isBotThinking by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var showRestartConfirmDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var matchId by remember { mutableIntStateOf(1) }

    fun vibrateTap() {
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

    val hasMandatoryCaptures = remember(gameState) {
        CheckersRules.getLegalMoves(gameState).any { it.isJump }
    }

    // Handles terminal game outcome sounds and haptics
    LaunchedEffect(gameState.status) {
        when (val status = gameState.status) {
            is GameStatus.MatchWon -> {
                val winner = status.matchWinner
                val isLocalWinner = when (selectedMode) {
                    GameMode.SOLO -> winner == PlayerId.PLAYER_1
                    GameMode.LOCAL_1V1 -> true
                    GameMode.BLUETOOTH_1V1 -> winner == localPlayer
                }
                if (isLocalWinner) {
                    soundManager.playVictory(settings.soundEnabled)
                    soundManager.vibrateVictory(settings.vibrationEnabled)
                } else {
                    soundManager.playDefeat(settings.soundEnabled)
                    soundManager.vibrateDefeat(settings.vibrationEnabled)
                }

                // Record match to database
                if (!matchRecorded) {
                    matchRecorded = true
                    viewModel.repository.recordMatch(
                        gameId = "checkers",
                        gameTitle = "Checkers",
                        mode = selectedMode.name,
                        player1Score = if (winner == PlayerId.PLAYER_1) 1 else 0,
                        player2Score = if (winner == PlayerId.PLAYER_2) 1 else 0,
                        winner = winner.displayName,
                        durationSeconds = 90
                    )
                }
            }
            is GameStatus.RoundDraw -> {
                soundManager.playDraw(settings.soundEnabled)
                if (!matchRecorded) {
                    matchRecorded = true
                    viewModel.repository.recordMatch(
                        gameId = "checkers",
                        gameTitle = "Checkers",
                        mode = selectedMode.name,
                        player1Score = 0,
                        player2Score = 0,
                        winner = "DRAW",
                        durationSeconds = 90
                    )
                }
            }
            else -> {}
        }
    }

    // Execute Move helper with sounds and haptics
    fun executeMove(move: CheckersMove) {
        val res = engine.makeMove(move)
        if (res.success) {
            gameState = engine.state
            selectedSquare = if (res.multiJumpAvailable) res.multiJumpAvailable.let { move.to } else null

            // Sound and haptic effects based on move type
            if (res.isKingPromotion) {
                soundManager.playKingPromotion(settings.soundEnabled)
                soundManager.vibrateKingPromotion(settings.vibrationEnabled)
            } else if (res.isCapture) {
                soundManager.playCapture(settings.soundEnabled)
                soundManager.vibrateCapture(settings.vibrationEnabled)
            } else {
                soundManager.playCoinMove(settings.soundEnabled)
                soundManager.vibrateMove(settings.vibrationEnabled)
            }

            // Sync move over Wi-Fi multiplayer
            if (isNearbyMode) {
                val payload = "${move.from.row},${move.from.col},${move.to.row},${move.to.col}"
                multiplayerManager.sendAction("CHECKERS_MOVE", payload)
                if (isHost) {
                    val syncPayload = serializeCheckersState(gameState)
                    multiplayerManager.sendAction("CHECKERS_SYNC", syncPayload)
                }
            }
        }
    }

    // Bot move execution in Solo mode
    LaunchedEffect(gameState.activePlayer, gameState.status, selectedMode, matchId) {
        val isBotTurn = selectedMode == GameMode.SOLO &&
            gameState.activePlayer == PlayerId.PLAYER_2 &&
            gameState.status is GameStatus.InProgress

        if (isBotTurn) {
            isBotThinking = true
            delay(500)
            val botMove = CheckersBot.decideMove(gameState, difficulty, PlayerId.PLAYER_2)
            if (botMove != null) {
                executeMove(botMove)
            }
            isBotThinking = false
        }
    }

    // Nearby Mode TCP Action Listener
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "CHECKERS_MOVE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 4) {
                            val from = CheckersSquare(parts[0].toInt(), parts[1].toInt())
                            val to = CheckersSquare(parts[2].toInt(), parts[3].toInt())
                            val move = CheckersMove(from, to)

                            if (isHost) {
                                // Authoritative Host verifies and applies move
                                if (gameState.activePlayer == PlayerId.PLAYER_2) {
                                    val res = engine.makeMove(move)
                                    if (res.success) {
                                        gameState = engine.state
                                        selectedSquare = null
                                        if (res.isKingPromotion) {
                                            soundManager.playKingPromotion(settings.soundEnabled)
                                        } else if (res.isCapture) {
                                            soundManager.playCapture(settings.soundEnabled)
                                        } else {
                                            soundManager.playCoinMove(settings.soundEnabled)
                                        }
                                        val syncPayload = serializeCheckersState(gameState)
                                        multiplayerManager.sendAction("CHECKERS_SYNC", syncPayload)
                                    } else {
                                        val syncPayload = serializeCheckersState(gameState)
                                        multiplayerManager.sendAction("CHECKERS_SYNC", syncPayload)
                                    }
                                }
                            } else {
                                // Client applies Host move
                                val res = engine.makeMove(move)
                                if (res.success) {
                                    gameState = engine.state
                                    selectedSquare = null
                                    if (res.isKingPromotion) {
                                        soundManager.playKingPromotion(settings.soundEnabled)
                                    } else if (res.isCapture) {
                                        soundManager.playCapture(settings.soundEnabled)
                                    } else {
                                        soundManager.playCoinMove(settings.soundEnabled)
                                    }
                                }
                            }
                        }
                    }
                    "CHECKERS_SYNC" -> {
                        val synced = deserializeCheckersState(action.payload)
                        if (synced != null) {
                            engine.syncState(synced)
                            gameState = synced
                            selectedSquare = null
                        }
                    }
                    "CHECKERS_REMATCH" -> {
                        engine.resetGame()
                        gameState = engine.state
                        selectedSquare = null
                        matchRecorded = false
                        matchId++
                        vibrateTap()
                    }
                }
            }
        }
    }

    BackHandler {
        onExit()
    }

    fun handleSquareClick(square: CheckersSquare) {
        if (gameState.status !is GameStatus.InProgress) return

        // Prevent moving when it's not the local player's turn
        if (selectedMode == GameMode.SOLO && gameState.activePlayer != PlayerId.PLAYER_1) return
        if (isNearbyMode && gameState.activePlayer != localPlayer) return

        val pieceAtSquare = gameState.board[square]

        // If player clicked on their own piece, select it
        if (pieceAtSquare != null && pieceAtSquare.player == gameState.activePlayer) {
            // If locked into continuing a multi-jump, can only select the forced piece
            if (gameState.mustContinueJumpingFrom != null && gameState.mustContinueJumpingFrom != square) {
                return
            }
            soundManager.playCoinMove(settings.soundEnabled)
            vibrateTap()
            selectedSquare = square
            return
        }

        // If player has a piece selected, check if this is a legal destination
        val currentSelected = selectedSquare
        if (currentSelected != null) {
            val destinationMove = legalMovesForSelected.firstOrNull { it.to == square }
            if (destinationMove != null) {
                executeMove(destinationMove)
            } else {
                // Tapped an invalid or empty square: deselect unless locked in multi-jump
                if (gameState.mustContinueJumpingFrom == null) {
                    selectedSquare = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation & Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, colors.border, CircleShape)
                        .bouncyClickable(onClick = onExit)
                        .testTag("checkers_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Title Banner
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🏁 Checkers",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Right action buttons: Rules & Restart
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Rules Dialog Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, colors.border, CircleShape)
                            .bouncyClickable { showRulesDialog = true }
                            .testTag("checkers_rules_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rules",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Restart Button with Confirmation
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, colors.border, CircleShape)
                            .bouncyClickable { showRestartConfirmDialog = true }
                            .testTag("checkers_restart_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Game",
                            tint = PlayfulOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Player Piece & King Count Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Red Player Card
                CheckersPlayerCard(
                    title = if (selectedMode == GameMode.SOLO) "YOU (RED)" else if (isNearbyMode && !isHost) "OPPONENT (RED)" else "PLAYER 1 (RED)",
                    piecesRemaining = gameState.redPiecesRemaining,
                    kingsCount = gameState.redKings,
                    isActiveTurn = gameState.activePlayer == PlayerId.PLAYER_1 && gameState.status is GameStatus.InProgress,
                    indicatorColor = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )

                // Black Player Card
                CheckersPlayerCard(
                    title = if (selectedMode == GameMode.SOLO) "BOT (BLACK)" else if (isNearbyMode && isHost) "OPPONENT (BLACK)" else "PLAYER 2 (BLACK)",
                    piecesRemaining = gameState.blackPiecesRemaining,
                    kingsCount = gameState.blackKings,
                    isActiveTurn = gameState.activePlayer == PlayerId.PLAYER_2 && gameState.status is GameStatus.InProgress,
                    indicatorColor = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Turn & Alert Banner
            CheckersStatusBanner(
                state = gameState,
                selectedMode = selectedMode,
                isBotThinking = isBotThinking,
                isNearbyMode = isNearbyMode,
                localPlayer = localPlayer,
                hasMandatoryCaptures = hasMandatoryCaptures
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 8x8 Board View
            CheckersBoardView(
                state = gameState,
                selectedSquare = selectedSquare,
                legalMovesForSelected = legalMovesForSelected,
                onSquareClicked = ::handleSquareClick,
                flipped = isNearbyMode && !isHost
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mode & Helper Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                PlayfulPill(
                    text = when (selectedMode) {
                        GameMode.SOLO -> "🤖 VS BOT (${difficulty.title})"
                        GameMode.LOCAL_1V1 -> "👥 2 PLAYERS (PASS & PLAY)"
                        GameMode.BLUETOOTH_1V1 -> "📱 WI-FI MULTIPLAYER (${if (isHost) "HOST" else "JOINED"})"
                    },
                    backgroundColor = PlayfulPurple.copy(alpha = 0.15f),
                    textColor = PlayfulPurple
                )
            }
        }

        // Restart Confirmation Dialog
        if (showRestartConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestartConfirmDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Restart Checkers Match?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to reset the current match? This move is final and will start a fresh 8×8 board.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Yes, Restart 🔄",
                        onClick = {
                            showRestartConfirmDialog = false
                            engine.resetGame()
                            gameState = engine.state
                            selectedSquare = null
                            matchRecorded = false
                            matchId++
                            vibrateTap()
                            if (isNearbyMode) {
                                multiplayerManager.sendAction("CHECKERS_REMATCH", "rematch")
                            }
                        },
                        backgroundColor = PlayfulOrange,
                        modifier = Modifier.testTag("confirm_restart_button")
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showRestartConfirmDialog = false }) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Rules Dialog
        if (showRulesDialog) {
            AlertDialog(
                onDismissRequest = { showRulesDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "🏁 Checkers Rules",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "• Normal Pieces: Move diagonally forward 1 step into an empty dark square.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Mandatory Captures: If any jump is available, you MUST capture. Jumps take priority over simple steps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Multi-Jumps: If your piece can jump again after capturing, you must continue jumping with that piece.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• King Promotion: Reaching the opponent's back rank crowns your piece as King (👑). Kings can move & jump both forward and backward!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Winning: Capture all opponent pieces or block all legal moves.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• No Undo: Once a move is made, it is final.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PlayfulOrange
                        )
                    }
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Got It! 👍",
                        onClick = { showRulesDialog = false },
                        backgroundColor = PlayfulGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        // Game Over Overlay (Victory / Defeat / Draw)
        AnimatedVisibility(
            visible = gameState.status.isFinished || gameState.status is GameStatus.RoundDraw,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            val status = gameState.status
            val isWon = status is GameStatus.MatchWon
            val winner = (status as? GameStatus.MatchWon)?.matchWinner
            val isLocalWinner = when (selectedMode) {
                GameMode.SOLO -> winner == PlayerId.PLAYER_1
                GameMode.LOCAL_1V1 -> true
                GameMode.BLUETOOTH_1V1 -> winner == localPlayer
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, colors.border, RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            status is GameStatus.RoundDraw -> "🤝"
                            isLocalWinner -> "🏆"
                            else -> "💀"
                        },
                        fontSize = 54.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when {
                            status is GameStatus.RoundDraw -> "IT'S A DRAW!"
                            selectedMode == GameMode.LOCAL_1V1 -> "${winner?.displayName?.uppercase()} WINS! 🎉"
                            isLocalWinner -> "VICTORY! 🎉"
                            else -> "DEFEAT! 💔"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            status is GameStatus.RoundDraw -> "40 moves with no capture or promotion. Game ended in a fair draw."
                            selectedMode == GameMode.LOCAL_1V1 -> "Masterful checkmating tactics and board control!"
                            isLocalWinner -> "You outmaneuvered your opponent and crowned the board!"
                            else -> "Nice battle! Sharpen your kings and try again."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons: Rematch & Change Mode
                    ChunkyButton(
                        text = "Play Again 🔄",
                        onClick = {
                            engine.resetGame()
                            gameState = engine.state
                            selectedSquare = null
                            matchRecorded = false
                            matchId++
                            vibrateTap()
                            if (isNearbyMode) {
                                multiplayerManager.sendAction("CHECKERS_REMATCH", "rematch")
                            }
                        },
                        backgroundColor = PlayfulGreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkers_play_again_button")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onChangeMode,
                        modifier = Modifier.testTag("checkers_change_mode_button")
                    ) {
                        Text(
                            text = "Change Mode / Exit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckersPlayerCard(
    title: String,
    piecesRemaining: Int,
    kingsCount: Int,
    isActiveTurn: Boolean,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isActiveTurn) indicatorColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isActiveTurn) 2.5.dp else 1.5.dp,
                color = if (isActiveTurn) indicatorColor else colors.border,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "$piecesRemaining",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pieces",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (kingsCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "👑 $kingsCount",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "Kings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckersStatusBanner(
    state: CheckersGameState,
    selectedMode: GameMode,
    isBotThinking: Boolean,
    isNearbyMode: Boolean,
    localPlayer: PlayerId,
    hasMandatoryCaptures: Boolean
) {
    val activeColor = if (state.activePlayer == PlayerId.PLAYER_1) Color(0xFFDC2626) else Color(0xFF1E293B)
    val isMyTurn = when {
        selectedMode == GameMode.SOLO -> state.activePlayer == PlayerId.PLAYER_1
        isNearbyMode -> state.activePlayer == localPlayer
        else -> true
    }

    val bannerText = when {
        isBotThinking -> "🤖 BOT IS THINKING... 💭"
        state.mustContinueJumpingFrom != null -> "⚡ MULTI-JUMP! KEEP JUMPING"
        hasMandatoryCaptures -> "⚔️ MANDATORY CAPTURE REQUIRED!"
        selectedMode == GameMode.SOLO -> if (isMyTurn) "🔴 YOUR TURN (RED)" else "⚫ BOT'S TURN"
        isNearbyMode -> if (isMyTurn) "🟢 YOUR TURN (${localPlayer.displayName.uppercase()})" else "⏳ OPPONENT'S TURN"
        else -> if (state.activePlayer == PlayerId.PLAYER_1) "🔴 RED'S TURN" else "⚫ BLACK'S TURN"
    }

    val bannerBg = when {
        state.mustContinueJumpingFrom != null -> PlayfulOrange
        hasMandatoryCaptures -> Color(0xFFDC2626)
        isMyTurn -> PlayfulGreen
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bannerBg)
            .border(1.5.dp, ChillTheme.colors.border, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = bannerText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = if (bannerBg == MaterialTheme.colorScheme.surface) MaterialTheme.colorScheme.onSurface else Color.White,
            textAlign = TextAlign.Center
        )
    }
}
