package com.example.ui.screens.detail.ludo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.ludo.LudoColor
import com.example.ui.components.bouncyClickable
import com.example.ui.screens.detail.snakesandladders.Real3DCubeDie
import com.example.ui.theme.ToyboxBorder

/**
 * 3D-to-2D Physical Board-Game Die and Roll control for Ludo.
 * Directly reuses the physical 3D perspective cube die from Snake and Ladders
 * with custom player-color bevel glow and seamless rolling animations.
 */
@Composable
fun LudoDiceView(
    diceValue: Int?,
    canRoll: Boolean,
    isRolling: Boolean,
    playerColor: LudoColor,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = Color(playerColor.primaryColorHex)
    val displayValue = (diceValue ?: 6).coerceIn(1, 6)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Physical 3D Cube Die (Identical rendering & tumbling physics to Snake & Ladders)
        Real3DCubeDie(
            targetFace = displayValue,
            isRolling = isRolling,
            canRoll = canRoll,
            onClick = onRollClick,
            dieSize = 82.dp,
            accentColor = themeColor,
            testTag = "ludo_dice_cube"
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Large "ROLL DICE" tactile button
        Box(
            modifier = Modifier
                .shadow(if (canRoll) 6.dp else 0.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (canRoll) {
                        Brush.verticalGradient(
                            colors = listOf(themeColor, Color(playerColor.darkColorHex))
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                .border(
                    2.dp,
                    if (canRoll) Color(playerColor.darkColorHex) else ToyboxBorder,
                    RoundedCornerShape(20.dp)
                )
                .bouncyClickable(enabled = canRoll, onClick = onRollClick)
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .testTag("ludo_roll_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRolling) "ROLLING... 🎲" else if (canRoll) "ROLL DICE 🎲" else "PICK SOLDIER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = if (canRoll) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
