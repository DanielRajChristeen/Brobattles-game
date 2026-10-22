package com.example.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.support.DeveloperSupportConfig
import com.example.core.support.RewardedAdManager
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.ToyboxBorder

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RewardedAdManager.initialize(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, ToyboxBorder, CircleShape)
                    .bouncyClickable(onClick = onBack)
                    .testTag("settings_back_button"),
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
                    text = "BroBattles Settings ⚙️",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tweak your vibes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // PLAYER IDENTITY / USERNAME GROUP
        Text(
            text = "PLAYER IDENTITY",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PlayfulPurple.copy(alpha = 0.14f))
                            .border(1.5.dp, PlayfulPurple.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👤", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Username",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Your name across games, lobbies & scoreboards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                var usernameInput by remember(settings.username) { mutableStateOf(settings.username) }
                val trimmedInput = usernameInput.trim()
                val isChanged = trimmedInput != settings.username && trimmedInput.isNotEmpty()
                var showSavedBadge by remember { mutableStateOf(false) }

                LaunchedEffect(showSavedBadge) {
                    if (showSavedBadge) {
                        kotlinx.coroutines.delay(2000)
                        showSavedBadge = false
                    }
                }

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { newValue ->
                        if (newValue.length <= 16) {
                            usernameInput = newValue
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setting_username_input"),
                    placeholder = {
                        Text("Enter your username")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (trimmedInput.isNotEmpty()) {
                                viewModel.setUsername(trimmedInput)
                                showSavedBadge = true
                                Toast.makeText(
                                    context,
                                    "Username saved locally as \"$trimmedInput\"! 🎮",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlayfulPurple,
                        unfocusedBorderColor = ToyboxBorder,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    trailingIcon = {
                        if (usernameInput.isNotEmpty()) {
                            IconButton(
                                onClick = { usernameInput = "" },
                                modifier = Modifier.testTag("setting_clear_username")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear username",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (usernameInput.isBlank()) {
                            "Username cannot be empty"
                        } else {
                            "${usernameInput.length}/16 characters"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (usernameInput.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    ChunkyButton(
                        text = if (showSavedBadge) "Saved! ✓" else "Save Username",
                        onClick = {
                            if (trimmedInput.isNotEmpty()) {
                                viewModel.setUsername(trimmedInput)
                                showSavedBadge = true
                                Toast.makeText(
                                    context,
                                    "Username saved locally as \"$trimmedInput\"! 🎮",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = trimmedInput.isNotEmpty() && (!showSavedBadge && (isChanged || usernameInput != settings.username)),
                        backgroundColor = if (showSavedBadge) PlayfulGreen else PlayfulPurple,
                        buttonHeight = 44.dp,
                        fontSize = 14.sp,
                        cornerRadius = 14.dp,
                        modifier = Modifier.testTag("save_username_button")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sound & Haptic Group
        Text(
            text = "FEEL & SOUND",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "🔊", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sound Effects",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Placement beeps and win/loss instrumentals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { viewModel.toggleSound(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlayfulPurple
                        ),
                        modifier = Modifier.testTag("setting_sound_toggle")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ToyboxBorder)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "📳", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Vibration",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Haptic feedback for match outcomes and moves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { viewModel.toggleVibration(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlayfulPurple
                        ),
                        modifier = Modifier.testTag("setting_haptic_toggle")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Look & Feel Theme Group
        Text(
            text = "LOOK & FEEL",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayfulThemeBubble(
                label = "Auto",
                emoji = "⚙️",
                isSelected = settings.themeMode.equals("SYSTEM", ignoreCase = true),
                onClick = { viewModel.setThemeMode("SYSTEM") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_option_SYSTEM")
            )
            PlayfulThemeBubble(
                label = "Light",
                emoji = "☀️",
                isSelected = settings.themeMode.equals("LIGHT", ignoreCase = true),
                onClick = { viewModel.setThemeMode("LIGHT") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_option_LIGHT")
            )
            PlayfulThemeBubble(
                label = "Dark",
                emoji = "🌙",
                isSelected = settings.themeMode.equals("DARK", ignoreCase = true),
                onClick = { viewModel.setThemeMode("DARK") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_option_DARK")
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Reset Scoreboard Group
        Text(
            text = "FRESH START",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🧹", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset Scoreboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Clear your match records and start over",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlayfulOrange.copy(alpha = 0.15f))
                        .bouncyClickable { showResetDialog = true }
                        .testTag("reset_stats_button")
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = PlayfulOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Developer Support Group
        Text(
            text = "DEVELOPER SUPPORT",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, ToyboxBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // ROW 1: Bug or Suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable {
                            try {
                                val uri = Uri.parse(DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL)
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Could not open browser. Please check connection.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .testTag("setting_feedback_row")
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🐞", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bug or Suggestions",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Help improve BroBattles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ToyboxBorder)
                )

                // ROW 2: Buy me a Tea
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable {
                            val activity = context.findActivity()
                            if (activity != null) {
                                RewardedAdManager.showDeveloperSupportRewardedAd(
                                    activity = activity,
                                    onRewardEarned = {
                                        Toast.makeText(
                                            context,
                                            "Thanks for supporting BroBattles! 🍵",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onAdDismissedWithoutReward = {
                                        Toast.makeText(
                                            context,
                                            "Ad wasn't completed.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onAdUnavailable = {
                                        Toast.makeText(
                                            context,
                                            "Tea break unavailable right now. Try again later.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "Tea break unavailable right now. Try again later.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .testTag("setting_buy_tea_row")
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🍵", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buy me a Tea",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Support the developer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Text(text = "🧹", fontSize = 40.sp) },
            title = {
                Text(
                    text = "Wipe Scoreboard?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "All your previous match records will be wiped clean. Ready for a blank slate?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ChunkyButton(
                    text = "Yes, Wipe! 🧹",
                    onClick = {
                        viewModel.clearStats()
                        showResetDialog = false
                    },
                    backgroundColor = PlayfulOrange,
                    modifier = Modifier.testTag("confirm_reset_button")
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun PlayfulThemeBubble(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = com.example.ui.theme.ChillTheme.colors
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) colors.selectedSurface else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.5.dp else 1.5.dp,
                color = if (isSelected) colors.accent else colors.border,
                shape = RoundedCornerShape(20.dp)
            )
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                ),
                color = if (isSelected) colors.accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

