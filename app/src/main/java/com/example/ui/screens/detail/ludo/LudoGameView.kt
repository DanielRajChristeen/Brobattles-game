package com.example.ui.screens.detail.ludo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.widthIn
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
import com.example.core.engine.ludo.LudoBotOpponent
import com.example.core.engine.ludo.LudoColor
import com.example.core.engine.ludo.LudoGameEngine
import com.example.core.engine.ludo.LudoGameState
import com.example.core.engine.ludo.LudoPlayerState
import com.example.core.engine.ludo.deserializeLudoState
import com.example.core.engine.ludo.serializeLudoState
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

    val soundManager = remember(context) { LudoSoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
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
    var isRolling by remember { mutableStateOf(false) }
    var isBotThinking by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showVictoryDialog by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }

    // Sound effects stop when sound toggle turned off
    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) soundManager.stopAllSounds()
    }

    // Nearby multiplayer network synchronization
    LaunchedEffect(Unit) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "LUDO_ROLL" -> {
                        if (isHost) {
                            soundManager.playDiceRoll(settings.soundEnabled)
                            soundManager.vibrateMove(settings.vibrationEnabled)
                            gameState = engine.rollDice()
                            multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                        }
                    }
                    "LUDO_MOVE" -> {
                        val tokenId = action.payload.toIntOrNull()
                        if (isHost && tokenId != null) {
                            val previousState = gameState
                            gameState = engine.moveToken(tokenId)
                            handleMoveAudioAndHaptics(previousState, gameState, soundManager, settings.soundEnabled, settings.vibrationEnabled)
                            multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                        }
                    }
                    "LUDO_SYNC" -> {
                        val synced = deserializeLudoState(action.payload)
                        if (synced != null) {
                            val prev = gameState
                            gameState = synced
                            engine.setState(synced)
                            handleMoveAudioAndHaptics(prev, synced, soundManager, settings.soundEnabled, settings.vibrationEnabled)
                        }
                    }
                    "LUDO_REMATCH" -> {
                        if (isHost) {
                            engine.resetGame(selectedMode, playerCount, customUsernames)
                            gameState = engine.state
                            matchRecorded = false
                            showVictoryDialog = false
                            multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                        } else {
                            engine.resetGame(selectedMode, playerCount, customUsernames)
                            gameState = engine.state
                            matchRecorded = false
                            showVictoryDialog = false
                        }
                    }
                }
            }
        }
    }

    // Check for victory dialog
    LaunchedEffect(gameState.winner) {
        if (gameState.winner != null && !showVictoryDialog) {
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
    LaunchedEffect(gameState.currentTurnIndex, gameState.canRoll, gameState.isGameOver) {
        if (selectedMode == GameMode.SOLO && !gameState.isGameOver && gameState.currentPlayer.isBot) {
            isBotThinking = true
            delay(500)

            // 1. Bot rolls dice
            soundManager.playDiceRoll(settings.soundEnabled)
            soundManager.vibrateMove(settings.vibrationEnabled)
            isRolling = true
            delay(350)
            isRolling = false

            val rolledState = engine.rollDice()
            gameState = rolledState

            // 2. If legal moves exist, bot picks move
            if (rolledState.selectableTokenIds.isNotEmpty()) {
                delay(600)
                val botMoveTokenId = bot.decideMove(rolledState, botDifficulty)
                if (botMoveTokenId != null) {
                    val prev = gameState
                    gameState = engine.moveToken(botMoveTokenId)
                    handleMoveAudioAndHaptics(prev, gameState, soundManager, settings.soundEnabled, settings.vibrationEnabled)
                }
            }
            isBotThinking = false
        }
    }

    fun handleRoll() {
        if (!gameState.canRoll || gameState.isGameOver || isRolling || isBotThinking) return
        val currentP = gameState.currentPlayer

        // In nearby multiplayer, verify it's our turn
        if (isNearbyMode && currentP.color != localColor) return

        scope.launch {
            soundManager.playDiceRoll(settings.soundEnabled)
            soundManager.vibrateMove(settings.vibrationEnabled)
            isRolling = true
            delay(350)
            isRolling = false

            if (isNearbyMode) {
                if (isHost) {
                    gameState = engine.rollDice()
                    multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
                } else {
                    multiplayerManager.sendAction("LUDO_ROLL", "")
                }
            } else {
                gameState = engine.rollDice()
            }
        }
    }

    fun handleTokenSelect(tokenId: Int) {
        if (gameState.canRoll || gameState.isGameOver || isBotThinking) return
        val currentP = gameState.currentPlayer

        // In nearby mode, verify it's our turn
        if (isNearbyMode && currentP.color != localColor) return
        if (tokenId !in gameState.selectableTokenIds) return

        val prev = gameState
        if (isNearbyMode) {
            if (isHost) {
                gameState = engine.moveToken(tokenId)
                handleMoveAudioAndHaptics(prev, gameState, soundManager, settings.soundEnabled, settings.vibrationEnabled)
                multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
            } else {
                multiplayerManager.sendAction("LUDO_MOVE", tokenId.toString())
            }
        } else {
            gameState = engine.moveToken(tokenId)
            handleMoveAudioAndHaptics(prev, gameState, soundManager, settings.soundEnabled, settings.vibrationEnabled)
        }
    }

    fun handleRematch() {
        if (isNearbyMode) {
            if (isHost) {
                engine.resetGame(selectedMode, playerCount, customUsernames)
                gameState = engine.state
                matchRecorded = false
                showVictoryDialog = false
                multiplayerManager.sendAction("LUDO_SYNC", serializeLudoState(gameState))
            } else {
                multiplayerManager.sendAction("LUDO_REMATCH", "")
            }
        } else {
            engine.resetGame(selectedMode, playerCount, customUsernames)
            gameState = engine.state
            matchRecorded = false
            showVictoryDialog = false
        }
    }

    val isMyTurn = !isNearbyMode || gameState.currentPlayer.color == localColor

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
                selectableTokenIds = if (isMyTurn && !isBotThinking) gameState.selectableTokenIds else emptyList(),
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
                .border(1.5.dp, Color(gameState.currentPlayer.color.primaryColorHex).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isBotThinking) "🤖 Bot is deliberating..." else gameState.statusText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(gameState.currentPlayer.color.darkColorHex),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dice Control
        LudoDiceView(
            diceValue = gameState.diceRoll,
            canRoll = gameState.canRoll && isMyTurn && !isBotThinking && !gameState.isGameOver,
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
                        text = "All 4 ${winningColor.displayName} tokens successfully reached the Home Center!",
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

private fun handleMoveAudioAndHaptics(
    prev: LudoGameState,
    current: LudoGameState,
    soundManager: LudoSoundManager,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean
) {
    if (current.winner != null) {
        soundManager.playVictory(soundEnabled)
        soundManager.vibrateVictory(vibrationEnabled)
        return
    }

    if (current.lastCapturedColor != null) {
        soundManager.playCapture(soundEnabled)
        soundManager.vibrateCapture(vibrationEnabled)
        return
    }

    // Check if any token finished
    val prevFinished = prev.players.sumOf { it.finishedCount }
    val currentFinished = current.players.sumOf { it.finishedCount }
    if (currentFinished > prevFinished) {
        soundManager.playHomeFinish(soundEnabled)
        soundManager.vibrateMove(vibrationEnabled)
        return
    }

    soundManager.playTokenMove(soundEnabled)
    soundManager.vibrateMove(vibrationEnabled)
}
