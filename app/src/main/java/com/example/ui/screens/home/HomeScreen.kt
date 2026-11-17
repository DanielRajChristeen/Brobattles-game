package com.example.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.registry.GameDefinition
import com.example.core.registry.GameRegistry
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    username: String = "Player",
    onNavigateToGame: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    var comingSoonGame by remember { mutableStateOf<GameDefinition?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = ChillTheme.colors

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            BroBattleDrawerSheet(
                username = username,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onNavigateHome = { },
                onNavigateToProfile = onNavigateToSettings,
                onNavigateToScoreboard = onNavigateToStats,
                onNavigateToSettings = onNavigateToSettings,
                onOpenAboutDialog = { showAboutDialog = true }
            )
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.05f),
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.15f, size.height * 0.05f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.secondaryAccent.copy(alpha = if (colors.isDark) 0.12f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.9f, size.height * 0.12f),
                            radius = size.width * 0.6f
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(size.width * 0.9f, size.height * 0.12f)
                    )
                }
        ) {
            val columnCount = when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }
            val horizontalPadding = if (maxWidth < 360.dp) 14.dp else 18.dp
            val gridSpacing = if (maxWidth < 360.dp) 12.dp else 14.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 12.dp,
                    bottom = 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("home_game_grid")
            ) {
                // Gaming Hub Header
                item(span = { GridItemSpan(columnCount) }) {
                    GamingHubHeader(
                        username = username,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToStats = onNavigateToStats
                    )
                }

                // Section label
                item(span = { GridItemSpan(columnCount) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = colors.secondaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "CHOOSE YOUR ARENA",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = colors.primaryText
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.border, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${GameRegistry.games.size} Games",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }

                // Game Selection Grid
                items(
                    items = GameRegistry.games,
                    key = { it.id }
                ) { game ->
                    GameSelectionCard(
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

            // Coming Soon Dialog (preserved)
            comingSoonGame?.let { game ->
                AlertDialog(
                    onDismissRequest = { comingSoonGame = null },
                    shape = RoundedCornerShape(26.dp),
                    containerColor = colors.surface,
                    icon = {
                        val isDark = colors.isDark
                        val currentIconRes = game.iconRes(isDark)
                        if (currentIconRes != null) {
                            Image(
                                painter = painterResource(id = currentIconRes),
                                contentDescription = game.title,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Text(text = game.emoji, fontSize = 42.sp)
                        }
                    },
                    title = {
                        Text(
                            text = "${game.title} is coming soon!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            color = colors.primaryText
                        )
                    },
                    text = {
                        Text(
                            text = "Tic Tac Toe is ready right now! Jump into Tic Tac Toe to battle the bot or play a friend.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = colors.secondaryText
                        )
                    },
                    confirmButton = {
                        ChunkyButton(
                            text = "Play Tic Tac Toe",
                            onClick = {
                                comingSoonGame = null
                                onNavigateToGame("xo")
                            },
                            backgroundColor = colors.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_ok_button")
                        )
                    }
                )
            }

            if (showAboutDialog) {
                AboutBroBattleDialog(
                    onDismiss = { showAboutDialog = false }
                )
            }
        }
    }
}

@Composable
private fun GamingHubHeader(
    username: String,
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val colors = ChillTheme.colors
    val cleanUsername = username.trim().ifBlank { "Player" }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.secondaryBackground,
        shadowElevation = if (colors.isDark) 8.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = if (colors.isDark) 0.45f else 0.25f),
                        colors.border,
                        colors.secondaryAccent.copy(alpha = if (colors.isDark) 0.40f else 0.22f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.08f),
                            Color.Transparent,
                            colors.secondaryAccent.copy(alpha = if (colors.isDark) 0.14f else 0.06f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderActionIconButton(
                    icon = Icons.Rounded.Menu,
                    contentDescription = "Open navigation menu",
                    tint = colors.primaryText,
                    containerColor = colors.surface,
                    borderColor = colors.border,
                    onClick = onOpenDrawer,
                    testTag = "home_menu_button"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF06162F))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(colors.accent, colors.secondaryAccent)
                            ),
                            shape = RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.brobattle_app_icon),
                        contentDescription = "BroBattle Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(15.dp))
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BROBATTLE",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp
                        ),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f),
                                        colors.secondaryAccent.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = 0.45f),
                                        colors.secondaryAccent.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.secondaryAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No Internet. Just Rivalry.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            ),
                            color = if (colors.isDark) Color(0xFFE0F7FA) else colors.secondaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Player Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.background.copy(alpha = if (colors.isDark) 0.55f else 0.78f))
                    .border(1.dp, colors.borderLight, RoundedCornerShape(14.dp))
                    .clickable(onClick = onNavigateToSettings)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (cleanUsername == "Player") "Playing as Player" else "Ready, $cleanUsername",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Offline & Wi-Fi 1v1",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeaderActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(15.dp))
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GameSelectionCard(
    game: GameDefinition,
    onClick: () -> Unit
) {
    val accentColor = Color(game.accentHex)
    val colors = ChillTheme.colors
    val isDark = colors.isDark
    val currentIconRes = game.iconRes(isDark)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "card_scale_${game.id}"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) accentColor else accentColor.copy(alpha = if (isDark) 0.36f else 0.25f),
        label = "card_border_${game.id}"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        shadowElevation = if (isDark) 6.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("game_card_${game.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isDark) 0.14f else 0.08f),
                            colors.surface
                        )
                    )
                )
                .padding(10.dp)
        ) {
            // Game Illustration Artwork Frame (1:1 aspect ratio + ContentScale.Fit avoids zooming/cropping 512x512 icons)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.secondaryBackground)
                    .border(
                        width = 1.dp,
                        color = colors.borderLight,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentIconRes != null) {
                    Image(
                        painter = painterResource(id = currentIconRes),
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = game.emoji,
                        fontSize = 46.sp
                    )
                }

                if (!game.isAvailable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SOON",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp
                            ),
                            color = colors.secondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Game Title & Action Indicator Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = game.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
