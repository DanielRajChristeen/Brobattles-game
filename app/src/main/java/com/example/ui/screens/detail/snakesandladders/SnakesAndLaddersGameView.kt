package com.example.ui.screens.detail.snakesandladders

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.bot.SnakesAndLaddersBotOpponent
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.snakesandladders.SnakesAndLaddersBoard
import com.example.core.engine.snakesandladders.SnakesAndLaddersGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersMove
import com.example.core.engine.snakesandladders.deserializeSnakesState
import com.example.core.engine.snakesandladders.serializeSnakesState
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

@Composable
fun SnakesAndLaddersGameView(
    selectedMode: GameMode,
    targetScore: Int = 1,
    initialPlayerCount: Int = 2,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1

    val connectedPlayers = (lobbyState as? MultiplayerLobbyState.InGame)?.players
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.players ?: emptyList())
    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)

    val localRoleString = (lobbyState as? MultiplayerLobbyState.InGame)?.localPlayer?.role
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.localPlayer?.role ?: "PLAYER_1")
    val localPlayerId = try {
        PlayerId.valueOf(localRoleString)
    } catch (e: Exception) {
        if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
    }

    var localPlayerCount by remember(initialPlayerCount) {
        mutableIntStateOf(initialPlayerCount.coerceIn(2, 5))
    }
    var syncedPlayerCount by remember(connectedPlayers.size) {
        mutableIntStateOf(if (isNearbyMode && connectedPlayers.isNotEmpty()) connectedPlayers.size.coerceIn(2, 5) else 2)
    }

    val activePlayerCount = when (selectedMode) {
        GameMode.SOLO -> 2
        GameMode.BLUETOOTH_1V1 -> syncedPlayerCount.coerceIn(2, 5)
        else -> localPlayerCount
    }

    LaunchedEffect(connectedPlayers.size) {
        if (isNearbyMode && connectedPlayers.isNotEmpty()) {
            syncedPlayerCount = connectedPlayers.size.coerceIn(2, 5)
        }
    }

    val engine = remember(activePlayerCount) {
        SnakesAndLaddersGameEngine(initialTargetScore = 1, initialPlayerCount = activePlayerCount)
    }
    val bot = remember { SnakesAndLaddersBotOpponent() }
    var gameState by remember(activePlayerCount) { mutableStateOf(engine.state) }

    var showPlayerCountDialog by remember { mutableStateOf(false) }

    // Dice & Animation States
    var displayedDice by remember { mutableIntStateOf(1) }
    var isRolling by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }
    var actionBanner by remember { mutableStateOf<String?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showWinDialog by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }

    // Board Dimensions in Px
    var boardPixelSize by remember { mutableFloatStateOf(0f) }

    // Per-player board square and animation states
    var playerSquares by remember(activePlayerCount) {
        mutableStateOf(IntArray(activePlayerCount) { 1 })
    }
    var playerCoords by remember(activePlayerCount) {
        mutableStateOf(Array(activePlayerCount) { Offset.Zero })
    }
    var playerPoses by remember(activePlayerCount) {
        mutableStateOf(Array(activePlayerCount) { TokenPose.IDLE })
    }
    var playerLimbPhases by remember(activePlayerCount) {
        mutableStateOf(FloatArray(activePlayerCount) { 0f })
    }

    // Snake Wiggle Animation
    var activeSnakeHead by remember { mutableStateOf<Int?>(null) }
    var snakeWigglePhase by remember { mutableFloatStateOf(0f) }

    fun vibrate() {
        if (settings.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun getCellOffset(square: Int): Offset {
        val cellSize = if (boardPixelSize > 0f) boardPixelSize / 10f else 40f
        return SnakesAndLaddersBoard.getSquareCenter(square, cellSize)
    }

    // Set initial coordinates for all players when board size is available
    LaunchedEffect(boardPixelSize, activePlayerCount) {
        if (boardPixelSize > 0f) {
            val newCoords = Array(activePlayerCount) { i ->
                getCellOffset(playerSquares.getOrElse(i) { 1 })
            }
            playerCoords = newCoords
        }
    }

    // Movement routine for any player (P1 to P5) with geometric animations
    fun executeMovement(player: PlayerId, roll: Int) {
        val playerIndex = player.ordinal
        if (playerIndex >= activePlayerCount) return

        scope.launch {
            isMoving = true
            val cellSize = boardPixelSize / 10f
            val startSquare = playerSquares.getOrElse(playerIndex) { 1 }
            val targetSquare = startSquare + roll

            // 1. Check Exact Roll Rule
            if (targetSquare > SnakesAndLaddersBoard.TOTAL_SQUARES) {
                actionBanner = "Need exact roll to reach 100! 🎯"
                vibrate()
                delay(950)
                actionBanner = null
                engine.makeMove(SnakesAndLaddersMove(roll), player)
                gameState = engine.state
                isMoving = false

                if (isNearbyMode && isHost) {
                    multiplayerManager.sendAction("SNAKES_SYNC", serializeSnakesState(engine.state, roll))
                }
                return@launch
            }

            // 2. Step-by-Step Normal Movement (Square by Square)
            for (step in (startSquare + 1)..targetSquare) {
                val stepTarget = getCellOffset(step)
                val prevTarget = getCellOffset(step - 1)

                val hopFrames = 6
                for (f in 1..hopFrames) {
                    val progress = f.toFloat() / hopFrames
                    val currentX = prevTarget.x + (stepTarget.x - prevTarget.x) * progress
                    val currentY = prevTarget.y + (stepTarget.y - prevTarget.y) * progress

                    val updatedCoords = playerCoords.clone()
                    val updatedPoses = playerPoses.clone()
                    val updatedPhases = playerLimbPhases.clone()

                    updatedCoords[playerIndex] = Offset(currentX, currentY)
                    updatedPoses[playerIndex] = TokenPose.HOPPING
                    updatedPhases[playerIndex] = progress

                    playerCoords = updatedCoords
                    playerPoses = updatedPoses
                    playerLimbPhases = updatedPhases

                    delay(20)
                }

                val updatedSquares = playerSquares.clone()
                updatedSquares[playerIndex] = step
                playerSquares = updatedSquares

                vibrate()
                delay(40)
            }

            val restingPoses = playerPoses.clone()
            restingPoses[playerIndex] = TokenPose.IDLE
            playerPoses = restingPoses
            delay(180)

            // 3. Check Ladder: Climb directly along geometric path
            val ladder = SnakesAndLaddersBoard.LADDERS.find { it.bottom == targetSquare }
            if (ladder != null) {
                actionBanner = "Climbing ladder! 🪜"
                val climbingPoses = playerPoses.clone()
                climbingPoses[playerIndex] = TokenPose.CLIMBING
                playerPoses = climbingPoses
                vibrate()

                val climbSteps = 24
                for (s in 1..climbSteps) {
                    val t = s.toFloat() / climbSteps
                    val pt = SnakesAndLaddersBoard.getLadderPoint(ladder, t, cellSize)

                    val updatedCoords = playerCoords.clone()
                    val updatedPhases = playerLimbPhases.clone()
                    updatedCoords[playerIndex] = pt
                    updatedPhases[playerIndex] = t * 4f
                    playerCoords = updatedCoords
                    playerLimbPhases = updatedPhases

                    delay(25)
                }

                val updatedSquares = playerSquares.clone()
                val updatedCoords = playerCoords.clone()
                val finalPoses = playerPoses.clone()

                updatedSquares[playerIndex] = ladder.top
                updatedCoords[playerIndex] = getCellOffset(ladder.top)
                finalPoses[playerIndex] = TokenPose.IDLE

                playerSquares = updatedSquares
                playerCoords = updatedCoords
                playerPoses = finalPoses

                vibrate()
                delay(220)
                actionBanner = null
            }

            // 4. Check Snake: Slide along curved Bezier path
            val snake = SnakesAndLaddersBoard.SNAKES.find { it.head == targetSquare }
            if (snake != null) {
                actionBanner = "Oops! Snake slide! 🐍"
                activeSnakeHead = snake.head
                val slidingPoses = playerPoses.clone()
                slidingPoses[playerIndex] = TokenPose.SLIDING
                playerPoses = slidingPoses
                vibrate()

                val slideSteps = 26
                for (s in 1..slideSteps) {
                    val t = s.toFloat() / slideSteps
                    snakeWigglePhase = t * 14f
                    val pt = SnakesAndLaddersBoard.getSnakePoint(snake, t, cellSize, snakeWigglePhase)

                    val updatedCoords = playerCoords.clone()
                    val updatedPhases = playerLimbPhases.clone()
                    updatedCoords[playerIndex] = pt
                    updatedPhases[playerIndex] = t * 2f
                    playerCoords = updatedCoords
                    playerLimbPhases = updatedPhases

                    delay(25)
                }

                activeSnakeHead = null
                snakeWigglePhase = 0f

                val updatedSquares = playerSquares.clone()
                val updatedCoords = playerCoords.clone()
                val finalPoses = playerPoses.clone()

                updatedSquares[playerIndex] = snake.tail
                updatedCoords[playerIndex] = getCellOffset(snake.tail)
                finalPoses[playerIndex] = TokenPose.IDLE

                playerSquares = updatedSquares
                playerCoords = updatedCoords
                playerPoses = finalPoses

                vibrate()
                delay(220)
                actionBanner = null
            }

            // 5. Update Game Engine
            engine.makeMove(SnakesAndLaddersMove(roll), player)
            gameState = engine.state
            isMoving = false

            if (isNearbyMode && isHost) {
                multiplayerManager.sendAction("SNAKES_SYNC", serializeSnakesState(engine.state, roll))
            }

            // 6. Check Win Condition
            if (gameState.status is GameStatus.MatchWon) {
                val celebratingPoses = playerPoses.clone()
                celebratingPoses[playerIndex] = TokenPose.CELEBRATING
                playerPoses = celebratingPoses

                delay(1300)
                showWinDialog = true
                if (!matchRecorded) {
                    matchRecorded = true
                    viewModel.repository.recordMatch(
                        gameId = "snakes_and_ladders",
                        gameTitle = "Snakes & Ladders",
                        mode = selectedMode.name,
                        player1Score = if (player == PlayerId.PLAYER_1) 1 else 0,
                        player2Score = if (player == PlayerId.PLAYER_2) 1 else 0,
                        winner = player.displayName,
                        durationSeconds = 60
                    )
                }
            }
        }
    }

    // Authoritative Dice Roll Execution
    fun handleDiceRoll(player: PlayerId) {
        if (player != gameState.activePlayer) return
        if (isRolling || isMoving || gameState.status !is GameStatus.InProgress) return

        if (isNearbyMode) {
            if (isHost) {
                // Host rolls authoritatively
                val rollResult = Random.nextInt(1, 7)
                scope.launch {
                    displayedDice = rollResult
                    isRolling = true
                    vibrate()

                    val rollJson = JSONObject().apply {
                        put("player", player.name)
                        put("roll", rollResult)
                    }.toString()
                    multiplayerManager.sendAction("SNAKES_ROLL_EXEC", rollJson)

                    delay(860)
                    isRolling = false
                    vibrate()
                    delay(150)
                    executeMovement(player, rollResult)
                }
            } else {
                // Client requests roll from authoritative host
                multiplayerManager.sendAction("SNAKES_REQUEST_ROLL", localPlayerId.name)
            }
            return
        }

        // Local & Solo Modes
        scope.launch {
            val rollResult = Random.nextInt(1, 7)
            displayedDice = rollResult
            isRolling = true
            vibrate()

            delay(860)
            isRolling = false
            vibrate()
            delay(150)

            executeMovement(player, rollResult)
        }
    }

    // Reset game handler
    fun resetBoard() {
        engine.resetGame(1)
        gameState = engine.state
        playerSquares = IntArray(activePlayerCount) { 1 }
        val newCoords = Array(activePlayerCount) { getCellOffset(1) }
        playerCoords = newCoords
        playerPoses = Array(activePlayerCount) { TokenPose.IDLE }
        playerLimbPhases = FloatArray(activePlayerCount) { 0f }
        actionBanner = null
        showWinDialog = false
        matchRecorded = false
        vibrate()
    }

    // Listen for incoming actions in Nearby Mode
    LaunchedEffect(isNearbyMode, activePlayerCount) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "SNAKES_REQUEST_ROLL" -> {
                        if (isHost) {
                            val requestedPlayerName = action.payload
                            val requestedPlayer = try { PlayerId.valueOf(requestedPlayerName) } catch (e: Exception) { null }
                            if (requestedPlayer != null &&
                                requestedPlayer == gameState.activePlayer &&
                                !isRolling &&
                                !isMoving &&
                                gameState.status is GameStatus.InProgress
                            ) {
                                val rollResult = Random.nextInt(1, 7)
                                scope.launch {
                                    displayedDice = rollResult
                                    isRolling = true
                                    vibrate()

                                    val rollJson = JSONObject().apply {
                                        put("player", requestedPlayer.name)
                                        put("roll", rollResult)
                                    }.toString()
                                    multiplayerManager.sendAction("SNAKES_ROLL_EXEC", rollJson)

                                    delay(860)
                                    isRolling = false
                                    vibrate()
                                    delay(150)
                                    executeMovement(requestedPlayer, rollResult)
                                }
                            }
                        }
                    }
                    "SNAKES_ROLL_EXEC" -> {
                        if (!isHost) {
                            try {
                                val json = JSONObject(action.payload)
                                val rollResult = json.getInt("roll")
                                val playerName = json.getString("player")
                                val movingPlayer = PlayerId.valueOf(playerName)

                                scope.launch {
                                    displayedDice = rollResult
                                    isRolling = true
                                    vibrate()
                                    delay(860)
                                    isRolling = false
                                    vibrate()
                                    delay(150)
                                    executeMovement(movingPlayer, rollResult)
                                }
                            } catch (e: Exception) {
                                // ignore parse error
                            }
                        }
                    }
                    "SNAKES_REQUEST_SYNC" -> {
                        if (isHost) {
                            multiplayerManager.sendAction("SNAKES_SYNC", serializeSnakesState(engine.state, displayedDice))
                        }
                    }
                    "SNAKES_SYNC" -> {
                        val synced = deserializeSnakesState(action.payload)
                        if (synced != null) {
                            if (synced.playerCount != syncedPlayerCount) {
                                syncedPlayerCount = synced.playerCount
                            }
                            engine.syncState(synced)
                            gameState = synced
                            val updatedSquares = synced.playerPositions.toIntArray()
                            playerSquares = updatedSquares
                            playerCoords = Array(synced.playerCount) { i -> getCellOffset(updatedSquares.getOrElse(i) { 1 }) }
                        }
                    }
                    "SNAKES_REMATCH" -> {
                        resetBoard()
                    }
                }
            }
        }
    }

    // Client requests initial authoritative state when joining or reconnecting
    LaunchedEffect(isNearbyMode, isHost) {
        if (isNearbyMode && !isHost) {
            delay(150)
            multiplayerManager.sendAction("SNAKES_REQUEST_SYNC", localPlayerId.name)
        }
    }

    // Autonomous Bot Turn in Solo Mode
    LaunchedEffect(selectedMode, gameState.activePlayer, gameState.status, isRolling, isMoving) {
        if (selectedMode == GameMode.SOLO &&
            gameState.activePlayer == PlayerId.PLAYER_2 &&
            gameState.status is GameStatus.InProgress &&
            !isRolling &&
            !isMoving
        ) {
            delay(600)
            handleDiceRoll(PlayerId.PLAYER_2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. TOP NAV BAR ---
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
                    .testTag("snakes_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Modes",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Current Turn Status Pill
            val currentActivePlayer = gameState.activePlayer
            val turnText = when {
                gameState.status is GameStatus.MatchWon -> "${(gameState.status as GameStatus.MatchWon).matchWinner.displayName.uppercase()} WINS! 🏆"
                isRolling -> "ROLLING... 🎲"
                isMoving -> "MOVING... 🚶"
                selectedMode == GameMode.SOLO && currentActivePlayer == PlayerId.PLAYER_1 -> "YOUR TURN 🎲"
                selectedMode == GameMode.SOLO && currentActivePlayer == PlayerId.PLAYER_2 -> "BOT'S TURN 🤖"
                isNearbyMode && currentActivePlayer == localPlayerId -> "YOUR TURN 🎲"
                isNearbyMode -> "${currentActivePlayer.displayName}'S TURN 🎲"
                else -> "${SnakesTokens.getEmoji(currentActivePlayer)} ${currentActivePlayer.displayName}'S TURN"
            }

            PlayfulPill(
                text = turnText,
                backgroundColor = PlayfulGreen.copy(alpha = 0.14f),
                textColor = PlayfulGreen
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Local Mode: Player Count Quick Switcher (2 to 5 players)
                if (selectedMode == GameMode.LOCAL_1V1) {
                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, ToyboxBorder, RoundedCornerShape(14.dp))
                            .bouncyClickable { showPlayerCountDialog = true }
                            .padding(horizontal = 10.dp)
                            .testTag("snakes_local_player_count_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$activePlayerCount P",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Help (?)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, ToyboxBorder, CircleShape)
                        .bouncyClickable { showHelpDialog = true }
                        .testTag("snakes_help_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("❓", fontSize = 18.sp)
                }

                // Restart (🔄)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, ToyboxBorder, CircleShape)
                        .bouncyClickable {
                            resetBoard()
                            if (isNearbyMode) {
                                multiplayerManager.sendAction("SNAKES_REMATCH", "")
                            }
                        }
                        .testTag("snakes_restart_button"),
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

        // --- 2. MULTI-PLAYER POSITION & STATUS HUD (Supports 2 to 5 Players) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (actionBanner != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayfulPill(
                            text = actionBanner ?: "",
                            backgroundColor = PlayfulGreen.copy(alpha = 0.15f),
                            textColor = PlayfulGreen
                        )
                    }
                }

                // Player Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until activePlayerCount) {
                        val playerEnum = PlayerId.entries[i]
                        val isTurn = gameState.activePlayer == playerEnum && gameState.status is GameStatus.InProgress
                        val square = playerSquares.getOrElse(i) { 1 }
                        val tokenColor = SnakesTokens.getColor(i)
                        val tokenEmoji = SnakesTokens.getEmoji(i)

                        val playerLabel = when {
                            selectedMode == GameMode.SOLO -> if (i == 0) "YOU" else "BOT"
                            isNearbyMode -> {
                                val netPlayer = connectedPlayers.getOrNull(i)
                                if (playerEnum == localPlayerId) "YOU" else netPlayer?.name?.take(7) ?: "P${i + 1}"
                            }
                            else -> if (activePlayerCount <= 3) "Player ${i + 1}" else "P${i + 1}"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isTurn) tokenColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    width = if (isTurn) 2.dp else 1.dp,
                                    color = if (isTurn) tokenColor else ToyboxBorder.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag("player_status_badge_$i"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = tokenEmoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = playerLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        fontSize = 10.sp,
                                        color = if (isTurn) tokenColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sq. $square",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isTurn) {
                                    Text(
                                        text = "TURN 🎲",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        fontSize = 8.sp,
                                        color = tokenColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 3. RICH BOARD GAME VIEW WITH COLLISION-SAFE TOKEN ARRANGEMENT ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.5.dp, ToyboxBorder, RoundedCornerShape(22.dp))
        ) {
            val boardPx = constraints.maxWidth.toFloat()
            boardPixelSize = boardPx
            val cellSize = boardPx / 10f

            // Group idle tokens sharing the same square and apply offsets
            val idlePlayersBySquare = (0 until activePlayerCount)
                .filter { playerPoses[it] == TokenPose.IDLE }
                .groupBy { playerSquares.getOrElse(it) { 1 } }

            val visualTokens = (0 until activePlayerCount).map { i ->
                val square = playerSquares.getOrElse(i) { 1 }
                val pose = playerPoses[i]
                val limbPhase = playerLimbPhases[i]

                val pos = if (pose == TokenPose.IDLE) {
                    val group = idlePlayersBySquare[square] ?: listOf(i)
                    val idxInGroup = group.indexOf(i)
                    val offset = getPlayerOffsetOnSquare(idxInGroup, group.size, cellSize)
                    (playerCoords.getOrNull(i) ?: getCellOffset(square)) + offset
                } else {
                    playerCoords.getOrNull(i) ?: getCellOffset(square)
                }

                PlayerTokenVisual(
                    playerIndex = i,
                    position = pos,
                    pose = pose,
                    limbPhase = limbPhase,
                    color = SnakesTokens.getColor(i)
                )
            }

            SnakesAndLaddersBoardView(
                playerTokens = visualTokens,
                activeSnakeHead = activeSnakeHead,
                snakeWigglePhase = snakeWigglePhase,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. 3D-STYLE PHYSICAL DICE CONTROL ---
        val canRoll = !isRolling && !isMoving &&
                gameState.status is GameStatus.InProgress &&
                (when {
                    selectedMode == GameMode.SOLO -> gameState.activePlayer == PlayerId.PLAYER_1
                    isNearbyMode -> gameState.activePlayer == localPlayerId
                    else -> true
                })

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Real3DCubeDie(
                targetFace = displayedDice,
                isRolling = isRolling,
                canRoll = canRoll,
                onClick = { handleDiceRoll(gameState.activePlayer) },
                modifier = Modifier.testTag("snakes_dice_button")
            )

            Spacer(modifier = Modifier.height(6.dp))

            val currentActive = gameState.activePlayer
            val promptText = when {
                isRolling -> "ROLLING... 🎲"
                isMoving -> "MOVING... 🚶"
                canRoll -> {
                    if (isNearbyMode) "TAP DICE TO ROLL! 🎲"
                    else if (selectedMode == GameMode.SOLO) "TAP DICE TO ROLL! 🎲"
                    else "TAP DICE TO ROLL (${currentActive.displayName}) 🎲"
                }
                selectedMode == GameMode.SOLO && currentActive == PlayerId.PLAYER_2 -> "Bot is rolling... 🤖"
                isNearbyMode -> "Waiting for ${currentActive.displayName} to roll... 🎲"
                else -> "WAITING..."
            }

            Text(
                text = promptText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = if (canRoll) PlayfulGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Help Dialog (?)
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text("🎲", fontSize = 42.sp) },
            title = {
                Text(
                    text = "HOW TO PLAY",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Supports 2 to 5 players locally and over Nearby Wi-Fi!", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("• Roll tokens in exact order: 🔴 P1, 🟣 P2, 🔵 P3, 🟤 P4, 🟢 P5.", style = MaterialTheme.typography.bodyMedium)
                    Text("• 🪜 Climb ladders straight to the top!", style = MaterialTheme.typography.bodyMedium, color = PlayfulGreen)
                    Text("• 🐍 Slide down snakes all the way to the tail!", style = MaterialTheme.typography.bodyMedium, color = PlayfulOrange)
                    Text("• First player to reach square 100 wins! (Exact roll required).", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "Got it! 🚀",
                    onClick = { showHelpDialog = false },
                    backgroundColor = PlayfulGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // Victory Dialog
    if (showWinDialog) {
        val winner = (gameState.status as? GameStatus.MatchWon)?.matchWinner ?: PlayerId.PLAYER_1
        val winnerIndex = winner.ordinal
        val winnerEmoji = SnakesTokens.getEmoji(winnerIndex)
        val isLocalWinner = when {
            selectedMode == GameMode.SOLO -> winner == PlayerId.PLAYER_1
            isNearbyMode -> winner == localPlayerId
            else -> false
        }

        AlertDialog(
            onDismissRequest = { /* require user action */ },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text(if (isLocalWinner) "🏆" else "👑", fontSize = 48.sp) },
            title = {
                val winTitle = when {
                    selectedMode == GameMode.SOLO -> if (winner == PlayerId.PLAYER_1) "YOU WIN! 🎉" else "BOT WON! 🤖"
                    isNearbyMode -> if (winner == localPlayerId) "YOU WIN! 🎉" else "${winner.displayName} WINS! 👑"
                    else -> "$winnerEmoji ${winner.displayName} WINS! 👑"
                }
                Text(
                    text = winTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
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
                        text = "Reached square 100! 🏁",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = PlayfulGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Winner: $winnerEmoji ${winner.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "PLAY AGAIN 🔄",
                    onClick = {
                        resetBoard()
                        if (isNearbyMode) {
                            multiplayerManager.sendAction("SNAKES_REMATCH", "")
                        }
                    },
                    backgroundColor = PlayfulGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snakes_rematch_button")
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

    // Local Mode: Number of Players Selection Dialog
    if (showPlayerCountDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerCountDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text("👥", fontSize = 42.sp) },
            title = {
                Text(
                    text = "NUMBER OF PLAYERS",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    (2..5).forEach { count ->
                        val isSelected = activePlayerCount == count
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PlayfulGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) PlayfulGreen else ToyboxBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable {
                                    localPlayerCount = count
                                    showPlayerCountDialog = false
                                    resetBoard()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("snakes_player_count_dialog_option_$count")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = (0 until count).joinToString(" ") { SnakesTokens.getEmoji(it) },
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "$count Players",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) PlayfulGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = PlayfulGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerCountDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Nearby Mode: Host / Connection Lost Alert
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
                        text = reason.ifBlank { "Lost connection to the host. The game session has ended." },
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
