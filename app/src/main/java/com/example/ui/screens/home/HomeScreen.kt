package com.example.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.registry.GameDefinition
import com.example.core.registry.GameRegistry
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder

@Composable
fun HomeScreen(
    username: String = "Player",
    onNavigateToGame: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    var comingSoonGame by remember { mutableStateOf<GameDefinition?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 16.dp,
                bottom = 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Ultra-simple Header (full width span)
            item(span = { GridItemSpan(2) }) {
                PlayfulSimpleHeader(
                    username = username,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToStats = onNavigateToStats
                )
            }

            // 2-Column Game Grid
            items(GameRegistry.games) { game ->
                CleanGameGridTile(
                    game = game,
                    onClick = {
                        if (game.isAvailable) {
                            onNavigateToGame(game.id)
                        } else {
                            comingSoonGame = game
                        }
                    }
                )
            }
        }

        // Lightweight playful dialog when tapping a coming soon game
        comingSoonGame?.let { game ->
            AlertDialog(
                onDismissRequest = { comingSoonGame = null },
                shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                icon = {
                    val isDark = ChillTheme.colors.isDark
                    val currentIconRes = game.iconRes(isDark)
                    if (currentIconRes != null) {
                        Image(
                            painter = painterResource(id = currentIconRes),
                            contentDescription = game.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(text = game.emoji, fontSize = 42.sp)
                    }
                },
                title = {
                    Text(
                        text = "${game.title} is coming soon! 👀",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Tic Tac Toe is ready right now! Jump into Tic Tac Toe to battle the bot or play a friend.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Play Tic Tac Toe 🚀",
                        onClick = {
                            comingSoonGame = null
                            onNavigateToGame("xo")
                        },
                        backgroundColor = PlayfulPurple,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_ok_button")
                    )
                }
            )
        }
    }
}

@Composable
private fun PlayfulSimpleHeader(
    username: String = "Player",
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Wifi play Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "WIFI PLAY",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (username.isNotBlank() && username != "Player") "Ready, $username!" else "No Internet. Just Rivalry.",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = PlayfulPurple
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Trophy button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onNavigateToStats)
                    .testTag("home_stats_button"),
                contentAlignment = Alignment.Center
            ) {
                Text("🏆", fontSize = 20.sp)
            }

            // Settings button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onNavigateToSettings)
                    .testTag("home_settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙️", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun CleanGameGridTile(
    game: GameDefinition,
    onClick: () -> Unit
) {
    val accentColor = Color(game.accentHex)
    val isDark = ChillTheme.colors.isDark
    val currentIconRes = game.iconRes(isDark)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (game.isAvailable) 2.dp else 1.5.dp,
                color = if (game.isAvailable) accentColor.copy(alpha = 0.45f) else ToyboxBorder,
                shape = RoundedCornerShape(26.dp)
            )
            .bouncyClickable(onClick = onClick)
            .testTag("game_card_${game.id}"),
        contentAlignment = Alignment.Center
    ) {
        if (currentIconRes != null) {
            Image(
                painter = painterResource(id = currentIconRes),
                contentDescription = game.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.emoji,
                    fontSize = 52.sp
                )
            }
        }

        // Optional tiny SOON pill top-end
        if (!game.isAvailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
