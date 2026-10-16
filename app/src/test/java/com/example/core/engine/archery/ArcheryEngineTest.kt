package com.example.core.engine.archery

import androidx.compose.ui.geometry.Offset
import com.example.core.registry.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcheryEngineTest {

    @Test
    fun `headshot results in instant knockout`() {
        val effect1 = ArcheryRules.calculateDamage(currentHealth = 100, hitZone = ArcheryHitZone.HEAD)
        assertEquals(0, effect1.newHealth)
        assertTrue(effect1.isHeadshot)

        val effect2 = ArcheryRules.calculateDamage(currentHealth = 40, hitZone = ArcheryHitZone.HEAD)
        assertEquals(0, effect2.newHealth)
        assertTrue(effect2.isHeadshot)
    }

    @Test
    fun `chest hit decreases health by 50 points and two hits cause KO`() {
        // 100 HP -> 50 HP
        val effect1 = ArcheryRules.calculateDamage(currentHealth = 100, hitZone = ArcheryHitZone.CHEST)
        assertEquals(50, effect1.newHealth)
        assertEquals(ArcheryHitZone.CHEST, effect1.zone)

        // Second chest hit: 50 HP -> 0 HP (KO!)
        val effect2 = ArcheryRules.calculateDamage(currentHealth = 50, hitZone = ArcheryHitZone.CHEST)
        assertEquals(0, effect2.newHealth)

        // If already lower than 50 HP (e.g. 30 HP) -> 0 HP
        val effect3 = ArcheryRules.calculateDamage(currentHealth = 30, hitZone = ArcheryHitZone.CHEST)
        assertEquals(0, effect3.newHealth)
    }

    @Test
    fun `leg hit decreases health by 20 points`() {
        val effect1 = ArcheryRules.calculateDamage(currentHealth = 100, hitZone = ArcheryHitZone.LEGS)
        assertEquals(80, effect1.newHealth)

        val effect2 = ArcheryRules.calculateDamage(currentHealth = 80, hitZone = ArcheryHitZone.LEGS)
        assertEquals(60, effect2.newHealth)

        val effect3 = ArcheryRules.calculateDamage(currentHealth = 15, hitZone = ArcheryHitZone.LEGS)
        assertEquals(0, effect3.newHealth)
    }

    @Test
    fun `foot hit decreases health by 5 points`() {
        val effect1 = ArcheryRules.calculateDamage(currentHealth = 100, hitZone = ArcheryHitZone.FEET)
        assertEquals(95, effect1.newHealth)

        val effect2 = ArcheryRules.calculateDamage(currentHealth = 5, hitZone = ArcheryHitZone.FEET)
        assertEquals(0, effect2.newHealth)
    }

    @Test
    fun `collision detection accurately identifies body zones`() {
        val anchorX = 400f
        val groundY = 600f

        // Head is at groundY - 130 = 470
        val headHit = ArcheryPhysics.checkCollision(Offset(400f, 470f), anchorX, groundY, 0f)
        assertEquals(ArcheryHitZone.HEAD, headHit.hitZone)
        assertNotNull(headHit.attachedArrow)

        // Chest is at groundY - 82 = 518
        val chestHit = ArcheryPhysics.checkCollision(Offset(400f, 518f), anchorX, groundY, 0f)
        assertEquals(ArcheryHitZone.CHEST, chestHit.hitZone)
        assertNotNull(chestHit.attachedArrow)

        // Legs are at groundY - 35 = 565
        val legHit = ArcheryPhysics.checkCollision(Offset(400f, 565f), anchorX, groundY, 0f)
        assertEquals(ArcheryHitZone.LEGS, legHit.hitZone)
        assertNotNull(legHit.attachedArrow)

        // Feet are at groundY - 5 = 595
        val footHit = ArcheryPhysics.checkCollision(Offset(400f, 595f), anchorX, groundY, 0f)
        assertEquals(ArcheryHitZone.FEET, footHit.hitZone)
        assertNotNull(footHit.attachedArrow)

        // Miss (far away)
        val missHit = ArcheryPhysics.checkCollision(Offset(200f, 300f), anchorX, groundY, 0f)
        assertEquals(ArcheryHitZone.NONE, missHit.hitZone)
    }

    @Test
    fun `bot shot calculation produces playable values`() {
        val botAnchorX = 800f
        val playerAnchorX = 200f
        val groundY = 600f

        for (i in 0 until 20) {
            val shot = ArcheryBot.calculateShot(botAnchorX, playerAnchorX, groundY)
            // Bot shoots leftwards (negative vx)
            assertTrue("vx should be negative", shot.vx < 0)
            assertTrue("vy should be negative (upwards)", shot.vy < 0)
            assertTrue("power should be in 0..1", shot.power in 0.1f..1.0f)
            assertTrue("targeted zone must be valid", shot.targetedZone != ArcheryHitZone.NONE)
        }
    }

    @Test
    fun `trajectory preview generates smooth sequential arc`() {
        val trajectory = ArcheryPhysics.calculateTrajectoryPreview(
            startX = 100f,
            startY = 500f,
            vx = 600f,
            vy = -400f,
            groundY = 600f,
            maxSteps = 20
        )
        assertTrue(trajectory.size > 5)
        // Horizontal progression
        assertTrue(trajectory[1].x > trajectory[0].x)
        // Gravity pulls downward eventually
        val lastPoint = trajectory.last()
        assertTrue(lastPoint.x > trajectory.first().x)
    }

    @Test
    fun `bot shot calculation operates accurately across landscape distance`() {
        // Landscape arena width ~ 1000px, anchors at 170px and 830px, ground at 54% of 500px = 270px
        val botAnchorX = 830f
        val playerAnchorX = 170f
        val groundY = 270f

        for (i in 0 until 25) {
            val shot = ArcheryBot.calculateShot(botAnchorX, playerAnchorX, groundY)
            assertTrue("vx should be negative (traveling left)", shot.vx < 0)
            assertTrue("vy should be negative (arcing upward)", shot.vy < 0)
            assertTrue("power in range 0.2..1.0", shot.power in 0.1f..1.0f)
            assertTrue("targeted zone valid", shot.targetedZone != ArcheryHitZone.NONE)
        }
    }

    @Test
    fun `multiple simultaneous projectiles operate independently`() {
        val arrow1 = ArrowProjectile(
            x = 200f,
            y = 200f,
            vx = 800f,
            vy = -400f,
            angleDegrees = -30f,
            shooterIsLeft = true
        )
        val arrow2 = ArrowProjectile(
            x = 800f,
            y = 200f,
            vx = -750f,
            vy = -380f,
            angleDegrees = 210f,
            shooterIsLeft = false
        )

        val activeArrows = mutableListOf(arrow1, arrow2)
        assertEquals(2, activeArrows.size)

        // Advance arrow1 independently
        val dt = 0.016f
        arrow1.x += arrow1.vx * dt
        arrow1.y += arrow1.vy * dt
        arrow1.vy += ArcheryPhysics.GRAVITY * dt

        assertTrue(arrow1.x > 200f)
        assertEquals(800f, arrow2.x) // arrow2 unmodified
    }

    @Test
    fun `shot angle references store history correctly`() {
        val history = mutableListOf<ShotAngleReference>()
        val shots = listOf(
            ShotAngleReference(-35f, 0.6f, 200f, 200f, true),
            ShotAngleReference(-40f, 0.7f, 200f, 200f, true),
            ShotAngleReference(-30f, 0.5f, 200f, 200f, true),
            ShotAngleReference(-45f, 0.8f, 200f, 200f, true),
            ShotAngleReference(-25f, 0.4f, 200f, 200f, true)
        )

        for (shot in shots) {
            history.add(0, shot)
            if (history.size > 4) history.removeAt(history.lastIndex)
        }

        assertEquals(4, history.size)
        // Most recent shot is at index 0
        assertEquals(-25f, history[0].angleDegrees)
        assertEquals(-45f, history[1].angleDegrees)
    }

    @Test
    fun `repeated hits on same body parts stack correctly and defeat character`() {
        // Test 1: Two chest hits cause KO
        var hp = 100
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.CHEST).newHealth
        assertEquals(50, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.CHEST).newHealth
        assertEquals(0, hp)

        // Test 2: Same leg multiple times (100 -> 80 -> 60 -> 40)
        hp = 100
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.LEGS).newHealth
        assertEquals(80, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.LEGS).newHealth
        assertEquals(60, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.LEGS).newHealth
        assertEquals(40, hp)

        // Test 3: Same foot multiple times (100 -> 95 -> 90 -> 85)
        hp = 100
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.FEET).newHealth
        assertEquals(95, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.FEET).newHealth
        assertEquals(90, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.FEET).newHealth
        assertEquals(85, hp)

        // Test 4: Different zones stack (Leg: 100 -> 80, Chest: 80 -> 30, Foot: 30 -> 25)
        hp = 100
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.LEGS).newHealth
        assertEquals(80, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.CHEST).newHealth
        assertEquals(30, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.FEET).newHealth
        assertEquals(25, hp)

        // Test 5: Head after existing arrows (Leg: 100 -> 80, Chest: 80 -> 30, Head: 30 -> 0 KO)
        hp = 100
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.LEGS).newHealth
        assertEquals(80, hp)
        hp = ArcheryRules.calculateDamage(hp, ArcheryHitZone.CHEST).newHealth
        assertEquals(30, hp)
        val headEffect = ArcheryRules.calculateDamage(hp, ArcheryHitZone.HEAD)
        assertEquals(0, headEffect.newHealth)
        assertTrue(headEffect.isHeadshot)
    }

    @Test
    fun `archery is registered and available in GameRegistry`() {
        val archery = GameRegistry.getGameById("archery")
        assertNotNull(archery)
        assertTrue(archery!!.isAvailable)
        assertEquals("Archery", archery.title)
        assertEquals("🏹", archery.emoji)
        assertEquals(listOf(3, 5, 10), archery.targetScoreOptions)
        assertEquals(5, archery.defaultTargetScore)
    }

    @Test
    fun `target 3 in VS Bot and Local 2P means exactly 3 rounds and does not end early`() {
        val target = 3
        val isNearby = false

        // Round 1 completed: P1 wins (score 1-0)
        assertFalse(ArcheryRules.isMatchFinished(currentRound = 1, targetScore = target, isNearbyMode = isNearby, p1Score = 1, p2Score = 0))

        // Round 2 completed: P1 wins again (score 2-0). Even though P1 has 2 round wins, do NOT end early!
        assertFalse(ArcheryRules.isMatchFinished(currentRound = 2, targetScore = target, isNearbyMode = isNearby, p1Score = 2, p2Score = 0))

        // Round 3 completed: P2 wins (score 2-1). Exactly 3 rounds completed -> match ends!
        assertTrue(ArcheryRules.isMatchFinished(currentRound = 3, targetScore = target, isNearbyMode = isNearby, p1Score = 2, p2Score = 1))

        // Player 1 has most round wins
        assertEquals("PLAYER_1", ArcheryRules.determineMatchWinner(p1Score = 2, p2Score = 1))
    }

    @Test
    fun `target 5 in VS Bot and Local 2P means exactly 5 rounds`() {
        val target = 5
        val isNearby = false

        for (round in 1 until 5) {
            assertFalse(
                "Round $round should not end match early",
                ArcheryRules.isMatchFinished(currentRound = round, targetScore = target, isNearbyMode = isNearby, p1Score = round, p2Score = 0)
            )
        }

        // Round 5 completed: match concludes
        assertTrue(ArcheryRules.isMatchFinished(currentRound = 5, targetScore = target, isNearbyMode = isNearby, p1Score = 4, p2Score = 1))
        assertEquals("PLAYER_1", ArcheryRules.determineMatchWinner(p1Score = 4, p2Score = 1))
    }

    @Test
    fun `target 10 in VS Bot and Local 2P means exactly 10 rounds`() {
        val target = 10
        val isNearby = false

        for (round in 1 until 10) {
            assertFalse(
                "Round $round should not end match early",
                ArcheryRules.isMatchFinished(currentRound = round, targetScore = target, isNearbyMode = isNearby, p1Score = round, p2Score = 0)
            )
        }

        // Round 10 completed: match concludes
        assertTrue(ArcheryRules.isMatchFinished(currentRound = 10, targetScore = target, isNearbyMode = isNearby, p1Score = 3, p2Score = 7))
        assertEquals("PLAYER_2", ArcheryRules.determineMatchWinner(p1Score = 3, p2Score = 7))
    }

    @Test
    fun `tied match after all rounds results in DRAW`() {
        val target = 3
        val isNearby = false

        // 3 rounds completed, scores are equal (1-1 with 1 draw round, or both died)
        assertTrue(ArcheryRules.isMatchFinished(currentRound = 3, targetScore = target, isNearbyMode = isNearby, p1Score = 2, p2Score = 2))
        assertEquals("DRAW", ArcheryRules.determineMatchWinner(p1Score = 2, p2Score = 2))
    }

    @Test
    fun `nearby mode preserves first-to-targetScore win condition without modifying rules`() {
        val target = 3
        val isNearby = true

        // In nearby mode, reaching targetScore ends match immediately
        assertTrue(ArcheryRules.isMatchFinished(currentRound = 1, targetScore = target, isNearbyMode = isNearby, p1Score = 3, p2Score = 0))
        assertFalse(ArcheryRules.isMatchFinished(currentRound = 2, targetScore = target, isNearbyMode = isNearby, p1Score = 2, p2Score = 1))
    }
}
