package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.support.DeveloperSupportConfig
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.DarkThemeColors
import com.example.ui.theme.LightThemeColors

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    val colors = ChillTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = if (colors.isDark) 0.14f else 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.06f),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.06f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sticky Top Header (stays cleanly anchored below status bar when keyboard opens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(15.dp))
                        .bouncyClickable(onClick = onBack)
                        .testTag("settings_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = colors.primaryText
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = colors.primaryText
                    )
                    Text(
                        text = "Customize your profile, sound & theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Scrollable Settings Body inside weight(1f) so IME resizes smoothly without overlapping header
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // PLAYER IDENTITY / USERNAME GROUP
                SettingsSectionHeader(title = "PLAYER IDENTITY")
                Spacer(modifier = Modifier.height(10.dp))

                SettingsCardContainer {
                    Column {
                        val activeInitial = settings.username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(colors.accent, colors.secondaryAccent)
                                        )
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeInitial,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom Username",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "Your name across games, lobbies & scoreboards",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        var usernameInput by remember(settings.username) { mutableStateOf(settings.username) }
                        val trimmedInput = usernameInput.trim()
                        val isChanged = trimmedInput != settings.username && trimmedInput.isNotEmpty()
                        val isBlank = usernameInput.isBlank()
                        val remainingChars = (16 - usernameInput.length).coerceAtLeast(0)
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
                                Text("Enter your username", color = colors.secondaryText.copy(alpha = 0.7f))
                            },
                            singleLine = true,
                            isError = isBlank,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (trimmedInput.isNotEmpty()) {
                                        viewModel.setUsername(trimmedInput)
                                        usernameInput = trimmedInput
                                        showSavedBadge = true
                                        focusManager.clearFocus()
                                        Toast.makeText(
                                            context,
                                            "Username saved locally as \"$trimmedInput\"! 🎮",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                errorBorderColor = colors.error,
                                focusedContainerColor = colors.background,
                                unfocusedContainerColor = colors.background,
                                errorContainerColor = colors.background,
                                cursorColor = colors.accent
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
                                            tint = colors.secondaryText
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (isBlank) {
                                            Text(
                                                text = "Username cannot be empty",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (isChanged) {
                                            Text(
                                                text = "● Unsaved changes",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.accent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = "✓ Battle name active",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = colors.success,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "$remainingChars left",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (remainingChars <= 3) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = when {
                                            remainingChars == 0 -> colors.error
                                            remainingChars <= 3 -> colors.warning
                                            else -> colors.secondaryText
                                        },
                                        maxLines = 1,
                                        modifier = Modifier.testTag("setting_characters_remaining")
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        ChunkyButton(
                            text = when {
                                showSavedBadge -> "Saved! ✓"
                                isChanged -> "Save Username"
                                else -> "Username Saved ✓"
                            },
                            onClick = {
                                if (trimmedInput.isNotEmpty()) {
                                    viewModel.setUsername(trimmedInput)
                                    usernameInput = trimmedInput
                                    showSavedBadge = true
                                    focusManager.clearFocus()
                                    Toast.makeText(
                                        context,
                                        "Username saved locally as \"$trimmedInput\"! 🎮",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = trimmedInput.isNotEmpty() && !showSavedBadge && isChanged,
                            backgroundColor = if (showSavedBadge) colors.success else colors.accent,
                            disabledBackgroundColor = colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.10f),
                            disabledContentColor = colors.accent.copy(alpha = if (colors.isDark) 0.85f else 0.70f),
                            buttonHeight = 48.dp,
                            fontSize = 15.sp,
                            cornerRadius = 15.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_username_button")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // FEEL & SOUND GROUP
                SettingsSectionHeader(title = "FEEL & SOUND")
                Spacer(modifier = Modifier.height(10.dp))

                SettingsCardContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Sound Effects Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = if (settings.soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                    tint = colors.accent
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Sound Effects",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "Placement beeps and win/loss instrumentals",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.soundEnabled,
                                onCheckedChange = { viewModel.toggleSound(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.secondaryText,
                                    uncheckedTrackColor = colors.surfaceVariant,
                                    uncheckedBorderColor = colors.border
                                ),
                                modifier = Modifier.testTag("setting_sound_toggle")
                            )
                        }

                        HorizontalDivider(color = colors.borderLight, thickness = 1.dp)

                        // Vibration Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Rounded.Vibration,
                                    tint = colors.secondaryAccent
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Vibration",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "Haptic feedback for match outcomes and moves",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.vibrationEnabled,
                                onCheckedChange = { viewModel.toggleVibration(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.secondaryText,
                                    uncheckedTrackColor = colors.surfaceVariant,
                                    uncheckedBorderColor = colors.border
                                ),
                                modifier = Modifier.testTag("setting_haptic_toggle")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // APPEARANCE / THEME GROUP
                SettingsSectionHeader(title = "APPEARANCE")
                Text(
                    text = "Choose Auto (system), Light, or Dark mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        label = "Auto",
                        subtitle = "System",
                        icon = Icons.Rounded.BrightnessAuto,
                        previewBgTop = LightThemeColors.background,
                        previewBgBottom = DarkThemeColors.background,
                        previewAccent = colors.accent,
                        isSelected = settings.themeMode.equals("SYSTEM", ignoreCase = true),
                        onClick = { viewModel.setThemeMode("SYSTEM") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_SYSTEM")
                    )
                    ThemeOptionCard(
                        label = "Light",
                        subtitle = "Crisp",
                        icon = Icons.Rounded.LightMode,
                        previewBgTop = LightThemeColors.background,
                        previewBgBottom = LightThemeColors.surface,
                        previewAccent = LightThemeColors.accent,
                        isSelected = settings.themeMode.equals("LIGHT", ignoreCase = true),
                        onClick = { viewModel.setThemeMode("LIGHT") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_LIGHT")
                    )
                    ThemeOptionCard(
                        label = "Dark",
                        subtitle = "Midnight",
                        icon = Icons.Rounded.DarkMode,
                        previewBgTop = DarkThemeColors.background,
                        previewBgBottom = DarkThemeColors.surface,
                        previewAccent = DarkThemeColors.accent,
                        isSelected = settings.themeMode.equals("DARK", ignoreCase = true),
                        onClick = { viewModel.setThemeMode("DARK") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_DARK")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // FRESH START / RESET SCOREBOARD GROUP
                SettingsSectionHeader(title = "FRESH START")
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surface,
                    shadowElevation = if (colors.isDark) 4.dp else 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = colors.error.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(
                            icon = Icons.Rounded.DeleteSweep,
                            tint = colors.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset Scoreboard",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.primaryText
                            )
                            Text(
                                text = "Clear your match records and start over",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.error.copy(alpha = if (colors.isDark) 0.20f else 0.12f))
                                .border(1.dp, colors.error.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .bouncyClickable { showResetDialog = true }
                                .testTag("reset_stats_button")
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // DEVELOPER SUPPORT GROUP
                SettingsSectionHeader(title = "DEVELOPER SUPPORT")
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = if (colors.isDark) 6.dp else 4.dp,
                    color = colors.coffeeContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, colors.coffeeBorder, RoundedCornerShape(22.dp))
                        .bouncyClickable {
                            try {
                                val uri = Uri.parse(DeveloperSupportConfig.BUY_ME_A_COFFEE_URL)
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
                        .testTag("setting_buy_coffee_row")
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
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
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
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Buy Me a Coffee",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = colors.primaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enjoying BroBattle? Support our development!",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.coffeeAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingsCardContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
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
                        SettingsIconBadge(
                            icon = Icons.Rounded.BugReport,
                            tint = colors.secondaryAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bug or Suggestions",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.primaryText
                            )
                            Text(
                                text = "Help improve BroBattle",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = colors.surface,
            modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(26.dp)),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.error.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Wipe Scoreboard?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = colors.primaryText
                )
            },
            text = {
                Text(
                    text = "All your previous match records will be wiped clean. Ready for a blank slate?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                ChunkyButton(
                    text = "Yes, Wipe Records",
                    onClick = {
                        viewModel.clearStats()
                        showResetDialog = false
                    },
                    backgroundColor = colors.error,
                    buttonHeight = 48.dp,
                    modifier = Modifier.testTag("confirm_reset_button")
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = colors.secondaryText
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = ChillTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = colors.secondaryText
    )
}

@Composable
private fun SettingsCardContainer(
    content: @Composable () -> Unit
) {
    val colors = ChillTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        shadowElevation = if (colors.isDark) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color
) {
    val colors = ChillTheme.colors
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tint.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ThemeOptionCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    previewBgTop: Color,
    previewBgBottom: Color,
    previewAccent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChillTheme.colors
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.border,
        label = "theme_card_border_$label"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.selectedSurface else colors.surface,
        label = "theme_card_bg_$label"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        modifier = modifier
            .height(106.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .bouncyClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Mini Theme Swatch Preview
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(previewBgTop, previewBgBottom)
                            )
                        )
                        .border(1.dp, colors.border, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = previewAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isSelected) colors.accent else colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }
        }
    }
}
