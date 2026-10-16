package com.example.core.engine.archery

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2

object ArcheryPhysics {
    const val GRAVITY = 950f // pixels/sec^2

    data class CollisionResult(
        val hitZone: ArcheryHitZone,
        val hitPoint: Offset,
        val attachedArrow: AttachedArrow?
    )

    /**
     * Checks if a point (arrow tip) collides with any body zone of the target stick figure.
     * @param point Arrow tip location
     * @param targetAnchorX Target character's X position
     * @param groundY Arena ground Y coordinate
     * @param arrowAngle Current angle of arrow in degrees
     */
    fun checkCollision(
        point: Offset,
        targetAnchorX: Float,
        groundY: Float,
        arrowAngle: Float
    ): CollisionResult {
        val dx = point.x - targetAnchorX
        val absDx = kotlin.math.abs(dx)

        val headCenterY = groundY - 130f
        val headRadius = 20f
        val chestTop = groundY - 110f
        val chestBottom = groundY - 55f
        val legTop = groundY - 55f
        val legBottom = groundY - 14f
        val footTop = groundY - 14f
        val footBottom = groundY + 4f

        // 1. Headshot check (circular / tight capsule)
        val dyHead = point.y - headCenterY
        if (dyHead * dyHead + dx * dx <= (headRadius + 4f) * (headRadius + 4f)) {
            return CollisionResult(
                hitZone = ArcheryHitZone.HEAD,
                hitPoint = point,
                attachedArrow = AttachedArrow(
                    zone = ArcheryHitZone.HEAD,
                    localOffsetX = dx,
                    localOffsetY = dyHead,
                    angleDegrees = arrowAngle
                )
            )
        }

        // 2. Chest / Torso check
        if (point.y in chestTop..chestBottom && absDx <= 26f) {
            return CollisionResult(
                hitZone = ArcheryHitZone.CHEST,
                hitPoint = point,
                attachedArrow = AttachedArrow(
                    zone = ArcheryHitZone.CHEST,
                    localOffsetX = dx,
                    localOffsetY = point.y - (chestTop + chestBottom) / 2f,
                    angleDegrees = arrowAngle
                )
            )
        }

        // 3. Legs check
        if (point.y in legTop..legBottom && absDx <= 30f) {
            return CollisionResult(
                hitZone = ArcheryHitZone.LEGS,
                hitPoint = point,
                attachedArrow = AttachedArrow(
                    zone = ArcheryHitZone.LEGS,
                    localOffsetX = dx,
                    localOffsetY = point.y - (legTop + legBottom) / 2f,
                    angleDegrees = arrowAngle
                )
            )
        }

        // 4. Feet check
        if (point.y in footTop..footBottom && absDx <= 34f) {
            return CollisionResult(
                hitZone = ArcheryHitZone.FEET,
                hitPoint = point,
                attachedArrow = AttachedArrow(
                    zone = ArcheryHitZone.FEET,
                    localOffsetX = dx,
                    localOffsetY = point.y - groundY,
                    angleDegrees = arrowAngle
                )
            )
        }

        return CollisionResult(ArcheryHitZone.NONE, point, null)
    }

    /**
     * Generates a smooth preview trajectory for aiming.
     */
    fun calculateTrajectoryPreview(
        startX: Float,
        startY: Float,
        vx: Float,
        vy: Float,
        groundY: Float = Float.MAX_VALUE,
        maxSteps: Int = 22
    ): List<Offset> {
        val points = mutableListOf<Offset>()
        var curX = startX
        var curY = startY
        var curVy = vy
        val dt = 0.045f

        for (i in 0 until maxSteps) {
            points.add(Offset(curX, curY))
            curX += vx * dt
            curY += curVy * dt
            curVy += GRAVITY * dt
            if (curY >= groundY + 10f) break
        }
        return points
    }
}
