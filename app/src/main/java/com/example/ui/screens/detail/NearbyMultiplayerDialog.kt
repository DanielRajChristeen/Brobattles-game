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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder

@Composable
fun NearbyMultiplayerDialog(
    game: GameDefinition,
    manager: LocalWifiMultiplayerManager,
    targetScore: Int = game.defaultTargetScore,
    onDismiss: () -> Unit,
    onGameStarted: () -> Unit
) {
    val lobbyState by manager.lobbyState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Host, 1 = Join
    var manualIp by remember { mutableStateOf("") }
    var showDirectConnect by remember { mutableStateOf(false) }

    // Start appropriate mode when tab changes or initially opens
    DisposableEffect(selectedTab) {
        if (selectedTab == 0) {
            manager.startHosting(gameId = game.id, gameTitle = game.title)
        } else {
            manager.startDiscovery()
        }
        onDispose {
            // keep alive unless dialog dismissed
        }
    }

    // Auto-advance safely when game starts
    androidx.compose.runtime.LaunchedEffect(lobbyState) {
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

                Spacer(modifier = Modifier.height(18.dp))

                // Content based on selected tab
                if (selectedTab == 0) {
                    HostLobbyContent(
                        game = game,
                        manager = manager,
                        lobbyState = lobbyState,
                        localIp = manager.getLocalIpAddress(),
                        onStartGame = {
                            val config = org.json.JSONObject().apply {
                                put("targetScore", targetScore)
                                put("gameId", game.id)
                                put("gameTitle", game.title)
                                put("chessHostColor", manager.chessHostColor.value)
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
                        onConnectSession = { session -> manager.connectToSession(session) }
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
    onStartGame: () -> Unit
) {
    val isConnected = lobbyState is MultiplayerLobbyState.Connected
    val players = when (lobbyState) {
        is MultiplayerLobbyState.Hosting -> lobbyState.players
        is MultiplayerLobbyState.Connected -> lobbyState.players
        else -> emptyList()
    }
    val opponent = players.firstOrNull { !it.isHost }
    val isChess = game.id.equals("chess", ignoreCase = true)
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isConnected) "Player connected! Tap Start Game to launch." else "Connect other phones to this phone's Wi-Fi or Hotspot, then have them tap Join Game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        // Chess Color Selection (Host only)
        if (isChess) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "HOST COLOR ASSIGNMENT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: White for Host (Default)
                val isWhiteSelected = chessHostColor == "WHITE"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isWhiteSelected) PlayfulPurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isWhiteSelected) 2.dp else 1.5.dp,
                            color = if (isWhiteSelected) PlayfulPurple else ToyboxBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .bouncyClickable {
                            manager.setChessHostColor("WHITE")
                        }
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                        .testTag("chess_host_color_white"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "♔", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "WHITE ⚪",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isWhiteSelected) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "You: White • Opponent: Black",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Option 2: Black for Host
                val isBlackSelected = chessHostColor == "BLACK"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBlackSelected) PlayfulPurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isBlackSelected) 2.dp else 1.5.dp,
                            color = if (isBlackSelected) PlayfulPurple else ToyboxBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .bouncyClickable {
                            manager.setChessHostColor("BLACK")
                        }
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                        .testTag("chess_host_color_black"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "♚", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BLACK ⚫",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isBlackSelected) PlayfulPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "You: Black • Opponent: White",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "PLAYERS (${players.size} / 2)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val hostRoleLabel = if (isChess) {
            if (chessHostColor == "WHITE") "HOST • WHITE ⚪" else "HOST • BLACK ⚫"
        } else "HOST"

        val clientRoleLabel = if (isChess) {
            if (chessHostColor == "WHITE") "PLAYER 2 • BLACK ⚫" else "PLAYER 2 • WHITE ⚪"
        } else "PLAYER 2"

        // Host player item
        PlayerItemRow(
            name = players.firstOrNull { it.isHost }?.name ?: "Player 1",
            roleLabel = hostRoleLabel,
            isReady = true,
            isHost = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Client player item
        if (opponent != null) {
            PlayerItemRow(
                name = opponent.name,
                roleLabel = clientRoleLabel,
                isReady = true,
                isHost = false
            )
        } else {
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
                        text = "Waiting for players...",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Start game button
        ChunkyButton(
            text = if (isConnected) "START GAME 🚀" else "WAITING FOR PLAYERS...",
            onClick = {
                if (isConnected) onStartGame()
            },
            enabled = isConnected,
            backgroundColor = if (isConnected) PlayfulGreen else MaterialTheme.colorScheme.surfaceVariant,
            testTag = "host_start_match_button"
        )
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
    val sessions = (lobbyState as? MultiplayerLobbyState.Discovering)?.discoveredSessions ?: emptyList()
    val isChess = game.id.equals("chess", ignoreCase = true)
    val chessHostColor by manager.chessHostColor.collectAsState()

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

                    if (isChess) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("chess_joiner_color_card")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ASSIGNED COLOR",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = if (chessHostColor == "WHITE") "You: BLACK ⚫ • Host: White ⚪" else "You: WHITE ⚪ • Host: Black ⚫",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PlayfulPurple
                                    )
                                }
                                Text(
                                    text = if (chessHostColor == "WHITE") "♚" else "♔",
                                    fontSize = 22.sp
                                )
                            }
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
