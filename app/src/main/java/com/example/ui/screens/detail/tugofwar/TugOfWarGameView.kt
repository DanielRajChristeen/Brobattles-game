package com.example.ui.screens.detail.tugofwar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.XOSoundAndHapticManager
import com.example.core.bot.TugOfWarBotOpponent
import com.example.core.engine.GameStatus
import com.example.core.engine.MoveResult
import com.example.core.engine.PlayerId
import com.example.core.engine.tugofwar.TugOfWarGameEngine
import com.example.core.engine.tugofwar.TugOfWarMove
import com.example.core.engine.tugofwar.TugOfWarState
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.PlayfulYellow
import com.example.ui.theme.ToyboxBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class TapBurst(
    val x: Float,
    val y: Float,
    val color: Color,
    val createdAt: Long
)

@Composable
fun TugOfWarGameView(
    selectedMode: GameMode,
    targetScore: Int,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val soundManager = remember(context) { XOSoundAndHapticManager(context) }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    // Immediately stop playing sounds if user switches Sound Effects OFF in Settings
    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) {
            soundManager.stopAllSounds()
        }
    }

    val engine = remember { TugOfWarGameEngine(initialTargetScore = targetScore) }
    val bot = remember { TugOfWarBotOpponent() }
    var gameState by remember { mutableStateOf(engine.state) }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayer = if (isHost) PlayerId.PLAYER_1 else PlayerId.PLAYER_2

    var countdown by remember { mutableStateOf<String?>("3") }
    var showTapHint by remember { mutableStateOf(false) }

    // Dynamic animation states for skeletal pull cycles (0.0 to 1.0)
    var p1PullPhase by remember { mutableFloatStateOf(0f) }
    var p2PullPhase by remember { mutableFloatStateOf(0f) }

    // Tap impulse / energy bursts (decay smoothly to 0)
    var p1TapEnergy by remember { mutableFloatStateOf(0f) }
    var p2TapEnergy by remember { mutableFloatStateOf(0f) }

    // Rope vibration / jerk
    var ropeJerk by remember { mutableFloatStateOf(0f) }

    // Finishing celebration & falling state machine
    var isFinishingRound by remember { mutableStateOf(false) }
    var finishProgress by remember { mutableFloatStateOf(0f) }
    var showResultDialog by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var hasPlayedOutcomeSound by remember { mutableStateOf(false) }

    val tapBursts = remember { mutableStateListOf<TapBurst>() }

    fun vibrate() {
        if (settings.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // High-performance animation frame loop: advances pull cycles, decay energy & jerks
    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (isActive) {
            withFrameMillis { frameTime ->
                val dt = if (lastTime == 0L) 16L else (frameTime - lastTime).coerceIn(4L, 50L)
                lastTime = frameTime

                val speed = (dt / 1000f)

                // Advance pull cycles
                if (p1PullPhase > 0f) {
                    p1PullPhase = (p1PullPhase + speed * (2.8f + p1TapEnergy * 2.2f))
                    if (p1PullPhase >= 1f) p1PullPhase = 0f
                }
                if (p2PullPhase > 0f) {
                    p2PullPhase = (p2PullPhase + speed * (2.8f + p2TapEnergy * 2.2f))
                    if (p2PullPhase >= 1f) p2PullPhase = 0f
                }

                // Decay energies
                p1TapEnergy = (p1TapEnergy - speed * 3.5f).coerceAtLeast(0f)
                p2TapEnergy = (p2TapEnergy - speed * 3.5f).coerceAtLeast(0f)
                ropeJerk = (ropeJerk * 0.88f)

                // Clean up expired tap particles (> 300ms)
                val now = System.currentTimeMillis()
                tapBursts.removeAll { now - it.createdAt > 320L }
            }
        }
    }

    // Countdown and tap hint sequence
    LaunchedEffect(gameState.roundNumber) {
        hasPlayedOutcomeSound = false
        countdown = "3"
        delay(400)
        countdown = "2"
        delay(400)
        countdown = "1"
        delay(400)
        countdown = "PULL! 🔥"
        delay(400)
        countdown = null

        // Show brief tap anywhere hint
        showTapHint = true
        delay(1800)
        showTapHint = false
    }

    // Bot autonomous pulling loop in SOLO mode
    LaunchedEffect(selectedMode, gameState.status, countdown, isFinishingRound) {
        if (selectedMode == GameMode.SOLO &&
            gameState.status is GameStatus.InProgress &&
            countdown == null &&
            !isFinishingRound
        ) {
            while (isActive && gameState.status is GameStatus.InProgress && !isFinishingRound) {
                val botMove = bot.decideMove(gameState, PlayerId.PLAYER_2)
                if (botMove != null && gameState.status is GameStatus.InProgress && !isFinishingRound) {
                    val result = engine.makeMove(botMove, PlayerId.PLAYER_2)
                    if (result is MoveResult.Success) {
                        gameState = result.newState
                        p2PullPhase = 0.05f
                        p2TapEnergy = 1.0f
                        ropeJerk = 6.0f
                    }
                }
            }
        }
    }

    // Subtle physical rope tension / centering friction
    LaunchedEffect(gameState.status, countdown, isFinishingRound) {
        if (gameState.status is GameStatus.InProgress && countdown == null && !isFinishingRound) {
            while (isActive && gameState.status is GameStatus.InProgress && !isFinishingRound) {
                delay(120)
                gameState = engine.applyFriction()
            }
        }
    }

    // Trigger finishing choreography and outcome audio when winning threshold is reached
    LaunchedEffect(gameState.status) {
        val status = gameState.status
        if ((status is GameStatus.MatchWon || status is GameStatus.RoundWon) && !isFinishingRound) {
            isFinishingRound = true
            showTapHint = false

            // Play outcome sound once per match / round
            if (!hasPlayedOutcomeSound) {
                hasPlayedOutcomeSound = true
                val winner = if (status is GameStatus.MatchWon) status.matchWinner else (status as GameStatus.RoundWon).winner
                soundManager.onTugOfWarMatchWon(
                    winner = winner,
                    localPlayer = localPlayer,
                    mode = selectedMode,
                    isSoundEnabled = settings.soundEnabled
                )
            }

            // Animate finishing progress 0.0 -> 1.0 over 1500ms
            val startTime = System.currentTimeMillis()
            val duration = 1500f
            while (isActive && finishProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                finishProgress = (elapsed / duration).coerceIn(0f, 1f)
                delay(16)
            }

            delay(300)
            if (status is GameStatus.MatchWon) {
                showResultDialog = true
                if (!matchRecorded) {
                    matchRecorded = true
                    viewModel.repository.recordMatch(
                        gameId = "tug_of_war",
                        gameTitle = "Tug of War",
                        mode = selectedMode.name,
                        player1Score = gameState.player1Score,
                        player2Score = gameState.player2Score,
                        winner = status.matchWinner.name,
                        durationSeconds = 30
                    )
                }
            }
        }
    }

    fun handleTap(player: PlayerId, touchPos: Offset?, sendNetwork: Boolean = true) {
        if (countdown != null || isFinishingRound) return
        if (gameState.status !is GameStatus.InProgress) return

        val res = engine.makeMove(TugOfWarMove(force = 1.0f), player)
        if (res is MoveResult.Success) {
            gameState = res.newState
            vibrate()

            // Play short, clean touchpad-style tap sound once per accepted user tap
            // In Nearby mode, only locally initiated taps trigger the tap sound (not remote network echo)
            if (sendNetwork) {
                soundManager.playTouchpadTap(settings.soundEnabled)
            }

            // Trigger physical pulling cycle
            if (player == PlayerId.PLAYER_1) {
                p1PullPhase = 0.05f
                p1TapEnergy = 1.0f
                ropeJerk = -7.0f
            } else {
                p2PullPhase = 0.05f
                p2TapEnergy = 1.0f
                ropeJerk = 7.0f
            }

            // Spawn immediate tap burst feedback
            if (touchPos != null) {
                val burstColor = if (player == PlayerId.PLAYER_1) PlayfulOrange else PlayfulPurple
                tapBursts.add(TapBurst(touchPos.x, touchPos.y, burstColor, System.currentTimeMillis()))
            }

            if (sendNetwork && isNearbyMode) {
                multiplayerManager.sendAction("TUG_PULL", if (player == PlayerId.PLAYER_1) "1" else "2")
            }
        }
    }

    // Listen for incoming actions in Nearby Mode for Tug of War
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "TUG_PULL" -> {
                        val player = if (action.payload == "1") PlayerId.PLAYER_1 else PlayerId.PLAYER_2
                        handleTap(player, null, sendNetwork = false)
                    }
                    "TUG_NEXT_ROUND" -> {
                        gameState = engine.startRound()
                        isFinishingRound = false
                        finishProgress = 0f
                        hasPlayedOutcomeSound = false
                        vibrate()
                    }
                    "TUG_REMATCH" -> {
                        val target = action.payload.toIntOrNull() ?: targetScore
                        engine.resetGame(target)
                        gameState = engine.state
                        isFinishingRound = false
                        finishProgress = 0f
                        showResultDialog = false
                        matchRecorded = false
                        hasPlayedOutcomeSound = false
                        vibrate()
                    }
                }
            }
        }
    }

    val isP1Winner = (gameState.status as? GameStatus.MatchWon)?.matchWinner == PlayerId.PLAYER_1 ||
            (gameState.status as? GameStatus.RoundWon)?.winner == PlayerId.PLAYER_1
    val isP2Winner = (gameState.status as? GameStatus.MatchWon)?.matchWinner == PlayerId.PLAYER_2 ||
            (gameState.status as? GameStatus.RoundWon)?.winner == PlayerId.PLAYER_2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
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
                    .testTag("tug_back_button"),
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
                backgroundColor = PlayfulOrange.copy(alpha = 0.12f),
                textColor = PlayfulOrange
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable {
                        engine.resetGame(targetScore)
                        gameState = engine.state
                        isFinishingRound = false
                        finishProgress = 0f
                        showResultDialog = false
                        matchRecorded = false
                        vibrate()
                    }
                    .testTag("tug_restart_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. LIVE WINNING PERCENTAGE DISPLAY ---
        val p1Percent = gameState.p1Percentage
        val p2Percent = gameState.p2Percentage
        val p1Ahead = p1Percent > 50
        val p2Ahead = p2Percent > 50

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(22.dp))
                .padding(vertical = 12.dp, horizontal = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Player info
                    Column(horizontalAlignment = Alignment.Start) {
                        val p1Label = when {
                            selectedMode == GameMode.SOLO -> "YOU"
                            isNearbyMode -> if (localPlayer == PlayerId.PLAYER_1) "YOU" else "OPPONENT"
                            else -> "PLAYER 1"
                        }
                        Text(
                            text = p1Label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = PlayfulOrange
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$p1Percent%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (p1Ahead) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "← AHEAD",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = PlayfulOrange
                                )
                            }
                        }
                    }

                    // Score pill
                    PlayfulPill(
                        text = "${gameState.player1Score} - ${gameState.player2Score}",
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )

                    // Right Player info
                    Column(horizontalAlignment = Alignment.End) {
                        val p2Label = when {
                            selectedMode == GameMode.SOLO -> "BOT"
                            isNearbyMode -> if (localPlayer == PlayerId.PLAYER_2) "YOU" else "OPPONENT"
                            else -> "PLAYER 2"
                        }
                        Text(
                            text = p2Label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = PlayfulPurple
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p2Ahead) {
                                Text(
                                    text = "AHEAD →",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = PlayfulPurple
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "$p2Percent%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Smooth Horizontal Advantage Balance Bar
                val barProgress by animateFloatAsState(
                    targetValue = (p1Percent / 100f).coerceIn(0f, 1f),
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
                    label = "bar_progress"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PlayfulOrange, PlayfulYellow)
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 3. FULL GAMEPLAY ARENA & INTERACTION SURFACE ---
        // Tapping anywhere in this arena drives pulling without requiring buttons!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, ToyboxBorder, RoundedCornerShape(26.dp))
                .pointerInput(selectedMode, countdown, isFinishingRound) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            for (change in event.changes) {
                                if (change.changedToDown()) {
                                    val touchX = change.position.x
                                    val player = if (selectedMode == GameMode.SOLO) {
                                        PlayerId.PLAYER_1
                                    } else if (isNearbyMode) {
                                        localPlayer
                                    } else {
                                        // Left half of arena is Player 1, Right half is Player 2
                                        if (touchX < size.width / 2f) PlayerId.PLAYER_1 else PlayerId.PLAYER_2
                                    }
                                    handleTap(player, change.position)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                .testTag("tug_gameplay_arena"),
            contentAlignment = Alignment.Center
        ) {
            val animatedRopePosition by animateFloatAsState(
                targetValue = gameState.ropePosition,
                animationSpec = spring(stiffness = 550f, dampingRatio = 0.65f),
                label = "rope_position"
            )

            // Dynamic Arena Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val arenaWidth = size.width
                val arenaHeight = size.height
                val groundY = arenaHeight * 0.76f
                val ropeY = groundY - 55f
                val centerX = arenaWidth / 2f

                // In 2-Player mode, draw subtle vertical center divider
                if (selectedMode == GameMode.LOCAL_1V1) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(centerX, 20f),
                        end = Offset(centerX, arenaHeight - 20f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Draw Ground
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.6f),
                    start = Offset(20f, groundY),
                    end = Offset(arenaWidth - 20f, groundY),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )

                // Winning Threshold Markers (Left & Right Goal Lines)
                val goalDistance = arenaWidth * 0.36f
                val leftGoalX = centerX - goalDistance
                val rightGoalX = centerX + goalDistance

                // Red Goal flags
                drawLine(
                    color = Color.Red.copy(alpha = 0.35f),
                    start = Offset(leftGoalX, groundY - 100f),
                    end = Offset(leftGoalX, groundY),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Red.copy(alpha = 0.35f),
                    start = Offset(rightGoalX, groundY - 100f),
                    end = Offset(rightGoalX, groundY),
                    strokeWidth = 3f
                )

                // High-frequency rope wobble when under tension
                val totalEnergy = (p1TapEnergy + p2TapEnergy).coerceAtMost(2.0f)
                val wobbleY = if (totalEnergy > 0.05f) {
                    (sin(System.currentTimeMillis() / 25.0) * (3.5f * totalEnergy)).toFloat()
                } else 0f

                // Calculate center marker position based on normalized rope position (-100 to +100)
                val baseMarkerX = centerX + (animatedRopePosition * (goalDistance / 100f))
                val markerX = baseMarkerX + ropeJerk

                // Anchor positions for stick figures
                val p1AnchorX = markerX - (arenaWidth * 0.32f).coerceAtLeast(125f)
                val p2AnchorX = markerX + (arenaWidth * 0.32f).coerceAtLeast(125f)

                // Draw Rope (woven cable texture)
                val ropeStart = (p1AnchorX - 45f).coerceAtLeast(8f)
                val ropeEnd = (p2AnchorX + 45f).coerceAtMost(arenaWidth - 8f)

                // Main rope body
                drawLine(
                    color = Color(0xFFD97706), // Golden rope amber
                    start = Offset(ropeStart, ropeY + wobbleY),
                    end = Offset(ropeEnd, ropeY + wobbleY),
                    strokeWidth = 16f,
                    cap = StrokeCap.Round
                )
                // Twisted strands inner texture
                drawLine(
                    color = Color(0xFF92400E),
                    start = Offset(ropeStart, ropeY + wobbleY - 1f),
                    end = Offset(ropeEnd, ropeY + wobbleY - 1f),
                    strokeWidth = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 14f), 0f),
                    cap = StrokeCap.Round
                )

                // Draw Center Red Stripe / Ribbon hanging physically down from rope
                drawRect(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(markerX - 7f, ropeY + wobbleY - 14f),
                    size = Size(14f, 40f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5f,
                    center = Offset(markerX, ropeY + wobbleY)
                )

                // Advantage factors for lean animations (-1f to +1f)
                val p1Advantage = (-animatedRopePosition / 100f).coerceIn(-1f, 1f)
                val p2Advantage = (animatedRopePosition / 100f).coerceIn(-1f, 1f)

                // Draw Player 1 Stick Figure (Left)
                drawSkeletalTugFigure(
                    isLeft = true,
                    anchorX = p1AnchorX,
                    groundY = groundY,
                    ropeY = ropeY + wobbleY,
                    pullingAdvantage = p1Advantage,
                    pullPhase = p1PullPhase,
                    energy = p1TapEnergy,
                    isWinner = isFinishingRound && isP1Winner,
                    isLoser = isFinishingRound && isP2Winner,
                    finishProgress = finishProgress,
                    color = PlayfulOrange,
                    strokeWidth = 11f
                )

                // Draw Player 2 Stick Figure (Right)
                drawSkeletalTugFigure(
                    isLeft = false,
                    anchorX = p2AnchorX,
                    groundY = groundY,
                    ropeY = ropeY + wobbleY,
                    pullingAdvantage = p2Advantage,
                    pullPhase = p2PullPhase,
                    energy = p2TapEnergy,
                    isWinner = isFinishingRound && isP2Winner,
                    isLoser = isFinishingRound && isP1Winner,
                    finishProgress = finishProgress,
                    color = PlayfulPurple,
                    strokeWidth = 11f
                )

                // Draw Tap Burst feedback rings
                val now = System.currentTimeMillis()
                for (burst in tapBursts) {
                    val age = (now - burst.createdAt).toFloat()
                    if (age in 0f..320f) {
                        val progress = age / 320f
                        val radius = 12f + (progress * 45f)
                        val alpha = (1f - progress).coerceIn(0f, 1f)
                        drawCircle(
                            color = burst.color.copy(alpha = alpha * 0.7f),
                            radius = radius,
                            center = Offset(burst.x, burst.y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * (1f - progress))
                        )
                    }
                }
            }

            // Brief, Non-Intrusive Floating Tap Hint
            if (showTapHint && countdown == null && !isFinishingRound) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    val hintText = when {
                        selectedMode == GameMode.SOLO || isNearbyMode -> "TAP ANYWHERE TO PULL! 👆"
                        else -> "P1 TAP LEFT 👈 | 👉 P2 TAP RIGHT"
                    }
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Countdown Overlay
            if (countdown != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 30.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = countdown ?: "",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                }
            }
        }

        // Round won button (shows only after finishing animation has settled)
        if (isFinishingRound && (gameState.status is GameStatus.RoundWon) && !(gameState.status is GameStatus.MatchWon)) {
            Spacer(modifier = Modifier.height(14.dp))
            ChunkyButton(
                text = "NEXT ROUND ⏭️",
                onClick = {
                    gameState = engine.startRound()
                    isFinishingRound = false
                    finishProgress = 0f
                    hasPlayedOutcomeSound = false
                    vibrate()
                    if (isNearbyMode) {
                        multiplayerManager.sendAction("TUG_NEXT_ROUND", "")
                    }
                },
                backgroundColor = PlayfulOrange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tug_next_round_button")
            )
        }
    }

    // Match Result Dialog (Appears only after finishing celebration & fall have concluded)
    if (showResultDialog) {
        val winner = (gameState.status as GameStatus.MatchWon).matchWinner
        val isP1MatchWinner = winner == PlayerId.PLAYER_1

        AlertDialog(
            onDismissRequest = { /* require user action */ },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                val iconText = when {
                    selectedMode == GameMode.SOLO -> if (isP1MatchWinner) "🏆" else "😵"
                    isNearbyMode -> if (winner == localPlayer) "🏆" else "😵"
                    else -> "🏆" // In local 2-player mode, both players' victories are celebrated
                }
                Text(
                    text = iconText,
                    fontSize = 46.sp
                )
            },
            title = {
                val dialogTitle = when {
                    selectedMode == GameMode.SOLO -> if (isP1MatchWinner) "YOU WON! 🎉" else "BOT WON! 🤖"
                    isNearbyMode -> if (winner == localPlayer) "YOU WON! 🎉" else "OPPONENT WON! 👑"
                    else -> if (isP1MatchWinner) "PLAYER 1 WINS! 👑" else "PLAYER 2 WINS! 👑"
                }
                Text(
                    text = dialogTitle,
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
                        text = "Nice pull! 💪",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = PlayfulOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Final: ${gameState.player1Score} - ${gameState.player2Score}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "REMATCH 🔄",
                    onClick = {
                        engine.resetGame(targetScore)
                        gameState = engine.state
                        isFinishingRound = false
                        finishProgress = 0f
                        showResultDialog = false
                        matchRecorded = false
                        hasPlayedOutcomeSound = false
                        vibrate()
                        if (isNearbyMode) {
                            multiplayerManager.sendAction("TUG_REMATCH", targetScore.toString())
                        }
                    },
                    backgroundColor = PlayfulOrange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tug_rematch_button")
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
