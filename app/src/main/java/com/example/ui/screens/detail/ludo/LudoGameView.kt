package com.example.ui.screens.detail.ludo

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.LudoSoundManager
import com.example.core.bot.BotDifficulty
import com.example.core.engine.ludo.LudoBoardCoordinates
import com.example.core.engine.ludo.LudoBotOpponent
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoGameEngine
import com.example.core.engine.ludo.LudoGameState
import com.example.core.engine.ludo.LudoPlayerState
import com.example.core.engine.ludo.LudoToken
import com.example.core.engine.ludo.deserializeLudoState
import com.example.core.engine.ludo.serializeLudoState
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class CapturedPawnRef(
    val color: LudoColor,
    val tokenId: Int,
    val fromStep: Int
)

private data class CapturedPawnPlan(
    val color: LudoColor,
    val tokenId: Int,
    val reversePath: List<Int>
)

@Composable
fun LudoGameView(
    selectedMode: GameMode,
    playerCount: Int = 2,
    botDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()

    var botJob by remember { mutableStateOf<Job?>(null) }

    val soundManager = remember(context) { LudoSoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
            botJob?.cancel()
        }
    }

    val bot = remember { LudoBotOpponent() }

    // Nearby networking
    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)

    val localColor = remember(lobbyState, isHost) {
        if (!isNearbyMode) {
            LudoColor.RED
        } else if (isHost) {
            LudoColor.RED
        } else {
            val localId = (lobbyState as? MultiplayerLobbyState.InGame)?.localPlayer?.id
                ?: (lobbyState as? MultiplayerLobbyState.Connected)?.localPlayer?.id
            val allPlayers = (lobbyState as? MultiplayerLobbyState.InGame)?.players
                ?: (lobbyState as? MultiplayerLobbyState.Connected)?.players ?: emptyList()
            val myIdx = allPlayers.indexOfFirst { it.id == localId }.coerceAtLeast(1)
            val colors = listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
            colors.getOrElse(myIdx) { LudoColor.GREEN }
        }
    }

    val customUsernames = remember(settings.username) {
        mapOf(LudoColor.RED to settings.username)
    }

    val engine = remember {
        LudoGameEngine(
            initialMode = selectedMode,
            initialPlayerCount = playerCount,
            usernames = customUsernames
        )
    }

    var gameState by remember { mutableStateOf(engine.state) }
    var displayedDice by remember { mutableIntStateOf(gameState.diceRoll ?: 6) }
    var isRolling by remember { mutableStateOf(false) }
    var isBotThinking by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }

    // Active animation layers
    var animatingToken by remember { mutableStateOf<AnimatingTokenState?>(null) }
    var capturedAnimatingTokens by remember { mutableStateOf<List<AnimatingCapturedState>>(emptyList()) }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showVictoryDialog by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }

    // Sound effects stop when sound toggle turned off
    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) soundManager.stopAllSounds()
    }

    /**
     * Executes the step-by-step forward hopping movement through each square,
     * followed by the backward return animation for any captured enemy pawns.
     */
    suspend fun performAnimatedMove(
        tokenId: Int,
        expectedColor: LudoColor? = null,
        onComplete: (LudoGameState) -> Unit = {}
    ) {
        val currentP = if (expectedColor != null) {
            gameState.players.firstOrNull { it.color == expectedColor } ?: gameState.currentPlayer
        } else {
            gameState.currentPlayer
        }
        val movingToken = currentP.tokens.firstOrNull { it.id == tokenId } ?: return
        val roll = gameState.diceRoll ?: return
        val fromStep = movingToken.step
        val toStep = if (fromStep == -1) 0 else fromStep + roll

        try {
            // 1. Identify enemy pawns that will be captured upon landing
            val capturedPawns = mutableListOf<CapturedPawnRef>()
            if (toStep in 0..LudoToken.STEP_LAST_TRACK) {
                val targetTrackIdx = (currentP.color.startIndex + toStep) % 52
                if (!LudoBoardCoordinates.isSafeSquare(targetTrackIdx)) {
                    for (p in gameState.players) {
                        if (p.color != currentP.color) {
                            for (t in p.tokens) {
                                if (t.isOnTrack && t.trackIndex == targetTrackIdx) {
                                    capturedPawns.add(CapturedPawnRef(p.color, t.id, t.step))
                                }
                            }
                        }
                    }
                }
            }

            isAnimating = true

            // 2. Step-by-Step Hopping Animation (Each intermediate square visited)
            val forwardPath = if (fromStep == -1) {
                listOf(-1, 0)
            } else {
                (fromStep..toStep).toList()
            }
            val numHops = forwardPath.size - 1

            for (hopIndex in 0 until numHops) {
                val hopDurationMs = 155L
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val frac = (elapsed.toFloat() / hopDurationMs).coerceIn(0f, 1f)
                    animatingToken = AnimatingTokenState(
                        color = currentP.color,
                        tokenId = tokenId,
                        path = forwardPath,
                        currentHopIndex = hopIndex,
                        hopFraction = frac
                    )
                    if (frac >= 1f) break
                    delay(16)
                }
                // Audio and haptic feedback on landing each square
                soundManager.playTokenMove(settings.soundEnabled)
                soundManager.vibrateMove(settings.vibrationEnabled)
            }
            animatingToken = null

            // 3. Backward Capture Animation (Return to origin yard along valid route)
            if (capturedPawns.isNotEmpty()) {
                soundManager.playCapture(settings.soundEnabled)
                soundManager.vibrateCapture(settings.vibrationEnabled)

                val capturedPlans = capturedPawns.map { info ->
                    val reversePath = (info.fromStep downTo 0).toList() + listOf(LudoToken.STEP_YARD)
                    CapturedPawnPlan(info.color, info.tokenId, reversePath)
                }

                val maxReverseHops = capturedPlans.maxOf { it.reversePath.size - 1 }
                val reverseDurationMs = (750L / maxReverseHops.coerceAtLeast(1)).coerceIn(50L, 95L)

                for (hopIndex in 0 until maxReverseHops) {
                    val startTime = System.currentTimeMillis()
                    while (true) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val frac = (elapsed.toFloat() / reverseDurationMs).coerceIn(0f, 1f)
                        capturedAnimatingTokens = capturedPlans.map { plan ->
                            val clampedIndex = hopIndex.coerceAtMost(plan.reversePath.size - 2)
                            AnimatingCapturedState(
                                color = plan.color,
                                tokenId = plan.tokenId,
                                reversePath = plan.reversePath,
                                currentHopIndex = clampedIndex,
                                hopFraction = if (hopIndex < plan.reversePath.size - 1) frac else 1f
                            )
                        }
                        if (frac >= 1f) break
                        delay(16)
                    }
                }
                capturedAnimatingTokens = emptyList()
            }

            // 4. Update Authoritative Game State from Engine
            val nextState = engine.moveToken(tokenId)

            if (toStep >= LudoToken.STEP_FINISH) {
                soundManager.playHomeFinish(settings.soundEnabled)
                soundManager.vibrateMove(settings.vibrationEnabled)
            }

            gameState = nextState
            onComplete(nextState)
        } finally {
            animatingToken = null
            capturedAnimatingTokens = emptyList()
            isAnimating = false
        }
    }

    // Nearby multiplayer network synchronization
    LaunchedEffect(Unit) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "LUDO_ROLL" -> {
                        if (isHost && !isRolling && !isAnimating) {
                            soundManager.playDiceRoll(settings.soundEnabled)
                            soundManager.vibrateMove(settings.vibrationEnabled)
                            isRolling = true
                            val rolledState = engine.rollDice()
                            displayedDice = rolledState.diceRoll ?: 6
                            delay(850)
                            isRolling = false
                            gameState = rolledState
                            multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                        }
                    }
                    "LUDO_MOVE" -> {
                        val tokenId = action.payload.toIntOrNull()
                        if (isHost && tokenId != null && !isAnimating) {
                            performAnimatedMove(tokenId) { finalState ->
                                multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(finalState))
                            }
                        }
                    }
                    "LUDO_SYNC" -> {
                        val synced = deserializeLudoState(action.payload)
                        if (synced != null) {
                            val prev = gameState
                            engine.setState(synced)

                            // Check if a dice roll occurred
                            if (synced.diceRoll != null && prev.diceRoll != synced.diceRoll) {
                                soundManager.playDiceRoll(settings.soundEnabled)
                                soundManager.vibrateMove(settings.vibrationEnabled)
                                isRolling = true
                                displayedDice = synced.diceRoll
                                delay(850)
                                isRolling = false
                            }

                            // Check if a move occurred between prev and synced
                            var movedColor: LudoColor? = null
                            var movedTokenId: Int? = null
                            var fromStep: Int? = null
                            var toStep: Int? = null

                            for (pPrev in prev.players) {
                                val pSynced = synced.players.firstOrNull { it.color == pPrev.color } ?: continue
                                for (tPrev in pPrev.tokens) {
                                    val tSynced = pSynced.tokens.firstOrNull { it.id == tPrev.id } ?: continue
                                    if (tSynced.step > tPrev.step && tPrev.step != LudoToken.STEP_YARD) {
                                        movedColor = pPrev.color
                                        movedTokenId = tPrev.id
                                        fromStep = tPrev.step
                                        toStep = tSynced.step
                                        break
                                    } else if (tPrev.step == LudoToken.STEP_YARD && tSynced.step == LudoToken.STEP_START) {
                                        movedColor = pPrev.color
                                        movedTokenId = tPrev.id
                                        fromStep = LudoToken.STEP_YARD
                                        toStep = LudoToken.STEP_START
                                        break
                                    }
                                }
                                if (movedTokenId != null) break
                            }

                            if (movedTokenId != null && movedColor != null && fromStep != null && toStep != null) {
                                // Animate step-by-step for guest
                                isAnimating = true
                                val forwardPath = if (fromStep == -1) listOf(-1, 0) else (fromStep..toStep).toList()
                                for (hopIndex in 0 until (forwardPath.size - 1)) {
                                    val hopDurationMs = 155L
                                    val startTime = System.currentTimeMillis()
                                    while (true) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val frac = (elapsed.toFloat() / hopDurationMs).coerceIn(0f, 1f)
                                        animatingToken = AnimatingTokenState(
                                            color = movedColor,
                                            tokenId = movedTokenId,
                                            path = forwardPath,
                                            currentHopIndex = hopIndex,
                                            hopFraction = frac
                                        )
                                        if (frac >= 1f) break
                                        delay(16)
                                    }
                                    soundManager.playTokenMove(settings.soundEnabled)
                                    soundManager.vibrateMove(settings.vibrationEnabled)
                                }
                                animatingToken = null

                                // Check for captured pawns in this sync
                                val capturedList = mutableListOf<CapturedPawnRef>()
                                for (pPrev in prev.players) {
                                    val pSynced = synced.players.firstOrNull { it.color == pPrev.color } ?: continue
                                    for (tPrev in pPrev.tokens) {
                                        val tSynced = pSynced.tokens.firstOrNull { it.id == tPrev.id } ?: continue
                                        if (tPrev.step >= 0 && tSynced.step == LudoToken.STEP_YARD) {
                                            capturedList.add(CapturedPawnRef(pPrev.color, tPrev.id, tPrev.step))
                                        }
                                    }
                                }

                                if (capturedList.isNotEmpty()) {
                                    soundManager.playCapture(settings.soundEnabled)
                                    soundManager.vibrateCapture(settings.vibrationEnabled)
                                    val capturedPlans = capturedList.map { info ->
                                        val reversePath = (info.fromStep downTo 0).toList() + listOf(LudoToken.STEP_YARD)
                                        CapturedPawnPlan(info.color, info.tokenId, reversePath)
                                    }
                                    val maxReverseHops = capturedPlans.maxOf { it.reversePath.size - 1 }
                                    val reverseDurationMs = (750L / maxReverseHops.coerceAtLeast(1)).coerceIn(50L, 95L)
                                    for (hopIndex in 0 until maxReverseHops) {
                                        val startTime = System.currentTimeMillis()
                                        while (true) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            val frac = (elapsed.toFloat() / reverseDurationMs).coerceIn(0f, 1f)
                                            capturedAnimatingTokens = capturedPlans.map { plan ->
                                                val clampedIndex = hopIndex.coerceAtMost(plan.reversePath.size - 2)
                                                AnimatingCapturedState(
                                                    color = plan.color,
                                                    tokenId = plan.tokenId,
                                                    reversePath = plan.reversePath,
                                                    currentHopIndex = clampedIndex,
                                                    hopFraction = if (hopIndex < plan.reversePath.size - 1) frac else 1f
                                                )
                                            }
                                            if (frac >= 1f) break
                                            delay(16)
                                        }
                                    }
                                    capturedAnimatingTokens = emptyList()
                                }

                                if (toStep >= LudoToken.STEP_FINISH) {
                                    soundManager.playHomeFinish(settings.soundEnabled)
                                }
                                isAnimating = false
                            }

                            gameState = synced
                        }
                    }
                    "LUDO_REMATCH" -> {
                        engine.resetGame(selectedMode, playerCount, customUsernames)
                        gameState = engine.state
                        displayedDice = 6
                        isRolling = false
                        isAnimating = false
                        matchRecorded = false
                        showVictoryDialog = false
                        if (isHost) {
                            multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                        }
                    }
                }
            }
        }
    }

    // Check for victory dialog
    LaunchedEffect(gameState.winner, isAnimating) {
        if (gameState.winner != null && !isAnimating && !showVictoryDialog) {
            showVictoryDialog = true
            soundManager.playVictory(settings.soundEnabled)
            soundManager.vibrateVictory(settings.vibrationEnabled)

            if (!matchRecorded) {
                matchRecorded = true
                viewModel.repository.recordMatch(
                    gameId = "ludo",
                    gameTitle = "Ludo",
                    mode = selectedMode.name,
                    player1Score = if (gameState.winner == LudoColor.RED) 4 else gameState.players.firstOrNull { it.color == LudoColor.RED }?.finishedCount ?: 0,
                    player2Score = if (gameState.winner != LudoColor.RED) 4 else gameState.players.getOrNull(1)?.finishedCount ?: 0,
                    winner = gameState.winner!!.name,
                    durationSeconds = 120
                )
            }
        }
    }

    // VS Bot automatic turn loop
    LaunchedEffect(gameState.currentTurnIndex, gameState.isGameOver) {
        if (selectedMode == GameMode.SOLO &&
            !gameState.isGameOver &&
            gameState.currentPlayer.isBot &&
            botJob?.isActive != true
        ) {
            botJob = scope.launch {
                isBotThinking = true
                try {
                    while (!gameState.isGameOver && gameState.currentPlayer.isBot && gameState.canRoll) {
                        delay(600)
                        if (gameState.isGameOver || !gameState.currentPlayer.isBot || !gameState.canRoll) break

                        // 1. Bot rolls 3D dice
                        soundManager.playDiceRoll(settings.soundEnabled)
                        soundManager.vibrateMove(settings.vibrationEnabled)
                        isRolling = true

                        val rolledState = engine.rollDice()
                        val roll = rolledState.diceRoll ?: 6
                        displayedDice = roll
                        delay(850)
                        isRolling = false
                        gameState = rolledState

                        // 2. If legal moves exist, bot chooses move and executes hopping animation
                        if (rolledState.selectableTokenIds.isNotEmpty()) {
                            delay(500)
                            val botMoveTokenId = bot.decideMove(rolledState, botDifficulty)
                            if (botMoveTokenId != null) {
                                performAnimatedMove(botMoveTokenId, expectedColor = rolledState.currentPlayer.color)
                            } else {
                                break
                            }
                        } else {
                            // Turn already passed to next player in engine.rollDice
                            break
                        }
                    }
                } catch (e: Exception) {
                    gameState = engine.state
                } finally {
                    isRolling = false
                    isBotThinking = false
                }
            }
        }
    }

    fun handleRoll() {
        if (!gameState.canRoll || gameState.isGameOver || isRolling || isBotThinking || isAnimating || gameState.currentPlayer.isBot) return
        val currentP = gameState.currentPlayer

        // In nearby multiplayer, verify it's our turn
        if (isNearbyMode && currentP.color != localColor) return

        scope.launch {
            soundManager.playDiceRoll(settings.soundEnabled)
            soundManager.vibrateMove(settings.vibrationEnabled)
            isRolling = true

            if (isNearbyMode) {
                if (isHost) {
                    val rolledState = engine.rollDice()
                    displayedDice = rolledState.diceRoll ?: 6
                    delay(850)
                    isRolling = false
                    gameState = rolledState
                    multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                } else {
                    multiplayerManager.sendAction("LUDO_ROLL", "")
                    delay(850)
                    isRolling = false
                }
            } else {
                val rolledState = engine.rollDice()
                displayedDice = rolledState.diceRoll ?: 6
                delay(850)
                isRolling = false
                gameState = rolledState
            }
        }
    }

    fun handleTokenSelect(tokenId: Int) {
        if (gameState.canRoll || gameState.isGameOver || isBotThinking || isAnimating || isRolling || gameState.currentPlayer.isBot) return
        val currentP = gameState.currentPlayer

        // In nearby mode, verify it's our turn
        if (isNearbyMode && currentP.color != localColor) return
        if (tokenId !in gameState.selectableTokenIds) return

        scope.launch {
            performAnimatedMove(tokenId, expectedColor = currentP.color) { finalState ->
                if (isNearbyMode) {
                    if (isHost) {
                        multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(finalState))
                    } else {
                        multiplayerManager.sendAction("LUDO_MOVE", tokenId.toString())
                    }
                }
            }
        }
    }

    fun handleRematch() {
        botJob?.cancel()
        botJob = null
        isBotThinking = false
        isRolling = false
        isAnimating = false

        if (isNearbyMode) {
            if (isHost) {
                engine.resetGame(selectedMode, playerCount, customUsernames)
                gameState = engine.state
                displayedDice = 6
                matchRecorded = false
                showVictoryDialog = false
                multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
            } else {
                multiplayerManager.sendAction("LUDO_REMATCH", "")
            }
        } else {
            engine.resetGame(selectedMode, playerCount, customUsernames)
            gameState = engine.state
            displayedDice = 6
            matchRecorded = false
            showVictoryDialog = false
        }
    }

    val isMyTurn = !isNearbyMode || gameState.currentPlayer.color == localColor
    val canUserInteract = isMyTurn && !isBotThinking && !isRolling && !isAnimating && !gameState.isGameOver

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = {
                        soundManager.stopAllSounds()
                        onChangeMode()
                    })
                    .testTag("ludo_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎲 LUDO",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (selectedMode) {
                        GameMode.SOLO -> "VS Bot (${botDifficulty.name.lowercase().replaceFirstChar { it.uppercase() }})"
                        GameMode.LOCAL_1V1 -> "Local (${gameState.players.size}P)"
                        GameMode.BLUETOOTH_1V1 -> "Nearby (${localColor.displayName})"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(gameState.currentPlayer.color.primaryColorHex)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = { showHelpDialog = true })
                    .testTag("ludo_help_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Rules & Help",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Players Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gameState.players.forEachIndexed { index, player ->
                val isTurn = index == gameState.currentTurnIndex && !gameState.isGameOver
                PlayerCard(
                    player = player,
                    isCurrentTurn = isTurn,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Center 15x15 Canvas Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(2.5.dp, ToyboxBorder, RoundedCornerShape(18.dp))
        ) {
            LudoBoardCanvas(
                gameState = gameState,
                selectableTokenIds = if (canUserInteract) gameState.selectableTokenIds else emptyList(),
                animatingToken = animatingToken,
                capturedAnimatingTokens = capturedAnimatingTokens,
                onTokenSelected = { handleTokenSelect(it) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Turn Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(gameState.currentPlayer.color.primaryColorHex).copy(alpha = 0.12f))
                .border(
                    1.5.dp,
                    Color(gameState.currentPlayer.color.primaryColorHex).copy(alpha = 0.35f),
                    RoundedCornerShape(14.dp)
                )
                .padding(vertical = 8.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val statusMessage = when {
                isRolling -> "🎲 Rolling the 3D dice..."
                isAnimating -> "🚶 Token is hopping..."
                isBotThinking -> "🤖 Bot is deliberating..."
                else -> gameState.statusText
            }
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(gameState.currentPlayer.color.darkColorHex),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3D Physical Cube Dice Control
        LudoDiceView(
            diceValue = displayedDice,
            canRoll = gameState.canRoll && canUserInteract,
            isRolling = isRolling,
            playerColor = gameState.currentPlayer.color,
            onRollClick = { handleRoll() },
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Victory Celebration Dialog
    if (showVictoryDialog && gameState.winner != null) {
        val winningColor = gameState.winner!!
        val winningPlayer = gameState.players.firstOrNull { it.color == winningColor }
        val winnerName = winningPlayer?.name ?: winningColor.displayName

        AlertDialog(
            onDismissRequest = { /* stay on victory screen */ },
            modifier = Modifier.testTag("ludo_victory_dialog"),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "🏆🎉", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$winnerName WINS!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = Color(winningColor.primaryColorHex),
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "All 4 ${winningColor.displayName} tokens reached the Home Center!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                ChunkyButton(
                    text = "REMATCH 🔄",
                    backgroundColor = Color(winningColor.primaryColorHex),
                    onClick = { handleRematch() },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        soundManager.stopAllSounds()
                        showVictoryDialog = false
                        onChangeMode()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Change Game Mode",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Rules & Instructions Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎲", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("How to Play Ludo", fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HelpRuleItem(emoji = "🚀", title = "Launch Tokens", description = "Roll a 1 or 6 to move a token from your yard onto the starting square.")
                    HelpRuleItem(emoji = "🔄", title = "Extra Turns", description = "Rolling a 1 or 6 grants an extra turn! Capturing an enemy token also grants an extra turn (max 1 extra turn).")
                    HelpRuleItem(emoji = "⚔️", title = "Captures", description = "Landing on a square with an opponent's token sends it back to their yard.")
                    HelpRuleItem(emoji = "⭐", title = "Safe Squares", description = "Tokens on the 4 star squares and starting squares are safe and cannot be captured.")
                    HelpRuleItem(emoji = "🎯", title = "Home Center", description = "Exact roll required to enter the center finish area. First player to get all 4 tokens to the center wins!")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it!", fontWeight = FontWeight.Bold, color = PlayfulPurple)
                }
            }
        )
    }
}

@Composable
private fun PlayerCard(
    player: LudoPlayerState,
    isCurrentTurn: Boolean,
    modifier: Modifier = Modifier
) {
    val themeColor = Color(player.color.primaryColorHex)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentTurn) themeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isCurrentTurn) 2.5.dp else 1.5.dp,
                color = if (isCurrentTurn) themeColor else ToyboxBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = player.color.tokenEmoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = player.name.take(9),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isCurrentTurn) FontWeight.Black else FontWeight.Bold
                    ),
                    color = if (isCurrentTurn) themeColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "🏁 ${player.finishedCount}/4",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                ),
                color = if (player.finishedCount > 0) themeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpRuleItem(emoji: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
