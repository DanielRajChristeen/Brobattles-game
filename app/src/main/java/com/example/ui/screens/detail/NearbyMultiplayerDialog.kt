package com.example.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.multiplayer.DiscoveredSession
import com.example.core.multiplayer.LocalWifiMultiplayerManager
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.registry.GameDefinition
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder

@Composable
fun NearbyMultiplayerDialog(
    game: GameDefinition,
    manager: LocalWifiMultiplayerManager,
    targetScore: Int = game.defaultTargetScore,
    username: String = manager.localDeviceName,
    ludoPlayerCount: Int = 4,
    snakesPlayerCount: Int = 2,
    dotBoardSize: DotBoardSize = DotBoardSize.MEDIUM,
    onDismiss: () -> Unit,
    onGameStarted: () -> Unit
) {
    val lobbyState by manager.lobbyState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Host, 1 = Join

    // Pre-Settings configured by Host before game launch
    var hostTargetScore by remember(targetScore) { mutableIntStateOf(targetScore) }
    var hostLudoPlayerCount by remember(ludoPlayerCount) { mutableIntStateOf(ludoPlayerCount) }
    var hostSnakesPlayerCount by remember(snakesPlayerCount) { mutableIntStateOf(snakesPlayerCount) }
    var hostDotBoardSize by remember(dotBoardSize) { mutableStateOf(dotBoardSize) }
    var hostCheckersTarget by remember { mutableIntStateOf(1) }
    var hostChessTarget by remember { mutableIntStateOf(1) }
    var hostBattleshipTarget by remember { mutableIntStateOf(1) }

    fun broadcastPreview() {
        val preview = org.json.JSONObject().apply {
            put("targetScore", hostTargetScore)
            put("ludoPlayerCount", hostLudoPlayerCount)
            put("snakesPlayerCount", hostSnakesPlayerCount)
            put("dotBoardSize", hostDotBoardSize.name)
            put("checkersTarget", hostCheckersTarget)
            put("chessTarget", hostChessTarget)
            put("battleshipTarget", hostBattleshipTarget)
            put("chessHostColor", manager.chessHostColor.value)
        }.toString()
        manager.broadcastHostConfigPreview(preview)
    }

    // Start appropriate mode when tab changes or initially opens
    DisposableEffect(selectedTab) {
        if (selectedTab == 0) {
            val maxOverride = when {
                game.id.equals("ludo", ignoreCase = true) -> hostLudoPlayerCount
                game.id.equals("snakes_and_ladders", ignoreCase = true) -> hostSnakesPlayerCount
                else -> 2
            }
            manager.startHosting(
                gameId = game.id,
                gameTitle = game.title,
                hostName = username,
                maxPlayersOverride = maxOverride
            )
            broadcastPreview()
        } else {
            manager.startDiscovery()
        }
        onDispose {
            // keep alive unless dialog dismissed
        }
    }

    // Auto-advance safely when game starts
    LaunchedEffect(lobbyState) {
        if (lobbyState is MultiplayerLobbyState.InGame) {
            onGameStarted()
        }
    }

    Dialog(onDismissRequest = {
        manager.disconnect()
        onDismiss()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, ToyboxBorder, RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PlayfulPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Local Wi-Fi",
                                tint = PlayfulPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local Wi-Fi Battle",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            manager.disconnect()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_multiplayer_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Tabs (HOST vs JOIN)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val hostBg by animateColorAsState(
                        targetValue = if (selectedTab == 0) PlayfulPurple else Color.Transparent,
                        label = "hostTab"
                    )
                    val joinBg by animateColorAsState(
                        targetValue = if (selectedTab == 1) PlayfulPurple else Color.Transparent,
                        label = "joinTab"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(hostBg)
                            .bouncyClickable {
                                if (selectedTab != 0) {
                                    selectedTab = 0
                                }
                            }
                            .testTag("tab_host_game"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👑 HOST GAME",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                            color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(joinBg)
                            .bouncyClickable {
                                if (selectedTab != 1) {
                                    selectedTab = 1
                                }
                            }
                            .testTag("tab_join_game"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔍 JOIN GAME",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                            color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on selected tab
                if (selectedTab == 0) {
                    HostLobbyContent(
                        game = game,
                        manager = manager,
                        lobbyState = lobbyState,
                        localIp = manager.getLocalIpAddress(),
                        hostTargetScore = hostTargetScore,
                        onTargetScoreChange = {
                            hostTargetScore = it
                            broadcastPreview()
                        },
                        hostLudoPlayerCount = hostLudoPlayerCount,
                        onLudoPlayerCountChange = {
                            hostLudoPlayerCount = it
                            manager.updateHostRoomCapacity(it)
                            broadcastPreview()
                        },
                        hostSnakesPlayerCount = hostSnakesPlayerCount,
                        onSnakesPlayerCountChange = {
                            hostSnakesPlayerCount = it
                            manager.updateHostRoomCapacity(it)
                            broadcastPreview()
                        },
                        hostDotBoardSize = hostDotBoardSize,
                        onDotBoardSizeChange = {
                            hostDotBoardSize = it
                            broadcastPreview()
                        },
                        hostCheckersTarget = hostCheckersTarget,
                        onCheckersTargetChange = {
                            hostCheckersTarget = it
                            broadcastPreview()
                        },
                        hostChessTarget = hostChessTarget,
                        onChessTargetChange = {
                            hostChessTarget = it
                            broadcastPreview()
                        },
                        hostBattleshipTarget = hostBattleshipTarget,
                        onBattleshipTargetChange = {
                            hostBattleshipTarget = it
                            broadcastPreview()
                        },
                        onStartGame = {
                            val config = org.json.JSONObject().apply {
                                put("gameId", game.id)
                                put("gameTitle", game.title)
                                put("targetScore", hostTargetScore)
                                put("chessHostColor", manager.chessHostColor.value)
                                put("ludoPlayerCount", hostLudoPlayerCount)
                                put("snakesPlayerCount", hostSnakesPlayerCount)
                                put("dotBoardSize", hostDotBoardSize.name)
                                put("checkersTargetScore", hostCheckersTarget)
                                put("chessTargetScore", hostChessTarget)
                                put("battleshipTargetScore", hostBattleshipTarget)
                            }.toString()
                            manager.launchGame(config)
                        }
                    )
                } else {
                    JoinLobbyContent(
                        game = game,
                        manager = manager,
                        lobbyState = lobbyState,
                        onRefreshDiscovery = { manager.startDiscovery() },
                        onConnectSession = { session -> manager.connectToSession(session, playerName = username) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HostLobbyContent(
    game: GameDefinition,
    manager: LocalWifiMultiplayerManager,
    lobbyState: MultiplayerLobbyState,
    localIp: String,
    hostTargetScore: Int,
    onTargetScoreChange: (Int) -> Unit,
    hostLudoPlayerCount: Int,
    onLudoPlayerCountChange: (Int) -> Unit,
    hostSnakesPlayerCount: Int,
    onSnakesPlayerCountChange: (Int) -> Unit,
    hostDotBoardSize: DotBoardSize,
    onDotBoardSizeChange: (DotBoardSize) -> Unit,
    hostCheckersTarget: Int,
    onCheckersTargetChange: (Int) -> Unit,
    hostChessTarget: Int,
    onChessTargetChange: (Int) -> Unit,
    hostBattleshipTarget: Int,
    onBattleshipTargetChange: (Int) -> Unit,
    onStartGame: () -> Unit
) {
    val isConnected = lobbyState is MultiplayerLobbyState.Connected
    val players = when (lobbyState) {
        is MultiplayerLobbyState.Hosting -> lobbyState.players
        is MultiplayerLobbyState.Connected -> lobbyState.players
        else -> emptyList()
    }
    val isChess = game.id.equals("chess", ignoreCase = true)
    val isSnakes = game.id.equals("snakes_and_ladders", ignoreCase = true)
    val isLudo = game.id.equals("ludo", ignoreCase = true)
    val isCheckers = game.id.equals("checkers", ignoreCase = true)

    val maxPlayers = when {
        isSnakes -> hostSnakesPlayerCount
        isLudo -> hostLudoPlayerCount
        else -> 2
    }
    val canStart = players.size >= 2

    val chessHostColor by manager.chessHostColor.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Game and network helper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PlayfulPurple.copy(alpha = 0.08f))
                .border(1.5.dp, PlayfulPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Game: ${game.title}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "HOST 👑",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = PlayfulPurple
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isConnected) "Players connected! Tap Start Game when ready." else "Connect other phones to this phone's Wi-Fi or Hotspot, then have them tap Join Game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // HOST MATCH PRE-SETTINGS SECTION
        // ==========================================
        HostMatchPreSettingsSection(
            game = game,
            hostTargetScore = hostTargetScore,
            onTargetScoreChange = onTargetScoreChange,
            hostLudoPlayerCount = hostLudoPlayerCount,
            onLudoPlayerCountChange = onLudoPlayerCountChange,
            hostSnakesPlayerCount = hostSnakesPlayerCount,
            onSnakesPlayerCountChange = onSnakesPlayerCountChange,
            hostDotBoardSize = hostDotBoardSize,
            onDotBoardSizeChange = onDotBoardSizeChange,
            hostCheckersTarget = hostCheckersTarget,
            onCheckersTargetChange = onCheckersTargetChange,
            hostChessTarget = hostChessTarget,
            onChessTargetChange = onChessTargetChange,
            hostBattleshipTarget = hostBattleshipTarget,
            onBattleshipTargetChange = onBattleshipTargetChange,
            manager = manager
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "PLAYERS (${players.size} / $maxPlayers)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val snakesEmojis = listOf("🔴", "🔵", "🟢", "🟡", "🟣")
        val ludoRoles = listOf("HOST • RED 🔴", "PLAYER 2 • GREEN 🟢", "PLAYER 3 • YELLOW 🟡", "PLAYER 4 • BLUE 🔵")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            players.forEachIndexed { idx, player ->
                val roleLabel = when {
                    isChess -> if (player.isHost) {
                        if (chessHostColor == "WHITE") "HOST • WHITE ⚪" else "HOST • BLACK ⚫"
                    } else {
                        if (chessHostColor == "WHITE") "PLAYER 2 • BLACK ⚫" else "PLAYER 2 • WHITE ⚪"
                    }
                    isCheckers -> if (player.isHost) {
                        "HOST • RED 🔴"
                    } else {
                        "PLAYER 2 • BLACK ⚫"
                    }
                    isSnakes -> {
                        val token = snakesEmojis.getOrElse(idx) { "🎮" }
                        if (player.isHost) "HOST • Player 1 $token" else "Player ${idx + 1} $token"
                    }
                    isLudo -> {
                        ludoRoles.getOrElse(idx) { "Player ${idx + 1}" }
                    }
                    else -> if (player.isHost) "HOST" else "PLAYER 2"
                }

                PlayerItemRow(
                    name = player.name,
                    roleLabel = roleLabel,
                    isReady = true,
                    isHost = player.isHost
                )
            }

            if (players.size < maxPlayers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PlayfulPurple
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isSnakes || isLudo) "Waiting for players... (${maxPlayers - players.size} slots open)" else "Waiting for players...",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Start game button
        val startButtonText = when {
            canStart && (isSnakes || isLudo) -> "START GAME (${players.size} PLAYERS) 🚀"
            canStart -> "START GAME 🚀"
            else -> "WAITING FOR PLAYERS..."
        }

        ChunkyButton(
            text = startButtonText,
            onClick = {
                if (canStart) onStartGame()
            },
            enabled = canStart,
            backgroundColor = if (canStart) PlayfulGreen else MaterialTheme.colorScheme.surfaceVariant,
            testTag = "host_start_match_button"
        )
    }
}

@Composable
private fun HostMatchPreSettingsSection(
    game: GameDefinition,
    hostTargetScore: Int,
    onTargetScoreChange: (Int) -> Unit,
    hostLudoPlayerCount: Int,
    onLudoPlayerCountChange: (Int) -> Unit,
    hostSnakesPlayerCount: Int,
    onSnakesPlayerCountChange: (Int) -> Unit,
    hostDotBoardSize: DotBoardSize,
    onDotBoardSizeChange: (DotBoardSize) -> Unit,
    hostCheckersTarget: Int,
    onCheckersTargetChange: (Int) -> Unit,
    hostChessTarget: Int,
    onChessTargetChange: (Int) -> Unit,
    hostBattleshipTarget: Int,
    onBattleshipTargetChange: (Int) -> Unit,
    manager: LocalWifiMultiplayerManager
) {
    val chessHostColor by manager.chessHostColor.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = PlayfulPurple,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "HOST MATCH PRE-SETTINGS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 1. Ludo Player Count (2, 3, 4 players)
        if (game.id.equals("ludo", ignoreCase = true)) {
            Text(
                text = "ROOM PLAYER CAPACITY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2 to "2P 🔴🟢", 3 to "3P 🔴🟢🟡", 4 to "4P 🔴🟢🟡🔵").forEach { (count, label) ->
                    val isSel = hostLudoPlayerCount == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onLudoPlayerCountChange(count) }
                            .testTag("host_ludo_count_$count"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 2. Snakes & Ladders Player Count (2–5 players)
        if (game.id.equals("snakes_and_ladders", ignoreCase = true)) {
            Text(
                text = "ROOM PLAYER CAPACITY (2–5)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(2 to "2P 🔴🔵", 3 to "3P 🔴🔵🟢", 4 to "4-PLY", 5 to "5-PLY").forEach { (count, label) ->
                    val isSel = hostSnakesPlayerCount == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onSnakesPlayerCountChange(count) }
                            .testTag("host_snakes_count_$count"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 3. Dot Connections Board Size (3x3, 4x4, 5x5 boxes)
        if (game.id.equals("dot_connections", ignoreCase = true)) {
            Text(
                text = "BOARD GRID SIZE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    DotBoardSize.SMALL to "3×3 (4×4 dots)",
                    DotBoardSize.MEDIUM to "4×4 (5×5 dots)",
                    DotBoardSize.LARGE to "5×5 (6×6 dots)"
                ).forEach { (size, label) ->
                    val isSel = hostDotBoardSize == size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onDotBoardSizeChange(size) }
                            .testTag("host_dot_size_${size.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = size.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${size.dots}×${size.dots} dots",
                                fontSize = 9.sp,
                                color = if (isSel) PlayfulPurple.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Tic Tac Toe (XO) Target Rounds
        if (game.id.equals("xo", ignoreCase = true)) {
            Text(
                text = "MATCH TARGET (ROUNDS)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3 to "3 ROUNDS", 5 to "5 ROUNDS", 10 to "10 ROUNDS").forEach { (rounds, label) ->
                    val isSel = hostTargetScore == rounds
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onTargetScoreChange(rounds) }
                            .testTag("host_xo_target_$rounds"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 5. Checkers Target Matches & Board Info
        if (game.id.equals("checkers", ignoreCase = true)) {
            Text(
                text = "MATCH TARGET",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "1 GAME", 3 to "BEST OF 3", 5 to "BEST OF 5").forEach { (target, label) ->
                    val isSel = hostCheckersTarget == target
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onCheckersTargetChange(target) }
                            .testTag("host_checkers_target_$target"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Board: Standard 8×8 Draughts with mandatory captures & multi-jumps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 6. Chess Host Color & Match Target
        if (game.id.equals("chess", ignoreCase = true)) {
            Text(
                text = "HOST COLOR ASSIGNMENT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isWhiteSelected = chessHostColor == "WHITE"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isWhiteSelected) PlayfulPurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .border(if (isWhiteSelected) 2.dp else 1.5.dp, if (isWhiteSelected) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                        .bouncyClickable { manager.setChessHostColor("WHITE") }
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                        .testTag("chess_host_color_white"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "♔ WHITE ⚪",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isWhiteSelected) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = "You: White • Opponent: Black", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                val isBlackSelected = chessHostColor == "BLACK"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBlackSelected) PlayfulPurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .border(if (isBlackSelected) 2.dp else 1.5.dp, if (isBlackSelected) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                        .bouncyClickable { manager.setChessHostColor("BLACK") }
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                        .testTag("chess_host_color_black"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "♚ BLACK ⚫",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isBlackSelected) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = "You: Black • Opponent: White", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 7. Tug of War Target Score
        if (game.id.equals("tug_of_war", ignoreCase = true)) {
            Text(
                text = "TARGET WINS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "1 WIN", 3 to "3 WINS", 5 to "5 WINS").forEach { (target, label) ->
                    val isSel = hostTargetScore == target
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onTargetScoreChange(target) }
                            .testTag("host_tug_target_$target"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 8. Battleship Match Target
        if (game.id.equals("battleship", ignoreCase = true)) {
            Text(
                text = "MATCH TARGET",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "1 BATTLE", 3 to "BEST OF 3").forEach { (target, label) ->
                    val isSel = hostBattleshipTarget == target
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PlayfulPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) PlayfulPurple else ToyboxBorder, RoundedCornerShape(12.dp))
                            .bouncyClickable { onBattleshipTargetChange(target) }
                            .testTag("host_battleship_target_$target"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isSel) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• 10×10 Ocean Grid (5 Ships: Carrier, Battleship, Cruiser, Sub, Destroyer)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun JoinLobbyContent(
    game: GameDefinition,
    manager: LocalWifiMultiplayerManager,
    lobbyState: MultiplayerLobbyState,
    onRefreshDiscovery: () -> Unit,
    onConnectSession: (DiscoveredSession) -> Unit
) {
    val isConnected = lobbyState is MultiplayerLobbyState.Connected
    val isConnecting = lobbyState is MultiplayerLobbyState.Connecting
    val rawSessions = (lobbyState as? MultiplayerLobbyState.Discovering)?.discoveredSessions ?: emptyList()
    val sessions = rawSessions.filter { it.gameId.equals(game.id, ignoreCase = true) }
    val isChess = game.id.equals("chess", ignoreCase = true)
    val isCheckers = game.id.equals("checkers", ignoreCase = true)
    val chessHostColor by manager.chessHostColor.collectAsState()
    val hostConfigPreviewStr by manager.hostConfigPreview.collectAsState()

    val hostConfigObj = remember(hostConfigPreviewStr) {
        if (hostConfigPreviewStr.isNotBlank()) {
            try { org.json.JSONObject(hostConfigPreviewStr) } catch (_: Exception) { null }
        } else null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AVAILABLE SESSIONS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            IconButton(
                onClick = onRefreshDiscovery,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = PlayfulPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isConnected) {
            val connectedState = lobbyState as MultiplayerLobbyState.Connected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlayfulGreen.copy(alpha = 0.12f))
                    .border(1.5.dp, PlayfulGreen, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Connected to ${connectedState.session.hostName}! 🎉",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = PlayfulGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Waiting for host to press Start Game...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    // Host Match Settings Preview Card for Guest
                    Spacer(modifier = Modifier.height(10.dp))
                    val hostSettingsDesc = when {
                        isChess -> "Host: ${if (chessHostColor == "WHITE") "White ⚪" else "Black ⚫"} (You: ${if (chessHostColor == "WHITE") "Black ⚫" else "White ⚪"})"
                        isCheckers -> "Host: RED 🔴 (You: BLACK ⚫) • 8×8 Draughts"
                        game.id.equals("ludo", ignoreCase = true) -> "Room Capacity: ${hostConfigObj?.optInt("ludoPlayerCount", connectedState.session.maxPlayers) ?: connectedState.session.maxPlayers} Players"
                        game.id.equals("snakes_and_ladders", ignoreCase = true) -> "Room Capacity: ${hostConfigObj?.optInt("snakesPlayerCount", connectedState.session.maxPlayers) ?: connectedState.session.maxPlayers} Players"
                        game.id.equals("dot_connections", ignoreCase = true) -> {
                            val sizeName = hostConfigObj?.optString("dotBoardSize", "MEDIUM") ?: "MEDIUM"
                            "Board Size: $sizeName Grid"
                        }
                        game.id.equals("xo", ignoreCase = true) -> "Match Target: ${hostConfigObj?.optInt("targetScore", 5) ?: 5} Rounds"
                        game.id.equals("tug_of_war", ignoreCase = true) -> "Target: First to ${hostConfigObj?.optInt("targetScore", 1) ?: 1} Wins"
                        game.id.equals("battleship", ignoreCase = true) -> "Match: 10×10 Ocean Fleet Battle"
                        else -> "Host-managed rules"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, ToyboxBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("host_settings_summary_card")
                    ) {
                        Column {
                            Text(
                                text = "👑 HOST'S MATCH SETTINGS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hostSettingsDesc,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = PlayfulPurple
                            )
                        }
                    }
                }
            }
        } else if (isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PlayfulPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Connecting to host...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        } else {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PlayfulPurple
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Searching for games...",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect to your friend's Wi-Fi network or Hotspot, then wait for their game to appear here.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        DiscoveredSessionItem(
                            session = session,
                            onJoin = { onConnectSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerItemRow(
    name: String,
    roleLabel: String,
    isReady: Boolean,
    isHost: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, ToyboxBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isHost) PlayfulOrange.copy(alpha = 0.2f) else PlayfulGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isHost) "👑" else "🎮",
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (isHost) PlayfulOrange else PlayfulGreen
                    )
                }
            }

            if (isReady) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(PlayfulGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ready",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredSessionItem(
    session: DiscoveredSession,
    onJoin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, ToyboxBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.hostName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${session.gameTitle} • ${session.currentPlayers} / ${session.maxPlayers} players",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PlayfulGreen)
                    .bouncyClickable { onJoin() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JOIN",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
            }
        }
    }
}
