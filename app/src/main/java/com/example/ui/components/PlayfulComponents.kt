package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChillTheme

/**
 * Adds a physical, bouncy scale and tactile press reaction.
 */
@Composable
fun Modifier.bouncyClickable(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "bouncy_scale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Chunky, tactile arcade-style push button with bottom 3D shadow depth.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    depthColor: Color = lerp(backgroundColor, Color.Black, 0.32f),
    disabledBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    leadingEmoji: String? = null,
    buttonHeight: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    cornerRadius: Dp = 18.dp,
    testTag: String = "chunky_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val offsetY by animateFloatAsState(
        targetValue = if (isPressed && enabled) 3.5f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "button_depth"
    )
    val themeColors = ChillTheme.colors

    Box(
        modifier = modifier
            .testTag(testTag)
            .bouncyClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                pressedScale = 0.985f,
                onClick = onClick
            )
    ) {
        // Bottom 3D shadow block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .offset(y = 3.5.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (enabled) depthColor else themeColors.border.copy(alpha = 0.5f)
                )
        )

        // Top button face
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .offset { IntOffset(0, offsetY.dp.roundToPx()) }
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    brush = if (enabled) {
                        Brush.verticalGradient(
                            colors = listOf(
                                lerp(backgroundColor, Color.White, 0.08f),
                                backgroundColor
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(disabledBackgroundColor, disabledBackgroundColor)
                        )
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = if (enabled) Color.White.copy(alpha = 0.28f) else themeColors.border,
                    shape = RoundedCornerShape(cornerRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (leadingEmoji != null) {
                    Text(
                        text = leadingEmoji,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (enabled) contentColor else disabledContentColor
                )
            }
        }
    }
}

/**
 * Friendly Pill Badge with clean borders and soft background.
 */
@Composable
fun PlayfulPill(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, textColor.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

/**
 * Large tactile Emoji Icon Badge for Game tiles.
 */
@Composable
fun GameEmojiBadge(
    emoji: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        accentColor.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.45f).sp
        )
    }
}
