package com.example.ui.screens.detail.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.ludo.LudoColor
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ToyboxBorder

@Composable
fun LudoDiceView(
    diceValue: Int?,
    canRoll: Boolean,
    isRolling: Boolean,
    playerColor: LudoColor,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRolling) {
        if (isRolling) {
            rotationAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
            rotationAnim.snapTo(0f)
        }
    }

    val themeColor = Color(playerColor.primaryColorHex)
    val displayValue = diceValue ?: 6

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 3D Styled Die Face
        Box(
            modifier = Modifier
                .size(68.dp)
                .rotate(rotationAnim.value)
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F0))
                    )
                )
                .border(2.5.dp, if (canRoll) themeColor else ToyboxBorder, RoundedCornerShape(18.dp))
                .bouncyClickable(enabled = canRoll, onClick = onRollClick)
                .testTag("ludo_dice_cube"),
            contentAlignment = Alignment.Center
        ) {
            DicePips(displayValue, pipColor = if (diceValue == null) Color.Gray else Color(0xFF1E293B))
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Large "ROLL DICE" Button
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
                            colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
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
                text = if (isRolling) "ROLLING... 🎲" else if (canRoll) "ROLL DICE 🎲" else "PICK TOKEN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = if (canRoll) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DicePips(value: Int, pipColor: Color) {
    val pipSize = 10.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        when (value) {
            1 -> {
                Box(
                    modifier = Modifier
                        .size(pipSize * 1.3f)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)) // Red center pip
                        .align(Alignment.Center)
                )
            }
            2 -> {
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomEnd))
            }
            3 -> {
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.Center))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomEnd))
            }
            4 -> {
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopEnd))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomEnd))
            }
            5 -> {
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.TopEnd))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.Center))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomStart))
                Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.BottomEnd))
            }
            6 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize(0.5f)) {
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor))
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor))
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor))
                        }
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.End))
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.End))
                            Box(modifier = Modifier.size(pipSize).clip(CircleShape).background(pipColor).align(Alignment.End))
                        }
                    }
                }
            }
        }
    }
}
