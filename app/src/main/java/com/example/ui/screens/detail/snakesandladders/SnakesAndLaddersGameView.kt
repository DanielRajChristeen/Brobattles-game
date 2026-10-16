package com.example.ui.screens.detail.snakesandladders

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.random.Random

@Composable
fun SnakesAndLaddersGameView(
    selectedMode: GameMode,
    targetScore: Int = 1, // Single game mode
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val engine = remember { SnakesAndLaddersGameEngine(initialTargetScore = 1) }
    val bot = remember { SnakesAndLaddersBotOpponent() }
    var gameState by remember { mutableStateOf(engine.state) }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayer = if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2

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

    // Visual Screen Coordinates for P1 & P2
    var p1Coord by remember { mutableStateOf<Offset?>(null) }
    var p2Coord by remember { mutableStateOf<Offset?>(null) }
    var p1Square by remember { mutableIntStateOf(gameState.p1Position) }
    var p2Square by remember { mutableIntStateOf(gameState.p2Position) }

    // Stick-Figure Animation States
    var p1Pose by remember { mutableStateOf(TokenPose.IDLE) }
    var p2Pose by remember { mutableStateOf(TokenPose.IDLE) }
    var p1LimbPhase by remember { mutableFloatStateOf(0f) }
    var p2LimbPhase by remember { mutableFloatStateOf(0f) }

    // Snake Wiggle Animation
    var activeSnakeHead by remember { mutableStateOf<Int?>(null) }
    var snakeWigglePhase by remember { mutableFloatStateOf(0f) }

    fun vibrate() {
        if (settings.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // Helper to calculate cell center
    fun getCellOffset(square: Int): Offset {
        val cellSize = if (boardPixelSize > 0f) boardPixelSize / 10f else 40f
        return SnakesAndLaddersBoard.getSquareCenter(square, cellSize)
    }

    // Initial position setup when board size is measured
    LaunchedEffect(boardPixelSize) {
        if (boardPixelSize > 0f) {
            p1Coord = getCellOffset(p1Square)
            p2Coord = getCellOffset(p2Square)
        }
    }

    // -------------------------------------------------------------
    // MOVEMENT ROUTINE WITH GEOMETRIC LADDER CLIMB & SNAKE SLIDE
    // -------------------------------------------------------------
    fun executeMovement(player: PlayerId, roll: Int) {
        scope.launch {
            isMoving = true
            val cellSize = boardPixelSize / 10f
            val startSquare = if (player == PlayerId.PLAYER_1) p1Square else p2Square
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
                return@launch
            }

            // 2. Step-by-Step Normal Movement (Square by Square)
            for (step in (startSquare + 1)..targetSquare) {
                val stepTarget = getCellOffset(step)
                val prevTarget = getCellOffset(step - 1)

                // Hop animation along step
                val hopFrames = 6
                for (f in 1..hopFrames) {
                    val progress = f.toFloat() / hopFrames
                    val currentX = prevTarget.x + (stepTarget.x - prevTarget.x) * progress
                    val currentY = prevTarget.y + (stepTarget.y - prevTarget.y) * progress

                    if (player == PlayerId.PLAYER_1) {
                        p1Coord = Offset(currentX, currentY)
                        p1Pose = TokenPose.HOPPING
                        p1LimbPhase = progress
                    } else {
                        p2Coord = Offset(currentX, currentY)
                        p2Pose = TokenPose.HOPPING
                        p2LimbPhase = progress
                    }
                    delay(20)
                }

                if (player == PlayerId.PLAYER_1) p1Square = step else p2Square = step
                vibrate()
                delay(40)
            }

            if (player == PlayerId.PLAYER_1) p1Pose = TokenPose.IDLE else p2Pose = TokenPose.IDLE
            delay(180)

            // 3. Check Ladder: Climb DIRECTLY along the ladder's geometric path!
            val ladder = SnakesAndLaddersBoard.LADDERS.find { it.bottom == targetSquare }
            if (ladder != null) {
                actionBanner = "Climbing ladder! 🪜"
                if (player == PlayerId.PLAYER_1) p1Pose = TokenPose.CLIMBING else p2Pose = TokenPose.CLIMBING
                vibrate()

                val climbSteps = 24
                for (s in 1..climbSteps) {
                    val t = s.toFloat() / climbSteps
                    val pt = SnakesAndLaddersBoard.getLadderPoint(ladder, t, cellSize)

                    if (player == PlayerId.PLAYER_1) {
                        p1Coord = pt
                        p1LimbPhase = t * 4f // Multiple limb cycles
                    } else {
                        p2Coord = pt
                        p2LimbPhase = t * 4f
                    }
                    delay(25)
                }

                // Land on ladder top
                if (player == PlayerId.PLAYER_1) {
                    p1Square = ladder.top
                    p1Coord = getCellOffset(ladder.top)
                    p1Pose = TokenPose.IDLE
                } else {
                    p2Square = ladder.top
                    p2Coord = getCellOffset(ladder.top)
                    p2Pose = TokenPose.IDLE
                }
                vibrate()
                delay(220)
                actionBanner = null
            }

            // 4. Check Snake: Slide DIRECTLY along the snake's actual curved body!
            val snake = SnakesAndLaddersBoard.SNAKES.find { it.head == targetSquare }
            if (snake != null) {
                actionBanner = "Oops! Snake slide! 🐍"
                activeSnakeHead = snake.head
                if (player == PlayerId.PLAYER_1) p1Pose = TokenPose.SLIDING else p2Pose = TokenPose.SLIDING
                vibrate()

                val slideSteps = 26
                for (s in 1..slideSteps) {
                    val t = s.toFloat() / slideSteps
                    snakeWigglePhase = t * 14f // Snake wiggles dynamically!
                    val pt = SnakesAndLaddersBoard.getSnakePoint(snake, t, cellSize, snakeWigglePhase)

                    if (player == PlayerId.PLAYER_1) {
                        p1Coord = pt
                        p1LimbPhase = t * 2f
                    } else {
                        p2Coord = pt
                        p2LimbPhase = t * 2f
                    }
                    delay(25)
                }

                // Land on snake tail
                activeSnakeHead = null
                snakeWigglePhase = 0f
                if (player == PlayerId.PLAYER_1) {
                    p1Square = snake.tail
                    p1Coord = getCellOffset(snake.tail)
                    p1Pose = TokenPose.IDLE
                } else {
                    p2Square = snake.tail
                    p2Coord = getCellOffset(snake.tail)
                    p2Pose = TokenPose.IDLE
                }
                vibrate()
                delay(220)
                actionBanner = null
            }

            // 5. Update Authoritative Game Engine State
            engine.makeMove(SnakesAndLaddersMove(roll), player)
            gameState = engine.state
            isMoving = false

            // 6. Check Win Condition
            if (gameState.status is GameStatus.MatchWon) {
                if (player == PlayerId.PLAYER_1) p1Pose = TokenPose.CELEBRATING else p2Pose = TokenPose.CELEBRATING
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
                        winner = player.name,
                        durationSeconds = 40
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // REAL 3D-STYLE PHYSICAL DICE ROLLER
    // -------------------------------------------------------------
    fun rollDice(player: PlayerId, sendNetwork: Boolean = true) {
        if (isRolling || isMoving || gameState.status !is GameStatus.InProgress) return
        scope.launch {
            // Determine result BEFORE visual animation starts
            val rollResult = Random.nextInt(1, 7)
            displayedDice = rollResult
            isRolling = true
            vibrate()

            if (sendNetwork && isNearbyMode) {
                multiplayerManager.sendAction("SNAKES_ROLL", rollResult.toString())
            }

            // Wait for 3D cube to tumble, land, bounce, and transition to 2D result
            delay(860)
            isRolling = false
            vibrate()
            delay(150)

            executeMovement(player, rollResult)
        }
    }

    // Listen for incoming actions in Nearby Mode for Snakes and Ladders
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "SNAKES_ROLL" -> {
                        val rollResult = action.payload.toIntOrNull() ?: return@collect
                        val remotePlayer = if (isHost) PlayerId.PLAYER_2 else PlayerId.PLAYER_1
                        scope.launch {
                            displayedDice = rollResult
                            isRolling = true
                            vibrate()
                            delay(860)
                            isRolling = false
                            vibrate()
                            delay(150)
                            executeMovement(remotePlayer, rollResult)
                        }
                    }
                    "SNAKES_REMATCH" -> {
                        engine.resetGame(1)
                        gameState = engine.state
                        p1Square = 1
                        p2Square = 1
                        p1Coord = getCellOffset(1)
                        p2Coord = getCellOffset(1)
                        p1Pose = TokenPose.IDLE
                        p2Pose = TokenPose.IDLE
                        actionBanner = null
                        showWinDialog = false
                        matchRecorded = false
                        vibrate()
                    }
                }
            }
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
            rollDice(PlayerId.PLAYER_2)
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

            // Current Turn Status
            val turnText = when {
                gameState.status is GameStatus.MatchWon -> "WINNER! 🏆"
                isRolling -> "ROLLING... 🎲"
                isMoving -> "MOVING... 🚶"
                selectedMode == GameMode.SOLO && gameState.activePlayer == PlayerId.PLAYER_1 -> "YOUR TURN 🎲"
                selectedMode == GameMode.SOLO && gameState.activePlayer == PlayerId.PLAYER_2 -> "BOT'S TURN 🤖"
                isNearbyMode && gameState.activePlayer == localPlayer -> "YOUR TURN 🎲"
                isNearbyMode -> "OPPONENT'S TURN 🎲"
                gameState.activePlayer == PlayerId.PLAYER_1 -> "PLAYER 1'S TURN 🎲"
                else -> "PLAYER 2'S TURN 🎲"
            }

            PlayfulPill(
                text = turnText,
                backgroundColor = PlayfulGreen.copy(alpha = 0.14f),
                textColor = PlayfulGreen
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            engine.resetGame(1)
                            gameState = engine.state
                            p1Square = 1
                            p2Square = 1
                            p1Coord = getCellOffset(1)
                            p2Coord = getCellOffset(1)
                            p1Pose = TokenPose.IDLE
                            p2Pose = TokenPose.IDLE
                            actionBanner = null
                            showWinDialog = false
                            matchRecorded = false
                            vibrate()
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

        // --- 2. LIGHTWEIGHT PLAYER POSITION HUD (NO ROUND ACCUMULATION) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 10.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player 1
                Column(horizontalAlignment = Alignment.Start) {
                    val p1Label = when {
                        selectedMode == GameMode.SOLO -> "YOU 🟠"
                        isNearbyMode -> if (localPlayer == PlayerId.PLAYER_1) "YOU 🟠" else "OPPONENT 🟠"
                        else -> "PLAYER 1 🟠"
                    }
                    Text(
                        text = p1Label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = PlayfulOrange
                    )
                    Text(
                        text = "Square $p1Square",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Action Banner
                if (actionBanner != null) {
                    PlayfulPill(
                        text = actionBanner ?: "",
                        backgroundColor = PlayfulGreen.copy(alpha = 0.15f),
                        textColor = PlayfulGreen
                    )
                } else {
                    PlayfulPill(
                        text = "FIRST TO 100",
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Player 2
                Column(horizontalAlignment = Alignment.End) {
                    val p2Label = when {
                        selectedMode == GameMode.SOLO -> "BOT 🟣"
                        isNearbyMode -> if (localPlayer == PlayerId.PLAYER_2) "YOU 🟣" else "OPPONENT 🟣"
                        else -> "PLAYER 2 🟣"
                    }
                    Text(
                        text = p2Label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = PlayfulPurple
                    )
                    Text(
                        text = "Square $p2Square",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 3. RICH BOARD GAME VIEW ---
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

            // Offset tokens slightly if occupying the exact same square
            val isSame = p1Square == p2Square && p1Pose == TokenPose.IDLE && p2Pose == TokenPose.IDLE
            val p1Pos = p1Coord ?: getCellOffset(p1Square)
            val p2Pos = p2Coord ?: getCellOffset(p2Square)

            val p1FinalPos = if (isSame) Offset(p1Pos.x - cellSize * 0.22f, p1Pos.y) else p1Pos
            val p2FinalPos = if (isSame) Offset(p2Pos.x + cellSize * 0.22f, p2Pos.y) else p2Pos

            SnakesAndLaddersBoardView(
                p1VisualPos = p1FinalPos,
                p2VisualPos = p2FinalPos,
                p1Pose = p1Pose,
                p2Pose = p2Pose,
                p1LimbPhase = p1LimbPhase,
                p2LimbPhase = p2LimbPhase,
                activeSnakeHead = activeSnakeHead,
                snakeWigglePhase = snakeWigglePhase,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. REAL 3D-STYLE PHYSICAL DICE CONTROL ---
        val canRoll = !isRolling && !isMoving &&
                gameState.status is GameStatus.InProgress &&
                (when {
                    selectedMode == GameMode.SOLO -> gameState.activePlayer == PlayerId.PLAYER_1
                    isNearbyMode -> gameState.activePlayer == localPlayer
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
                onClick = { rollDice(gameState.activePlayer) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    isRolling -> "ROLLING... 🎲"
                    isMoving -> "MOVING... 🚶"
                    canRoll -> "TAP DICE TO ROLL! 🎲"
                    selectedMode == GameMode.SOLO && gameState.activePlayer == PlayerId.PLAYER_2 -> "Bot is rolling... 🤖"
                    isNearbyMode && gameState.activePlayer != localPlayer -> "Opponent's turn to roll... 🎲"
                    else -> "WAITING..."
                },
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
                    Text("• Roll the dice to move your stick figure forward.", style = MaterialTheme.typography.bodyMedium)
                    Text("• 🪜 Climb ladders directly to the top!", style = MaterialTheme.typography.bodyMedium, color = PlayfulGreen)
                    Text("• 🐍 Slide down snakes all the way to the tail!", style = MaterialTheme.typography.bodyMedium, color = PlayfulOrange)
                    Text("• First to 100 wins! (Exact roll required).", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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

    // Victory Dialog (Single Game - No Next Round!)
    if (showWinDialog) {
        val winner = (gameState.status as? GameStatus.MatchWon)?.matchWinner ?: PlayerId.PLAYER_1
        val isP1Winner = winner == PlayerId.PLAYER_1

        AlertDialog(
            onDismissRequest = { /* require user action */ },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text(if (isP1Winner) "🏆" else "😵", fontSize = 48.sp) },
            title = {
                val winTitle = when {
                    selectedMode == GameMode.SOLO -> if (isP1Winner) "YOU WIN! 🎉" else "BOT WON! 🤖"
                    isNearbyMode -> if (winner == localPlayer) "YOU WIN! 🎉" else "OPPONENT WINS! 👑"
                    else -> if (isP1Winner) "PLAYER 1 WINS! 👑" else "PLAYER 2 WINS! 👑"
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
                        text = "Made it to 100! 🏁",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = PlayfulGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pure classic board game fun!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "PLAY AGAIN 🔄",
                    onClick = {
                        engine.resetGame(1)
                        gameState = engine.state
                        p1Square = 1
                        p2Square = 1
                        p1Coord = getCellOffset(1)
                        p2Coord = getCellOffset(1)
                        p1Pose = TokenPose.IDLE
                        p2Pose = TokenPose.IDLE
                        actionBanner = null
                        showWinDialog = false
                        matchRecorded = false
                        vibrate()
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
}
