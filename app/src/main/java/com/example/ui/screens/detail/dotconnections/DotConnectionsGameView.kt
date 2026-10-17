package com.example.ui.screens.detail.dotconnections

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotConnectionsBot
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotDifficulty
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotMoveResult
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.core.engine.dotconnections.LinePlacement
import com.example.core.engine.dotconnections.DotBox
import com.example.core.engine.dotconnections.DotConnectionsGameState
import com.example.core.multiplayer.MultiplayerLobbyState
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DotConnectionsGameView(
    selectedMode: GameMode,
    difficulty: DotDifficulty = DotDifficulty.MEDIUM,
    boardSize: DotBoardSize = DotBoardSize.MEDIUM,
    initialPlayerCount: Int = 2,
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

    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)

    // In Nearby mode, number of players is based on connected players (2 to 5)
    val nearbyPlayers = (lobbyState as? MultiplayerLobbyState.InGame)?.players
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.players ?: emptyList())
    val initialNearbyCount = if (nearbyPlayers.isNotEmpty()) nearbyPlayers.size else initialPlayerCount
    var syncedPlayerCount by remember { mutableIntStateOf(initialNearbyCount.coerceIn(2, 5)) }

    val activePlayerCount = when {
        selectedMode == GameMode.SOLO -> 2
        isNearbyMode -> syncedPlayerCount.coerceIn(2, 5)
        else -> initialPlayerCount.coerceIn(2, 5)
    }

    val localPlayerRole = (lobbyState as? MultiplayerLobbyState.InGame)?.localPlayer?.role
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.localPlayer?.role ?: "PLAYER_1")
    val localPlayer = if (isNearbyMode) {
        DotPlayer.entries.firstOrNull { it.name.equals(localPlayerRole, ignoreCase = true) } ?: DotPlayer.PLAYER_1
    } else {
        DotPlayer.PLAYER_1
    }

    // Player custom display names map
    val playerDisplayNames = remember { mutableStateMapOf<DotPlayer, String>() }

    // Sync player display names from nearbyPlayers
    LaunchedEffect(nearbyPlayers) {
        if (isNearbyMode) {
            nearbyPlayers.forEachIndexed { index, p ->
                val playerEnum = DotPlayer.fromOrdinal(index)
                if (p.name.isNotBlank()) {
                    playerDisplayNames[playerEnum] = p.name
                }
            }
        }
    }

    val engine = remember {
        DotConnectionsEngine(boardSize, activePlayerCount)
    }
    var gameState by remember { mutableStateOf(engine.state) }

    LaunchedEffect(boardSize, initialPlayerCount) {
        if (!isNearbyMode) {
            engine.resetGame(boardSize, initialPlayerCount)
            gameState = engine.state
        }
    }

    var isBotThinking by remember { mutableStateOf(false) }
    var showExtraTurnBanner by remember { mutableStateOf(false) }
    var extraTurnPlayer by remember { mutableStateOf<DotPlayer?>(null) }
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
        engine.resetGame(boardSize, activePlayerCount)
        gameState = engine.state
        isBotThinking = false
        showExtraTurnBanner = false
        extraTurnPlayer = null
        matchRecorded = false
        gameSessionId++
        vibrate()

        if (isNearbyMode && isHost) {
            multiplayerManager.sendAction("DOT_SYNC", serializeDotState(engine.state, playerDisplayNames))
        }
    }

    // Broadcasts authoritative state to all connected devices from host
    fun broadcastState() {
        if (isNearbyMode && isHost) {
            multiplayerManager.sendAction("DOT_SYNC", serializeDotState(gameState, playerDisplayNames))
        }
    }

    LaunchedEffect(nearbyPlayers.size) {
        if (isNearbyMode && nearbyPlayers.size in 2..5) {
            syncedPlayerCount = nearbyPlayers.size
            if (isHost && engine.state.placedEdges.isEmpty()) {
                engine.resetGame(boardSize, nearbyPlayers.size)
                gameState = engine.state
                broadcastState()
            }
        }
    }

    fun handleUserMove(edge: DotEdge) {
        if (isBotThinking || gameState.isGameOver) return
        if (selectedMode == GameMode.SOLO && gameState.activePlayer != DotPlayer.PLAYER_1) return
        if (isNearbyMode && gameState.activePlayer != localPlayer) return
        if (gameState.isEdgeOccupied(edge)) return

        if (isNearbyMode) {
            if (isHost) {
                // Host makes authoritative move directly
                val result = engine.makeMove(edge, DotPlayer.PLAYER_1)
                if (result is DotMoveResult.Success) {
                    gameState = result.newState
                    if (result.extraTurn) {
                        showExtraTurnBanner = true
                        extraTurnPlayer = DotPlayer.PLAYER_1
                        vibrate(isBig = true)
                    } else {
                        showExtraTurnBanner = false
                        extraTurnPlayer = null
                        vibrate(isBig = false)
                    }
                    broadcastState()
                }
            } else {
                // Client requests move from authoritative host
                val payload = JSONObject().apply {
                    put("player", localPlayer.name)
                    put("ori", edge.orientation.name)
                    put("r", edge.row)
                    put("c", edge.col)
                }.toString()
                multiplayerManager.sendAction("DOT_REQUEST_EDGE", payload)
            }
        } else {
            // Local mode (2-5 players) or Solo mode
            val result = engine.makeMove(edge, gameState.activePlayer)
            if (result is DotMoveResult.Success) {
                gameState = result.newState
                if (result.extraTurn) {
                    showExtraTurnBanner = true
                    extraTurnPlayer = gameState.activePlayer
                    vibrate(isBig = true)
                } else {
                    showExtraTurnBanner = false
                    extraTurnPlayer = null
                    vibrate(isBig = false)
                }
            }
        }
    }

    // Listen for incoming actions in Nearby Mode
    LaunchedEffect(isNearbyMode, isHost) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "DOT_REQUEST_EDGE" -> {
                        if (isHost) {
                            try {
                                val json = JSONObject(action.payload)
                                val playerName = json.getString("player")
                                val requestedPlayer = DotPlayer.valueOf(playerName)
                                val ori = EdgeOrientation.valueOf(json.getString("ori"))
                                val r = json.getInt("r")
                                val c = json.getInt("c")
                                val edge = DotEdge(ori, r, c)

                                // Authoritative check on Host: player must be active and move must be legal
                                if (requestedPlayer == gameState.activePlayer &&
                                    !gameState.isGameOver &&
                                    !gameState.isEdgeOccupied(edge)
                                ) {
                                    val result = engine.makeMove(edge, requestedPlayer)
                                    if (result is DotMoveResult.Success) {
                                        gameState = result.newState
                                        if (result.extraTurn) {
                                            showExtraTurnBanner = true
                                            extraTurnPlayer = requestedPlayer
                                            vibrate(isBig = true)
                                        } else {
                                            showExtraTurnBanner = false
                                            extraTurnPlayer = null
                                            vibrate(isBig = false)
                                        }
                                        broadcastState()
                                    }
                                } else {
                                    // Invalid move or out-of-turn: re-broadcast authoritative state
                                    broadcastState()
                                }
                            } catch (e: Exception) {
                                // Ignore malformed action
                            }
                        }
                    }
                    "DOT_REQUEST_SYNC" -> {
                        if (isHost) {
                            broadcastState()
                        }
                    }
                    "DOT_SYNC" -> {
                        val parsed = deserializeDotState(action.payload)
                        if (parsed != null) {
                            val (syncedState, syncedNames) = parsed
                            syncedPlayerCount = syncedState.playerCount
                            engine.syncState(syncedState)
                            gameState = syncedState
                            syncedNames.forEach { (p, name) -> playerDisplayNames[p] = name }
                            if (syncedState.lastCompletedBoxes.isNotEmpty()) {
                                showExtraTurnBanner = true
                                extraTurnPlayer = syncedState.activePlayer
                                vibrate(isBig = true)
                            } else {
                                showExtraTurnBanner = false
                                extraTurnPlayer = null
                            }
                        }
                    }
                    "DOT_REMATCH" -> {
                        engine.resetGame(boardSize, activePlayerCount)
                        gameState = engine.state
                        isBotThinking = false
                        showExtraTurnBanner = false
                        extraTurnPlayer = null
                        matchRecorded = false
                        gameSessionId++
                        vibrate()
                        if (isHost) {
                            broadcastState()
                        }
                    }
                }
            }
        }
    }

    // Proactive initial sync on launch / reconnect
    LaunchedEffect(isNearbyMode, isHost) {
        if (isNearbyMode) {
            if (isHost) {
                delay(120)
                broadcastState()
            } else {
                delay(150)
                multiplayerManager.sendAction("DOT_REQUEST_SYNC", localPlayer.name)
            }
        }
    }

    // Autonomous Bot in VS BOT mode: continues playing consecutively as long as it earns extra turns
    LaunchedEffect(gameState.activePlayer, gameState.isGameOver, selectedMode, gameSessionId) {
        if (selectedMode != GameMode.SOLO) return@LaunchedEffect

        while (gameState.activePlayer == DotPlayer.PLAYER_2 && !gameState.isGameOver) {
            isBotThinking = true
            delay(420)
            val botMove = DotConnectionsBot.decideMove(gameState, difficulty)
            if (botMove != null) {
                val result = engine.makeMove(botMove, DotPlayer.PLAYER_2)
                if (result is DotMoveResult.Success) {
                    gameState = result.newState
                    if (result.extraTurn) {
                        showExtraTurnBanner = true
                        extraTurnPlayer = DotPlayer.PLAYER_2
                        vibrate(isBig = true)
                        delay(400)
                    } else {
                        showExtraTurnBanner = false
                        extraTurnPlayer = null
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
            val currentWinner = gameState.winner
            val winnerName = when {
                currentWinner != null -> currentWinner.name
                gameState.isDraw -> "DRAW"
                else -> "DRAW"
            }

            viewModel.repository.recordMatch(
                gameId = "dot_connections",
                gameTitle = "Dot Connections",
                mode = selectedMode.name,
                player1Score = gameState.getScore(DotPlayer.PLAYER_1),
                player2Score = gameState.getScore(DotPlayer.PLAYER_2),
                winner = winnerName,
                durationSeconds = 90
            )
        }
    }

    BackHandler {
        onExit()
    }

    fun getPlayerDisplayName(player: DotPlayer): String {
        return when {
            selectedMode == GameMode.SOLO -> {
                if (player == DotPlayer.PLAYER_1) "YOU" else "BOT (${difficulty.title})"
            }
            isNearbyMode -> {
                if (player == localPlayer) {
                    "${playerDisplayNames[player] ?: player.displayName} (YOU)"
                } else {
                    playerDisplayNames[player] ?: player.displayName
                }
            }
            else -> {
                player.displayName
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Controls Bar: Back, Title / Grid Info, Help, Restart
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
                    text = "${boardSize.dots}×${boardSize.dots} GRID • $activePlayerCount PLAYERS",
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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Compact Scalable Player Scoreboard (2 to 5 players)
        val participatingPlayers = gameState.participatingPlayers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (participatingPlayers.size > 2) Modifier.horizontalScroll(rememberScrollState())
                    else Modifier
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            participatingPlayers.forEach { player ->
                val isTurn = gameState.activePlayer == player && !gameState.isGameOver
                val isNext = gameState.activePlayer.next(gameState.playerCount) == player &&
                    !gameState.isGameOver && participatingPlayers.size > 2
                PlayerScoreCard(
                    player = player,
                    name = getPlayerDisplayName(player),
                    score = gameState.getScore(player),
                    isTurn = isTurn,
                    isNext = isNext,
                    modifier = if (participatingPlayers.size <= 2) Modifier.weight(1f) else Modifier.widthIn(min = 115.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Status Pill: Highlights Active Player's Turn and indicates Next Player
        val activePlayer = gameState.activePlayer
        val nextPlayer = activePlayer.next(gameState.playerCount)
        when {
            gameState.isGameOver -> {
                val currentWinner = gameState.winner
                val winnerText = when {
                    currentWinner != null -> {
                        val winnerName = getPlayerDisplayName(currentWinner)
                        "${currentWinner.emoji} $winnerName WON! 🏆"
                    }
                    else -> "IT'S A DRAW! 🤝"
                }
                PlayfulPill(
                    text = winnerText,
                    backgroundColor = PlayfulYellow.copy(alpha = 0.22f),
                    textColor = Color(0xFFD97706)
                )
            }
            showExtraTurnBanner -> {
                val playerLabel = extraTurnPlayer?.let { getPlayerDisplayName(it) } ?: "PLAYER"
                PlayfulPill(
                    text = "${extraTurnPlayer?.emoji ?: "🎁"} $playerLabel COMPLETED BOX! EXTRA TURN! 🎁",
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
                    text = if (activePlayer == DotPlayer.PLAYER_1) "YOUR TURN • CONNECT 2 DOTS" else "BOT'S TURN 🤖",
                    backgroundColor = activePlayer.composeColor.copy(alpha = 0.14f),
                    textColor = activePlayer.composeColor
                )
            }
            isNearbyMode -> {
                val isMyTurn = activePlayer == localPlayer
                val turnText = if (isMyTurn) {
                    "YOUR TURN (${activePlayer.displayName.uppercase()}) • NEXT: ${nextPlayer.displayName.uppercase()}"
                } else {
                    "${activePlayer.emoji} ${getPlayerDisplayName(activePlayer).uppercase()}'S TURN • NEXT: ${nextPlayer.displayName.uppercase()}"
                }
                PlayfulPill(
                    text = turnText,
                    backgroundColor = activePlayer.composeColor.copy(alpha = 0.14f),
                    textColor = activePlayer.composeColor
                )
            }
            else -> {
                val turnText = "${activePlayer.emoji} ${activePlayer.displayName.uppercase()}'S TURN • NEXT: ${nextPlayer.emoji} ${nextPlayer.displayName.uppercase()}"
                PlayfulPill(
                    text = turnText,
                    backgroundColor = activePlayer.composeColor.copy(alpha = 0.14f),
                    textColor = activePlayer.composeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Central Game Board
        val isUserInteractive = !gameState.isGameOver && !isBotThinking &&
            (selectedMode != GameMode.SOLO || gameState.activePlayer == DotPlayer.PLAYER_1) &&
            (!isNearbyMode || gameState.activePlayer == localPlayer)

        DotConnectionsBoardView(
            state = gameState,
            onEdgeSelected = { handleUserMove(it) },
            isInteractive = isUserInteractive,
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
            icon = { Text(text = "🔲", fontSize = 42.sp) },
            title = {
                Text(
                    text = "How to Play (2–5 Players)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Connect two neighboring dots with a line. Players take turns in order (🔴 Red -> 🟣 Purple -> 🔵 Blue -> 🟤 Brown -> 🟢 Green).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "2. Complete the 4th side of any box to claim it and earn an EXTRA TURN! You can claim multiple boxes in a single move.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlayfulGreen
                    )
                    Text(
                        text = "3. If no box is completed, the turn passes to the next player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "4. Claim the most boxes when the grid is filled to win!",
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

    // End Game Dialog with Player Rankings
    if (gameState.isGameOver) {
        val rankings = gameState.rankings
        val isTie = gameState.isDraw
        val winner = gameState.winner

        val titleText = when {
            isTie -> "🤝 IT'S A TIE!"
            selectedMode == GameMode.SOLO && winner == DotPlayer.PLAYER_1 -> "🏆 YOU WIN!"
            selectedMode == GameMode.SOLO -> "🤖 BOT WINS!"
            isNearbyMode && winner == localPlayer -> "🏆 YOU WIN!"
            isNearbyMode && winner != null -> "🏆 ${getPlayerDisplayName(winner).uppercase()} WINS!"
            winner != null -> "🏆 ${winner.displayName.uppercase()} WINS!"
            else -> "MATCH COMPLETE"
        }

        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Text(
                    text = if (isTie) "🤝" else "🏆",
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
                        text = if (isTie) "Equally matched scores! Multiple players tied for 1st place."
                        else "All boxes claimed! Final player rankings:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Final Player Rankings List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rankings.forEachIndexed { rankIndex, (player, score) ->
                            val isWinner = !isTie && rankIndex == 0
                            val medal = when (rankIndex) {
                                0 -> if (isTie) "🤝" else "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${rankIndex + 1}."
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isWinner) player.composeColor.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = medal,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(player.composeColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = getPlayerDisplayName(player),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isWinner) FontWeight.Black else FontWeight.Bold
                                        ),
                                        color = if (isWinner) player.composeColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "$score ${if (score == 1) "box" else "boxes"}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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

    // Nearby Mode Disconnected Alert Dialog
    if (isNearbyMode && lobbyState is MultiplayerLobbyState.Disconnected) {
        val reason = (lobbyState as MultiplayerLobbyState.Disconnected).reason
        AlertDialog(
            onDismissRequest = { /* user must choose an action */ },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text("🔌", fontSize = 42.sp) },
            title = {
                Text(
                    text = "Connection Lost",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = reason.ifBlank { "Lost connection to the session. The multiplayer game has ended." },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "Back to Modes 🎮",
                    onClick = {
                        multiplayerManager.disconnect()
                        onChangeMode()
                    },
                    backgroundColor = PlayfulPurple,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        multiplayerManager.disconnect()
                        onExit()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exit to Home 🏠", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun PlayerScoreCard(
    player: DotPlayer,
    name: String,
    score: Int,
    isTurn: Boolean,
    isNext: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    val playerColor = player.composeColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isTurn) playerColor.copy(alpha = 0.16f)
                else colors.surfaceVariant
            )
            .border(
                width = if (isTurn) 2.5.dp else if (isNext) 1.5.dp else 1.dp,
                color = if (isTurn) playerColor
                else if (isNext) playerColor.copy(alpha = 0.5f)
                else colors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(playerColor)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = if (isTurn) playerColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTurn) {
                    Text(
                        text = "TURN 🎯",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        fontSize = 10.sp,
                        color = playerColor
                    )
                } else if (isNext) {
                    Text(
                        text = "NEXT ⏱️",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        fontSize = 9.sp,
                        color = playerColor.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = player.symbol,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "$score",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Authoritative JSON State Serialization and Deserialization for Nearby Sync
// -------------------------------------------------------------------------

internal fun serializeDotState(
    state: DotConnectionsGameState,
    playerNames: Map<DotPlayer, String> = emptyMap()
): String {
    val json = JSONObject()
    json.put("boardSize", state.boardSize.name)
    json.put("playerCount", state.playerCount)
    json.put("activePlayer", state.activePlayer.name)
    json.put("extraTurn", state.lastCompletedBoxes.isNotEmpty())
    json.put("isGameOver", state.isGameOver)
    json.put("winner", state.winner?.name ?: "")
    json.put("isDraw", state.isDraw)
    json.put("lastEdge", state.lastMoveEdge?.let { "${it.orientation.name},${it.row},${it.col}" } ?: "")

    val edgesArray = JSONArray()
    for ((edge, placement) in state.placedEdges) {
        val edgeObj = JSONObject()
        edgeObj.put("ori", edge.orientation.name)
        edgeObj.put("r", edge.row)
        edgeObj.put("c", edge.col)
        edgeObj.put("owner", placement.owner.name)
        edgesArray.put(edgeObj)
    }
    json.put("edges", edgesArray)

    val boxesArray = JSONArray()
    for ((coord, box) in state.boxes) {
        val boxObj = JSONObject()
        boxObj.put("r", coord.first)
        boxObj.put("c", coord.second)
        boxObj.put("owner", box.owner?.name ?: "")
        boxesArray.put(boxObj)
    }
    json.put("boxes", boxesArray)

    val scoresObj = JSONObject()
    for (player in state.participatingPlayers) {
        scoresObj.put(player.name, state.getScore(player))
    }
    json.put("scores", scoresObj)

    val namesObj = JSONObject()
    for ((player, name) in playerNames) {
        namesObj.put(player.name, name)
    }
    json.put("names", namesObj)

    return json.toString()
}

internal fun deserializeDotState(jsonString: String): Pair<DotConnectionsGameState, Map<DotPlayer, String>>? {
    return try {
        val json = JSONObject(jsonString)
        val boardSize = DotBoardSize.valueOf(json.getString("boardSize"))
        val playerCount = json.optInt("playerCount", 2).coerceIn(2, 5)
        val activePlayer = DotPlayer.valueOf(json.getString("activePlayer"))
        val isGameOver = json.getBoolean("isGameOver")
        val winnerStr = json.optString("winner", "")
        val winner = if (winnerStr.isNotBlank()) DotPlayer.valueOf(winnerStr) else null
        val isDraw = json.optBoolean("isDraw", false)

        val lastEdgeStr = json.optString("lastEdge", "")
        val lastEdge = if (lastEdgeStr.isNotBlank()) {
            val parts = lastEdgeStr.split(",")
            if (parts.size == 3) {
                DotEdge(EdgeOrientation.valueOf(parts[0]), parts[1].toInt(), parts[2].toInt())
            } else null
        } else null

        val placedEdges = mutableMapOf<DotEdge, LinePlacement>()
        val edgesArray = json.getJSONArray("edges")
        for (i in 0 until edgesArray.length()) {
            val obj = edgesArray.getJSONObject(i)
            val ori = EdgeOrientation.valueOf(obj.getString("ori"))
            val r = obj.getInt("r")
            val c = obj.getInt("c")
            val owner = DotPlayer.valueOf(obj.getString("owner"))
            val edge = DotEdge(ori, r, c)
            placedEdges[edge] = LinePlacement(edge, owner)
        }

        val boxes = mutableMapOf<Pair<Int, Int>, DotBox>()
        val boxesArray = json.getJSONArray("boxes")
        for (i in 0 until boxesArray.length()) {
            val obj = boxesArray.getJSONObject(i)
            val r = obj.getInt("r")
            val c = obj.getInt("c")
            val ownerStr = obj.optString("owner", "")
            val owner = if (ownerStr.isNotBlank()) DotPlayer.valueOf(ownerStr) else null
            boxes[Pair(r, c)] = DotBox(r, c, owner)
        }

        val scores = mutableMapOf<DotPlayer, Int>()
        val scoresObj = json.optJSONObject("scores")
        if (scoresObj != null) {
            for (player in DotPlayer.activePlayers(playerCount)) {
                scores[player] = scoresObj.optInt(player.name, 0)
            }
        }

        val playerNames = mutableMapOf<DotPlayer, String>()
        val namesObj = json.optJSONObject("names")
        if (namesObj != null) {
            for (player in DotPlayer.activePlayers(playerCount)) {
                val n = namesObj.optString(player.name, "")
                if (n.isNotBlank()) playerNames[player] = n
            }
        }

        val p1 = scores[DotPlayer.PLAYER_1] ?: 0
        val p2 = scores[DotPlayer.PLAYER_2] ?: 0

        val state = DotConnectionsGameState(
            boardSize = boardSize,
            playerCount = playerCount,
            placedEdges = placedEdges,
            boxes = boxes,
            activePlayer = activePlayer,
            playerScores = scores,
            p1Score = p1,
            p2Score = p2,
            lastMoveEdge = lastEdge,
            isGameOver = isGameOver,
            winner = winner,
            isDraw = isDraw
        )
        Pair(state, playerNames)
    } catch (e: Exception) {
        null
    }
}
