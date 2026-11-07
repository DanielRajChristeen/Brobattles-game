package com.example.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.multiplayer.LocalWifiMultiplayerManager
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.ui.components.ChunkyButton
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder

/**
 * Dialog rendered during active Nearby multiplayer when an opponent temporarily loses connection,
 * screen-locks their phone, or exits the app.
 *
 * Implements a 10-second countdown grace window allowing the opponent to return and resume
 * gameplay seamlessly without dumping players back to the lobby immediately.
 */
@Composable
fun NearbyReconnectDialog(
    manager: LocalWifiMultiplayerManager,
    onExitToLobby: () -> Unit
) {
    val isPaused by manager.isConnectionPaused.collectAsState()
    val remainingSeconds by manager.pauseRemainingSeconds.collectAsState()
    val pauseReason by manager.pauseReason.collectAsState()
    val lobbyState by manager.lobbyState.collectAsState()
    val colors = ChillTheme.colors

    val isDisconnected = lobbyState is MultiplayerLobbyState.Disconnected
    val shouldShow = isPaused || isDisconnected

    if (!shouldShow) return

    val isWaiting = isPaused && remainingSeconds != null && (remainingSeconds ?: 0) > 0
    val disconnectReason = (lobbyState as? MultiplayerLobbyState.Disconnected)?.reason ?: pauseReason

    AlertDialog(
        onDismissRequest = {
            // Prevent accidental outside dismissal during active countdown
            if (!isWaiting) {
                manager.exitLobbyFromGrace()
                onExitToLobby()
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, colors.border, RoundedCornerShape(24.dp))
            .testTag("nearby_reconnect_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isWaiting) PlayfulOrange.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWaiting) Icons.Default.HourglassBottom else Icons.Default.WifiOff,
                        contentDescription = "Connection Status",
                        tint = if (isWaiting) PlayfulOrange else Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = if (isWaiting) "Game Paused ⏳" else "Opponent Disconnected",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isWaiting) {
                    Text(
                        text = "Opponent temporarily lost connection (they may have locked their screen or minimized the app).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 10s Countdown Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PlayfulPurple.copy(alpha = 0.1f))
                            .border(1.5.dp, PlayfulPurple.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = PlayfulPurple
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "WAITING: ${remainingSeconds ?: 10}s",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = PlayfulPurple
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Auto-resumes when opponent unlocks/reopens app!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PlayfulGreen,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (disconnectReason.isNotBlank()) disconnectReason else "The 10-second reconnect window expired. Opponent did not return to the game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (isWaiting) {
                TextButton(
                    onClick = {
                        manager.exitLobbyFromGrace()
                        onExitToLobby()
                    },
                    modifier = Modifier.testTag("exit_grace_early_button")
                ) {
                    Text(
                        text = "Exit to Lobby Now",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ChunkyButton(
                    text = "Back to Lobby 🏠",
                    onClick = {
                        manager.exitLobbyFromGrace()
                        onExitToLobby()
                    },
                    backgroundColor = PlayfulPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("back_to_lobby_button")
                )
            }
        }
    )
}
