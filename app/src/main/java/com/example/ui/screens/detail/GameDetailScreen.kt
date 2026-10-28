package com.example.ui.screens.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.core.audio.XOSoundAndHapticManager
import com.example.core.bot.BotDifficulty
import com.example.core.bot.XOBotOpponent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotDifficulty
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.XOState
import com.example.core.engine.xo.deserializeXOState
import com.example.core.engine.xo.serializeXOState
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.registry.GameMode
import com.example.core.registry.GameRegistry
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.core.engine.checkers.CheckersDifficulty
import com.example.ui.screens.detail.checkers.CheckersGameView
import com.example.ui.screens.detail.chess.ChessGameView
import com.example.ui.screens.detail.dotconnections.DotConnectionsGameView
import com.example.ui.screens.detail.ludo.LudoGameView
import com.example.ui.screens.detail.snakesandladders.SnakesAndLaddersGameView
import com.example.ui.screens.detail.tugofwar.TugOfWarGameView
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPink
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GameDetailScreen(
    gameId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val game = GameRegistry.getGameById(gameId) ?: GameRegistry.XO
    val settings by viewModel.settings.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(GameMode.SOLO) }
    var chessDifficulty by remember { mutableStateOf(ChessDifficulty.MEDIUM) }
    var chessPlayerColor by remember { mutableStateOf(ChessPlayerColor.AUTO) }
    var checkersDifficulty by rememberSaveable { mutableStateOf(CheckersDifficulty.MEDIUM) }
    var dotDifficulty by rememberSaveable { mutableStateOf(DotDifficulty.MEDIUM) }
    var dotBoardSize by rememberSaveable { mutableStateOf(DotBoardSize.MEDIUM) }
    var xoDifficulty by rememberSaveable { mutableStateOf(BotDifficulty.MEDIUM) }
    var ludoPlayerCount by rememberSaveable { mutableIntStateOf(2) }
    var ludoBotDifficulty by rememberSaveable { mutableStateOf(BotDifficulty.MEDIUM) }
    var snakesPlayerCount by rememberSaveable { mutableIntStateOf(2) }
    var targetScore by remember { mutableIntStateOf(game.defaultTargetScore) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    var showNearbyDialog by remember { mutableStateOf(false) }

    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayerId = if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2

    val isXoGame = game.id.equals("xo", ignoreCase = true)
    val xoSoundManager = remember(context) { XOSoundAndHapticManager(context) }
    DisposableEffect(xoSoundManager) {
        onDispose {
            xoSoundManager.release()
        }
    }

    // Immediately stop playing sounds if user switches Sound Effects OFF in Settings
    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) {
            xoSoundManager.stopAllSounds()
        }
    }

    BackHandler(enabled = true) {
        if (isPlaying) {
            xoSoundManager.stopAll()
            isPlaying = false
            multiplayerManager.disconnect()
        } else {
            onBack()
        }
    }

    // XO Engine and Bot
    val engine = remember { XOGameEngine(initialTargetScore = targetScore) }
    val bot = remember(xoDifficulty) { XOBotOpponent(difficulty = xoDifficulty) }
    var gameState by remember { mutableStateOf(engine.state) }
    var isBotThinking by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }

    var lastObservedBoard by remember { mutableStateOf<List<PlayerId?>?>(null) }
    var lastProcessedOutcomeKey by remember { mutableStateOf<String?>(null) }

    // Piece Placement Beep: Triggered once per rendered valid piece placement (local, bot, or remote)
    LaunchedEffect(gameState.board, isPlaying, isXoGame) {
        if (isPlaying && isXoGame) {
            val currentBoard = gameState.board
            val prevBoard = lastObservedBoard
            if (prevBoard != null && currentBoard != prevBoard && currentBoard.any { it != null }) {
                xoSoundManager.playPlacementBeep(settings.soundEnabled)
            }
            lastObservedBoard = currentBoard
        } else {
            lastObservedBoard = null
        }
    }

    // Round and Match Outcome Sound & Vibration
    LaunchedEffect(gameState.status, gameState.roundNumber, isPlaying, isXoGame) {
        if (!isPlaying || !isXoGame) {
            lastProcessedOutcomeKey = null
            return@LaunchedEffect
        }

        val isTwoPlayerMode = selectedMode == GameMode.LOCAL_1V1
        val localPlayer = if (selectedMode == GameMode.BLUETOOTH_1V1) {
            localPlayerId ?: PlayerId.PLAYER_1
        } else {
            PlayerId.PLAYER_1
        }

        when (val currentStatus = gameState.status) {
            is GameStatus.InProgress -> {
                // Round is actively playing; reset key so next terminal outcome triggers
                lastProcessedOutcomeKey = null
            }
            is GameStatus.RoundWon -> {
                val outcomeKey = "ROUND_WON_${gameState.roundNumber}_${currentStatus.winner.name}"
                if (lastProcessedOutcomeKey != outcomeKey) {
                    lastProcessedOutcomeKey = outcomeKey
                    xoSoundManager.onRoundWon(
                        winner = currentStatus.winner,
                        localPlayer = localPlayer,
                        isTwoPlayerMode = isTwoPlayerMode,
                        isSoundEnabled = settings.soundEnabled
                    )
                }
            }
            is GameStatus.RoundDraw -> {
                val outcomeKey = "ROUND_DRAW_${gameState.roundNumber}"
                if (lastProcessedOutcomeKey != outcomeKey) {
                    lastProcessedOutcomeKey = outcomeKey
                    // Draw: do not classify as win or loss; no victory or failure effects
                }
            }
            is GameStatus.MatchWon -> {
                val isTie = gameState.player1Score == gameState.player2Score
                val outcomeKey = "MATCH_WON_${gameState.roundNumber}_${currentStatus.matchWinner.name}_${gameState.player1Score}_${gameState.player2Score}"
                if (lastProcessedOutcomeKey != outcomeKey) {
                    lastProcessedOutcomeKey = outcomeKey
                    xoSoundManager.onMatchWon(
                        matchWinner = currentStatus.matchWinner,
                        localPlayer = localPlayer,
                        isTwoPlayerMode = isTwoPlayerMode,
                        isTie = isTie,
                        isSoundEnabled = settings.soundEnabled,
                        isVibrationEnabled = settings.vibrationEnabled
                    )
                }
            }
            else -> {
                // Safe handling for any unhandled state (e.g. NotStarted)
            }
        }
    }

    fun vibrate() {
        if (settings.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun startPlaying() {
        engine.initializeGame(targetScore = targetScore)
        gameState = engine.state
        matchRecorded = false
        isPlaying = true
        vibrate()
    }

    // Listen for game start event in Nearby Multiplayer
    LaunchedEffect(Unit) {
        multiplayerManager.gameStartEvent.collect { startInfo ->
            if (startInfo.gameId.equals(game.id, ignoreCase = true) || startInfo.gameId.equals("xo", ignoreCase = true) || isNearbyMode || selectedMode == GameMode.BLUETOOTH_1V1) {
                if (startInfo.configPayload.isNotBlank()) {
                    try {
                        val config = org.json.JSONObject(startInfo.configPayload)
                        val configTarget = config.optInt("targetScore", -1)
                        if (configTarget > 0) {
                            targetScore = configTarget
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                showNearbyDialog = false
                selectedMode = GameMode.BLUETOOTH_1V1
                startPlaying()
            }
        }
    }

    fun handleMove(move: XOMove) {
        if (isBotThinking) return
        if (gameState.status !is GameStatus.InProgress) return

        val currentPlayer = gameState.activePlayer
        if (selectedMode == GameMode.SOLO && currentPlayer != PlayerId.PLAYER_1) return
        if (isNearbyMode && currentPlayer != localPlayerId) return

        if (isNearbyMode) {
            if (isHost) {
                // Host is authoritative: execute move, update state, and distribute authoritative state
                val result = engine.makeMove(move, PlayerId.PLAYER_1)
                if (result is MoveResult.Success) {
                    gameState = result.newState
                    vibrate()
                    val syncPayload = serializeXOState(result.newState, move)
                    multiplayerManager.sendAction("XO_SYNC", syncPayload)
                    val legacyPayload = if (move.fromIndex != null) "${move.fromIndex},${move.toIndex}" else "${move.toIndex}"
                    multiplayerManager.sendAction("XO_MOVE", legacyPayload)
                }
            } else {
                // Client: validate move locally, send to host, and optimistically apply
                if (engine.validateMove(move, PlayerId.PLAYER_2)) {
                    val payload = if (move.fromIndex != null) "${move.fromIndex},${move.toIndex}" else "${move.toIndex}"
                    multiplayerManager.sendAction("XO_MOVE", payload)
                    val res = engine.makeMove(move, PlayerId.PLAYER_2)
                    if (res is MoveResult.Success) {
                        gameState = res.newState
                        vibrate()
                    }
                }
            }
            return
        }

        // Solo and Local 1v1 modes
        val result = engine.makeMove(move, currentPlayer)
        if (result is MoveResult.Success) {
            gameState = result.newState
            vibrate()

            // If Solo and Bot's turn next, trigger bot
            if (selectedMode == GameMode.SOLO &&
                gameState.status is GameStatus.InProgress &&
                gameState.activePlayer == PlayerId.PLAYER_2
            ) {
                isBotThinking = true
                scope.launch {
                    val botMove = bot.decideMove(gameState, PlayerId.PLAYER_2)
                    if (botMove != null) {
                        val botResult = engine.makeMove(botMove, PlayerId.PLAYER_2)
                        if (botResult is MoveResult.Success) {
                            gameState = botResult.newState
                            vibrate()
                        }
                    }
                    isBotThinking = false
                }
            }
        }
    }

    // Listen for incoming actions in Nearby Mode for Tic Tac Toe
    LaunchedEffect(isPlaying, isNearbyMode) {
        if (isPlaying && isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "XO_MOVE" -> {
                        val parts = action.payload.split(",")
                        val move = if (parts.size >= 2) {
                            val from = parts[0].toIntOrNull()
                            val to = parts[1].toIntOrNull()
                            if (from != null && to != null) XOMove(toIndex = to, fromIndex = from) else null
                        } else {
                            val to = action.payload.toIntOrNull()
                            if (to != null) XOMove(toIndex = to) else null
                        }
                        if (move != null) {
                            if (isHost) {
                                // Authoritative host validation and execution for client (PLAYER_2)
                                if (gameState.status is GameStatus.InProgress && gameState.activePlayer == PlayerId.PLAYER_2) {
                                    val res = engine.makeMove(move, PlayerId.PLAYER_2)
                                    if (res is MoveResult.Success) {
                                        gameState = res.newState
                                        vibrate()
                                        val syncPayload = serializeXOState(res.newState, move)
                                        multiplayerManager.sendAction("XO_SYNC", syncPayload)
                                    } else {
                                        // Reject invalid move: distribute authoritative state to roll back client
                                        val syncPayload = serializeXOState(gameState)
                                        multiplayerManager.sendAction("XO_SYNC", syncPayload)
                                    }
                                } else {
                                    // Out of turn move: re-synchronize client
                                    val syncPayload = serializeXOState(gameState)
                                    multiplayerManager.sendAction("XO_SYNC", syncPayload)
                                }
                            } else {
                                // Client receives host move (fallback if XO_SYNC delayed)
                                if (gameState.activePlayer == PlayerId.PLAYER_1) {
                                    val res = engine.makeMove(move, PlayerId.PLAYER_1)
                                    if (res is MoveResult.Success) {
                                        gameState = res.newState
                                        vibrate()
                                    }
                                }
                            }
                        }
                    }
                    "XO_SYNC" -> {
                        val syncedState = deserializeXOState(action.payload)
                        if (syncedState != null) {
                            engine.syncState(syncedState)
                            gameState = syncedState
                            vibrate()
                        }
                    }
                    "XO_NEXT_ROUND" -> {
                        if (isHost) {
                            gameState = engine.startRound()
                            vibrate()
                            val syncPayload = serializeXOState(gameState)
                            multiplayerManager.sendAction("XO_SYNC", syncPayload)
                        } else {
                            gameState = engine.startRound()
                            vibrate()
                        }
                    }
                    "XO_REMATCH" -> {
                        val target = action.payload.toIntOrNull() ?: targetScore
                        if (isHost) {
                            engine.resetGame(target)
                            gameState = engine.state
                            matchRecorded = false
                            vibrate()
                            val syncPayload = serializeXOState(gameState)
                            multiplayerManager.sendAction("XO_SYNC", syncPayload)
                        } else {
                            engine.resetGame(target)
                            gameState = engine.state
                            matchRecorded = false
                            vibrate()
                        }
                    }
                }
            }
        }
    }

    // Record match to local database when match finishes
    LaunchedEffect(gameState.status) {
        val status = gameState.status
        if (status is GameStatus.MatchWon && !matchRecorded) {
            matchRecorded = true
            val winnerName = if (gameState.player1Score == gameState.player2Score) "DRAW" else status.matchWinner.name
            viewModel.repository.recordMatch(
                gameId = game.id,
                gameTitle = game.title,
                mode = selectedMode.name,
                player1Score = gameState.player1Score,
                player2Score = gameState.player2Score,
                winner = winnerName,
                durationSeconds = 60
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (!isPlaying) {
            // Minimal Mode Selection / Launch Screen
            CleanLaunchView(
                game = game,
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                chessDifficulty = chessDifficulty,
                onChessDifficultySelected = { chessDifficulty = it },
                chessPlayerColor = chessPlayerColor,
                onChessPlayerColorSelected = { chessPlayerColor = it },
                checkersDifficulty = checkersDifficulty,
                onCheckersDifficultySelected = { checkersDifficulty = it },
                dotDifficulty = dotDifficulty,
                onDotDifficultySelected = { dotDifficulty = it },
                dotBoardSize = dotBoardSize,
                onDotBoardSizeSelected = { dotBoardSize = it },
                xoDifficulty = xoDifficulty,
                onXoDifficultySelected = { xoDifficulty = it },
                ludoPlayerCount = ludoPlayerCount,
                onLudoPlayerCountSelected = { ludoPlayerCount = it },
                ludoBotDifficulty = ludoBotDifficulty,
                onLudoBotDifficultySelected = { ludoBotDifficulty = it },
                snakesPlayerCount = snakesPlayerCount,
                onSnakesPlayerCountSelected = { snakesPlayerCount = it },
                targetScore = targetScore,
                onTargetScoreSelected = { targetScore = it },
                onBack = onBack,
                onPlay = {
                    if (selectedMode == GameMode.BLUETOOTH_1V1) {
                        showNearbyDialog = true
                    } else {
                        startPlaying()
                    }
                }
            )
        } else {
            when {
                game.id.equals("checkers", ignoreCase = true) -> {
                    CheckersGameView(
                        selectedMode = selectedMode,
                        difficulty = checkersDifficulty,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                game.id.equals("ludo", ignoreCase = true) -> {
                    LudoGameView(
                        selectedMode = selectedMode,
                        playerCount = ludoPlayerCount,
                        botDifficulty = ludoBotDifficulty,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                game.id.equals("dot_connections", ignoreCase = true) -> {
                    DotConnectionsGameView(
                        selectedMode = selectedMode,
                        difficulty = dotDifficulty,
                        boardSize = dotBoardSize,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                game.id.equals("chess", ignoreCase = true) -> {
                    ChessGameView(
                        selectedMode = selectedMode,
                        difficulty = chessDifficulty,
                        playerColorPreference = chessPlayerColor,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                game.id.equals("tug_of_war", ignoreCase = true) -> {
                    TugOfWarGameView(
                        selectedMode = selectedMode,
                        targetScore = targetScore,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                game.id.equals("snakes_and_ladders", ignoreCase = true) -> {
                    SnakesAndLaddersGameView(
                        selectedMode = selectedMode,
                        targetScore = targetScore,
                        playerCount = snakesPlayerCount,
                        viewModel = viewModel,
                        onChangeMode = {
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
                else -> {
                    // Interactive Playable XO Screen
                    CleanXOPlayView(
                        gameState = gameState,
                        selectedMode = selectedMode,
                        localPlayerId = if (isNearbyMode) localPlayerId else null,
                        username = settings.username,
                        isBotThinking = isBotThinking,
                        onMove = { handleMove(it) },
                        onNextRound = {
                            if (isNearbyMode) {
                                if (isHost) {
                                    gameState = engine.startRound()
                                    vibrate()
                                    val syncPayload = serializeXOState(gameState)
                                    multiplayerManager.sendAction("XO_SYNC", syncPayload)
                                } else {
                                    multiplayerManager.sendAction("XO_NEXT_ROUND", "")
                                }
                            } else {
                                gameState = engine.startRound()
                                vibrate()
                                if (selectedMode == GameMode.SOLO && gameState.activePlayer == PlayerId.PLAYER_2) {
                                    isBotThinking = true
                                    scope.launch {
                                        val botMove = bot.decideMove(gameState, PlayerId.PLAYER_2)
                                        if (botMove != null) {
                                            val res = engine.makeMove(botMove, PlayerId.PLAYER_2)
                                            if (res is MoveResult.Success) {
                                                gameState = res.newState
                                                vibrate()
                                            }
                                        }
                                        isBotThinking = false
                                    }
                                }
                            }
                        },
                        onRematch = {
                            if (isNearbyMode) {
                                if (isHost) {
                                    engine.resetGame(targetScore)
                                    gameState = engine.state
                                    matchRecorded = false
                                    vibrate()
                                    val syncPayload = serializeXOState(gameState)
                                    multiplayerManager.sendAction("XO_SYNC", syncPayload)
                                } else {
                                    multiplayerManager.sendAction("XO_REMATCH", targetScore.toString())
                                }
                            } else {
                                engine.resetGame(targetScore)
                                gameState = engine.state
                                matchRecorded = false
                                vibrate()
                            }
                        },
                        onChangeMode = {
                            xoSoundManager.stopAll()
                            isPlaying = false
                            multiplayerManager.disconnect()
                        },
                        onExit = {
                            xoSoundManager.stopAll()
                            multiplayerManager.disconnect()
                            onBack()
                        }
                    )
                }
            }
        }

        // Show Nearby Local Wi-Fi Multiplayer Lobby Dialog
        if (showNearbyDialog) {
            NearbyMultiplayerDialog(
                game = game,
                manager = multiplayerManager,
                targetScore = targetScore,
                username = settings.username,
                ludoPlayerCount = ludoPlayerCount,
                onDismiss = { showNearbyDialog = false },
                onGameStarted = {
                    showNearbyDialog = false
                    startPlaying()
                }
            )
        }

        // Opponent disconnected alert during active multiplayer session
        if (isPlaying && isNearbyMode && lobbyState is MultiplayerLobbyState.Disconnected) {
            val reason = (lobbyState as MultiplayerLobbyState.Disconnected).reason
            AlertDialog(
                onDismissRequest = {
                    isPlaying = false
                    multiplayerManager.disconnect()
                },
                title = { Text("Opponent Disconnected", fontWeight = FontWeight.Bold) },
                text = { Text(reason) },
                confirmButton = {
                    TextButton(onClick = {
                        isPlaying = false
                        multiplayerManager.disconnect()
                    }) {
                        Text("Back to Lobby")
                    }
                }
            )
        }
    }
}

@Composable
private fun CleanLaunchView(
    game: com.example.core.registry.GameDefinition,
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
    chessDifficulty: ChessDifficulty = ChessDifficulty.MEDIUM,
    onChessDifficultySelected: (ChessDifficulty) -> Unit = {},
    chessPlayerColor: ChessPlayerColor = ChessPlayerColor.AUTO,
    onChessPlayerColorSelected: (ChessPlayerColor) -> Unit = {},
    checkersDifficulty: CheckersDifficulty = CheckersDifficulty.MEDIUM,
    onCheckersDifficultySelected: (CheckersDifficulty) -> Unit = {},
    dotDifficulty: DotDifficulty = DotDifficulty.MEDIUM,
    onDotDifficultySelected: (DotDifficulty) -> Unit = {},
    dotBoardSize: DotBoardSize = DotBoardSize.MEDIUM,
    onDotBoardSizeSelected: (DotBoardSize) -> Unit = {},
    xoDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    onXoDifficultySelected: (BotDifficulty) -> Unit = {},
    ludoPlayerCount: Int = 2,
    onLudoPlayerCountSelected: (Int) -> Unit = {},
    ludoBotDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    onLudoBotDifficultySelected: (BotDifficulty) -> Unit = {},
    snakesPlayerCount: Int = 2,
    onSnakesPlayerCountSelected: (Int) -> Unit = {},
    targetScore: Int,
    onTargetScoreSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val accentColor = Color(game.accentHex)
    val colors = com.example.ui.theme.ChillTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Simple Back Button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, CircleShape)
                .bouncyClickable(onClick = onBack)
                .testTag("detail_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large friendly title & icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (game.imageRes != null) {
                Image(
                    painter = painterResource(id = game.imageRes),
                    contentDescription = game.title,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Text(
                    text = game.emoji,
                    fontSize = 44.sp,
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
            Column {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select mode",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = PlayfulPurple
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Mode Options (clean, no paragraphs of text)
        val isMultiPlayerGame = game.id.equals("snakes_and_ladders", ignoreCase = true) ||
                game.id.equals("ludo", ignoreCase = true)
        val localLabel = if (isMultiPlayerGame) "👥 LOCAL" else "👥 2 PLAYERS"

        val modes = listOf(
            GameMode.SOLO to "🤖 VS BOT",
            GameMode.LOCAL_1V1 to localLabel,
            GameMode.BLUETOOTH_1V1 to "📱 NEARBY"
        )

        modes.forEach { (mode, label) ->
            val isSelected = selectedMode == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = if (isSelected) 2.5.dp else 1.5.dp,
                        color = if (isSelected) accentColor else ToyboxBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .bouncyClickable { onModeSelected(mode) }
                    .testTag("mode_option_${mode.name}")
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // CHESS ALONE: BOT DIFFICULTY & YOUR COLOR ONLY when VS BOT is selected
        if (game.id.equals("chess", ignoreCase = true) && selectedMode == GameMode.SOLO) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "BOT DIFFICULTY",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(ChessDifficulty.EASY, ChessDifficulty.MEDIUM, ChessDifficulty.HARD).forEach { diff ->
                    val isSelected = chessDifficulty == diff
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onChessDifficultySelected(diff) }
                            .testTag("chess_difficulty_${diff.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = diff.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "YOUR COLOR",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    ChessPlayerColor.WHITE to "⚪ WHITE",
                    ChessPlayerColor.BLACK to "⚫ BLACK",
                    ChessPlayerColor.AUTO to "🎲 AUTO"
                ).forEach { (colorOption, label) ->
                    val isSelected = chessPlayerColor == colorOption
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onChessPlayerColorSelected(colorOption) }
                            .testTag("chess_color_${colorOption.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // CHECKERS: BOT DIFFICULTY ONLY when VS BOT is selected
        if (game.id.equals("checkers", ignoreCase = true) && selectedMode == GameMode.SOLO) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "BOT DIFFICULTY",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(CheckersDifficulty.EASY, CheckersDifficulty.MEDIUM, CheckersDifficulty.HARD).forEach { diff ->
                    val isSelected = checkersDifficulty == diff
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onCheckersDifficultySelected(diff) }
                            .testTag("checkers_difficulty_${diff.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = diff.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // DOT CONNECTIONS: BOARD SIZE & BOT DIFFICULTY
        if (game.id.equals("dot_connections", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "BOARD SIZE",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(DotBoardSize.SMALL, DotBoardSize.MEDIUM, DotBoardSize.LARGE).forEach { sizeOption ->
                    val isSelected = dotBoardSize == sizeOption
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onDotBoardSizeSelected(sizeOption) }
                            .testTag("dot_board_size_${sizeOption.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = sizeOption.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${sizeOption.dots}×${sizeOption.dots}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedMode == GameMode.SOLO) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "BOT DIFFICULTY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(DotDifficulty.EASY, DotDifficulty.MEDIUM, DotDifficulty.HARD).forEach { diff ->
                        val isSelected = dotDifficulty == diff
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) accentColor else colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable { onDotDifficultySelected(diff) }
                                .testTag("dot_difficulty_${diff.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = diff.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (game.id.equals("xo", ignoreCase = true) && selectedMode == GameMode.SOLO) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "BOT DIFFICULTY",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(BotDifficulty.EASY, BotDifficulty.MEDIUM, BotDifficulty.HARD).forEach { diff ->
                    val isSelected = xoDifficulty == diff
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onXoDifficultySelected(diff) }
                            .testTag("xo_difficulty_${diff.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = diff.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // LUDO: BOT DIFFICULTY (for VS Bot) or PLAYER COUNT (for Local/Nearby)
        if (game.id.equals("ludo", ignoreCase = true)) {
            if (selectedMode == GameMode.SOLO) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "BOT DIFFICULTY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(BotDifficulty.EASY, BotDifficulty.MEDIUM, BotDifficulty.HARD).forEach { diff ->
                        val isSelected = ludoBotDifficulty == diff
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) accentColor else colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable { onLudoBotDifficultySelected(diff) }
                                .testTag("ludo_difficulty_${diff.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = diff.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "PLAYER COUNT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(2 to "2 PLAYERS", 3 to "3 PLAYERS", 4 to "4 PLAYERS").forEach { (count, label) ->
                        val isSelected = ludoPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) accentColor else colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable { onLudoPlayerCountSelected(count) }
                                .testTag("ludo_player_count_$count"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // SNAKES AND LADDERS: LOCAL MULTIPLAYER PLAYER COUNT (2 to 5 players)
        if (game.id.equals("snakes_and_ladders", ignoreCase = true) && selectedMode == GameMode.LOCAL_1V1) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "PLAYER COUNT (2–5 PLAYERS)",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("snakes_player_count_dropdown"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    2 to "🔴🔵",
                    3 to "🔴🔵🟢",
                    4 to "4-PLY",
                    5 to "5-PLY"
                ).forEach { (count, emj) ->
                    val isSelected = snakesPlayerCount == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = if (isSelected) accentColor else colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onSnakesPlayerCountSelected(count) }
                            .testTag("snakes_player_count_$count"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${count}P",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = emj,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (game.targetScoreOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            // Target Score Chips
            val targetScoreHeader = if (game.id.equals("xo", ignoreCase = true)) "MATCH TARGET" else "FIRST TO..."
            Text(
                text = targetScoreHeader,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                game.targetScoreOptions.forEach { score ->
                    val isSelected = targetScore == score
                    val chipText = if (game.id.equals("xo", ignoreCase = true)) "$score ROUNDS" else "$score WINS"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.5.dp,
                                color = if (isSelected) accentColor else ToyboxBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable { onTargetScoreSelected(score) }
                            .testTag("score_chip_$score"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Chunky Instant Play Button
        val playButtonText = when {
            game.id.equals("chess", ignoreCase = true) -> "PLAY ♟️"
            else -> "PLAY 🎮"
        }
        ChunkyButton(
            text = playButtonText,
            onClick = onPlay,
            backgroundColor = accentColor,
            testTag = "start_game_button"
        )
    }
}

@Composable
private fun CleanXOPlayView(
    gameState: XOState,
    selectedMode: GameMode,
    localPlayerId: PlayerId? = null,
    username: String = "Player",
    isBotThinking: Boolean,
    onMove: (XOMove) -> Unit,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    onChangeMode: () -> Unit,
    onExit: () -> Unit
) {
    val isMatchFinished = gameState.status is GameStatus.MatchWon
    val isRoundFinished = gameState.status is GameStatus.RoundWon || gameState.status is GameStatus.RoundDraw

    val activeMarksCount = gameState.board.count { it == gameState.activePlayer }
    val isMovementPhase = activeMarksCount >= 3
    val canInteract = gameState.status is GameStatus.InProgress &&
        !isBotThinking &&
        (selectedMode != GameMode.SOLO || gameState.activePlayer == PlayerId.PLAYER_1) &&
        (selectedMode != GameMode.BLUETOOTH_1V1 || gameState.activePlayer == localPlayerId)

    var selectedCellIndex by remember(gameState.board, gameState.activePlayer, gameState.roundNumber) {
        mutableStateOf<Int?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Nav Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onChangeMode)
                    .testTag("xo_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Modes",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            PlayfulPill(
                text = when (selectedMode) {
                    GameMode.SOLO -> "🤖 VS BOT"
                    GameMode.LOCAL_1V1 -> "👥 2 PLAYERS"
                    GameMode.BLUETOOTH_1V1 -> "📱📱 NEARBY"
                },
                backgroundColor = PlayfulPurple.copy(alpha = 0.12f),
                textColor = PlayfulPurple
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onRematch)
                    .testTag("xo_restart_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scoreboard Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(22.dp))
                .padding(vertical = 12.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // P1
                Column(horizontalAlignment = Alignment.Start) {
                    val p1Label = when {
                        selectedMode == GameMode.SOLO -> "$username (YOU) ❌"
                        selectedMode == GameMode.BLUETOOTH_1V1 -> if (localPlayerId == PlayerId.PLAYER_1) "$username (YOU) ❌" else "OPPONENT ❌"
                        else -> "$username ❌"
                    }
                    Text(
                        text = p1Label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = PlayfulPurple
                    )
                    Text(
                        text = "${gameState.player1Score}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Target
                PlayfulPill(
                    text = "ROUND ${gameState.roundNumber} / ${gameState.targetScore}",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // P2
                Column(horizontalAlignment = Alignment.End) {
                    val p2Label = when {
                        selectedMode == GameMode.SOLO -> "BOT ⭕"
                        selectedMode == GameMode.BLUETOOTH_1V1 -> if (localPlayerId == PlayerId.PLAYER_2) "$username (YOU) ⭕" else "OPPONENT ⭕"
                        else -> "PLAYER 2 ⭕"
                    }
                    Text(
                        text = p2Label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = PlayfulOrange
                    )
                    Text(
                        text = "${gameState.player2Score}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Turn / Status Banner
        val turnText = when {
            gameState.status is GameStatus.RoundWon -> {
                val winner = (gameState.status as GameStatus.RoundWon).winner
                if (selectedMode == GameMode.BLUETOOTH_1V1) {
                    if (winner == localPlayerId) "YOU WON THE ROUND! 🎉" else "OPPONENT SCORED! 🎉"
                } else if (winner == PlayerId.PLAYER_1) {
                    if (selectedMode == GameMode.SOLO) "YOU WON THE ROUND! 🎉" else "PLAYER 1 SCORED! 🎉"
                } else if (selectedMode == GameMode.SOLO) {
                    "BOT SCORED! 🤖"
                } else {
                    "PLAYER 2 SCORED! 🎉"
                }
            }
            gameState.status is GameStatus.RoundDraw -> "ROUND DRAW! 🤝"
            isBotThinking -> "BOT THINKING... 🤖"
            selectedMode == GameMode.BLUETOOTH_1V1 -> {
                if (gameState.activePlayer == localPlayerId) {
                    if (isMovementPhase) {
                        if (selectedCellIndex == null) "TAP YOUR MARK TO MOVE 🔄" else "TAP AN EMPTY SQUARE 🎯"
                    } else "YOUR TURN 🎯"
                } else "OPPONENT'S TURN 📱"
            }
            gameState.activePlayer == PlayerId.PLAYER_1 -> {
                if (selectedMode == GameMode.SOLO) {
                    if (isMovementPhase) {
                        if (selectedCellIndex == null) "TAP YOUR ❌ TO MOVE 🔄" else "MOVE TO AN EMPTY SQUARE 🎯"
                    } else "YOUR TURN ❌"
                } else {
                    if (isMovementPhase) {
                        if (selectedCellIndex == null) "P1: TAP ❌ TO MOVE 🔄" else "P1: TAP EMPTY SQUARE 🎯"
                    } else "PLAYER 1'S TURN ❌"
                }
            }
            else -> {
                if (selectedMode == GameMode.SOLO) "BOT'S TURN ⭕"
                else {
                    if (isMovementPhase) {
                        if (selectedCellIndex == null) "P2: TAP ⭕ TO MOVE 🔄" else "P2: TAP EMPTY SQUARE 🎯"
                    } else "PLAYER 2'S TURN ⭕"
                }
            }
        }

        Text(
            text = turnText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            ),
            color = when {
                gameState.status is GameStatus.RoundWon -> PlayfulGreen
                gameState.status is GameStatus.RoundDraw -> MaterialTheme.colorScheme.onSurfaceVariant
                gameState.activePlayer == PlayerId.PLAYER_1 -> PlayfulPurple
                else -> PlayfulOrange
            }
        )

        if (gameState.status is GameStatus.InProgress) {
            Spacer(modifier = Modifier.height(4.dp))
            val helperText = if (isMovementPhase) {
                if (selectedCellIndex == null) "Select one of your marks to move" else "Select an empty square to move into"
            } else {
                val marksLeft = 3 - activeMarksCount
                "Place mark ($marksLeft remaining before moving)"
            }
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3x3 Playful Grid
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, ToyboxBorder, RoundedCornerShape(28.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            val cellPlayer = gameState.board[index]
                            val isOwnMark = cellPlayer == gameState.activePlayer
                            val isSelected = selectedCellIndex == index
                            val isWinningCell = gameState.winningPattern?.contains(index) == true

                            val isCellClickable = if (!canInteract) false else {
                                if (isMovementPhase) {
                                    isOwnMark || (cellPlayer == null && selectedCellIndex != null)
                                } else {
                                    cellPlayer == null
                                }
                            }

                            val isValidMoveTarget = isMovementPhase && selectedCellIndex != null && cellPlayer == null

                            val cellBg = when {
                                isWinningCell -> PlayfulGreen.copy(alpha = 0.22f)
                                isSelected -> PlayfulPurple.copy(alpha = 0.22f)
                                isValidMoveTarget -> PlayfulPurple.copy(alpha = 0.10f)
                                cellPlayer != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.background
                            }

                            val borderColor = when {
                                isWinningCell -> PlayfulGreen
                                isSelected -> PlayfulPurple
                                isValidMoveTarget -> PlayfulPurple.copy(alpha = 0.6f)
                                isMovementPhase && isOwnMark && canInteract -> if (cellPlayer == PlayerId.PLAYER_1) PlayfulPurple else PlayfulOrange
                                else -> ToyboxBorder
                            }

                            val borderWidth = when {
                                isWinningCell -> 2.5.dp
                                isSelected -> 3.dp
                                isValidMoveTarget -> 2.dp
                                isMovementPhase && isOwnMark && canInteract -> 2.dp
                                else -> 1.5.dp
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(cellBg)
                                    .border(
                                        width = borderWidth,
                                        color = borderColor,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .bouncyClickable(
                                        enabled = isCellClickable,
                                        onClick = {
                                            if (isMovementPhase) {
                                                if (isOwnMark) {
                                                    selectedCellIndex = if (isSelected) null else index
                                                } else if (cellPlayer == null && selectedCellIndex != null) {
                                                    val from = selectedCellIndex!!
                                                    selectedCellIndex = null
                                                    onMove(XOMove(toIndex = index, fromIndex = from))
                                                }
                                            } else {
                                                if (cellPlayer == null) {
                                                    onMove(XOMove(toIndex = index))
                                                }
                                            }
                                        }
                                    )
                                    .testTag("xo_cell_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cellPlayer != null) {
                                    Text(
                                        text = if (cellPlayer == PlayerId.PLAYER_1) "❌" else "⭕",
                                        fontSize = 38.sp
                                    )
                                } else if (isValidMoveTarget) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(PlayfulPurple.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Next Round Button if round finished but match not finished
        if (isRoundFinished && !isMatchFinished) {
            ChunkyButton(
                text = "NEXT ROUND (${gameState.roundNumber + 1}/${gameState.targetScore}) ⏭️",
                onClick = onNextRound,
                backgroundColor = PlayfulPurple,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .testTag("xo_next_round_button")
            )
        }
    }

    // Match Won Celebration Dialog
    if (isMatchFinished) {
        val winner = (gameState.status as GameStatus.MatchWon).matchWinner
        val isP1Winner = winner == PlayerId.PLAYER_1
        val isTie = gameState.player1Score == gameState.player2Score
        val isLocal2Player = selectedMode == GameMode.LOCAL_1V1

        // In local 2-player mode, the device hosts both players; both Player 1 and Player 2 victories are celebrated as victories
        val isVictory = when {
            isTie -> false
            isLocal2Player -> true
            selectedMode == GameMode.BLUETOOTH_1V1 -> winner == localPlayerId
            else -> isP1Winner
        }

        val didLocalWin = if (selectedMode == GameMode.BLUETOOTH_1V1) {
            winner == localPlayerId
        } else {
            isP1Winner
        }

        AlertDialog(
            onDismissRequest = { /* Require action button */ },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Text(
                    text = if (isTie) "🤝" else if (isVictory) "🏆" else "😵",
                    fontSize = 46.sp
                )
            },
            title = {
                Text(
                    text = when {
                        isTie -> "MATCH TIED! 🤝"
                        selectedMode == GameMode.BLUETOOTH_1V1 -> if (didLocalWin) "YOU WON! 🎉" else "OPPONENT WINS! 👑"
                        isP1Winner -> if (selectedMode == GameMode.SOLO) "YOU WON! 🎉" else "PLAYER 1 WINS! 👑"
                        else -> if (selectedMode == GameMode.SOLO) "BOT WON! 🤖" else "PLAYER 2 WINS! 👑"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Final (${gameState.targetScore} Rounds): ${gameState.player1Score} - ${gameState.player2Score}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = PlayfulPurple
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Match saved to your Trophy Room! 🏆",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "REMATCH 🔄",
                    onClick = onRematch,
                    backgroundColor = PlayfulPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rematch_button")
                )
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onChangeMode) {
                        Text("Modes", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onExit) {
                        Text("Home 🏠", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}
