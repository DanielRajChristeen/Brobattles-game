package com.example.ui.screens.detail

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.core.multiplayer.RejoinRequest
import com.example.core.multiplayer.RejoinRequestStatus
import com.example.ui.components.ChunkyButton
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange

/**
 * Dialog rendered during active Nearby multiplayer when an opponent temporarily loses connection,
 * screen-locks their phone, or exits the app — and on the Host side whenever a disconnected
 * original player sends an incoming rejoin request.
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
    val isSessionHost by manager.isSessionHost.collectAsState()
    val pendingRejoinRequests by manager.pendingRejoinRequests.collectAsState()
    val rejoinStatus by manager.rejoinRequestStatus.collectAsState()
    val lastMatchSession by manager.lastMatchSession.collectAsState()
    val colors = ChillTheme.colors

    val isDisconnected = lobbyState is MultiplayerLobbyState.Disconnected
    val hasIncomingRejoin = isSessionHost && pendingRejoinRequests.isNotEmpty()
    val shouldShow = isPaused || isDisconnected || hasIncomingRejoin

    if (!shouldShow) return

    val isWaiting = isPaused && remainingSeconds != null && (remainingSeconds ?: 0) > 0
    val disconnectReason = (lobbyState as? MultiplayerLobbyState.Disconnected)?.reason ?: pauseReason

    val dialogTitle = when {
        hasIncomingRejoin -> "Rejoin Request"
        isWaiting -> "Game Paused ⏳"
        else -> "Opponent Disconnected"
    }

    AlertDialog(
        onDismissRequest = {
            // Prevent accidental outside dismissal during active countdown or pending host approval
            if (!isWaiting && !hasIncomingRejoin && rejoinStatus !is RejoinRequestStatus.Pending) {
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
                val badgeColor = when {
                    hasIncomingRejoin -> PlayfulGreen
                    isWaiting -> PlayfulOrange
                    else -> Color(0xFFEF4444)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            hasIncomingRejoin -> Icons.Default.PersonAdd
                            isWaiting -> Icons.Default.HourglassBottom
                            else -> Icons.Default.WifiOff
                        },
                        contentDescription = "Connection Status",
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Host side: Incoming Rejoin Request cards (shown immediately in real time)
                if (hasIncomingRejoin) {
                    pendingRejoinRequests.forEach { request ->
                        IncomingRejoinRequestCard(
                            request = request,
                            onAccept = { manager.acceptRejoinRequest(request.playerId) },
                            onReject = { manager.rejectRejoinRequest(request.playerId) }
                        )
                    }
                }

                if (isWaiting && !hasIncomingRejoin) {
                    Text(
                        text = if (isSessionHost) {
                            "Opponent temporarily lost connection. Waiting for them to send a rejoin request..."
                        } else {
                            "Connection to host interrupted. You can request to rejoin the ongoing match."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // 10s Countdown Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "WAITING: ${remainingSeconds ?: 10}s",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isSessionHost) {
                                    "Incoming rejoin requests will appear here automatically."
                                } else {
                                    "Tap Request Rejoin below to reconnect to the match."
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PlayfulGreen,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (!hasIncomingRejoin) {
                    Text(
                        text = if (disconnectReason.isNotBlank()) {
                            disconnectReason
                        } else {
                            "Opponent disconnected from the match."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Client side: Rejoin request status & action
                if (!isSessionHost) {
                    when (val status = rejoinStatus) {
                        is RejoinRequestStatus.Pending -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PlayfulOrange.copy(alpha = 0.12f))
                                    .border(1.5.dp, PlayfulOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                                    .testTag("rejoin_pending_banner"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = PlayfulOrange
                                    )
                                    Text(
                                        text = "Rejoin request sent. Waiting for host approval...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        is RejoinRequestStatus.Rejected -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                    .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                                    .testTag("rejoin_rejected_banner"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.reason,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFEF4444),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {}
                    }

                    if (lastMatchSession != null && rejoinStatus !is RejoinRequestStatus.Pending) {
                        Button(
                            onClick = { manager.requestRejoin(lastMatchSession) },
                            colors = ButtonDefaults.buttonColors(containerColor = PlayfulGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("request_rejoin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Request Rejoin",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Request Rejoin",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isWaiting || hasIncomingRejoin) {
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
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("back_to_lobby_button")
                )
            }
        }
    )
}

@Composable
internal fun IncomingRejoinRequestCard(
    request: RejoinRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlayfulGreen.copy(alpha = 0.1f))
            .border(1.5.dp, PlayfulGreen.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("rejoin_request_card_${request.playerId}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PlayfulGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Rejoin Request",
                        tint = PlayfulGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.playerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Requested to rejoin (${request.role.replace("_", " ")})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reject_rejoin_${request.playerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reject Rejoin",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reject",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = Color(0xFFEF4444)
                    )
                }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = PlayfulGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_rejoin_${request.playerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Accept Rejoin",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Accept",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                }
            }
        }
    }
}
