package com.example.ui.screens.detail.battleship

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.core.engine.battleship.ShipOrientation
import com.example.core.engine.battleship.ShipType

/**
 * Authentic vector-based naval warship graphics renderer for Battleship.
 *
 * Renders realistic top-down warship silhouettes, deck structures, gun turrets,
 * flight decks, conning towers, localized damage (fire & hull breaches),
 * and sunken wrecks pixel-perfectly across arbitrary grid dimensions.
 */
object BattleshipShipGraphics {

    val HullSteel = Color(0xFF64748B)
    val HullSteelDark = Color(0xFF475569)
    val HullSteelLight = Color(0xFF94A3B8)
    val DeckPlating = Color(0xFF334155)
    val DeckFlightLine = Color(0xFFF1F5F9)
    val TurretColor = Color(0xFF1E293B)
    val TurretBarrel = Color(0xFF0F172A)
    val SubmarineDark = Color(0xFF1E293B)
    val SubmarineAccent = Color(0xFF38BDF8)
    val DamageFire = Color(0xFFEF4444)
    val DamageEmbers = Color(0xFFF59E0B)
    val DamageSmoke = Color(0xFF18181B)
    val SunkOverlay = Color(0x990A192F)
    val SelectionGlow = Color(0xFF38BDF8)

    /**
     * Draws an entire contiguous ship for previews or overview cards.
     */
    @Composable
    fun FullShipView(
        shipType: ShipType,
        orientation: ShipOrientation = ShipOrientation.HORIZONTAL,
        isSunk: Boolean = false,
        isSelected: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier) {
            val isVert = orientation == ShipOrientation.VERTICAL
            val degrees = if (isVert) 90f else 0f

            rotate(degrees, pivot = center) {
                val shipWidth = if (isVert) size.height else size.width
                val shipHeight = if (isVert) size.width else size.height

                drawFullShip(
                    shipType = shipType,
                    w = shipWidth,
                    h = shipHeight,
                    isSunk = isSunk,
                    isSelected = isSelected
                )
            }
        }
    }

    private fun DrawScope.drawFullShip(
        shipType: ShipType,
        w: Float,
        h: Float,
        isSunk: Boolean,
        isSelected: Boolean
    ) {
        val padY = h * 0.12f
        val hullHeight = h - padY * 2
        val hullTop = padY

        val hullPath = Path().apply {
            // Stern (left)
            moveTo(w * 0.04f, hullTop + hullHeight * 0.2f)
            lineTo(w * 0.02f, hullTop + hullHeight * 0.8f)
            // Bottom edge
            lineTo(w * 0.82f, hullTop + hullHeight)
            // Bow (pointed prow on right)
            lineTo(w * 0.98f, hullTop + hullHeight * 0.5f)
            lineTo(w * 0.82f, hullTop)
            // Top edge back to stern
            lineTo(w * 0.04f, hullTop)
            close()
        }

        // Hull base color
        val baseColor = if (shipType == ShipType.SUBMARINE) SubmarineDark else HullSteelDark
        drawPath(hullPath, baseColor)

        // Hull highlight border
        val borderColor = if (isSelected) SelectionGlow else HullSteelLight
        val strokeW = if (isSelected) 3f else 1.5f
        drawPath(hullPath, borderColor, style = Stroke(strokeW))

        when (shipType) {
            ShipType.CARRIER -> drawCarrierDetails(w, hullTop, hullHeight)
            ShipType.BATTLESHIP -> drawBattleshipDetails(w, hullTop, hullHeight)
            ShipType.CRUISER -> drawCruiserDetails(w, hullTop, hullHeight)
            ShipType.SUBMARINE -> drawSubmarineDetails(w, hullTop, hullHeight)
            ShipType.DESTROYER -> drawDestroyerDetails(w, hullTop, hullHeight)
        }

        if (isSunk) {
            drawRect(SunkOverlay, size = size)
        }
    }

    /**
     * Draws a single grid cell's slice of a placed ship.
     * [segmentIndex]: 0 for stern (rear), (length - 1) for bow (front prow), intermediate for midsection.
     */
    @Composable
    fun ShipCellSegment(
        shipType: ShipType,
        orientation: ShipOrientation,
        segmentIndex: Int,
        isHit: Boolean,
        isSunk: Boolean,
        isSelected: Boolean,
        modifier: Modifier = Modifier
    ) {
        Box(modifier = modifier) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val isVert = orientation == ShipOrientation.VERTICAL
                val degrees = if (isVert) 90f else 0f

                rotate(degrees, pivot = center) {
                    drawSegment(
                        shipType = shipType,
                        segmentIndex = segmentIndex,
                        isSunk = isSunk,
                        isSelected = isSelected
                    )
                }

                if (isHit) {
                    drawLocalizedDamage()
                }

                if (isSunk) {
                    drawRect(SunkOverlay, size = size)
                }
            }
        }
    }

    private fun DrawScope.drawSegment(
        shipType: ShipType,
        segmentIndex: Int,
        isSunk: Boolean,
        isSelected: Boolean
    ) {
        val totalLength = shipType.length
        val isStern = segmentIndex == 0
        val isBow = segmentIndex == totalLength - 1

        val w = size.width
        val h = size.height
        val padY = h * 0.15f
        val hullTop = padY
        val hullHeight = h - padY * 2

        val path = Path()

        if (isStern) {
            // Curved stern rear
            path.moveTo(w * 0.2f, hullTop + hullHeight * 0.15f)
            path.lineTo(w * 0.15f, hullTop + hullHeight * 0.85f)
            path.lineTo(w, hullTop + hullHeight)
            path.lineTo(w, hullTop)
            path.close()
        } else if (isBow) {
            // Tapered bow cutting forward
            path.moveTo(0f, hullTop)
            path.lineTo(w * 0.7f, hullTop + hullHeight * 0.1f)
            path.lineTo(w * 0.95f, hullTop + hullHeight * 0.5f)
            path.lineTo(w * 0.7f, hullTop + hullHeight * 0.9f)
            path.lineTo(0f, hullTop + hullHeight)
            path.close()
        } else {
            // Midsection straight hull
            path.moveTo(0f, hullTop)
            path.lineTo(w, hullTop)
            path.lineTo(w, hullTop + hullHeight)
            path.lineTo(0f, hullTop + hullHeight)
            path.close()
        }

        val baseColor = if (shipType == ShipType.SUBMARINE) SubmarineDark else HullSteelDark
        drawPath(path, baseColor)

        val border = if (isSelected) SelectionGlow else HullSteelLight
        drawPath(path, border, style = Stroke(if (isSelected) 3f else 1.2f))

        // Inner deck & specific ship segment hardware
        when (shipType) {
            ShipType.CARRIER -> drawCarrierSegment(segmentIndex, totalLength, w, hullTop, hullHeight)
            ShipType.BATTLESHIP -> drawBattleshipSegment(segmentIndex, totalLength, w, hullTop, hullHeight)
            ShipType.CRUISER -> drawCruiserSegment(segmentIndex, totalLength, w, hullTop, hullHeight)
            ShipType.SUBMARINE -> drawSubmarineSegment(segmentIndex, totalLength, w, hullTop, hullHeight)
            ShipType.DESTROYER -> drawDestroyerSegment(segmentIndex, totalLength, w, hullTop, hullHeight)
        }
    }

    // ==========================================
    // Full Ship Hardware Renderers
    // ==========================================

    private fun DrawScope.drawCarrierDetails(w: Float, top: Float, h: Float) {
        // Flight deck asphalt
        drawRect(
            color = DeckPlating,
            topLeft = Offset(w * 0.06f, top + h * 0.1f),
            size = Size(w * 0.82f, h * 0.8f)
        )
        // Yellow/white runway line
        drawLine(
            color = DeckFlightLine,
            start = Offset(w * 0.08f, top + h * 0.45f),
            end = Offset(w * 0.82f, top + h * 0.45f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        // Island tower on starboard (bottom edge)
        drawRoundRect(
            color = TurretColor,
            topLeft = Offset(w * 0.48f, top + h * 0.75f),
            size = Size(w * 0.16f, h * 0.22f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }

    private fun DrawScope.drawBattleshipDetails(w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f

        // Forward Main Gun Turret 1 (Triple Barrel)
        drawTurret(Offset(w * 0.72f, centerY), radius = h * 0.22f, angleRad = 0f)
        // Forward Main Gun Turret 2
        drawTurret(Offset(w * 0.56f, centerY), radius = h * 0.20f, angleRad = 0f)

        // Armored Conning Tower / Bridge
        drawRoundRect(
            color = HullSteelLight,
            topLeft = Offset(w * 0.38f, centerY - h * 0.25f),
            size = Size(w * 0.12f, h * 0.5f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Aft Main Gun Turret 3
        drawTurret(Offset(w * 0.22f, centerY), radius = h * 0.20f, angleRad = Math.PI.toFloat())
    }

    private fun DrawScope.drawCruiserDetails(w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        // Forward dual turret
        drawTurret(Offset(w * 0.70f, centerY), radius = h * 0.20f, angleRad = 0f)
        // Bridge
        drawRoundRect(
            color = TurretColor,
            topLeft = Offset(w * 0.42f, centerY - h * 0.22f),
            size = Size(w * 0.15f, h * 0.44f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        // Aft dual turret
        drawTurret(Offset(w * 0.22f, centerY), radius = h * 0.18f, angleRad = Math.PI.toFloat())
    }

    private fun DrawScope.drawSubmarineDetails(w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        // Conning tower (sail)
        drawRoundRect(
            color = SubmarineAccent,
            topLeft = Offset(w * 0.40f, centerY - h * 0.18f),
            size = Size(w * 0.22f, h * 0.36f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Periscope / mast
        drawCircle(Color.White, radius = 2f, center = Offset(w * 0.48f, centerY))
    }

    private fun DrawScope.drawDestroyerDetails(w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        // Forward single naval cannon
        drawTurret(Offset(w * 0.65f, centerY), radius = h * 0.22f, angleRad = 0f)
        // Bridge & depth charge rack
        drawRect(TurretColor, topLeft = Offset(w * 0.25f, centerY - h * 0.2f), size = Size(w * 0.25f, h * 0.4f))
    }

    // ==========================================
    // Segment Cell Hardware Details
    // ==========================================

    private fun DrawScope.drawCarrierSegment(idx: Int, total: Int, w: Float, top: Float, h: Float) {
        drawRect(DeckPlating, topLeft = Offset(0f, top + h * 0.12f), size = Size(w, h * 0.76f))
        // Dashed runway centerline
        drawLine(
            DeckFlightLine,
            start = Offset(0f, top + h * 0.45f),
            end = Offset(w, top + h * 0.45f),
            strokeWidth = 2f
        )
        // Island tower on segment 2
        if (idx == 2) {
            drawRoundRect(
                TurretColor,
                topLeft = Offset(w * 0.2f, top + h * 0.70f),
                size = Size(w * 0.6f, h * 0.28f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }

    private fun DrawScope.drawBattleshipSegment(idx: Int, total: Int, w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        when (idx) {
            0 -> drawTurret(Offset(w * 0.5f, centerY), radius = h * 0.25f, angleRad = Math.PI.toFloat())
            1 -> drawRoundRect(
                HullSteelLight,
                topLeft = Offset(w * 0.15f, centerY - h * 0.3f),
                size = Size(w * 0.7f, h * 0.6f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            2 -> drawTurret(Offset(w * 0.5f, centerY), radius = h * 0.25f, angleRad = 0f)
            3 -> drawTurret(Offset(w * 0.4f, centerY), radius = h * 0.26f, angleRad = 0f)
        }
    }

    private fun DrawScope.drawCruiserSegment(idx: Int, total: Int, w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        when (idx) {
            0 -> drawTurret(Offset(w * 0.5f, centerY), radius = h * 0.22f, angleRad = Math.PI.toFloat())
            1 -> drawRoundRect(
                TurretColor,
                topLeft = Offset(w * 0.2f, centerY - h * 0.25f),
                size = Size(w * 0.6f, h * 0.5f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            2 -> drawTurret(Offset(w * 0.45f, centerY), radius = h * 0.24f, angleRad = 0f)
        }
    }

    private fun DrawScope.drawSubmarineSegment(idx: Int, total: Int, w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        if (idx == 1) {
            drawRoundRect(
                SubmarineAccent,
                topLeft = Offset(w * 0.15f, centerY - h * 0.22f),
                size = Size(w * 0.7f, h * 0.44f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawCircle(Color.White, radius = 2.5f, center = Offset(w * 0.5f, centerY))
        }
    }

    private fun DrawScope.drawDestroyerSegment(idx: Int, total: Int, w: Float, top: Float, h: Float) {
        val centerY = top + h * 0.5f
        if (idx == 0) {
            drawRect(TurretColor, topLeft = Offset(w * 0.2f, centerY - h * 0.25f), size = Size(w * 0.6f, h * 0.5f))
        } else {
            drawTurret(Offset(w * 0.4f, centerY), radius = h * 0.24f, angleRad = 0f)
        }
    }

    private fun DrawScope.drawTurret(pos: Offset, radius: Float, angleRad: Float) {
        // Turret body
        drawCircle(TurretColor, radius = radius, center = pos)
        drawCircle(HullSteelLight, radius = radius, center = pos, style = Stroke(1.2f))

        // Barrels
        val dirX = kotlin.math.cos(angleRad)
        val dirY = kotlin.math.sin(angleRad)
        val barrelLen = radius * 1.5f

        drawLine(
            color = TurretBarrel,
            start = pos,
            end = Offset(pos.x + dirX * barrelLen, pos.y + dirY * barrelLen),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    /**
     * Renders localized battle damage (fire, smoke, breach) on a damaged cell.
     */
    private fun DrawScope.drawLocalizedDamage() {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.5f, h * 0.5f)

        // Charred hull breach hole
        drawCircle(DamageSmoke, radius = w * 0.32f, center = c)

        // Intense inner fire core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DamageEmbers, DamageFire, Color.Transparent),
                center = c,
                radius = w * 0.35f
            ),
            radius = w * 0.35f,
            center = c
        )

        // Fire sparks
        drawCircle(DamageEmbers, radius = 3.5f, center = Offset(w * 0.35f, h * 0.35f))
        drawCircle(DamageFire, radius = 3.0f, center = Offset(w * 0.65f, h * 0.40f))
        drawCircle(DamageEmbers, radius = 2.5f, center = Offset(w * 0.50f, h * 0.70f))
    }
}
