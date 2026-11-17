package com.example.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.core.registry.GameRegistry
import com.example.core.support.DeveloperSupportConfig
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme

@Composable
fun BroBattleDrawerSheet(
    username: String,
    onCloseDrawer: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToScoreboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenAboutDialog: () -> Unit
) {
    val context = LocalContext.current
    val colors = ChillTheme.colors
    val displayName = username.trim().ifBlank { "Player" }
    val avatarInitial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "P"

    ModalDrawerSheet(
        drawerContainerColor = colors.secondaryBackground,
        drawerContentColor = colors.primaryText,
        modifier = Modifier
            .widthIn(min = 280.dp, max = 336.dp)
            .fillMaxHeight()
            .testTag("brobattle_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Drawer Gaming Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.surface,
                                colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.10f),
                                colors.secondaryAccent.copy(alpha = if (colors.isDark) 0.14f else 0.08f)
                            )
                        )
                    )
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp)
                    .testTag("drawer_header")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.brobattle_app_icon),
                                contentDescription = "BroBattle Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color(0xFF06162F))
                                    .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BroBattle",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "No Internet. Just Rivalry.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.secondaryAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onCloseDrawer,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("drawer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close menu",
                                tint = colors.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.borderLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Player Avatar & Identity Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.background.copy(alpha = if (colors.isDark) 0.55f else 0.85f))
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .clickable {
                                onCloseDrawer()
                                onNavigateToProfile()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(colors.accent, colors.secondaryAccent)
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarInitial,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black
                                ),
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Player Identity",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ARENA MENU",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            DrawerNavItem(
                icon = Icons.Rounded.Home,
                label = "Home",
                isSelected = true,
                accentColor = colors.accent,
                onClick = {
                    onCloseDrawer()
                    onNavigateHome()
                },
                testTag = "drawer_item_home"
            )
            DrawerNavItem(
                icon = Icons.Rounded.Person,
                label = "Player Identity",
                accentColor = colors.secondaryAccent,
                onClick = {
                    onCloseDrawer()
                    onNavigateToProfile()
                },
                testTag = "drawer_item_profile"
            )
            DrawerNavItem(
                icon = Icons.Rounded.EmojiEvents,
                label = "Scoreboard & History",
                accentColor = Color(0xFFF59E0B),
                onClick = {
                    onCloseDrawer()
                    onNavigateToScoreboard()
                },
                testTag = "drawer_item_scoreboard"
            )
            DrawerNavItem(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                accentColor = colors.accent,
                onClick = {
                    onCloseDrawer()
                    onNavigateToSettings()
                },
                testTag = "drawer_item_settings"
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = colors.borderLight, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "COMMUNITY & INFO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            DrawerNavItem(
                icon = Icons.Rounded.Info,
                label = "About BroBattle",
                accentColor = colors.secondaryAccent,
                onClick = {
                    onCloseDrawer()
                    onOpenAboutDialog()
                },
                testTag = "drawer_item_about"
            )
            DrawerNavItem(
                icon = Icons.Rounded.Share,
                label = "Share App",
                accentColor = colors.success,
                onClick = {
                    onCloseDrawer()
                    shareBroBattleApp(context)
                },
                testTag = "drawer_item_share"
            )
            DrawerNavItem(
                icon = Icons.Rounded.StarRate,
                label = "Rate Us",
                accentColor = Color(0xFFF59E0B),
                onClick = {
                    onCloseDrawer()
                    openPlayStoreListing(context)
                },
                testTag = "drawer_item_rate"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Prominent Elevated Buy Me a Coffee Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                shadowElevation = if (colors.isDark) 6.dp else 4.dp,
                color = colors.coffeeContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, colors.coffeeBorder, RoundedCornerShape(20.dp))
                    .bouncyClickable {
                        onCloseDrawer()
                        openExternalUrl(
                            context = context,
                            url = DeveloperSupportConfig.BUY_ME_A_COFFEE_URL,
                            errorMessage = "Could not open browser. Please try again later."
                        )
                    }
                    .testTag("drawer_buy_coffee_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.coffeeAccent.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.coffeeAccent.copy(alpha = 0.18f))
                            .border(1.dp, colors.coffeeAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalCafe,
                            contentDescription = "Buy Me a Coffee",
                            tint = colors.coffeeAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buy Me a Coffee",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enjoying BroBattle? Support our development!",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background.copy(alpha = if (colors.isDark) 0.45f else 0.7f))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("drawer_footer"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.primaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Made with ❤️ by Two Men Works",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onCloseDrawer()
                            openExternalUrl(
                                context = context,
                                url = DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL,
                                errorMessage = "Could not open browser. Please check connection."
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Feedback,
                        contentDescription = null,
                        tint = colors.secondaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Feedback & Suggestions",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.secondaryAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = ChillTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) colors.selectedSurface else Color.Transparent
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.20f) else colors.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else colors.secondaryText,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (isSelected) colors.primaryText else colors.primaryText.copy(alpha = 0.90f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!isSelected) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.secondaryText.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AboutBroBattleDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = ChillTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = colors.surface,
        modifier = Modifier
            .border(1.dp, colors.border, RoundedCornerShape(26.dp))
            .testTag("about_brobattle_dialog"),
        icon = {
            Image(
                painter = painterResource(id = R.drawable.brobattle_app_icon),
                contentDescription = "BroBattle Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF06162F))
                    .border(1.5.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BroBattle",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = colors.primaryText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "No Internet. Just Rivalry.",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.secondaryAccent,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Play ${GameRegistry.games.size} classic tabletop & arcade duels completely offline — challenge smart bots, pass-and-play with friends, or connect nearby on Local Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} • Made with ❤️ by Two Men Works",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primaryText
                    )
                }
            }
        },
        confirmButton = {
            ChunkyButton(
                text = "Back to Arena",
                onClick = onDismiss,
                backgroundColor = colors.accent,
                buttonHeight = 48.dp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = {
                    openExternalUrl(
                        context = context,
                        url = DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL,
                        errorMessage = "Could not open browser. Please check connection."
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Send Feedback",
                    color = colors.secondaryAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

internal fun openExternalUrl(
    context: Context,
    url: String,
    errorMessage: String
) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun shareBroBattleApp(context: Context) {
    try {
        val shareText = "Challenge me on BroBattle! Play ${GameRegistry.games.size} offline & Local Wi-Fi duel games — No Internet. Just Rivalry.\nhttps://play.google.com/store/apps/details?id=${context.packageName}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BroBattle — Offline & Local Duel Games")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(sendIntent, "Share BroBattle").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open share options.", Toast.LENGTH_SHORT).show()
    }
}

private fun openPlayStoreListing(context: Context) {
    val pkg = context.packageName
    try {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(marketIntent)
    } catch (_: Exception) {
        openExternalUrl(
            context = context,
            url = "https://play.google.com/store/apps/details?id=$pkg",
            errorMessage = "Play Store is unavailable on this device."
        )
    }
}
