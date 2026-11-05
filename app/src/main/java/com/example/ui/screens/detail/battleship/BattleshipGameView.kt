package com.example.ui.screens.detail.battleship

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.BattleshipSoundManager
import com.example.core.engine.battleship.BattleshipBot
import com.example.core.engine.battleship.BattleshipEngine
import com.example.core.engine.battleship.BattleshipPhase
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.CellCoordinate
import com.example.core.engine.battleship.PlayerFleetState
import com.example.core.engine.battleship.ShipOrientation
import com.example.core.engine.battleship.ShipPlacement
import com.example.core.engine.battleship.ShipType
import com.example.core.engine.battleship.ShotOutcome
import com.example.core.engine.battleship.TargetingRadar
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.components.ChunkyButton
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import com.example.ui.theme.PlayfulOrange
import com.example.ui.theme.PlayfulPink
import com.example.ui.theme.PlayfulPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BattleshipGameView(
    selectedMode: GameMode,
    viewModel: MainViewModel,
    onChangeMode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val colors = ChillTheme.colors

    val soundManager = remember { BattleshipSoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    LaunchedEffect(settings.soundEnabled) {
        if (!settings.soundEnabled) {
            soundManager.stopAllSounds()
        }
    }

    val engine = remember { BattleshipEngine() }
    var gameState by remember { mutableStateOf(engine.state) }
    val bot = remember { BattleshipBot() }

    val multiplayerManager = viewModel.multiplayerManager
    val lobbyState by multiplayerManager.lobbyState.collectAsState()
    val isNearbyMode = selectedMode == GameMode.BLUETOOTH_1V1
    val isHost = (lobbyState as? MultiplayerLobbyState.InGame)?.isHost
        ?: ((lobbyState as? MultiplayerLobbyState.Connected)?.isHost ?: true)
    val localPlayer = if (isHost) BattleshipPlayer.PLAYER_1 else BattleshipPlayer.PLAYER_2

    // Local placement state
    var selectedShipType by remember { mutableStateOf<ShipType?>(ShipType.CARRIER) }
    var deploymentOrientation by remember { mutableStateOf(ShipOrientation.HORIZONTAL) }
    var activeBoardTab by remember { mutableIntStateOf(0) } // 0 = RADAR (Attack), 1 = FLEET (Defense)

    // Handover & UI state
    var isHandoverVisible by remember { mutableStateOf(false) }
    var handoverTargetPlayer by remember { mutableStateOf(BattleshipPlayer.PLAYER_1) }
    var isBotThinking by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }
    var hasPlayedOutcomeAudio by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var remotePlayerReady by remember { mutableStateOf(false) }
    var sessionKey by remember { mutableIntStateOf(0) }

    // Multi-stage attack animation state & exact screen coordinate mapping
    var activeAttack by remember { mutableStateOf<ActiveAttackAnimation?>(null) }
    var isAttackAnimating by remember { mutableStateOf(false) }

    val radarCellCoordinatesMap = remember { mutableStateMapOf<CellCoordinate, LayoutCoordinates>() }
    var radarOverlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val fleetCellCoordinatesMap = remember { mutableStateMapOf<CellCoordinate, LayoutCoordinates>() }
    var fleetOverlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun getRadarCellCenter(coord: CellCoordinate): Offset {
        val overlay = radarOverlayCoordinates
        if (overlay != null && overlay.isAttached && overlay.size.width > 0 && overlay.size.height > 0) {
            val rowLabelW = with(density) { 18.dp.toPx() }
            val gridW = overlay.size.width.toFloat() - rowLabelW
            val cellW = (gridW / 10f).coerceAtLeast(1f)
            val cellH = (overlay.size.height.toFloat() / 10f).coerceAtLeast(1f)
            return Offset(rowLabelW + (coord.col + 0.5f) * cellW, (coord.row + 0.5f) * cellH)
        }
        try {
            val cellCoords = radarCellCoordinatesMap[coord]
            if (cellCoords != null && overlay != null && cellCoords.isAttached && overlay.isAttached) {
                val center = overlay.localPositionOf(
                    cellCoords,
                    Offset(cellCoords.size.width / 2f, cellCoords.size.height / 2f)
                )
                if (center != Offset.Zero) return center
            }
        } catch (_: Throwable) {}
        return Offset.Zero
    }

    fun getRadarCellSize(coord: CellCoordinate): Float {
        val overlay = radarOverlayCoordinates
        if (overlay != null && overlay.isAttached && overlay.size.width > 0) {
            val rowLabelW = with(density) { 18.dp.toPx() }
            return ((overlay.size.width.toFloat() - rowLabelW) / 10f).coerceAtLeast(20f)
        }
        val cellCoords = radarCellCoordinatesMap[coord]
        return if (cellCoords != null && cellCoords.isAttached) cellCoords.size.width.toFloat().coerceAtLeast(20f) else 40f
    }

    fun getFleetCellCenter(coord: CellCoordinate): Offset {
        val overlay = fleetOverlayCoordinates
        if (overlay != null && overlay.isAttached && overlay.size.width > 0 && overlay.size.height > 0) {
            val rowLabelW = with(density) { 18.dp.toPx() }
            val gridW = overlay.size.width.toFloat() - rowLabelW
            val cellW = (gridW / 10f).coerceAtLeast(1f)
            val cellH = (overlay.size.height.toFloat() / 10f).coerceAtLeast(1f)
            return Offset(rowLabelW + (coord.col + 0.5f) * cellW, (coord.row + 0.5f) * cellH)
        }
        try {
            val cellCoords = fleetCellCoordinatesMap[coord]
            if (cellCoords != null && overlay != null && cellCoords.isAttached && overlay.isAttached) {
                val center = overlay.localPositionOf(
                    cellCoords,
                    Offset(cellCoords.size.width / 2f, cellCoords.size.height / 2f)
                )
                if (center != Offset.Zero) return center
            }
        } catch (_: Throwable) {}
        return Offset.Zero
    }

    fun getFleetCellSize(coord: CellCoordinate): Float {
        val overlay = fleetOverlayCoordinates
        if (overlay != null && overlay.isAttached && overlay.size.width > 0) {
            val rowLabelW = with(density) { 18.dp.toPx() }
            return ((overlay.size.width.toFloat() - rowLabelW) / 10f).coerceAtLeast(20f)
        }
        val cellCoords = fleetCellCoordinatesMap[coord]
        return if (cellCoords != null && cellCoords.isAttached) cellCoords.size.width.toFloat().coerceAtLeast(20f) else 40f
    }

    val isDeployment = gameState.phase == BattleshipPhase.DEPLOYMENT_P1 ||
        gameState.phase == BattleshipPhase.DEPLOYMENT_P2 ||
        gameState.phase == BattleshipPhase.WAITING_FOR_REMOTE_DEPLOYMENT

    fun vibrate(isBig: Boolean = false) {
        if (settings.vibrationEnabled) {
            val effect = if (isBig) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
            view.performHapticFeedback(effect)
        }
    }

    fun restartGame() {
        engine.resetGame()
        gameState = engine.state
        bot.reset()
        selectedShipType = ShipType.CARRIER
        deploymentOrientation = ShipOrientation.HORIZONTAL
        activeBoardTab = 0
        isHandoverVisible = false
        isBotThinking = false
        matchRecorded = false
        hasPlayedOutcomeAudio = false
        remotePlayerReady = false
        activeAttack = null
        isAttackAnimating = false
        soundManager.stopAllSounds()
        sessionKey++
        vibrate()
    }

    // ==========================================
    // Fleet Deployment Handlers
    // ==========================================
    fun onDeployCellClick(row: Int, col: Int, deployingPlayer: BattleshipPlayer) {
        val currentFleet = gameState.fleetFor(deployingPlayer)
        val clickedCoord = CellCoordinate(row, col)
        val existingShip = currentFleet.getShipAt(clickedCoord)

        // If clicking on an already placed ship: select it for modification / rotation / removal
        if (existingShip != null) {
            selectedShipType = existingShip.shipType
            deploymentOrientation = existingShip.orientation
            soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
            return
        }

        val ship = selectedShipType ?: return

        // Placing or moving the selected ship to this coordinate
        if (BattleshipEngine.isValidPlacement(ship, row, col, deploymentOrientation, currentFleet.placements)) {
            engine.placeShip(deployingPlayer, ship, row, col, deploymentOrientation)
            gameState = engine.state
            soundManager.playPlacement(settings.soundEnabled, settings.vibrationEnabled)

            // Auto-advance to next unplaced ship if available
            val updatedFleet = gameState.fleetFor(deployingPlayer)
            val unplaced = ShipType.ALL_SHIPS.firstOrNull { type ->
                updatedFleet.placements.none { it.shipType == type }
            }
            if (unplaced != null) {
                selectedShipType = unplaced
            }
        } else {
            // Invalid placement attempt
            vibrate(isBig = true)
        }
    }

    fun onRotateSelectedShip(deployingPlayer: BattleshipPlayer) {
        val ship = selectedShipType ?: return
        val currentFleet = gameState.fleetFor(deployingPlayer)
        val isPlaced = currentFleet.placements.any { it.shipType == ship }

        if (isPlaced) {
            val rotated = engine.rotateShip(deployingPlayer, ship)
            if (rotated) {
                gameState = engine.state
                deploymentOrientation = if (deploymentOrientation == ShipOrientation.HORIZONTAL) {
                    ShipOrientation.VERTICAL
                } else ShipOrientation.HORIZONTAL
                soundManager.playRotation(settings.soundEnabled, settings.vibrationEnabled)
            } else {
                // Rotation blocked by boundary or neighboring vessel
                vibrate(isBig = true)
            }
        } else {
            // Simply toggle pending orientation for next placement
            deploymentOrientation = if (deploymentOrientation == ShipOrientation.HORIZONTAL) {
                ShipOrientation.VERTICAL
            } else ShipOrientation.HORIZONTAL
            soundManager.playRotation(settings.soundEnabled, settings.vibrationEnabled)
        }
    }

    fun onRemoveSelectedShip(deployingPlayer: BattleshipPlayer, ship: ShipType) {
        engine.removeShip(deployingPlayer, ship)
        gameState = engine.state
        selectedShipType = ship
        soundManager.playRotation(settings.soundEnabled, settings.vibrationEnabled)
    }

    fun onClearAllShips(deployingPlayer: BattleshipPlayer) {
        for (ship in ShipType.ALL_SHIPS) {
            engine.removeShip(deployingPlayer, ship)
        }
        gameState = engine.state
        selectedShipType = ShipType.CARRIER
        deploymentOrientation = ShipOrientation.HORIZONTAL
        soundManager.playRotation(settings.soundEnabled, settings.vibrationEnabled)
    }

    fun onConfirmDeployment(deployingPlayer: BattleshipPlayer) {
        val fleet = gameState.fleetFor(deployingPlayer)
        if (!fleet.isDeploymentComplete) return

        when (selectedMode) {
            GameMode.SOLO -> {
                // Deploy bot fleet automatically & begin battle!
                val botFleet = BattleshipEngine.generateRandomFleet()
                engine.setFleet(BattleshipPlayer.PLAYER_2, botFleet)
                engine.setPhase(BattleshipPhase.BATTLE)
                gameState = engine.state
                soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
            }
            GameMode.LOCAL_1V1 -> {
                if (deployingPlayer == BattleshipPlayer.PLAYER_1) {
                    // Privacy handover to Player 2
                    handoverTargetPlayer = BattleshipPlayer.PLAYER_2
                    isHandoverVisible = true
                    selectedShipType = ShipType.CARRIER
                    soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                } else {
                    // Player 2 finished -> Handover back to Player 1 to start battle!
                    engine.setPhase(BattleshipPhase.BATTLE)
                    gameState = engine.state
                    handoverTargetPlayer = BattleshipPlayer.PLAYER_1
                    isHandoverVisible = true
                    soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                }
            }
            GameMode.BLUETOOTH_1V1 -> {
                multiplayerManager.sendAction("BATTLESHIP_READY", "")
                if (remotePlayerReady) {
                    engine.setPhase(BattleshipPhase.BATTLE)
                    gameState = engine.state
                    soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                } else {
                    engine.setPhase(BattleshipPhase.WAITING_FOR_REMOTE_DEPLOYMENT)
                    gameState = engine.state
                }
            }
        }
    }

    // ==========================================
    // Multi-Stage Animated Combat Firing
    // ==========================================
    fun onTargetCellClick(coord: CellCoordinate) {
        if (gameState.phase != BattleshipPhase.BATTLE || isBotThinking || isAttackAnimating) return
        if (isHandoverVisible) return

        if (isNearbyMode && gameState.activePlayer != localPlayer) return
        if (selectedMode == GameMode.SOLO && gameState.activePlayer != BattleshipPlayer.PLAYER_1) return

        val firingPlayer = gameState.activePlayer
        val radar = gameState.radarFor(firingPlayer)
        if (radar.hasFiredAt(coord)) return

        if (isNearbyMode) {
            // In WiFi Play, trigger launch and send action
            soundManager.playLaunch(settings.soundEnabled)
            multiplayerManager.sendAction("BATTLESHIP_FIRE", "${coord.row},${coord.col}")
            return
        }

        // Solo & Local 1v1: Execute full visual multi-stage attack
        scope.launch {
            try {
                activeBoardTab = 0 // Automatically display Radar (Attack) tab for human strike
                isAttackAnimating = true

                // Ensure layout coordinates of tapped cell are resolved
                var targetCenter = getRadarCellCenter(coord)
                var retryCount = 0
                while (targetCenter == Offset.Zero && retryCount < 10) {
                    delay(20)
                    targetCenter = getRadarCellCenter(coord)
                    retryCount++
                }
                val cellSizePx = getRadarCellSize(coord)

                // Peek at outcome to determine animation visuals
                val enemyFleet = gameState.fleetFor(firingPlayer.opponent())
                val hitShip = enemyFleet.getShipAt(coord)
                val isHit = hitShip != null

                // Stage 1: Target Lock (1,000 ms)
                activeAttack = AttackAnimationData(
                    attacker = firingPlayer,
                    isBotAttacker = false,
                    targetCoord = coord,
                    targetBoard = BoardType.RADAR,
                    targetCenterInOverlay = targetCenter,
                    cellSizePx = cellSizePx,
                    isHit = isHit,
                    isSunk = false,
                    stage = AttackAnimationStage.TARGET_LOCK,
                    bannerTitle = if (selectedMode == GameMode.LOCAL_1V1 && firingPlayer == BattleshipPlayer.PLAYER_2) "⚔️ PLAYER 2 ATTACK" else "⚔️ YOUR ATTACK",
                    bannerSubtitle = "Targeting enemy fleet...",
                    resultText = "",
                    announcement = "Target lock acquired at ${('A' + coord.col)}${coord.row + 1}"
                )
                soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                delay(AttackTimingConfig.TARGET_LOCK_MS)

                // Stage 2: Bomb Flight / Free Fall (1,000 ms)
                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.PROJECTILE_FLIGHT,
                    bannerSubtitle = "Bomb falling from above..."
                )
                soundManager.playLaunch(settings.soundEnabled)
                delay(AttackTimingConfig.PROJECTILE_FLIGHT_MS)

                // Stage 3: Impact (Explosion or Splash) (1,200 ms)
                val outcome = engine.fireShot(firingPlayer, coord)

                val isSunk = (outcome as? ShotOutcome.Hit)?.isSunk ?: false
                val sunkShip = (outcome as? ShotOutcome.Hit)?.shipType?.title

                val humanResultText = when {
                    isSunk -> "ENEMY SHIP SUNK! 💥"
                    isHit -> "DIRECT HIT! 🎯"
                    else -> "SHOT MISSED! 🌊"
                }

                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.IMPACT,
                    isSunk = isSunk,
                    sunkShipName = sunkShip,
                    bannerSubtitle = humanResultText,
                    resultText = humanResultText
                )

                if (isHit) {
                    if (isSunk) {
                        soundManager.playShipSunk(settings.soundEnabled, settings.vibrationEnabled)
                    } else {
                        soundManager.playHit(settings.soundEnabled, settings.vibrationEnabled)
                    }
                } else {
                    soundManager.playMiss(settings.soundEnabled, settings.vibrationEnabled)
                }
                delay(AttackTimingConfig.IMPACT_MS)

                // Stage 4: Result Announcement (1,500 ms)
                val announcementText = when {
                    engine.state.isGameOver -> "VICTORY! ALL ENEMY VESSELS SUNK!"
                    isSunk -> "DIRECT HIT! $sunkShip SUNK! TAKE ANOTHER SHOT!"
                    isHit -> "DIRECT HIT! EXTRA SHOT AWARDED!"
                    else -> "SPLASH! SHOT MISSED."
                }
                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.RESULT_ANNOUNCEMENT,
                    bannerSubtitle = announcementText,
                    announcement = announcementText
                )
                delay(AttackTimingConfig.RESULT_ANNOUNCEMENT_MS)

                // Stage 5: Turn Transition (500 ms)
                delay(AttackTimingConfig.TURN_TRANSITION_MS)

                // Commit authoritative game state after human animation finishes cleanly
                gameState = engine.state

                if (!isHit && selectedMode == GameMode.LOCAL_1V1 && !engine.state.isGameOver) {
                    handoverTargetPlayer = firingPlayer.opponent()
                    isHandoverVisible = true
                }
            } catch (_: Exception) {
                gameState = engine.state
            } finally {
                activeAttack = null
                isAttackAnimating = false
            }
        }
    }

    // ==========================================
    // Autonomous Bot Turn with Visible Multi-Stage Bombing
    // ==========================================
    LaunchedEffect(gameState.activePlayer, gameState.phase, selectedMode, sessionKey) {
        if (selectedMode != GameMode.SOLO) return@LaunchedEffect
        if (gameState.phase != BattleshipPhase.BATTLE) return@LaunchedEffect
        if (gameState.activePlayer != BattleshipPlayer.PLAYER_2) return@LaunchedEffect

        while (gameState.activePlayer == BattleshipPlayer.PLAYER_2 && !gameState.isGameOver) {
            try {
                // Automatically switch to MY FLEET (DEFENSE) board for incoming bot strike!
                activeBoardTab = 1
                isBotThinking = true

                // Stage 0: Thinking / Preparation (1,200 ms)
                delay(AttackTimingConfig.THINKING_MS)

                // Stage 1: Bot decides move & locks target (1,000 ms)
                val targetCoord = bot.decideMove(gameState.p2Radar)
                isBotThinking = false
                isAttackAnimating = true

                // Ensure fleet board layout is measured
                var targetCenter = getFleetCellCenter(targetCoord)
                var retryCount = 0
                while (targetCenter == Offset.Zero && retryCount < 10) {
                    delay(20)
                    targetCenter = getFleetCellCenter(targetCoord)
                    retryCount++
                }
                val cellSizePx = getFleetCellSize(targetCoord)

                val humanFleet = gameState.p1Fleet
                val hitShip = humanFleet.getShipAt(targetCoord)
                val isHit = hitShip != null

                activeAttack = AttackAnimationData(
                    attacker = BattleshipPlayer.PLAYER_2,
                    isBotAttacker = true,
                    targetCoord = targetCoord,
                    targetBoard = BoardType.MY_FLEET,
                    targetCenterInOverlay = targetCenter,
                    cellSizePx = cellSizePx,
                    isHit = isHit,
                    isSunk = false,
                    stage = AttackAnimationStage.TARGET_LOCK,
                    bannerTitle = "🤖 BOT ATTACKING YOU!",
                    bannerSubtitle = "Incoming naval strike...",
                    resultText = "",
                    announcement = "Warning! Strike locked at ${('A' + targetCoord.col)}${targetCoord.row + 1}"
                )
                soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                delay(AttackTimingConfig.TARGET_LOCK_MS)

                // Stage 2: Bomb Flight / Free Fall (1,000 ms)
                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.PROJECTILE_FLIGHT,
                    bannerSubtitle = "Bomb dropping on your fleet..."
                )
                soundManager.playLaunch(settings.soundEnabled)
                delay(AttackTimingConfig.PROJECTILE_FLIGHT_MS)

                // Stage 3: Impact (Explosion / Splash) (1,200 ms)
                val outcome = engine.fireShot(BattleshipPlayer.PLAYER_2, targetCoord)
                bot.onShotResult(targetCoord, outcome)

                val isSunk = (outcome as? ShotOutcome.Hit)?.isSunk ?: false
                val sunkShip = (outcome as? ShotOutcome.Hit)?.shipType?.title

                val botResultText = when {
                    isSunk -> "YOUR SHIP SUNK! 🚢"
                    isHit -> "YOUR SHIP HIT! 💥"
                    else -> "BOT MISSED! 🌊"
                }

                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.IMPACT,
                    isSunk = isSunk,
                    sunkShipName = sunkShip,
                    bannerSubtitle = botResultText,
                    resultText = botResultText
                )

                if (isHit) {
                    if (isSunk) {
                        soundManager.playShipSunk(settings.soundEnabled, settings.vibrationEnabled)
                    } else {
                        soundManager.playHit(settings.soundEnabled, settings.vibrationEnabled)
                    }
                } else {
                    soundManager.playMiss(settings.soundEnabled, settings.vibrationEnabled)
                }
                delay(AttackTimingConfig.IMPACT_MS)

                // Stage 4: Result Announcement (1,500 ms)
                val announcementText = when {
                    engine.state.isGameOver -> "BOT DESTROYED YOUR FLEET!"
                    isSunk -> "YOUR $sunkShip SUNK! BOT ATTACKING AGAIN!"
                    isHit -> "YOUR SHIP WAS HIT! BOT GETS ANOTHER SHOT!"
                    else -> "BOT MISSED! YOUR TURN!"
                }
                activeAttack = activeAttack?.copy(
                    stage = AttackAnimationStage.RESULT_ANNOUNCEMENT,
                    bannerSubtitle = botResultText,
                    announcement = announcementText
                )
                delay(AttackTimingConfig.RESULT_ANNOUNCEMENT_MS)

                // Stage 5: Turn Transition (500 ms)
                delay(AttackTimingConfig.TURN_TRANSITION_MS)

                // Commit updated game state after bot attack animation finishes
                gameState = engine.state

                // If bot missed and it is now human turn, automatically switch back to Radar tab!
                if (!isHit && engine.state.activePlayer == BattleshipPlayer.PLAYER_1 && !engine.state.isGameOver) {
                    activeBoardTab = 0
                }
            } catch (_: Exception) {
                gameState = engine.state
            } finally {
                activeAttack = null
                isAttackAnimating = false
            }
        }
    }

    // ==========================================
    // WiFi Play Networking Action Listener
    // ==========================================
    LaunchedEffect(isNearbyMode) {
        if (isNearbyMode) {
            multiplayerManager.incomingActions.collect { action ->
                when (action.actionType) {
                    "BATTLESHIP_READY" -> {
                        remotePlayerReady = true
                        val myFleet = gameState.fleetFor(localPlayer)
                        if (myFleet.isDeploymentComplete) {
                            engine.setPhase(BattleshipPhase.BATTLE)
                            gameState = engine.state
                            soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                        }
                    }
                    "BATTLESHIP_FIRE" -> {
                        val parts = action.payload.split(",")
                        if (parts.size == 2) {
                            val r = parts[0].toIntOrNull() ?: return@collect
                            val c = parts[1].toIntOrNull() ?: return@collect
                            val coord = CellCoordinate(r, c)
                            val remotePlayer = localPlayer.opponent()

                            val myFleet = gameState.fleetFor(localPlayer)
                            val targetShip = myFleet.getShipAt(coord)
                            val isHit = targetShip != null

                            val updatedIncoming = myFleet.incomingShots + (coord to isHit)
                            val updatedMyFleet = myFleet.copy(incomingShots = updatedIncoming)
                            val isSunk = targetShip != null && updatedMyFleet.isShipSunk(targetShip)
                            val sunkCode = if (isSunk && targetShip != null) targetShip.shipType.name else "NONE"

                            multiplayerManager.sendAction(
                                "BATTLESHIP_RESULT",
                                "$r,$c,$isHit,$sunkCode"
                            )

                            engine.applyRemoteIncomingShot(
                                remotePlayer,
                                coord,
                                isHit,
                                if (isSunk) targetShip?.shipType else null
                            )
                            gameState = engine.state

                            if (isHit) {
                                if (isSunk) {
                                    soundManager.playShipSunk(settings.soundEnabled, settings.vibrationEnabled)
                                } else {
                                    soundManager.playHit(settings.soundEnabled, settings.vibrationEnabled)
                                }
                            } else {
                                soundManager.playMiss(settings.soundEnabled, settings.vibrationEnabled)
                            }
                        }
                    }
                    "BATTLESHIP_RESULT" -> {
                        val parts = action.payload.split(",")
                        if (parts.size == 4) {
                            val r = parts[0].toIntOrNull() ?: return@collect
                            val c = parts[1].toIntOrNull() ?: return@collect
                            val isHit = parts[2].toBooleanStrictOrNull() ?: false
                            val sunkName = parts[3]
                            val sunkType = if (sunkName != "NONE") runCatching { ShipType.valueOf(sunkName) }.getOrNull() else null

                            val coord = CellCoordinate(r, c)
                            engine.applyRemoteIncomingShot(localPlayer, coord, isHit, sunkType)
                            gameState = engine.state

                            if (isHit) {
                                if (sunkType != null) {
                                    soundManager.playShipSunk(settings.soundEnabled, settings.vibrationEnabled)
                                } else {
                                    soundManager.playHit(settings.soundEnabled, settings.vibrationEnabled)
                                }
                            } else {
                                soundManager.playMiss(settings.soundEnabled, settings.vibrationEnabled)
                            }
                        }
                    }
                    "BATTLESHIP_REMATCH" -> {
                        restartGame()
                    }
                }
            }
        }
    }

    // ==========================================
    // Match Record & Outcome Audio
    // ==========================================
    LaunchedEffect(gameState.isGameOver) {
        if (gameState.isGameOver && !matchRecorded) {
            matchRecorded = true
            val winnerName = when (gameState.winner) {
                BattleshipPlayer.PLAYER_1 -> "PLAYER_1"
                BattleshipPlayer.PLAYER_2 -> "PLAYER_2"
                else -> "DRAW"
            }
            viewModel.repository.recordMatch(
                gameId = "battleship",
                gameTitle = "Battleship",
                mode = selectedMode.name,
                player1Score = gameState.p1Radar.totalHits,
                player2Score = gameState.p2Radar.totalHits,
                winner = winnerName,
                durationSeconds = 120
            )
        }
    }

    LaunchedEffect(gameState.isGameOver, sessionKey) {
        if (gameState.isGameOver && !hasPlayedOutcomeAudio) {
            hasPlayedOutcomeAudio = true
            delay(400)
            val outcome = BattleshipSoundManager.resolveOutcomeAudio(
                winner = gameState.winner,
                mode = selectedMode,
                localPlayer = localPlayer
            )
            soundManager.playOutcome(outcome, settings.soundEnabled)
        }
    }

    BackHandler {
        onExit()
    }

    val isDark = colors.isDark
    val battleshipBg = Brush.verticalGradient(
        if (isDark) {
            listOf(
                Color(0xFF0A192F), // Deep ocean naval navy
                Color(0xFF0F2B48), // Ocean water teal-blue
                Color(0xFF061426)
            )
        } else {
            listOf(
                Color(0xFFE0F2FE), // Clean light ocean blue
                Color(0xFFBAE6FD), // Sky blue water
                Color(0xFFF0F9FF)  // Soft nautical wash
            )
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(battleshipBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                        .border(1.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD), CircleShape)
                        .bouncyClickable(onClick = onExit)
                        .testTag("battleship_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit",
                        tint = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "BATTLESHIP",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        ),
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                    val modeLabel = when (selectedMode) {
                        GameMode.SOLO -> "VS BOT (HUNT & TARGET)"
                        GameMode.LOCAL_1V1 -> "PASS & PLAY (LOCAL 1v1)"
                        GameMode.BLUETOOTH_1V1 -> "WIFI PLAY (PEER-TO-PEER)"
                    }
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                            .border(1.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD), CircleShape)
                            .bouncyClickable { showHelpDialog = true }
                            .testTag("battleship_help_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help",
                            tint = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                            .border(1.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD), CircleShape)
                            .bouncyClickable { restartGame() }
                            .testTag("battleship_restart_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart",
                            tint = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Main Screen Routing: Fleet Deployment vs Battle Arena
            if (isDeployment) {
                val currentDeployingPlayer = if (selectedMode == GameMode.LOCAL_1V1 && handoverTargetPlayer == BattleshipPlayer.PLAYER_2) {
                    BattleshipPlayer.PLAYER_2
                } else if (isNearbyMode) {
                    localPlayer
                } else {
                    BattleshipPlayer.PLAYER_1
                }

                FleetDeploymentView(
                    deployingPlayer = currentDeployingPlayer,
                    fleetState = gameState.fleetFor(currentDeployingPlayer),
                    selectedShip = selectedShipType,
                    orientation = deploymentOrientation,
                    onSelectShip = {
                        selectedShipType = it
                        soundManager.playPlacement(settings.soundEnabled, settings.vibrationEnabled)
                    },
                    onRotateShip = { onRotateSelectedShip(currentDeployingPlayer) },
                    onCellClick = { r, c -> onDeployCellClick(r, c, currentDeployingPlayer) },
                    onRemoveShip = { ship -> onRemoveSelectedShip(currentDeployingPlayer, ship) },
                    onClearAll = { onClearAllShips(currentDeployingPlayer) },
                    isWaitingForRemote = gameState.phase == BattleshipPhase.WAITING_FOR_REMOTE_DEPLOYMENT,
                    onConfirm = { onConfirmDeployment(currentDeployingPlayer) }
                )
            } else {
                val viewingPlayer = when {
                    selectedMode == GameMode.SOLO -> BattleshipPlayer.PLAYER_1
                    isNearbyMode -> localPlayer
                    else -> gameState.activePlayer
                }

                // DATA PRIVACY: Ensure the UI receives ONLY the fleet and radar it is allowed to display!
                // In SOLO mode: NEVER pass bot's fleet to the UI. Only human fleet is shown on MY FLEET.
                // In Nearby (WiFi) mode: NEVER pass remote opponent's unhit fleet to the UI.
                val displayRadar = when {
                    selectedMode == GameMode.SOLO -> gameState.p1Radar
                    isNearbyMode -> gameState.radarFor(localPlayer)
                    else -> gameState.radarFor(viewingPlayer)
                }

                val displayFleet = when {
                    selectedMode == GameMode.SOLO -> gameState.p1Fleet
                    isNearbyMode -> gameState.fleetFor(localPlayer)
                    else -> gameState.fleetFor(viewingPlayer)
                }

                BattleArenaView(
                    viewingPlayer = viewingPlayer,
                    activePlayer = gameState.activePlayer,
                    radar = displayRadar,
                    myFleet = displayFleet,
                    activeTab = activeBoardTab,
                    onTabChanged = { if (!isAttackAnimating && !isBotThinking) activeBoardTab = it },
                    onTargetClick = { onTargetCellClick(it) },
                    isBotThinking = isBotThinking,
                    isAttackAnimating = isAttackAnimating,
                    activeAttack = activeAttack,
                    latestAnnouncement = activeAttack?.announcement?.ifBlank { null } ?: gameState.latestAnnouncement,
                    selectedMode = selectedMode,
                    localPlayer = localPlayer,
                    onRadarCellPositioned = { coord, coords -> radarCellCoordinatesMap[coord] = coords },
                    onRadarOverlayPositioned = { radarOverlayCoordinates = it },
                    onFleetCellPositioned = { coord, coords -> fleetCellCoordinatesMap[coord] = coords },
                    onFleetOverlayPositioned = { fleetOverlayCoordinates = it },
                    getRadarCellCenter = { getRadarCellCenter(it) },
                    getFleetCellCenter = { getFleetCellCenter(it) }
                )
            }
        }

        // 3. Local Pass-the-Device Privacy Handover Screen
        if (isHandoverVisible && selectedMode == GameMode.LOCAL_1V1) {
            PrivacyHandoverOverlay(
                targetPlayer = handoverTargetPlayer,
                isDeployment = isDeployment,
                onReady = {
                    isHandoverVisible = false
                    soundManager.playSonarPing(settings.soundEnabled, settings.vibrationEnabled)
                }
            )
        }

        // 4. Game Over Summary Dialog
        if (gameState.isGameOver) {
            val didPlayer1Win = gameState.winner == BattleshipPlayer.PLAYER_1
            val titleText = when {
                selectedMode == GameMode.SOLO && didPlayer1Win -> "🏆 FLEET VICTORY!"
                selectedMode == GameMode.SOLO -> "💥 FLEET DEFEAT!"
                isNearbyMode && gameState.winner == localPlayer -> "🏆 FLEET VICTORY!"
                isNearbyMode -> "💥 FLEET DEFEAT!"
                didPlayer1Win -> "🏆 PLAYER 1 WINS!"
                else -> "🏆 PLAYER 2 WINS!"
            }

            val p1Radar = gameState.p1Radar
            val p2Radar = gameState.p2Radar

            AlertDialog(
                onDismissRequest = {},
                shape = RoundedCornerShape(24.dp),
                containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF),
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "All enemy naval vessels have been destroyed and sunk!",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF0F9FF))
                                .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("P1 SHOTS", style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                                Text("${p1Radar.totalShotsFired}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = if (isDark) Color.White else Color(0xFF0F172A))
                                Text("Hits: ${p1Radar.totalHits}", style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                                Text("Acc: ${p1Radar.accuracyPercentage}%", style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val p2Label = if (selectedMode == GameMode.SOLO) "BOT SHOTS" else "P2 SHOTS"
                                Text(p2Label, style = MaterialTheme.typography.labelSmall, color = PlayfulPink, fontWeight = FontWeight.Bold)
                                Text("${p2Radar.totalShotsFired}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = if (isDark) Color.White else Color(0xFF0F172A))
                                Text("Hits: ${p2Radar.totalHits}", style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                                Text("Acc: ${p2Radar.accuracyPercentage}%", style = MaterialTheme.typography.labelSmall, color = PlayfulPink)
                            }
                        }
                    }
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Play Again 🔄",
                        backgroundColor = Color(0xFF0284C7),
                        onClick = {
                            restartGame()
                            if (isNearbyMode) {
                                multiplayerManager.sendAction("BATTLESHIP_REMATCH", "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = onChangeMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Mode", fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    }
                }
            )
        }

        // 5. Help Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                shape = RoundedCornerShape(22.dp),
                containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF),
                title = { Text("Battleship Rules 📜", fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color(0xFF0F172A)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val bodyColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                        Text("• 10×10 Grid: Deploy your 5 naval vessels (Carrier 5, Battleship 4, Cruiser 3, Submarine 3, Destroyer 2).", color = bodyColor, fontSize = 13.sp)
                        Text("• Manual Fleet Editing: Tap any placed ship to select, rotate in place, or remove and reposition.", color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("• Extra-Shot Rule: Every valid HIT or SUNK grants another shot immediately!", color = PlayfulGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("• Turn Switch: A MISS passes the turn to your opponent.", color = bodyColor, fontSize = 13.sp)
                        Text("• Attack Animations: Realistic crosshairs, artillery shells, fireball explosions, and water splashes.", color = bodyColor, fontSize = 13.sp)
                        Text("• Victory: Sink all 5 enemy vessels (17 total hits) to rule the seas!", color = Color(0xFFD97706), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Got It!", fontWeight = FontWeight.Black, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                    }
                }
            )
        }
    }
}

// ==========================================
// Fleet Deployment Screen with Editable Ships
// ==========================================
@Composable
private fun FleetDeploymentView(
    deployingPlayer: BattleshipPlayer,
    fleetState: PlayerFleetState,
    selectedShip: ShipType?,
    orientation: ShipOrientation,
    onSelectShip: (ShipType) -> Unit,
    onRotateShip: () -> Unit,
    onCellClick: (Int, Int) -> Unit,
    onRemoveShip: (ShipType) -> Unit,
    onClearAll: () -> Unit,
    isWaitingForRemote: Boolean,
    onConfirm: () -> Unit
) {
    val placedTypes = fleetState.placements.map { it.shipType }.toSet()
    val progress = "${fleetState.placements.size}/${ShipType.ALL_SHIPS.size}"
    val isSelectedShipPlaced = selectedShip != null && placedTypes.contains(selectedShip)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isDark = ChillTheme.colors.isDark
        val playerLabel = when (deployingPlayer) {
            BattleshipPlayer.PLAYER_1 -> "PLAYER 1"
            BattleshipPlayer.PLAYER_2 -> "PLAYER 2"
        }
        Text(
            text = "$playerLabel FLEET DEPLOYMENT",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
        Text(
            text = "Position all 5 vessels on the 10×10 grid ($progress deployed)",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 10x10 Ocean Grid
        OceanBattleshipGrid(
            gridSize = 10,
            onCellClick = onCellClick,
            cellContent = { r, c ->
                val coord = CellCoordinate(r, c)
                val placedShip = fleetState.getShipAt(coord)

                if (placedShip != null) {
                    val isSelected = selectedShip == placedShip.shipType
                    val segmentIndex = placedShip.occupiedCoordinates.indexOf(coord)

                    BattleshipShipGraphics.ShipCellSegment(
                        shipType = placedShip.shipType,
                        orientation = placedShip.orientation,
                        segmentIndex = segmentIndex,
                        isHit = false,
                        isSunk = false,
                        isSelected = isSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isDark) Color(0xFF0F2B48).copy(alpha = 0.45f) else Color(0xFFE0F2FE)
                            )
                            .border(
                                0.5.dp,
                                if (isDark) Color(0xFF1E3A5F).copy(alpha = 0.4f) else Color(0xFFBAE6FD)
                            )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Ship Action Bar: Rotate / Remove / Clear
        if (selectedShip != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF))
                    .border(1.5.dp, if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header row: Selected ship name, orientation, status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SELECTED: ${selectedShip.title.uppercase()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isSelectedShipPlaced) {
                                "Placed on board • Tap cell to move"
                            } else {
                                "Ready to deploy • ${selectedShip.length} cells"
                            },
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Orientation Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF0F9FF))
                            .border(1.dp, if (isDark) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFFBAE6FD), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (orientation == ShipOrientation.HORIZONTAL) "HORIZ ➡️" else "VERT ⬇️",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0284C7)
                        )
                    }
                }

                // Action Buttons Row: Rotate & Remove with equal flexible weights and proper touch targets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotate Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7))
                            .bouncyClickable(onClick = onRotateShip)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("rotate_selected_ship_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ROTATE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // Remove Button (if placed)
                    if (isSelectedShipPlaced) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDC2626))
                                .bouncyClickable { onRemoveShip(selectedShip) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("remove_selected_ship_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "REMOVE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Vessel Selector Cards with Top-Down Visuals
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            ShipType.ALL_SHIPS.forEach { ship ->
                val isPlaced = placedTypes.contains(ship)
                val isSelected = selectedShip == ship

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f)
                            else if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF0284C7) else if (isDark) Color(0xFF1E293B) else Color(0xFFDCE3EC),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectShip(ship) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("ship_select_${ship.name.lowercase()}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Visual warship miniature
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(22.dp)
                        ) {
                            BattleshipShipGraphics.FullShipView(
                                shipType = ship,
                                isSelected = isSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = ship.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Text(
                                text = "${ship.length} cells",
                                fontSize = 10.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }

                    if (isPlaced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DEPLOYED ✓",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = PlayfulGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✕",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = PlayfulPink,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRemoveShip(ship) }
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = if (isSelected) "SELECTED" else "SELECT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isWaitingForRemote) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Fleet Deployed! Waiting for opponent... ⏳",
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (placedTypes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                            .bouncyClickable(onClick = onClearAll),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CLEAR ALL", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color(0xFF334155))
                    }
                }

                ChunkyButton(
                    text = if (fleetState.isDeploymentComplete) "START BATTLE 🚀" else "DEPLOY ALL ($progress)",
                    backgroundColor = if (fleetState.isDeploymentComplete) Color(0xFF0284C7) else (if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                    contentColor = if (fleetState.isDeploymentComplete) Color.White else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                    enabled = fleetState.isDeploymentComplete,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    testTag = "start_battle_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// Battle Arena View with Real-Time Attack Effects
// ==========================================
@Composable
private fun BattleArenaView(
    viewingPlayer: BattleshipPlayer,
    activePlayer: BattleshipPlayer,
    radar: TargetingRadar,
    myFleet: PlayerFleetState,
    activeTab: Int,
    onTabChanged: (Int) -> Unit,
    onTargetClick: (CellCoordinate) -> Unit,
    isBotThinking: Boolean,
    isAttackAnimating: Boolean,
    activeAttack: ActiveAttackAnimation?,
    latestAnnouncement: String,
    selectedMode: GameMode,
    localPlayer: BattleshipPlayer,
    onRadarCellPositioned: (CellCoordinate, LayoutCoordinates) -> Unit,
    onRadarOverlayPositioned: (LayoutCoordinates) -> Unit,
    onFleetCellPositioned: (CellCoordinate, LayoutCoordinates) -> Unit,
    onFleetOverlayPositioned: (LayoutCoordinates) -> Unit,
    getRadarCellCenter: (CellCoordinate) -> Offset,
    getFleetCellCenter: (CellCoordinate) -> Offset
) {
    val isMyTurn = (selectedMode == GameMode.SOLO && activePlayer == BattleshipPlayer.PLAYER_1) ||
        (selectedMode == GameMode.LOCAL_1V1 && activePlayer == viewingPlayer) ||
        (selectedMode == GameMode.BLUETOOTH_1V1 && activePlayer == localPlayer)
    val isDark = ChillTheme.colors.isDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status & Turn Banner or Dedicated Attacker Identification Banner
        if (activeAttack != null) {
            AttackerIdentificationBanner(activeAttack = activeAttack)
        } else if (isBotThinking) {
            val thinkingBg = if (isDark) {
                Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFF991B1B), Color(0xFF450A0A)))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFFFEE2E2), Color(0xFFFFEDD5), Color(0xFFFEE2E2)))
            }
            val thinkingBorder = if (isDark) Color(0xFFEF4444) else Color(0xFFF87171)
            val thinkingTitleColor = if (isDark) Color.White else Color(0xFF991B1B)
            val thinkingSubColor = if (isDark) Color(0xFFFDE047) else Color(0xFFB45309)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(thinkingBg)
                    .border(2.dp, thinkingBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("bot_thinking_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🤖 BOT ATTACKING YOU!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = thinkingTitleColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Calculating naval strike coordinates...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = thinkingSubColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDC2626))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TARGETING 📡",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            val turnBg = if (isDark) {
                if (isMyTurn) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF0F172A)
            } else {
                if (isMyTurn) Color(0xFFE0F2FE) else Color(0xFFFFFFFF)
            }
            val turnBorder = if (isDark) {
                if (isMyTurn) Color(0xFF38BDF8) else Color(0xFF1E293B)
            } else {
                if (isMyTurn) Color(0xFF0284C7) else Color(0xFFDCE3EC)
            }
            val turnTitleColor = if (isDark) {
                if (isMyTurn) Color(0xFF38BDF8) else Color(0xFF94A3B8)
            } else {
                if (isMyTurn) Color(0xFF0284C7) else Color(0xFF64748B)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(turnBg)
                    .border(
                        width = 1.5.dp,
                        color = turnBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val turnTitle = when {
                            isMyTurn -> "YOUR TURN — DROP BOMB! 🎯"
                            else -> "OPPONENT'S TURN ⏳"
                        }
                        Text(
                            text = turnTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                            color = turnTitleColor
                        )
                        if (latestAnnouncement.isNotBlank()) {
                            Text(
                                text = latestAnnouncement,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF172033)
                            )
                        }
                    }

                    // Fleet Afloat summary
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "FLEET AFLOAT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        Text(
                            text = "${myFleet.remainingShipsCount}/5",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) PlayfulGreen else Color(0xFF16A34A)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Tabs: 🎯 RADAR (Attack) vs 🛡️ MY FLEET (Defense)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeTab == 0) Color(0xFF0284C7) else (if (isDark) Color.Transparent else Color(0xFFF1F5F9)))
                    .bouncyClickable(enabled = !isAttackAnimating && !isBotThinking) { onTabChanged(0) }
                    .padding(vertical = 7.dp)
                    .testTag("radar_tab_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎯 RADAR (ATTACK)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (activeTab == 0) Color.White else (if (isDark) Color(0xFF94A3B8) else Color(0xFF475569))
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeTab == 1) Color(0xFF0284C7) else (if (isDark) Color.Transparent else Color(0xFFF1F5F9)))
                    .bouncyClickable(enabled = !isAttackAnimating && !isBotThinking) { onTabChanged(1) }
                    .padding(vertical = 7.dp)
                    .testTag("fleet_tab_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️ MY FLEET (${myFleet.remainingShipsCount}/5)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (activeTab == 1) Color.White else (if (isDark) Color(0xFF94A3B8) else Color(0xFF475569))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid Display based on active tab
        if (activeTab == 0) {
            // Radar Board: Enemy targeting
            OceanBattleshipGrid(
                gridSize = 10,
                onCellClick = { r, c ->
                    if (!isAttackAnimating && !isBotThinking) {
                        onTargetClick(CellCoordinate(r, c))
                    }
                },
                onCellPositioned = onRadarCellPositioned,
                onGridPositioned = onRadarOverlayPositioned,
                overlayContent = {
                    if (activeAttack != null && activeAttack.targetBoard == BoardType.RADAR) {
                        AttackEffectOverlay(
                            attackData = activeAttack,
                            modifier = Modifier.matchParentSize(),
                            liveCellCenterProvider = getRadarCellCenter
                        )
                    }
                },
                cellContent = { r, c ->
                    val coord = CellCoordinate(r, c)
                    val isTargeted = activeAttack != null && activeAttack.targetBoard == BoardType.RADAR && activeAttack.targetCoord == coord
                    val isFired = radar.shots.containsKey(coord)
                    val isHit = radar.shots[coord] == true

                    val cellModifier = if (isTargeted) {
                        Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color(0xFF38BDF8))
                    } else {
                        Modifier.fillMaxSize()
                    }

                    if (isFired) {
                        if (isHit) {
                            // Fiery breach on hit
                            Box(
                                modifier = cellModifier
                                    .background(if (isDark) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFFFEE2E2))
                                    .border(0.5.dp, if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔥", fontSize = 11.sp)
                            }
                        } else {
                            // Water splash on miss
                            Box(
                                modifier = cellModifier
                                    .background(if (isDark) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFFE0F2FE))
                                    .border(0.5.dp, if (isDark) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFFBAE6FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🌊", fontSize = 9.sp)
                            }
                        }
                    } else {
                        // Untried ocean water cell
                        val untriedBg = if (isDark) {
                            if (isTargeted) Color(0xFF0284C7).copy(alpha = 0.4f) else Color(0xFF0F2B48).copy(alpha = 0.65f)
                        } else {
                            if (isTargeted) Color(0xFFBAE6FD) else Color(0xFFF0F9FF)
                        }
                        val untriedBorder = if (isDark) {
                            if (isTargeted) Color(0xFF38BDF8) else Color(0xFF1E3A5F).copy(alpha = 0.5f)
                        } else {
                            if (isTargeted) Color(0xFF0284C7) else Color(0xFFBAE6FD).copy(alpha = 0.7f)
                        }
                        Box(
                            modifier = cellModifier
                                .background(untriedBg)
                                .border(0.5.dp, untriedBorder)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Radar Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color.White)
                    .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFDCE3EC), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("Shots: ${radar.totalShotsFired}", fontSize = 11.sp, color = if (isDark) Color.White else Color(0xFF172033), fontWeight = FontWeight.Bold)
                Text("Hits: ${radar.totalHits}", fontSize = 11.sp, color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                Text("Misses: ${radar.totalMisses}", fontSize = 11.sp, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                Text("Sunk: ${radar.sunkEnemyShips.size}/5", fontSize = 11.sp, color = if (isDark) PlayfulGreen else Color(0xFF16A34A), fontWeight = FontWeight.Bold)
            }
        } else {
            // My Fleet Board (Defense)
            OceanBattleshipGrid(
                gridSize = 10,
                onCellClick = { _, _ -> },
                onCellPositioned = onFleetCellPositioned,
                onGridPositioned = onFleetOverlayPositioned,
                overlayContent = {
                    if (activeAttack != null && activeAttack.targetBoard == BoardType.MY_FLEET) {
                        AttackEffectOverlay(
                            attackData = activeAttack,
                            modifier = Modifier.matchParentSize(),
                            liveCellCenterProvider = getFleetCellCenter
                        )
                    }
                },
                cellContent = { r, c ->
                    val coord = CellCoordinate(r, c)
                    val isTargeted = activeAttack != null && activeAttack.targetBoard == BoardType.MY_FLEET && activeAttack.targetCoord == coord
                    val ship = myFleet.getShipAt(coord)
                    val isFiredAt = myFleet.incomingShots.containsKey(coord)
                    val isHit = myFleet.incomingShots[coord] == true

                    val cellModifier = if (isTargeted) {
                        Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color(0xFFEF4444))
                    } else {
                        Modifier.fillMaxSize()
                    }

                    if (ship != null) {
                        val isSunk = myFleet.isShipSunk(ship)
                        val segmentIndex = ship.occupiedCoordinates.indexOf(coord)

                        Box(modifier = cellModifier) {
                            BattleshipShipGraphics.ShipCellSegment(
                                shipType = ship.shipType,
                                orientation = ship.orientation,
                                segmentIndex = segmentIndex,
                                isHit = isHit,
                                isSunk = isSunk,
                                isSelected = isTargeted,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (isFiredAt) {
                        Box(
                            modifier = cellModifier
                                .background(if (isDark) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌊", fontSize = 9.sp)
                        }
                    } else {
                        val emptyFleetBg = if (isDark) {
                            if (isTargeted) Color(0xFFEF4444).copy(alpha = 0.3f) else Color(0xFF0F2B48).copy(alpha = 0.5f)
                        } else {
                            if (isTargeted) Color(0xFFFEE2E2) else Color(0xFFF0F9FF)
                        }
                        val emptyFleetBorder = if (isDark) {
                            if (isTargeted) Color(0xFFEF4444) else Color(0xFF1E3A5F).copy(alpha = 0.4f)
                        } else {
                            if (isTargeted) Color(0xFFEF4444) else Color(0xFFBAE6FD).copy(alpha = 0.7f)
                        }
                        Box(
                            modifier = cellModifier
                                .background(emptyFleetBg)
                                .border(0.5.dp, emptyFleetBorder)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fleet Vessel Health List
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                myFleet.placements.forEach { placement ->
                    val isSunk = myFleet.isShipSunk(placement)
                    val hitsOnThis = placement.occupiedCoordinates.count { myFleet.incomingShots[it] == true }
                    val remainingHealth = placement.shipType.length - hitsOnThis

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF0F172A) else Color.White)
                            .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFDCE3EC), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(55.dp).height(16.dp)) {
                                BattleshipShipGraphics.FullShipView(
                                    shipType = placement.shipType,
                                    isSunk = isSunk,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = placement.shipType.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSunk) Color(0xFFEF4444) else (if (isDark) Color.White else Color(0xFF172033))
                            )
                        }

                        Text(
                            text = if (isSunk) "DESTROYED 💥" else "HEALTH: $remainingHealth/${placement.shipType.length}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSunk) Color(0xFFEF4444) else (if (isDark) PlayfulGreen else Color(0xFF16A34A))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 10x10 Nautical Grid with Ocean Theme
// ==========================================
@Composable
private fun OceanBattleshipGrid(
    gridSize: Int = 10,
    onCellClick: (Int, Int) -> Unit,
    onCellPositioned: ((CellCoordinate, LayoutCoordinates) -> Unit)? = null,
    onGridPositioned: ((LayoutCoordinates) -> Unit)? = null,
    overlayContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    cellContent: @Composable (Int, Int) -> Unit
) {
    val isDark = ChillTheme.colors.isDark
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF07192C) else Color(0xFFFFFFFF))
            .border(1.5.dp, if (isDark) Color(0xFF1E3A5F) else Color(0xFFBAE6FD), RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Columns A to J Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(18.dp))
            Row(modifier = Modifier.weight(1f)) {
                for (c in 0 until gridSize) {
                    val letter = ('A' + c).toString()
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Grid Rows 1 to 10 wrapped with Overlay in a single Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onGridPositioned?.invoke(it) }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (r in 0 until gridSize) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Row Number 1 to 10
                        Box(
                            modifier = Modifier.width(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${r + 1}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                        }

                        Row(modifier = Modifier.weight(1f)) {
                            for (c in 0 until gridSize) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(0.8.dp)
                                        .onGloballyPositioned { cellCoords ->
                                            onCellPositioned?.invoke(CellCoordinate(r, c), cellCoords)
                                        }
                                        .clickable { onCellClick(r, c) }
                                        .testTag("cell_${r}_${c}")
                                ) {
                                    cellContent(r, c)
                                }
                            }
                        }
                    }
                }
            }

            overlayContent?.invoke(this)
        }
    }
}

// ==========================================
// Pass-the-Device Privacy Handover Overlay
// ==========================================
@Composable
private fun PrivacyHandoverOverlay(
    targetPlayer: BattleshipPlayer,
    isDeployment: Boolean,
    onReady: () -> Unit
) {
    val isDark = ChillTheme.colors.isDark
    val playerName = if (targetPlayer == BattleshipPlayer.PLAYER_1) "PLAYER 1" else "PLAYER 2"
    val accent = if (targetPlayer == BattleshipPlayer.PLAYER_1) Color(0xFF0284C7) else PlayfulPink

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF050B14) else Color(0xFFF5F7FB))
            .clickable(enabled = false) {}
            .testTag("privacy_handover_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text("🔒", fontSize = 50.sp)

            Text(
                text = "PASS DEVICE TO $playerName",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = if (isDark) Color.White else Color(0xFF172033),
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isDeployment) {
                    "Keep your screen hidden! Hand the phone over so $playerName can deploy their naval fleet in private."
                } else {
                    "Shot missed! Turn passes. Hand the phone over so $playerName can take command without seeing your coordinates."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChunkyButton(
                text = "I'm Ready 🚢",
                backgroundColor = accent,
                onClick = onReady,
                modifier = Modifier.fillMaxWidth(0.8f),
                testTag = "handover_ready_button"
            )
        }
    }
}
