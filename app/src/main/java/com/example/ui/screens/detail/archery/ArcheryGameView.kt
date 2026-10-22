package com.example.ui.screens.detail.archery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.archery.ArcheryBot
import com.example.core.engine.archery.ArcheryCharacterAnimState
import com.example.core.engine.archery.ArcheryDifficulty
import com.example.core.engine.archery.ArcheryHitZone
import com.example.core.engine.archery.ArcheryPhysics
import com.example.core.engine.archery.ArcheryRules
import com.example.core.engine.archery.ArrowProjectile
import com.example.core.engine.archery.AttachedArrow
import com.example.core.engine.archery.ShotAngleReference
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.PlayfulPill
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPurple
import com.example.ui.theme.PlayfulYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val CANONICAL_ARENA_WIDTH = 1000f
private const val CANONICAL_ARENA_HEIGHT = 560f
private const val CANONICAL_GROUND_Y = CANONICAL_ARENA_HEIGHT * 0.54f // 302.4f
private const val CANONICAL_P1_ANCHOR_X = CANONICAL_ARENA_WIDTH * 0.17f // 170f
private const val CANONICAL_P2_ANCHOR_X = CANONICAL_ARENA_WIDTH * 0.83f // 830f
private const val CANONICAL_P1_PLATFORM_END = CANONICAL_ARENA_WIDTH * 0.28f // 280f
private const val CANONICAL_P2_PLATFORM_START = CANONICAL_ARENA_WIDTH * 0.72f // 720f
private const val CANONICAL_P1_CENTER_X = CANONICAL_P1_ANCHOR_X + 18f // 188f
private const val CANONICAL_P1_CENTER_Y = CANONICAL_GROUND_Y - 75f // 227.4f
private const val CANONICAL_P2_CENTER_X = CANONICAL_P2_ANCHOR_X - 18f // 812f
private const val CANONICAL_P2_CENTER_Y = CANONICAL_GROUND_Y - 75f // 227.4f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ArcheryGameView(
    selectedMode: GameMode,
    targetScore: Int,
    difficulty: ArcheryDifficulty = ArcheryDifficulty.MEDIUM,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val colors = ChillTheme.colors
    val isDark = colors.isDark

    // Intercept back button to return to portrait configuration cleanly
    BackHandler {
        onChangeMode()
    }

    // 1. ORIENTATION LIFECYCLE: Lock to LANDSCAPE strictly during gameplay, restore PORTRAIT on exit
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Match Scores
    var p1Score by remember { mutableIntStateOf(0) }
    var p2Score by remember { mutableIntStateOf(0) }
    var currentRound by remember { mutableIntStateOf(1) }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? com.example.core.multiplayer.MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayerIsLeft = if (isNearbyMode) isHost else true

    // Round Health
    var p1Health by remember { mutableIntStateOf(100) }
    var p2Health by remember { mutableIntStateOf(100) }

    // Persistent Attached Arrows
    val p1AttachedArrows = remember { mutableStateListOf<AttachedArrow>() }
    val p2AttachedArrows = remember { mutableStateListOf<AttachedArrow>() }

    // Anim States
    var p1AnimState by remember { mutableStateOf(ArcheryCharacterAnimState.IDLE) }
    var p2AnimState by remember { mutableStateOf(ArcheryCharacterAnimState.IDLE) }
    var p1StaggerProgress by remember { mutableFloatStateOf(0f) }
    var p2StaggerProgress by remember { mutableFloatStateOf(0f) }
    var p1LastHitZone by remember { mutableStateOf<ArcheryHitZone?>(null) }
    var p2LastHitZone by remember { mutableStateOf<ArcheryHitZone?>(null) }

    // Synchronized Arena Dimensions (measured dynamically from Canvas)
    var arenaWidth by remember { mutableFloatStateOf(0f) }
    var arenaHeight by remember { mutableFloatStateOf(0f) }

    fun screenToCanonical(screenOffset: Offset): Offset {
        val curW = if (arenaWidth > 0f) arenaWidth else CANONICAL_ARENA_WIDTH
        val curH = if (arenaHeight > 0f) arenaHeight else CANONICAL_ARENA_HEIGHT
        val sx = curW / CANONICAL_ARENA_WIDTH
        val sy = curH / CANONICAL_ARENA_HEIGHT
        return Offset(screenOffset.x / sx, screenOffset.y / sy)
    }

    fun canonicalToScreenX(canonicalX: Float): Float {
        val curW = if (arenaWidth > 0f) arenaWidth else CANONICAL_ARENA_WIDTH
        return (canonicalX / CANONICAL_ARENA_WIDTH) * curW
    }

    fun canonicalToScreenY(canonicalY: Float): Float {
        val curH = if (arenaHeight > 0f) arenaHeight else CANONICAL_ARENA_HEIGHT
        return (canonicalY / CANONICAL_ARENA_HEIGHT) * curH
    }

    // Multi-Arrow Simultaneous Flying System
    val flyingArrows = remember { mutableStateListOf<ArrowProjectile>() }

    // Continuous Frame Ticker for 60 FPS Canvas invalidation
    var gameFrameTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Previous Shot Angle Reference Markers (recent 3–5 shots)
    val p1PreviousShots = remember { mutableStateListOf<ShotAngleReference>() }
    val p2PreviousShots = remember { mutableStateListOf<ShotAngleReference>() }

    // ------------------------------------------------------------------------
    // GEOMETRIC AIM MODEL:
    // Direction from ARCHER -> FINGER = Elevation Angle (6° to 84°)
    // Distance from ARCHER -> FINGER = Draw Power (15% to 100%)
    // Authoritative target angle for physics + smooth visual angle for bow
    // ------------------------------------------------------------------------
    var p1IsDragging by remember { mutableStateOf(false) }
    var p1TargetAngleDeg by remember { mutableFloatStateOf(35f) }
    var p1VisualAngleDeg by remember { mutableFloatStateOf(35f) }
    var p1DragPower by remember { mutableFloatStateOf(0f) }
    var p1FingerPos by remember { mutableStateOf<Offset?>(null) }
    var p1IsPullMode by remember { mutableStateOf(false) }

    // Player 2 Aiming State (For local 2-Player simultaneous duel)
    var p2IsDragging by remember { mutableStateOf(false) }
    var p2TargetAngleDeg by remember { mutableFloatStateOf(35f) }
    var p2VisualAngleDeg by remember { mutableFloatStateOf(35f) }
    var p2DragPower by remember { mutableFloatStateOf(0f) }
    var p2FingerPos by remember { mutableStateOf<Offset?>(null) }
    var p2IsPullMode by remember { mutableStateOf(false) }

    var p1LastAimSendTime by remember { mutableLongStateOf(0L) }
    var p2LastAimSendTime by remember { mutableLongStateOf(0L) }

    // Round status & outcomes
    var roundWinner by remember { mutableStateOf<String?>(null) }
    var roundToast by remember { mutableStateOf<String?>(null) }
    var isTransitioningRound by remember { mutableStateOf(false) }
    var showMatchWonDialog by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Countdown State (Synchronized across both players)
    var countdownText by remember { mutableStateOf<String?>(null) }
    var isCountdownBlockingInput by remember { mutableStateOf(true) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun vibrate(zone: ArcheryHitZone? = null) {
        if (!settings.vibrationEnabled) return
        when (zone) {
            ArcheryHitZone.FEET -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            ArcheryHitZone.LEGS -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            ArcheryHitZone.CHEST -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            ArcheryHitZone.HEAD -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            else -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun runCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            isCountdownBlockingInput = true
            countdownText = "3"
            if (settings.vibrationEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            delay(600)
            countdownText = "2"
            if (settings.vibrationEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            delay(600)
            countdownText = "1"
            if (settings.vibrationEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            delay(600)
            countdownText = "GO!"
            isCountdownBlockingInput = false // Enable gameplay only when GO appears!
            if (settings.vibrationEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            delay(600)
            countdownText = null
        }
    }

    // Launch countdown before the very first round starts
    LaunchedEffect(Unit) {
        if (!isNearbyMode || isHost) {
            runCountdown()
            if (isNearbyMode && isHost) {
                multiplayerManager.sendAction("ARCHERY_COUNTDOWN", "1")
            }
        }
    }

    // Reset for Next Round
    fun startNextRound() {
        p1AttachedArrows.clear()
        p2AttachedArrows.clear()
        p1Health = 100
        p2Health = 100
        p1AnimState = ArcheryCharacterAnimState.IDLE
        p2AnimState = ArcheryCharacterAnimState.IDLE
        p1StaggerProgress = 0f
        p2StaggerProgress = 0f
        p1LastHitZone = null
        p2LastHitZone = null
        flyingArrows.clear()
        roundWinner = null
        roundToast = null
        isTransitioningRound = false
        p1IsDragging = false
        p2IsDragging = false
        p1FingerPos = null
        p2FingerPos = null
        p1IsPullMode = false
        p2IsPullMode = false
    }

    // Reset Complete Match
    fun restartMatch() {
        countdownJob?.cancel()
        p1Score = 0
        p2Score = 0
        currentRound = 1
        showMatchWonDialog = false
        matchRecorded = false
        p1PreviousShots.clear()
        p2PreviousShots.clear()
        startNextRound()
        vibrate()
        if (!isNearbyMode || isHost) {
            runCountdown()
            if (isNearbyMode && isHost) {
                multiplayerManager.sendAction("ARCHERY_COUNTDOWN", "1")
            }
        }
    }

    fun fireRemoteArrow(shooterIsLeft: Boolean, elevation: Float, power: Float, arrowId: Long = System.nanoTime()) {
        val launchSpeed = 500f + (power * 850f)
        val rad = elevation * (PI / 180.0)
        val vx = if (shooterIsLeft) (cos(rad) * launchSpeed).toFloat() else (-cos(rad) * launchSpeed).toFloat()
        val vy = (-sin(rad) * launchSpeed).toFloat()
        val angle = if (shooterIsLeft) -elevation else (-180f + elevation)
        val arrow = ArrowProjectile(
            id = arrowId,
            x = if (shooterIsLeft) CANONICAL_P1_CENTER_X else CANONICAL_P2_CENTER_X,
            y = if (shooterIsLeft) CANONICAL_P1_CENTER_Y else CANONICAL_P2_CENTER_Y,
            vx = vx,
            vy = vy,
            angleDegrees = angle,
            shooterIsLeft = shooterIsLeft
        )
        flyingArrows.add(arrow)

        if (shooterIsLeft) {
            p1PreviousShots.add(0, ShotAngleReference(elevation, power, CANONICAL_P1_CENTER_X, CANONICAL_P1_CENTER_Y, true, false))
            if (p1PreviousShots.size > 5) p1PreviousShots.removeAt(p1PreviousShots.lastIndex)
        } else {
            p2PreviousShots.add(0, ShotAngleReference(elevation, power, CANONICAL_P2_CENTER_X, CANONICAL_P2_CENTER_Y, false, false))
            if (p2PreviousShots.size > 5) p2PreviousShots.removeAt(p2PreviousShots.lastIndex)
        }
        vibrate()
    }

    // Listen for incoming actions in Nearby Mode for Archery
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "ARCHERY_AIM" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 4) {
                            val shooter = parts[0]
                            val angle = parts[1].toFloatOrNull() ?: 35f
                            val power = parts[2].toFloatOrNull() ?: 0.5f
                            val isPull = parts[3] == "1"
                            if (shooter == "1" && !localPlayerIsLeft) {
                                p1AnimState = ArcheryCharacterAnimState.AIMING
                                p1TargetAngleDeg = angle
                                p1VisualAngleDeg = angle
                                p1DragPower = power
                                p1IsPullMode = isPull
                                p1IsDragging = true
                            } else if (shooter == "2" && localPlayerIsLeft) {
                                p2AnimState = ArcheryCharacterAnimState.AIMING
                                p2TargetAngleDeg = angle
                                p2VisualAngleDeg = angle
                                p2DragPower = power
                                p2IsPullMode = isPull
                                p2IsDragging = true
                            }
                        }
                    }
                    "ARCHERY_CANCEL_AIM" -> {
                        val shooter = action.payload.trim()
                        if (shooter == "1" && !localPlayerIsLeft) {
                            p1IsDragging = false
                            p1FingerPos = null
                            if (p1Health > 0) p1AnimState = ArcheryCharacterAnimState.IDLE
                        } else if (shooter == "2" && localPlayerIsLeft) {
                            p2IsDragging = false
                            p2FingerPos = null
                            if (p2Health > 0) p2AnimState = ArcheryCharacterAnimState.IDLE
                        }
                    }
                    "ARCHERY_FIRE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 3) {
                            val isLeft = parts[0] == "1"
                            val elevation = parts[1].toFloatOrNull() ?: 35f
                            val power = parts[2].toFloatOrNull() ?: 0.5f
                            val remoteArrowId = parts.getOrNull(3)?.toLongOrNull() ?: System.nanoTime()
                            if (isLeft) {
                                p1IsDragging = false
                                p1FingerPos = null
                                if (p1Health > 0) p1AnimState = ArcheryCharacterAnimState.IDLE
                            } else {
                                p2IsDragging = false
                                p2FingerPos = null
                                if (p2Health > 0) p2AnimState = ArcheryCharacterAnimState.IDLE
                            }
                            fireRemoteArrow(isLeft, elevation, power, remoteArrowId)
                        }
                    }
                    "ARCHERY_HIT" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 7 && !isHost) {
                            val targetIsP2 = parts[0] == "2"
                            val hitZoneName = parts[1]
                            val offsetX = parts[2].toFloatOrNull() ?: 0f
                            val offsetY = parts[3].toFloatOrNull() ?: 0f
                            val arrowAngle = parts[4].toFloatOrNull() ?: 0f
                            val newP1Hp = parts[5].toIntOrNull() ?: p1Health
                            val newP2Hp = parts[6].toIntOrNull() ?: p2Health
                            val hitArrowId = parts.getOrNull(7)?.toLongOrNull()

                            val hitZone = try { ArcheryHitZone.valueOf(hitZoneName) } catch (_: Exception) { ArcheryHitZone.NONE }
                            val attached = AttachedArrow(
                                id = hitArrowId ?: System.nanoTime(),
                                zone = hitZone,
                                localOffsetX = offsetX,
                                localOffsetY = offsetY,
                                angleDegrees = arrowAngle
                            )

                            // Consume and remove the flying arrow from shooter
                            val shooterIsLeft = targetIsP2
                            val activeArrow = (if (hitArrowId != null) flyingArrows.firstOrNull { it.id == hitArrowId } else null)
                                ?: flyingArrows.firstOrNull { it.shooterIsLeft == shooterIsLeft && it.isFlying }
                            if (activeArrow != null) {
                                activeArrow.hasProcessedImpact = true
                                activeArrow.isFlying = false
                                flyingArrows.remove(activeArrow)
                            }

                            vibrate(hitZone)
                            if (targetIsP2) {
                                p2AttachedArrows.add(attached)
                                val damageDealt = p2Health - newP2Hp
                                p2Health = newP2Hp
                                p2LastHitZone = hitZone
                                p2StaggerProgress = 1f
                                if (newP2Hp > 0) {
                                    p2AnimState = ArcheryCharacterAnimState.RECOIL
                                    roundToast = "${hitZone.displayName} HIT! -$damageDealt HP 🎯"
                                }
                            } else {
                                p1AttachedArrows.add(attached)
                                val damageDealt = p1Health - newP1Hp
                                p1Health = newP1Hp
                                p1LastHitZone = hitZone
                                p1StaggerProgress = 1f
                                if (newP1Hp > 0) {
                                    p1AnimState = ArcheryCharacterAnimState.RECOIL
                                    roundToast = "${hitZone.displayName} HIT! -$damageDealt HP 🎯"
                                }
                            }
                        }
                    }
                    "ARCHERY_ROUND_CONCLUDE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size >= 6 && !isHost) {
                            val winner = parts[0]
                            val reason = parts[1]
                            val newP1Score = parts[2].toIntOrNull() ?: p1Score
                            val newP2Score = parts[3].toIntOrNull() ?: p2Score
                            val roundNum = parts[4].toIntOrNull() ?: currentRound
                            val isMatchFinished = parts[5] == "1"

                            if (roundWinner == null) {
                                roundWinner = winner
                                roundToast = reason
                                p1Score = newP1Score
                                p2Score = newP2Score
                                currentRound = roundNum

                                when (winner) {
                                    "P1" -> {
                                        p1AnimState = ArcheryCharacterAnimState.CELEBRATING
                                        p2AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
                                    }
                                    "P2" -> {
                                        p2AnimState = ArcheryCharacterAnimState.CELEBRATING
                                        p1AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
                                    }
                                    else -> {
                                        p1AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
                                        p2AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
                                    }
                                }

                                scope.launch {
                                    isTransitioningRound = true
                                    delay(2200)
                                    if (isMatchFinished) {
                                        showMatchWonDialog = true
                                        if (!matchRecorded) {
                                            matchRecorded = true
                                            val winnerName = if (p1Score >= targetScore) "PLAYER_1" else "PLAYER_2"
                                            viewModel.repository.recordMatch(
                                                gameId = "archery",
                                                gameTitle = "Archery",
                                                mode = selectedMode.name,
                                                player1Score = p1Score,
                                                player2Score = p2Score,
                                                winner = winnerName,
                                                durationSeconds = 120
                                            )
                                        }
                                    } else {
                                        currentRound++
                                        startNextRound()
                                    }
                                }
                            }
                        }
                    }
                    "ARCHERY_COUNTDOWN" -> {
                        runCountdown()
                    }
                    "ARCHERY_REMATCH" -> {
                        restartMatch()
                    }
                }
            }
        }
    }

    // Conclude round safely once
    fun concludeRound(winner: String, reason: String) {
        if (roundWinner != null) return
        roundWinner = winner
        roundToast = reason

        when (winner) {
            "P1" -> {
                p1Score++
                p1AnimState = ArcheryCharacterAnimState.CELEBRATING
                p2AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
            }
            "P2" -> {
                p2Score++
                p2AnimState = ArcheryCharacterAnimState.CELEBRATING
                p1AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
            }
            else -> {
                p1Score++
                p2Score++
                p1AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
                p2AnimState = ArcheryCharacterAnimState.KNOCKED_OUT
            }
        }

        if (isNearbyMode && isHost) {
            val isFinished = ArcheryRules.isMatchFinished(
                currentRound = currentRound,
                targetScore = targetScore,
                isNearbyMode = isNearbyMode,
                p1Score = p1Score,
                p2Score = p2Score
            )
            multiplayerManager.sendAction(
                "ARCHERY_ROUND_CONCLUDE",
                "$winner,$reason,$p1Score,$p2Score,$currentRound,${if (isFinished) 1 else 0}"
            )
        }

        scope.launch {
            isTransitioningRound = true
            delay(2200)

            val isMatchFinished = ArcheryRules.isMatchFinished(
                currentRound = currentRound,
                targetScore = targetScore,
                isNearbyMode = isNearbyMode,
                p1Score = p1Score,
                p2Score = p2Score
            )

            if (isMatchFinished) {
                showMatchWonDialog = true
                if (!matchRecorded) {
                    matchRecorded = true
                    val winnerName = if (isNearbyMode) {
                        if (p1Score >= targetScore) "PLAYER_1" else "PLAYER_2"
                    } else {
                        ArcheryRules.determineMatchWinner(p1Score, p2Score)
                    }
                    viewModel.repository.recordMatch(
                        gameId = "archery",
                        gameTitle = "Archery",
                        mode = selectedMode.name,
                        player1Score = p1Score,
                        player2Score = p2Score,
                        winner = winnerName,
                        durationSeconds = 120
                    )
                }
            } else {
                currentRound++
                startNextRound()
                if (!isNearbyMode || isHost) {
                    runCountdown()
                    if (isNearbyMode && isHost) {
                        multiplayerManager.sendAction("ARCHERY_COUNTDOWN", "$currentRound")
                    }
                }
            }
        }
    }

    // Handle projectile impact on opponent
    fun handleArrowImpact(
        arrow: ArrowProjectile,
        targetIsP2: Boolean,
        hitResult: ArcheryPhysics.CollisionResult
    ) {
        if (arrow.hasProcessedImpact) return
        arrow.hasProcessedImpact = true
        arrow.isFlying = false
        val hitZone = hitResult.hitZone

        if (hitZone != ArcheryHitZone.NONE && hitResult.attachedArrow != null) {
            vibrate(hitZone)
            val attached = if (targetIsP2) {
                val existingCount = p2AttachedArrows.count { it.zone == hitZone }
                val jitterX = if (existingCount > 0) (existingCount * 4f) * (if (existingCount % 2 == 0) 1f else -1f) else 0f
                val jitterY = if (existingCount > 0) (existingCount * 3.5f) * (if (existingCount % 2 == 1) 1f else -1f) else 0f
                val a = hitResult.attachedArrow.copy(
                    localOffsetX = hitResult.attachedArrow.localOffsetX + jitterX,
                    localOffsetY = hitResult.attachedArrow.localOffsetY + jitterY
                )
                p2AttachedArrows.add(a)

                val prevHealth = p2Health
                val effect = ArcheryRules.calculateDamage(p2Health, hitZone)
                val damageDealt = prevHealth - effect.newHealth
                p2Health = effect.newHealth
                p2LastHitZone = hitZone
                p2StaggerProgress = 1f

                if (effect.isHeadshot || effect.newHealth <= 0) {
                    concludeRound("P1", if (effect.isHeadshot) "HEADSHOT! 🎯" else "KNOCKOUT! 💥")
                } else {
                    p2AnimState = ArcheryCharacterAnimState.RECOIL
                    roundToast = "${hitZone.displayName} HIT! -$damageDealt HP 🎯"
                }
                a
            } else {
                val existingCount = p1AttachedArrows.count { it.zone == hitZone }
                val jitterX = if (existingCount > 0) (existingCount * 4f) * (if (existingCount % 2 == 0) 1f else -1f) else 0f
                val jitterY = if (existingCount > 0) (existingCount * 3.5f) * (if (existingCount % 2 == 1) 1f else -1f) else 0f
                val a = hitResult.attachedArrow.copy(
                    localOffsetX = hitResult.attachedArrow.localOffsetX + jitterX,
                    localOffsetY = hitResult.attachedArrow.localOffsetY + jitterY
                )
                p1AttachedArrows.add(a)

                val prevHealth = p1Health
                val effect = ArcheryRules.calculateDamage(p1Health, hitZone)
                val damageDealt = prevHealth - effect.newHealth
                p1Health = effect.newHealth
                p1LastHitZone = hitZone
                p1StaggerProgress = 1f

                if (effect.isHeadshot || effect.newHealth <= 0) {
                    concludeRound("P2", if (effect.isHeadshot) "HEADSHOT! 🎯" else "KNOCKOUT! 💥")
                } else {
                    p1AnimState = ArcheryCharacterAnimState.RECOIL
                    roundToast = "${hitZone.displayName} HIT! -$damageDealt HP 🎯"
                }
                a
            }

            if (isNearbyMode && isHost) {
                multiplayerManager.sendAction(
                    "ARCHERY_HIT",
                    "${if (targetIsP2) 2 else 1},${hitZone.name},${attached.localOffsetX},${attached.localOffsetY},${attached.angleDegrees},$p1Health,$p2Health,${arrow.id}"
                )
            }
        }
    }

    // High performance frame loop: physics, visual angle smoothing & collision detection at 60 FPS
    LaunchedEffect(currentRound, isTransitioningRound, showMatchWonDialog) {
        var lastTime = 0L
        while (isActive) {
            withFrameMillis { frameTime ->
                val dt = if (lastTime == 0L) 0.016f else ((frameTime - lastTime) / 1000f).coerceIn(0.005f, 0.04f)
                lastTime = frameTime

                // Visual angle smooth catch-up using shortest angular path (immediate 1:1 feel, zero lag, filters digitizer noise)
                fun shortestAngleDelta(target: Float, current: Float): Float {
                    var diff = (target - current) % 360f
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    return diff
                }
                p1VisualAngleDeg += shortestAngleDelta(p1TargetAngleDeg, p1VisualAngleDeg) * 0.65f
                p2VisualAngleDeg += shortestAngleDelta(p2TargetAngleDeg, p2VisualAngleDeg) * 0.65f

                // Decay stagger reactions smoothly
                if (p1StaggerProgress > 0f) {
                    p1StaggerProgress = (p1StaggerProgress - dt * 2.5f).coerceAtLeast(0f)
                }
                if (p2StaggerProgress > 0f) {
                    p2StaggerProgress = (p2StaggerProgress - dt * 2.5f).coerceAtLeast(0f)
                }

                // Advance flying arrows and run collision detection
                if (flyingArrows.isNotEmpty()) {
                    val deadArrows = mutableListOf<ArrowProjectile>()
                    for (arrow in flyingArrows) {
                        if (!arrow.isFlying) {
                            deadArrows.add(arrow)
                            continue
                        }

                        arrow.x += arrow.vx * dt
                        arrow.y += arrow.vy * dt
                        arrow.vy += ArcheryPhysics.GRAVITY * dt
                        arrow.angleDegrees = (atan2(arrow.vy.toDouble(), arrow.vx.toDouble()) * (180.0 / PI)).toFloat()

                        arrow.trail.add(Offset(arrow.x, arrow.y))
                        if (arrow.trail.size > 8) arrow.trail.removeAt(0)

                        // Collision Detection against opponent in canonical coordinate space
                        val targetAnchorX = if (arrow.shooterIsLeft) CANONICAL_P2_ANCHOR_X else CANONICAL_P1_ANCHOR_X
                        val collision = ArcheryPhysics.checkCollision(
                            point = Offset(arrow.x, arrow.y),
                            targetAnchorX = targetAnchorX,
                            groundY = CANONICAL_GROUND_Y,
                            arrowAngle = arrow.angleDegrees
                        )

                        val canCheckCollision = (!isNearbyMode || isHost)
                        if (canCheckCollision && collision.hitZone != ArcheryHitZone.NONE && !arrow.hasProcessedImpact) {
                            handleArrowImpact(arrow, targetIsP2 = arrow.shooterIsLeft, hitResult = collision)
                            deadArrows.add(arrow)
                        } else if (arrow.x <= CANONICAL_P1_PLATFORM_END && arrow.y >= CANONICAL_GROUND_Y + 4f && (!arrow.shooterIsLeft || canCheckCollision)) {
                            arrow.isFlying = false
                            deadArrows.add(arrow)
                        } else if (arrow.x >= CANONICAL_P2_PLATFORM_START && arrow.y >= CANONICAL_GROUND_Y + 4f && (arrow.shooterIsLeft || canCheckCollision)) {
                            arrow.isFlying = false
                            deadArrows.add(arrow)
                        } else if (arrow.y > CANONICAL_ARENA_HEIGHT + 60f || arrow.x < -60f || arrow.x > CANONICAL_ARENA_WIDTH + 60f || arrow.y < -150f) {
                            arrow.isFlying = false
                            deadArrows.add(arrow)
                        }
                    }

                    if (deadArrows.isNotEmpty()) {
                        flyingArrows.removeAll(deadArrows)
                    }
                }

                gameFrameTick = frameTime
            }
        }
    }

    // Autonomous Bot in VS BOT mode (tuned by selected difficulty)
    LaunchedEffect(selectedMode, difficulty, isTransitioningRound, showMatchWonDialog, currentRound, arenaWidth, isCountdownBlockingInput) {
        if (selectedMode != GameMode.SOLO) return@LaunchedEffect
        val profile = ArcheryBot.getProfile(difficulty)

        while (isActive) {
            val waitMs = kotlin.random.Random.nextLong(profile.minDelayMs, profile.maxDelayMs)
            delay(waitMs)

            if (p2Health > 0 && p1Health > 0 && !isTransitioningRound && !showMatchWonDialog && !isCountdownBlockingInput) {
                // Bot begins drawing bow
                p2AnimState = ArcheryCharacterAnimState.AIMING
                p2DragPower = kotlin.random.Random.nextFloat().coerceIn(0.45f, 0.85f)

                // Hold time
                val holdMs = kotlin.random.Random.nextLong(profile.holdMinMs, profile.holdMaxMs)
                delay(holdMs)

                if (p2Health > 0 && !isTransitioningRound && !showMatchWonDialog && !isCountdownBlockingInput) {
                    val botShot = ArcheryBot.calculateShot(
                        botAnchorX = CANONICAL_P2_ANCHOR_X,
                        playerAnchorX = CANONICAL_P1_ANCHOR_X,
                        groundY = CANONICAL_GROUND_Y,
                        difficulty = difficulty
                    )
                    // The bot's elevation above horizontal
                    val botElevation = (180f + botShot.angleDegrees).coerceIn(6f, 84f)
                    p2TargetAngleDeg = botElevation
                    p2VisualAngleDeg = botElevation
                    p2AnimState = ArcheryCharacterAnimState.IDLE

                    val arrow = ArrowProjectile(
                        x = CANONICAL_P2_CENTER_X,
                        y = CANONICAL_P2_CENTER_Y,
                        vx = botShot.vx,
                        vy = botShot.vy,
                        angleDegrees = botShot.angleDegrees,
                        shooterIsLeft = false
                    )
                    flyingArrows.add(arrow)

                    p2PreviousShots.add(0, ShotAngleReference(botElevation, botShot.power, CANONICAL_P2_CENTER_X, CANONICAL_P2_CENTER_Y, false))
                    if (p2PreviousShots.size > 5) p2PreviousShots.removeAt(p2PreviousShots.lastIndex)

                    vibrate()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("archery_game_view")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ----------------------------------------------------------------
            // 1. TOP HUD BAR: Clean Landscape HUD
            // ----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Back to Configuration + Player 1 Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, colors.border, CircleShape)
                            .bouncyClickable(onClick = onChangeMode)
                            .testTag("archery_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        val p1Label = when {
                            selectedMode == GameMode.SOLO -> "YOU 👤 🏹"
                            isNearbyMode -> if (localPlayerIsLeft) "YOU 👤 🏹" else "OPPONENT 🏹"
                            else -> "PLAYER 1 🏹"
                        }
                        Text(
                            text = p1Label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = PlayfulOrange
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$p1Health%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.border)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(p1Health / 100f)
                                        .background(PlayfulOrange)
                                )
                            }
                        }
                    }
                }

                // CENTER: Match Format, Score & Current Aim Angle indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeLabel = if (selectedMode == GameMode.SOLO) "BOT (${difficulty.title})" else "2P"
                    val matchPillText = if (isNearbyMode) {
                        "BEST OF $targetScore • $p1Score — $p2Score"
                    } else {
                        "ROUND $currentRound / $targetScore • $p1Score — $p2Score"
                    }
                    PlayfulPill(
                        text = matchPillText,
                        backgroundColor = colors.accent.copy(alpha = 0.14f),
                        textColor = colors.accent
                    )

                    if (p1IsDragging) {
                        PlayfulPill(
                            text = "AIM: %.1f° • %d%%".format(p1TargetAngleDeg, (p1DragPower * 100).toInt()),
                            backgroundColor = PlayfulOrange.copy(alpha = 0.18f),
                            textColor = PlayfulOrange
                        )
                    } else if (roundToast != null) {
                        PlayfulPill(
                            text = roundToast ?: "",
                            backgroundColor = PlayfulYellow.copy(alpha = 0.20f),
                            textColor = Color(0xFFD97706)
                        )
                    }
                }

                // RIGHT: Opponent Status + Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        val p2Label = when {
                            selectedMode == GameMode.SOLO -> "BOT (${difficulty.title}) 🤖"
                            isNearbyMode -> if (!localPlayerIsLeft) "YOU 👤 🏹" else "OPPONENT 🏹"
                            else -> "PLAYER 2 🏹"
                        }
                        Text(
                            text = p2Label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = PlayfulPurple
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.border)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(p2Health / 100f)
                                        .background(PlayfulPurple)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$p2Health%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, colors.border, CircleShape)
                                .bouncyClickable { showHelpDialog = true }
                                .testTag("archery_help_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Help",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, colors.border, CircleShape)
                                .bouncyClickable { restartMatch() }
                                .testTag("archery_restart_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ----------------------------------------------------------------
            // 2. CENTRAL PHYSICAL ARENA (TWO-SIDED CHASM)
            // ----------------------------------------------------------------
            val currentP1Health by rememberUpdatedState(p1Health)
            val currentP2Health by rememberUpdatedState(p2Health)
            val currentSelectedMode by rememberUpdatedState(selectedMode)
            val currentRoundWinner by rememberUpdatedState(roundWinner)
            val currentIsCountdownBlockingInput by rememberUpdatedState(isCountdownBlockingInput)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                    .border(2.dp, colors.border, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var p1PointerId: androidx.compose.ui.input.pointer.PointerId? = null
                            var p2PointerId: androidx.compose.ui.input.pointer.PointerId? = null

                            fun getArenaW() = if (arenaWidth > 0f) arenaWidth else size.width.toFloat()
                            fun getArenaH() = if (arenaHeight > 0f) arenaHeight else size.height.toFloat()

                            // Single Authoritative Aim Vector: Supports both Point Mode (drag in front) and Pull-Back Mode (pull behind)
                            fun updateP1(pos: Offset, forceSend: Boolean = false) {
                                p1FingerPos = pos
                                val cPos = screenToCanonical(pos)
                                val dx = cPos.x - CANONICAL_P1_CENTER_X
                                val dy = cPos.y - CANONICAL_P1_CENTER_Y // Screen Y: dy > 0 is downward, dy < 0 is upward
                                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                val isPull = dx < 0f
                                p1IsPullMode = isPull

                                // Deadzone: if finger is within 14px of bow, preserve last stable direction
                                if (dist >= 14f) {
                                    val rawAngleRad = if (!isPull) {
                                        // POINT MODE (finger is in front / right):
                                        // Shot direction: bow -> finger = (dx, dy)
                                        // Screen elevation: -dy (upward) / dx (forward)
                                        atan2((-dy).toDouble(), dx.toDouble())
                                    } else {
                                        // PULL-BACK MODE (finger is behind / left):
                                        // Pull vector: bow -> finger = (dx, dy)
                                        // Shot vector: finger -> bow = (-dx, -dy)
                                        // Elevation: dy / (-dx)
                                        atan2(dy.toDouble(), (-dx).toDouble())
                                    }
                                    val deg = (rawAngleRad * (180.0 / PI)).toFloat().coerceIn(-30.0f, 85.0f)
                                    p1TargetAngleDeg = deg
                                    p1VisualAngleDeg = deg // Strict 1:1 synchronization with gesture line
                                }
                                p1DragPower = (dist / 220f).coerceIn(0.15f, 1.0f)

                                if (isNearbyMode && localPlayerIsLeft) {
                                    val now = System.currentTimeMillis()
                                    if (forceSend || now - p1LastAimSendTime > 45L) {
                                        p1LastAimSendTime = now
                                        multiplayerManager.sendAction(
                                            "ARCHERY_AIM",
                                            "1,%.1f,%.2f,%d".format(java.util.Locale.US, p1TargetAngleDeg, p1DragPower, if (p1IsPullMode) 1 else 0)
                                        )
                                    }
                                }
                            }

                            fun updateP2(pos: Offset, forceSend: Boolean = false) {
                                p2FingerPos = pos
                                val cPos = screenToCanonical(pos)
                                val forwardDx = CANONICAL_P2_CENTER_X - cPos.x // Leftward forward distance (> 0 in front, < 0 behind)
                                val dy = cPos.y - CANONICAL_P2_CENTER_Y
                                val dist = hypot((cPos.x - CANONICAL_P2_CENTER_X).toDouble(), dy.toDouble()).toFloat()

                                val isPull = forwardDx < 0f // finger is behind Player 2 (to the right)
                                p2IsPullMode = isPull

                                if (dist >= 14f) {
                                    val rawAngleRad = if (!isPull) {
                                        // POINT MODE (finger is in front / left):
                                        // Screen elevation: -dy (upward) / forwardDx (forward)
                                        atan2((-dy).toDouble(), forwardDx.toDouble())
                                    } else {
                                        // PULL-BACK MODE (finger is behind / right):
                                        // Pull vector: forwardDx < 0, shot vector: -forwardDx > 0
                                        // Elevation: dy / (-forwardDx)
                                        atan2(dy.toDouble(), (-forwardDx).toDouble())
                                    }
                                    val deg = (rawAngleRad * (180.0 / PI)).toFloat().coerceIn(-30.0f, 85.0f)
                                    p2TargetAngleDeg = deg
                                    p2VisualAngleDeg = deg // Strict 1:1 synchronization with gesture line
                                }
                                p2DragPower = (dist / 220f).coerceIn(0.15f, 1.0f)

                                if (isNearbyMode && !localPlayerIsLeft) {
                                    val now = System.currentTimeMillis()
                                    if (forceSend || now - p2LastAimSendTime > 45L) {
                                        p2LastAimSendTime = now
                                        multiplayerManager.sendAction(
                                            "ARCHERY_AIM",
                                            "2,%.1f,%.2f,%d".format(java.util.Locale.US, p2TargetAngleDeg, p2DragPower, if (p2IsPullMode) 1 else 0)
                                        )
                                    }
                                }
                            }

                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val curW = getArenaW()
                            val canTouchP1 = (currentSelectedMode != GameMode.BLUETOOTH_1V1 || localPlayerIsLeft)
                            val canTouchP2 = (currentSelectedMode == GameMode.LOCAL_1V1 || (currentSelectedMode == GameMode.BLUETOOTH_1V1 && !localPlayerIsLeft))
                            val isInputBlocked = currentIsCountdownBlockingInput

                            // Assign pointer based on which half was touched
                            if (!isInputBlocked && canTouchP1 && firstDown.position.x < curW * 0.5f && currentP1Health > 0 && currentRoundWinner == null) {
                                p1PointerId = firstDown.id
                                p1IsDragging = true
                                p1AnimState = ArcheryCharacterAnimState.AIMING
                                updateP1(firstDown.position, forceSend = true)
                                p1VisualAngleDeg = p1TargetAngleDeg // Instant initial visual sync! Zero jump!
                                vibrate()
                                firstDown.consume()
                            } else if (!isInputBlocked && canTouchP2 && firstDown.position.x >= curW * 0.5f && currentP2Health > 0 && currentRoundWinner == null) {
                                p2PointerId = firstDown.id
                                p2IsDragging = true
                                p2AnimState = ArcheryCharacterAnimState.AIMING
                                updateP2(firstDown.position, forceSend = true)
                                p2VisualAngleDeg = p2TargetAngleDeg // Instant initial visual sync! Zero jump!
                                vibrate()
                                firstDown.consume()
                            }

                            while (p1PointerId != null || p2PointerId != null) {
                                val event = awaitPointerEvent()

                                // Support simultaneous dual touch for Local 2-Player mode
                                for (change in event.changes) {
                                    if (change.pressed && !change.previousPressed) {
                                        val curW2 = getArenaW()
                                        val canTouchP1_2 = (currentSelectedMode != GameMode.BLUETOOTH_1V1 || localPlayerIsLeft)
                                        val canTouchP2_2 = (currentSelectedMode == GameMode.LOCAL_1V1 || (currentSelectedMode == GameMode.BLUETOOTH_1V1 && !localPlayerIsLeft))
                                        if (!isInputBlocked && canTouchP1_2 && p1PointerId == null && change.position.x < curW2 * 0.5f && currentP1Health > 0 && currentRoundWinner == null) {
                                            p1PointerId = change.id
                                            p1IsDragging = true
                                            p1AnimState = ArcheryCharacterAnimState.AIMING
                                            updateP1(change.position, forceSend = true)
                                            p1VisualAngleDeg = p1TargetAngleDeg
                                            vibrate()
                                            change.consume()
                                        } else if (!isInputBlocked && canTouchP2_2 && p2PointerId == null && change.position.x >= curW2 * 0.5f && currentP2Health > 0 && currentRoundWinner == null) {
                                            p2PointerId = change.id
                                            p2IsDragging = true
                                            p2AnimState = ArcheryCharacterAnimState.AIMING
                                            updateP2(change.position, forceSend = true)
                                            p2VisualAngleDeg = p2TargetAngleDeg
                                            vibrate()
                                            change.consume()
                                        }
                                    }
                                }

                                // Process Player 1 aim tracking & release
                                p1PointerId?.let { id ->
                                    val change = event.changes.find { it.id == id }
                                    if (change == null || !change.pressed) {
                                        // Finger released: Fire projectile at exact target angle and power!
                                        if (p1IsDragging && getArenaW() > 0f) {
                                            val elevation = p1TargetAngleDeg
                                            val power = p1DragPower
                                            val launchSpeed = 500f + (power * 850f)
                                            val rad = elevation * (PI / 180.0)
                                            val vx = (cos(rad) * launchSpeed).toFloat()
                                            val vy = (-sin(rad) * launchSpeed).toFloat()
                                            val arrowId = System.nanoTime()

                                            val arrow = ArrowProjectile(
                                                id = arrowId,
                                                x = CANONICAL_P1_CENTER_X,
                                                y = CANONICAL_P1_CENTER_Y,
                                                vx = vx,
                                                vy = vy,
                                                angleDegrees = -elevation,
                                                shooterIsLeft = true
                                            )
                                            flyingArrows.add(arrow)

                                            p1PreviousShots.add(0, ShotAngleReference(elevation, power, CANONICAL_P1_CENTER_X, CANONICAL_P1_CENTER_Y, true, isPullMode = p1IsPullMode))
                                            if (p1PreviousShots.size > 5) p1PreviousShots.removeAt(p1PreviousShots.lastIndex)

                                            vibrate()
                                            if (isNearbyMode) {
                                                multiplayerManager.sendAction(
                                                    "ARCHERY_FIRE",
                                                    "1,%.1f,%.2f,%d".format(java.util.Locale.US, elevation, power, arrowId)
                                                )
                                            }
                                        } else if (isNearbyMode && localPlayerIsLeft) {
                                            multiplayerManager.sendAction("ARCHERY_CANCEL_AIM", "1")
                                        }
                                        p1IsDragging = false
                                        p1FingerPos = null
                                        if (currentP1Health > 0) p1AnimState = ArcheryCharacterAnimState.IDLE
                                        p1PointerId = null
                                        change?.consume()
                                    } else {
                                        change.consume()
                                        updateP1(change.position)
                                    }
                                }

                                // Process Player 2 aim tracking & release
                                p2PointerId?.let { id ->
                                    val change = event.changes.find { it.id == id }
                                    if (change == null || !change.pressed) {
                                        // Finger released: Fire projectile at exact target angle and power!
                                        if (p2IsDragging && getArenaW() > 0f) {
                                            val elevation = p2TargetAngleDeg
                                            val power = p2DragPower
                                            val launchSpeed = 500f + (power * 850f)
                                            val rad = elevation * (PI / 180.0)
                                            val vx = (-cos(rad) * launchSpeed).toFloat()
                                            val vy = (-sin(rad) * launchSpeed).toFloat()
                                            val arrowId = System.nanoTime()

                                            val arrow = ArrowProjectile(
                                                id = arrowId,
                                                x = CANONICAL_P2_CENTER_X,
                                                y = CANONICAL_P2_CENTER_Y,
                                                vx = vx,
                                                vy = vy,
                                                angleDegrees = -180f + elevation,
                                                shooterIsLeft = false
                                            )
                                            flyingArrows.add(arrow)

                                            p2PreviousShots.add(0, ShotAngleReference(elevation, power, CANONICAL_P2_CENTER_X, CANONICAL_P2_CENTER_Y, false, isPullMode = p2IsPullMode))
                                            if (p2PreviousShots.size > 5) p2PreviousShots.removeAt(p2PreviousShots.lastIndex)

                                            vibrate()
                                            if (isNearbyMode) {
                                                multiplayerManager.sendAction(
                                                    "ARCHERY_FIRE",
                                                    "2,%.1f,%.2f,%d".format(java.util.Locale.US, elevation, power, arrowId)
                                                )
                                            }
                                        } else if (isNearbyMode && !localPlayerIsLeft) {
                                            multiplayerManager.sendAction("ARCHERY_CANCEL_AIM", "2")
                                        }
                                        p2IsDragging = false
                                        p2FingerPos = null
                                        if (currentP2Health > 0) p2AnimState = ArcheryCharacterAnimState.IDLE
                                        p2PointerId = null
                                        change?.consume()
                                    } else {
                                        change.consume()
                                        updateP2(change.position)
                                    }
                                }
                            }
                        }
                    }
            ) {

                // Main Gameplay Renderer
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val _tick = gameFrameTick

                    val arenaW = size.width
                    val arenaH = size.height

                    if (arenaWidth != arenaW || arenaHeight != arenaH) {
                        arenaWidth = arenaW
                        arenaHeight = arenaH
                    }

                    val scaleX = if (arenaW > 0f) arenaW / CANONICAL_ARENA_WIDTH else 1f
                    val scaleY = if (arenaH > 0f) arenaH / CANONICAL_ARENA_HEIGHT else 1f

                    scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                        val groundY = CANONICAL_GROUND_Y
                        val p1AnchorX = CANONICAL_P1_ANCHOR_X
                        val p2AnchorX = CANONICAL_P2_ANCHOR_X

                        val p1PlatformEnd = CANONICAL_P1_PLATFORM_END
                        val p2PlatformStart = CANONICAL_P2_PLATFORM_START

                        val p1CenterX = CANONICAL_P1_CENTER_X
                        val p1CenterY = CANONICAL_P1_CENTER_Y
                        val p2CenterX = CANONICAL_P2_CENTER_X
                        val p2CenterY = CANONICAL_P2_CENTER_Y

                        // --------------------------------------------------------
                        // 1. OPEN AIR CANYON BACKGROUND ATMOSPHERE
                        // --------------------------------------------------------
                        val chasmColor = if (isDark) Color(0xFF030712).copy(alpha = 0.55f) else Color(0xFFE2E8F0).copy(alpha = 0.45f)
                        drawRect(
                            color = chasmColor,
                            topLeft = Offset(0f, groundY + 30f),
                            size = androidx.compose.ui.geometry.Size(CANONICAL_ARENA_WIDTH, CANONICAL_ARENA_HEIGHT - groundY)
                        )

                        // Wind streaks
                        val windY1 = groundY - 40f
                        val windY2 = groundY + 60f
                        drawLine(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                            start = Offset(CANONICAL_ARENA_WIDTH * 0.35f, windY1),
                            end = Offset(CANONICAL_ARENA_WIDTH * 0.65f, windY1),
                            strokeWidth = 1.5f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.04f),
                            start = Offset(CANONICAL_ARENA_WIDTH * 0.40f, windY2),
                            end = Offset(CANONICAL_ARENA_WIDTH * 0.60f, windY2),
                            strokeWidth = 1.5f,
                            cap = StrokeCap.Round
                        )

                        // --------------------------------------------------------
                        // 2. SEPARATE PLATFORMS
                        // --------------------------------------------------------
                        drawArcheryPlatform(
                            isLeft = true,
                            platformStartX = 0f,
                            platformEndX = p1PlatformEnd,
                            groundY = groundY,
                            isDark = isDark
                        )

                        drawArcheryPlatform(
                            isLeft = false,
                            platformStartX = p2PlatformStart,
                            platformEndX = CANONICAL_ARENA_WIDTH,
                            groundY = groundY,
                            isDark = isDark
                        )

                        // --------------------------------------------------------
                        // 3. PREVIOUS SHOT ANGLE MARKERS (3-5 RECENT SHOTS)
                        // --------------------------------------------------------
                        drawPreviousShotMarkers(p1PreviousShots, PlayfulOrange)
                        drawPreviousShotMarkers(p2PreviousShots, PlayfulPurple)

                        // --------------------------------------------------------
                        // 4. DIRECT GESTURE AIM LINES (POINT FORWARD OR PULL BACKWARD)
                        // --------------------------------------------------------
                        if (p1IsDragging) {
                            val bowOrigin = Offset(p1CenterX, p1CenterY)
                            val rad = p1TargetAngleDeg * (PI / 180.0)
                            val shotDirX = cos(rad).toFloat()
                            val shotDirY = -sin(rad).toFloat()

                            val visualLength = (p1DragPower * 220f).coerceIn(36f, 240f)

                            val lineDirX = if (p1IsPullMode) -shotDirX else shotDirX
                            val lineDirY = if (p1IsPullMode) -shotDirY else shotDirY
                            val gestureEnd = Offset(p1CenterX + lineDirX * visualLength, p1CenterY + lineDirY * visualLength)

                            // Outer soft glowing aura
                            drawLine(
                                color = PlayfulOrange.copy(alpha = 0.22f),
                                start = bowOrigin,
                                end = gestureEnd,
                                strokeWidth = 6.0f,
                                cap = StrokeCap.Round
                            )
                            // Inner crisp direct gesture line
                            drawLine(
                                color = PlayfulOrange.copy(alpha = 0.85f),
                                start = bowOrigin,
                                end = gestureEnd,
                                strokeWidth = 2.4f,
                                cap = StrokeCap.Round
                            )
                            // Touch indicator node (●) at the gesture end (finger position)
                            drawCircle(
                                color = PlayfulOrange.copy(alpha = 0.30f),
                                radius = 11f,
                                center = gestureEnd
                            )
                            drawCircle(
                                color = PlayfulOrange,
                                radius = 6f,
                                center = gestureEnd
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.5f,
                                center = gestureEnd
                            )
                        }

                        if (p2IsDragging) {
                            val bowOrigin = Offset(p2CenterX, p2CenterY)
                            val rad = p2TargetAngleDeg * (PI / 180.0)
                            val shotDirX = -cos(rad).toFloat()
                            val shotDirY = -sin(rad).toFloat()

                            val visualLength = (p2DragPower * 220f).coerceIn(36f, 240f)

                            val lineDirX = if (p2IsPullMode) -shotDirX else shotDirX
                            val lineDirY = if (p2IsPullMode) -shotDirY else shotDirY
                            val gestureEnd = Offset(p2CenterX + lineDirX * visualLength, p2CenterY + lineDirY * visualLength)

                            drawLine(
                                color = PlayfulPurple.copy(alpha = 0.22f),
                                start = bowOrigin,
                                end = gestureEnd,
                                strokeWidth = 6.0f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = PlayfulPurple.copy(alpha = 0.85f),
                                start = bowOrigin,
                                end = gestureEnd,
                                strokeWidth = 2.4f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = PlayfulPurple.copy(alpha = 0.30f),
                                radius = 11f,
                                center = gestureEnd
                            )
                            drawCircle(
                                color = PlayfulPurple,
                                radius = 6f,
                                center = gestureEnd
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.5f,
                                center = gestureEnd
                            )
                        }

                        // --------------------------------------------------------
                        // 5. TRAJECTORY PREVIEWS (EXACT SAME PHYSICS AS ARROW)
                        // --------------------------------------------------------
                        if (p1IsDragging) {
                            val launchSpeed = 500f + (p1DragPower * 850f)
                            val rad = p1TargetAngleDeg * (PI / 180.0)
                            val vx = (cos(rad) * launchSpeed).toFloat()
                            val vy = (-sin(rad) * launchSpeed).toFloat()

                            val preview = ArcheryPhysics.calculateTrajectoryPreview(
                                startX = p1CenterX,
                                startY = p1CenterY,
                                vx = vx,
                                vy = vy,
                                maxSteps = 24
                            )
                            for (i in preview.indices) {
                                val pt = preview[i]
                                val alpha = (1f - (i.toFloat() / preview.size.toFloat())).coerceIn(0.20f, 0.85f)
                                drawCircle(
                                    color = PlayfulOrange.copy(alpha = alpha),
                                    radius = 2.8f,
                                    center = pt
                                )
                            }
                        }

                        if (p2IsDragging) {
                            val launchSpeed = 500f + (p2DragPower * 850f)
                            val rad = p2TargetAngleDeg * (PI / 180.0)
                            val vx = (-cos(rad) * launchSpeed).toFloat()
                            val vy = (-sin(rad) * launchSpeed).toFloat()

                            val preview = ArcheryPhysics.calculateTrajectoryPreview(
                                startX = p2CenterX,
                                startY = p2CenterY,
                                vx = vx,
                                vy = vy,
                                maxSteps = 24
                            )
                            for (i in preview.indices) {
                                val pt = preview[i]
                                val alpha = (1f - (i.toFloat() / preview.size.toFloat())).coerceIn(0.20f, 0.85f)
                                drawCircle(
                                    color = PlayfulPurple.copy(alpha = alpha),
                                    radius = 2.8f,
                                    center = pt
                                )
                            }
                        }

                        // --------------------------------------------------------
                        // 6. STICK FIGURE CHARACTERS WITH UNIFIED AUTHORITATIVE ROTATION
                        // --------------------------------------------------------
                        drawArcheryStickFigure(
                            isLeft = true,
                            anchorX = p1AnchorX,
                            groundY = groundY,
                            health = p1Health,
                            animState = p1AnimState,
                            aimAngleDegrees = -p1TargetAngleDeg,
                            aimPower = if (p1IsDragging || p1AnimState == ArcheryCharacterAnimState.AIMING) p1DragPower else 0f,
                            staggerProgress = p1StaggerProgress,
                            lastHitZone = p1LastHitZone,
                            attachedArrows = p1AttachedArrows,
                            color = PlayfulOrange,
                            bowColor = Color(0xFFD97706),
                            strokeWidth = 7.5f,
                            timeMs = _tick
                        )

                        drawArcheryStickFigure(
                            isLeft = false,
                            anchorX = p2AnchorX,
                            groundY = groundY,
                            health = p2Health,
                            animState = p2AnimState,
                            aimAngleDegrees = -180f + p2TargetAngleDeg,
                            aimPower = if (p2IsDragging || p2AnimState == ArcheryCharacterAnimState.AIMING) p2DragPower else 0f,
                            staggerProgress = p2StaggerProgress,
                            lastHitZone = p2LastHitZone,
                            attachedArrows = p2AttachedArrows,
                            color = PlayfulPurple,
                            bowColor = Color(0xFFD97706),
                            strokeWidth = 7.5f,
                            timeMs = _tick
                        )

                        // --------------------------------------------------------
                        // 7. SIMULTANEOUS FLYING ARROWS RENDERING
                        // --------------------------------------------------------
                        for (arrow in flyingArrows) {
                            if (!arrow.isFlying) continue

                            val arrowOffset = Offset(arrow.x, arrow.y)
                            val rad = (arrow.angleDegrees * PI / 180f).toFloat()
                            val cosA = cos(rad)
                            val sinA = sin(rad)
                            val shaftLen = 42f

                            // Draw arrow trail
                            for (i in arrow.trail.indices) {
                                val trailPt = arrow.trail[i]
                                val progress = (i + 1).toFloat() / arrow.trail.size.toFloat()
                                drawCircle(
                                    color = Color.LightGray.copy(alpha = progress * 0.4f),
                                    radius = 2f * progress,
                                    center = trailPt
                                )
                            }

                            // Arrow shaft
                            drawLine(
                                color = Color(0xFF78350F),
                                start = Offset(arrow.x - cosA * shaftLen, arrow.y - sinA * shaftLen),
                                end = arrowOffset,
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round
                            )
                            // Metal arrowhead
                            drawLine(
                                color = Color(0xFFF1F5F9),
                                start = Offset(arrow.x - cosA * 8f, arrow.y - sinA * 8f),
                                end = arrowOffset,
                                strokeWidth = 5.5f,
                                cap = StrokeCap.Round
                            )
                            // Feathers
                            val featherBack = Offset(arrow.x - cosA * (shaftLen - 4f), arrow.y - sinA * (shaftLen - 4f))
                            val fletchColor = if (arrow.shooterIsLeft) PlayfulOrange else PlayfulPurple
                            drawLine(
                                color = fletchColor,
                                start = Offset(arrow.x - cosA * shaftLen, arrow.y - sinA * shaftLen),
                                end = featherBack,
                                strokeWidth = 6f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Visible Countdown Overlay (3 -> 2 -> 1 -> GO!)
                if (countdownText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(
                                width = 2.dp,
                                color = if (countdownText == "GO!") PlayfulGreen else colors.accent.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 36.dp, vertical = 18.dp)
                            .testTag("archery_countdown_overlay"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownText ?: "",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = if (countdownText == "GO!") PlayfulGreen else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // --------------------------------------------------------------------
        // MATCH OVER DIALOG
        // --------------------------------------------------------------------
        if (showMatchWonDialog) {
            val isTie = !isNearbyMode && (p1Score == p2Score)
            val isP1Winner = if (isNearbyMode) p1Score >= targetScore else p1Score > p2Score
            val didUserWin = if (selectedMode == GameMode.SOLO) isP1Winner else true

            AlertDialog(
                onDismissRequest = {},
                shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                icon = {
                    Text(
                        text = when {
                            isTie -> "🤝"
                            didUserWin -> "🏆"
                            else -> "😵"
                        },
                        fontSize = 44.sp
                    )
                },
                title = {
                    val archeryWinTitle = when {
                        isTie -> "🤝 MATCH TIED!"
                        selectedMode == GameMode.SOLO && isP1Winner -> "🏆 YOU WON!"
                        selectedMode == GameMode.SOLO -> "🏹 BOT WON!"
                        isNearbyMode && ((isP1Winner && localPlayerIsLeft) || (!isP1Winner && !localPlayerIsLeft)) -> "🏆 YOU WON!"
                        isNearbyMode -> "🏹 OPPONENT WON!"
                        isP1Winner -> "🏆 PLAYER 1 WINS!"
                        else -> "🏆 PLAYER 2 WINS!"
                    }
                    Text(
                        text = archeryWinTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    val matchResultText = if (isTie) {
                        "Final Match Score: $p1Score — $p2Score ($targetScore Rounds)\nIt's an even match! Well fought!"
                    } else {
                        "Final Match Score: $p1Score — $p2Score\nAwesome simultaneous archery duel!"
                    }
                    Text(
                        text = matchResultText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    ChunkyButton(
                        text = "PLAY AGAIN 🔄",
                        onClick = {
                            restartMatch()
                            if (isNearbyMode) {
                                multiplayerManager.sendAction("ARCHERY_REMATCH", "")
                            }
                        },
                        backgroundColor = colors.accent,
                        modifier = Modifier.fillMaxWidth().testTag("archery_play_again_button")
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().testTag("archery_exit_button")
                    ) {
                        Text("Home 🏠", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // --------------------------------------------------------------------
        // HOW TO PLAY DIALOG
        // --------------------------------------------------------------------
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Archery Duel 🏹",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "• GEOMETRIC AIM: Touch your side. The direction from your archer to your finger sets the aim angle. Moving finger UP raises bow, DOWN lowers bow!\n" +
                                   "• DRAW POWER: Moving your finger farther from your archer draws more power!\n" +
                                   "• PREVIOUS AIM: Subtle dotted markers show where your previous arrows were aimed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HIT ZONES & DAMAGE:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                            color = colors.accent
                        )
                        Text(
                            text = "🎯 HEAD: Instant Knockout!\n" +
                                   "🛡️ CHEST: Health becomes 50%.\n" +
                                   "🦵 LEGS: -20 Health.\n" +
                                   "👟 FEET: -5 Health.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val formatRuleText = if (isNearbyMode) {
                            "First player to win $targetScore rounds wins the match!"
                        } else {
                            "Play all $targetScore rounds — player with the most round wins takes the match!"
                        }
                        Text(
                            text = formatRuleText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Got it! 🏹",
                        onClick = { showHelpDialog = false },
                        backgroundColor = colors.accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
    }
}
