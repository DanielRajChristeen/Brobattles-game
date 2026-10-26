package com.example.ui.screens.detail.snakesandladders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.ChillTheme
import com.example.ui.theme.PlayfulGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D-to-2D Physical Board-Game Die.
 *
 * Full Animation Lifecycle:
 * 1. IDLE: Clean, front-facing 2D die with pips, soft table shadow, and rounded beveled edge.
 * 2. 3D ROLL: Transforms into a true 3D perspective cube tumbling across X, Y, and Z axes.
 *    Multiple faces (Front, Top, Side) are visible with directional shading and attached rotating pips.
 * 3. 3D LANDING & BOUNCE: Descends to the table surface and executes a physical contact bounce.
 * 4. 3D -> 2D TRANSITION: Smoothly eases pitch, yaw, and perspective straight-on toward the camera (150-200ms).
 * 5. FINAL 2D RESULT: Settles into a clean, flat 2D face displaying the predetermined roll result.
 * 6. READY FOR NEXT TURN: Stays in clean 2D until tapped again.
 */
@Composable
fun Real3DCubeDie(
    targetFace: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dieSize: androidx.compose.ui.unit.Dp = 100.dp,
    accentColor: Color? = null,
    testTag: String = "snakes_dice_control"
) {
    val rollAnim = remember { Animatable(0f) }

    LaunchedEffect(isRolling) {
        if (isRolling) {
            rollAnim.snapTo(0f)
            rollAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 850,
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
                )
            )
        }
    }

    val density = LocalDensity.current.density
    val colors = ChillTheme.colors
    val isDark = colors.isDark

    val animProgress = if (isRolling) rollAnim.value else 1f

    Box(
        modifier = modifier
            .size(dieSize)
            .bouncyClickable(enabled = canRoll, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(dieSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val sDensity = size.width / 100f

            // Animation Timeline Breakdown:
            // 0.00 - 0.10: Morph 2D -> 3D and lift into air
            // 0.10 - 0.70: 3D multi-axis tumbling in perspective
            // 0.70 - 0.84: Descend, land on table, and bounce
            // 0.84 - 1.00: 3D -> 2D transition: rotate straight-on into flat front face
            // 1.00 / !isRolling: Flat 2D resting result

            val t = animProgress
            val isResting2D = !isRolling || t >= 0.999f

            // Elevation and Table Shadow
            val elevationPx: Float
            val shadowScale: Float
            val shadowAlpha: Float

            if (!isRolling || isResting2D) {
                elevationPx = 0f
                shadowScale = 1.0f
                shadowAlpha = if (isDark) 0.38f else 0.22f
            } else if (t < 0.70f) {
                // Lifted high in the air
                val liftNorm = sin((t / 0.70f) * PI.toFloat()).coerceAtLeast(0f)
                elevationPx = (liftNorm * 34f) * sDensity
                shadowScale = (1f - liftNorm * 0.35f).coerceIn(0.55f, 1f)
                shadowAlpha = ((if (isDark) 0.35f else 0.20f) - liftNorm * 0.14f).coerceIn(0.06f, 0.35f)
            } else if (t < 0.84f) {
                // Contact bounce
                val bounceFrac = ((t - 0.70f) / 0.14f).coerceIn(0f, 1f)
                val bounceHeight = sin(bounceFrac * PI.toFloat()) * (10f * sDensity)
                elevationPx = bounceHeight
                shadowScale = (1f - (bounceHeight / (10f * sDensity)) * 0.18f).coerceIn(0.8f, 1f)
                shadowAlpha = if (isDark) 0.36f else 0.20f
            } else {
                // Transition to 2D: resting flush on table
                elevationPx = 0f
                shadowScale = 1.0f
                shadowAlpha = if (isDark) 0.38f else 0.22f
            }

            // Draw soft table contact shadow
            drawOval(
                color = Color.Black.copy(alpha = shadowAlpha),
                topLeft = Offset(cx - 36f * sDensity * shadowScale, cy + 24f * sDensity),
                size = Size(72f * sDensity * shadowScale, 20f * sDensity * shadowScale)
            )

            if (isResting2D) {
                // ==========================================
                // CLEAN FLAT 2D RESTING STATE
                // ==========================================
                val dieSize = 64f * sDensity
                val dieCorner = 18f * sDensity
                val dieLeft = cx - dieSize / 2f
                val dieTop = cy - dieSize / 2f

                val diePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = dieLeft,
                            top = dieTop,
                            right = dieLeft + dieSize,
                            bottom = dieTop + dieSize,
                            cornerRadius = CornerRadius(dieCorner, dieCorner)
                        )
                    )
                }

                // 1. Die Body (Clean Crisp Ivory / White)
                drawPath(path = diePath, color = Color(0xFFFFFFFF))

                // 2. Tactile Bevel Border (Theme Aware, or Accent Color if canRoll is true)
                val borderColor = if (canRoll) (accentColor ?: PlayfulGreen) else colors.border
                val borderWidth = if (canRoll) 2.4f * sDensity else 1.6f * sDensity
                drawPath(
                    path = diePath,
                    color = borderColor,
                    style = Stroke(width = borderWidth)
                )

                // 3. Crisp Flat 2D Pips
                val pips = getPipLocalCoords(targetFace)
                val pipRadius = 4.2f * sDensity
                val pipScale = dieSize * 0.36f

                for (pip in pips) {
                    val px = cx + pip.x * pipScale
                    val py = cy + pip.y * pipScale

                    // Recessed Pip Base
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = pipRadius,
                        center = Offset(px, py)
                    )

                    // Specular highlight on pip
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = pipRadius * 0.32f,
                        center = Offset(px - pipRadius * 0.28f, py - pipRadius * 0.28f)
                    )
                }
            } else {
                // ==========================================
                // 3D PERSPECTIVE CUBE & 3D->2D TRANSITION
                // ==========================================
                val halfSize = 25f * sDensity
                val vBase = arrayOf(
                    floatArrayOf(-halfSize, -halfSize, -halfSize), // 0
                    floatArrayOf( halfSize, -halfSize, -halfSize), // 1
                    floatArrayOf( halfSize,  halfSize, -halfSize), // 2
                    floatArrayOf(-halfSize,  halfSize, -halfSize), // 3
                    floatArrayOf(-halfSize, -halfSize,  halfSize), // 4
                    floatArrayOf( halfSize, -halfSize,  halfSize), // 5
                    floatArrayOf( halfSize,  halfSize,  halfSize), // 6
                    floatArrayOf(-halfSize,  halfSize,  halfSize)  // 7
                )

                // 3D Euler Angles across phases
                val landingPitch = 18f * (PI.toFloat() / 180f)
                val landingYaw = -20f * (PI.toFloat() / 180f)

                val rotX: Float
                val rotY: Float
                val rotZ: Float
                val camDist: Float

                if (t < 0.10f) {
                    // Phase 1: Morph into 3D
                    val morph = (t / 0.10f).coerceIn(0f, 1f)
                    rotX = landingPitch * morph
                    rotY = landingYaw * morph
                    rotZ = 0f
                    camDist = 340f * sDensity
                } else if (t < 0.70f) {
                    // Phase 2: Full 3D Tumble
                    val spinT = (1f - (t - 0.10f) / 0.60f)
                    rotX = landingPitch + (spinT * 4f * PI.toFloat())
                    rotY = landingYaw - (spinT * 6f * PI.toFloat())
                    rotZ = sin(((t - 0.10f) / 0.60f) * PI.toFloat()) * 0.7f
                    camDist = 340f * sDensity
                } else if (t < 0.84f) {
                    // Phase 3: Land & Bounce
                    rotX = landingPitch
                    rotY = landingYaw
                    rotZ = 0f
                    camDist = 340f * sDensity
                } else {
                    // Phase 4: 3D -> 2D Smooth Transition
                    val transFrac = ((t - 0.84f) / 0.16f).coerceIn(0f, 1f)
                    // Smooth ease out
                    val ease = 1f - (1f - transFrac) * (1f - transFrac)
                    rotX = landingPitch * (1f - ease)
                    rotY = landingYaw * (1f - ease)
                    rotZ = 0f
                    // Smoothly transition perspective distance to flat
                    camDist = (340f + ease * 1200f) * sDensity
                }

                fun rotate3D(p: FloatArray): FloatArray {
                    val x = p[0]; val y = p[1]; val z = p[2]

                    // Rotate around X
                    val cosX = cos(rotX); val sinX = sin(rotX)
                    val y1 = y * cosX - z * sinX
                    val z1 = y * sinX + z * cosX
                    val x1 = x

                    // Rotate around Y
                    val cosY = cos(rotY); val sinY = sin(rotY)
                    val x2 = x1 * cosY + z1 * sinY
                    val y2 = y1
                    val z2 = -x1 * sinY + z1 * cosY

                    // Rotate around Z
                    val cosZ = cos(rotZ); val sinZ = sin(rotZ)
                    val x3 = x2 * cosZ - y2 * sinZ
                    val y3 = x2 * sinZ + y2 * cosZ
                    val z3 = z2

                    return floatArrayOf(x3, y3, z3)
                }

                val vTrans = Array(8) { rotate3D(vBase[it]) }

                fun project(p: FloatArray): Offset {
                    val scale = camDist / (camDist + p[2])
                    val px = cx + p[0] * scale
                    val py = (cy - elevationPx) + p[1] * scale
                    return Offset(px, py)
                }

                val vProj = Array(8) { project(vTrans[it]) }

                // Deterministic face mapping with targetFace on the Front (+Z) face
                val faceNumbers = getFaceValuesForTarget(targetFace)

                data class Face(
                    val indices: IntArray,
                    val faceValue: Int,
                    val normalBase: FloatArray
                )

                val faces = listOf(
                    Face(intArrayOf(4, 5, 6, 7), faceNumbers[0], floatArrayOf(0f, 0f, 1f)),   // Front (+Z) = targetFace
                    Face(intArrayOf(1, 0, 3, 2), faceNumbers[1], floatArrayOf(0f, 0f, -1f)),  // Back (-Z)
                    Face(intArrayOf(0, 1, 5, 4), faceNumbers[2], floatArrayOf(0f, -1f, 0f)),  // Top (-Y)
                    Face(intArrayOf(7, 6, 2, 3), faceNumbers[3], floatArrayOf(0f, 1f, 0f)),   // Bottom (+Y)
                    Face(intArrayOf(5, 1, 2, 6), faceNumbers[4], floatArrayOf(1f, 0f, 0f)),   // Right (+X)
                    Face(intArrayOf(0, 4, 7, 3), faceNumbers[5], floatArrayOf(-1f, 0f, 0f))   // Left (-X)
                )

                // Directional Light Vector
                val lx = -0.32f; val ly = -0.78f; val lz = 0.54f
                val lLen = sqrt(lx * lx + ly * ly + lz * lz)
                val nlx = lx / lLen; val nly = ly / lLen; val nlz = lz / lLen

                // Painter's algorithm
                val sortedFaces = faces.map { face ->
                    val avgZ = face.indices.sumOf { vTrans[it][2].toDouble() } / 4.0
                    val rotNormal = rotate3D(face.normalBase)
                    face to (avgZ to rotNormal)
                }.sortedBy { it.second.first }

                for ((face, depthAndNormal) in sortedFaces) {
                    val rotNormal = depthAndNormal.second
                    if (rotNormal[2] <= 0f) continue // Backface culling

                    val dot = rotNormal[0] * nlx + rotNormal[1] * nly + rotNormal[2] * nlz
                    val brightness = (0.58f + 0.42f * dot.coerceAtLeast(0f)).coerceIn(0.48f, 1.0f)

                    val faceColor = Color(
                        red = (0.99f * brightness).coerceIn(0f, 1f),
                        green = (0.98f * brightness).coerceIn(0f, 1f),
                        blue = (0.94f * brightness).coerceIn(0f, 1f)
                    )

                    val edgeColor = Color(
                        red = (0.75f * brightness).coerceIn(0f, 1f),
                        green = (0.73f * brightness).coerceIn(0f, 1f),
                        blue = (0.70f * brightness).coerceIn(0f, 1f)
                    )

                    val p0 = vProj[face.indices[0]]
                    val p1 = vProj[face.indices[1]]
                    val p2 = vProj[face.indices[2]]
                    val p3 = vProj[face.indices[3]]

                    val facePath = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }

                    drawPath(path = facePath, color = faceColor)
                    drawPath(
                        path = facePath,
                        color = edgeColor,
                        style = Stroke(width = 1.5f * sDensity, cap = StrokeCap.Round)
                    )

                    // Render attached pips on 3D face
                    val pipCoords = getPipLocalCoords(face.faceValue)
                    val rawV0 = vBase[face.indices[0]]
                    val rawV1 = vBase[face.indices[1]]
                    val rawV2 = vBase[face.indices[2]]
                    val rawV3 = vBase[face.indices[3]]

                    for (pip in pipCoords) {
                        val u = (pip.x + 1f) / 2f
                        val v = (pip.y + 1f) / 2f

                        val px = (1f - u) * (1f - v) * rawV0[0] + u * (1f - v) * rawV1[0] + u * v * rawV2[0] + (1f - u) * v * rawV3[0]
                        val py = (1f - u) * (1f - v) * rawV0[1] + u * (1f - v) * rawV1[1] + u * v * rawV2[1] + (1f - u) * v * rawV3[1]
                        val pz = (1f - u) * (1f - v) * rawV0[2] + u * (1f - v) * rawV1[2] + u * v * rawV2[2] + (1f - u) * v * rawV3[2]

                        val pip3D = rotate3D(floatArrayOf(
                            px + face.normalBase[0] * 0.4f * sDensity,
                            py + face.normalBase[1] * 0.4f * sDensity,
                            pz + face.normalBase[2] * 0.4f * sDensity
                        ))

                        val pipProj = project(pip3D)
                        val pipScale = camDist / (camDist + pip3D[2])
                        val pipRadius = 3.8f * sDensity * pipScale

                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = pipRadius,
                            center = pipProj
                        )

                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f),
                            radius = pipRadius * 0.35f,
                            center = Offset(pipProj.x - pipRadius * 0.28f, pipProj.y - pipRadius * 0.28f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns face values such that the front (+Z) face is strictly [targetRoll]
 * and opposing faces sum to 7.
 */
private fun getFaceValuesForTarget(targetRoll: Int): IntArray {
    return when (targetRoll) {
        1 -> intArrayOf(1, 6, 2, 5, 3, 4) // Front=1, Back=6, Top=2, Bottom=5, Right=3, Left=4
        2 -> intArrayOf(2, 5, 6, 1, 3, 4) // Front=2, Back=5, Top=6, Bottom=1, Right=3, Left=4
        3 -> intArrayOf(3, 4, 2, 5, 6, 1) // Front=3, Back=4, Top=2, Bottom=5, Right=6, Left=1
        4 -> intArrayOf(4, 3, 2, 5, 1, 6) // Front=4, Back=3, Top=2, Bottom=5, Right=1, Left=6
        5 -> intArrayOf(5, 2, 1, 6, 3, 4) // Front=5, Back=2, Top=1, Bottom=6, Right=3, Left=4
        else -> intArrayOf(6, 1, 5, 2, 3, 4) // Front=6, Back=1, Top=5, Bottom=2, Right=3, Left=4
    }
}

/**
 * Normalized 2D coordinates [-1, 1] for dice pips on a face.
 */
private fun getPipLocalCoords(faceNumber: Int): List<Offset> {
    return when (faceNumber) {
        1 -> listOf(Offset(0f, 0f))
        2 -> listOf(Offset(-0.52f, -0.52f), Offset(0.52f, 0.52f))
        3 -> listOf(Offset(-0.55f, -0.55f), Offset(0f, 0f), Offset(0.55f, 0.55f))
        4 -> listOf(
            Offset(-0.52f, -0.52f), Offset(0.52f, -0.52f),
            Offset(-0.52f, 0.52f), Offset(0.52f, 0.52f)
        )
        5 -> listOf(
            Offset(-0.54f, -0.54f), Offset(0.54f, -0.54f),
            Offset(0f, 0f),
            Offset(-0.54f, 0.54f), Offset(0.54f, 0.54f)
        )
        else -> listOf( // 6
            Offset(-0.52f, -0.60f), Offset(0.52f, -0.60f),
            Offset(-0.52f, 0f), Offset(0.52f, 0f),
            Offset(-0.52f, 0.60f), Offset(0.52f, 0.60f)
        )
    }
}
