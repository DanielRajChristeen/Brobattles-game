package com.example.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.model.MatchRecord
import com.example.core.registry.GameRegistry
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPink
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.PlayfulYellow
import com.example.ui.theme.ToyboxBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val matches by viewModel.matchHistory.collectAsState()
    val totalMatches by viewModel.totalMatchesCount.collectAsState()

    val p1Wins = remember(matches) { matches.count { it.winner == "PLAYER_1" } }
    val winRate = remember(matches, totalMatches) {
        if (totalMatches > 0) "${(p1Wins * 100 / totalMatches)}%" else "100%"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onBack)
                    .testTag("stats_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Trophy Room 🏆",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Scoreboard & brag rights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Playful Triple Stat Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlayfulStatBadge(
                        emoji = "🏆",
                        label = "WINS",
                        value = p1Wins.toString(),
                        accentColor = PlayfulYellow,
                        modifier = Modifier.weight(1f)
                    )
                    PlayfulStatBadge(
                        emoji = "🔥",
                        label = "BATTLES",
                        value = totalMatches.toString(),
                        accentColor = PlayfulOrange,
                        modifier = Modifier.weight(1f)
                    )
                    PlayfulStatBadge(
                        emoji = "😎",
                        label = "WIN RATE",
                        value = winRate,
                        accentColor = PlayfulPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "RECENT BATTLES",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (matches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, ToyboxBorder, RoundedCornerShape(26.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🎮", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No battles yet!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Grab a friend or play the bot to fill your trophy shelf.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            ChunkyButton(
                                text = "LET'S PLAY! 🚀",
                                onClick = onBack,
                                backgroundColor = PlayfulPurple,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                        }
                    }
                }
            } else {
                items(matches) { match ->
                    PlayfulMatchItem(match)
                }
            }
        }
    }
}

@Composable
private fun PlayfulStatBadge(
    emoji: String,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = accentColor
            )
        }
    }
}

@Composable
private fun PlayfulMatchItem(match: MatchRecord) {
    val dateStr = remember(match.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(match.timestamp))
    }

    val isWinner = match.winner == "PLAYER_1"
    val isDraw = match.winner == "DRAW"
    val gameDef = remember(match.gameId) { GameRegistry.getGameById(match.gameId) }
    val accentColor = remember(gameDef) { gameDef?.let { Color(it.accentHex) } ?: PlayfulPurple }

    val isXo = match.gameId.equals("xo", ignoreCase = true) || match.gameTitle.contains("Tic Tac Toe", ignoreCase = true)
    val isLocal2Player = match.mode.equals("LOCAL_1V1", ignoreCase = true) ||
        match.mode.contains("LOCAL", ignoreCase = true) ||
        match.mode.contains("Pass the phone", ignoreCase = true) ||
        match.mode.contains("2 Player", ignoreCase = true)
    val isP1Winner = match.winner.equals("PLAYER_1", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, ToyboxBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("match_item_card_${match.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left game icon badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = gameDef?.emoji ?: "🎮", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.gameTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${match.mode} · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Outcome & Score
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val pillText = when {
                    isDraw -> "TIE 🤝"
                    isXo && isLocal2Player -> if (isP1Winner) "Player 1 wins" else "Player 2 wins"
                    isWinner -> "YOU WON 🎉"
                    else -> "OOF 😵"
                }

                val pillBg = when {
                    isDraw -> MaterialTheme.colorScheme.surfaceVariant
                    isXo && isLocal2Player -> if (isP1Winner) PlayfulPurple.copy(alpha = 0.18f) else PlayfulOrange.copy(alpha = 0.18f)
                    isWinner -> PlayfulGreen.copy(alpha = 0.18f)
                    else -> PlayfulOrange.copy(alpha = 0.18f)
                }

                val pillTextColor = when {
                    isDraw -> MaterialTheme.colorScheme.onSurfaceVariant
                    isXo && isLocal2Player -> if (isP1Winner) PlayfulPurple else PlayfulOrange
                    isWinner -> PlayfulGreen
                    else -> PlayfulOrange
                }

                PlayfulPill(
                    text = pillText,
                    backgroundColor = pillBg,
                    textColor = pillTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${match.player1Score} - ${match.player2Score}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
