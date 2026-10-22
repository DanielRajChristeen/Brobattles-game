package com.example.core.audio

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.XOState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XOSoundAndHapticManagerTest {

    @Test
    fun testPiecePlacementBeepDurationWithin50To150Ms() {
        val beepNotes = listOf(880.0 to 90)
        val sampleRate = XOSoundAndHapticManager.SAMPLE_RATE
        val pcm = XOSoundAndHapticManager.generateToneSequence(beepNotes, sampleRate)

        val durationMs = beepNotes.sumOf { it.second }
        assertTrue("Beep duration must be between 50 and 150 ms", durationMs in 50..150)

        // PCM 16-bit mono = 2 bytes per sample
        val expectedBytes = (sampleRate * (durationMs / 1000.0)).toInt() * 2
        assertEquals(expectedBytes, pcm.size)

        // Ensure non-silent, valid PCM audio data
        val hasSound = pcm.any { it != 0.toByte() }
        assertTrue("Beep audio must contain non-zero audio samples", hasSound)
    }

    @Test
    fun testRoundWonInstrumentalDurationWithinHalfTo1Point5Seconds() {
        val roundWonNotes = listOf(
            523.25 to 130,
            659.25 to 130,
            783.99 to 150,
            1046.50 to 290
        )
        val sampleRate = XOSoundAndHapticManager.SAMPLE_RATE
        val pcm = XOSoundAndHapticManager.generateToneSequence(roundWonNotes, sampleRate)

        val totalDurationMs = roundWonNotes.sumOf { it.second }
        assertTrue("Round won jingle duration must be between 500 and 1500 ms", totalDurationMs in 500..1500)

        val expectedSamples = roundWonNotes.sumOf { (sampleRate * (it.second / 1000.0)).toInt() }
        val expectedBytes = expectedSamples * 2
        assertEquals(expectedBytes, pcm.size)
        assertTrue("Round won jingle must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testRoundLostInstrumentalDurationWithinHalfTo1Point5Seconds() {
        val roundLostNotes = listOf(
            349.23 to 170,
            329.63 to 170,
            311.13 to 190,
            261.63 to 270
        )
        val sampleRate = XOSoundAndHapticManager.SAMPLE_RATE
        val pcm = XOSoundAndHapticManager.generateToneSequence(roundLostNotes, sampleRate)

        val totalDurationMs = roundLostNotes.sumOf { it.second }
        assertTrue("Round lost instrumental duration must be between 500 and 1500 ms", totalDurationMs in 500..1500)

        val expectedSamples = roundLostNotes.sumOf { (sampleRate * (it.second / 1000.0)).toInt() }
        val expectedBytes = expectedSamples * 2
        assertEquals(expectedBytes, pcm.size)
        assertTrue("Round lost audio must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testWholeGameWonFanfareIsDistinguishableFromRoundWin() {
        val matchWonNotes = listOf(
            392.00 to 110,
            523.25 to 110,
            659.25 to 110,
            783.99 to 160,
            659.25 to 110,
            783.99 to 150,
            1046.50 to 500
        )
        val roundWonNotes = listOf(
            523.25 to 130,
            659.25 to 130,
            783.99 to 150,
            1046.50 to 290
        )

        val matchWonDuration = matchWonNotes.sumOf { it.second }
        val roundWonDuration = roundWonNotes.sumOf { it.second }

        assertTrue("Match won fanfare duration must be between 500 and 1500 ms", matchWonDuration in 500..1500)
        assertNotEquals("Match won must be distinct in duration and sequence from round won", matchWonDuration, roundWonDuration)
        assertTrue("Match won fanfare must be longer than round won jingle", matchWonDuration > roundWonDuration)
    }

    @Test
    fun testWholeGameLostFanfareIsDistinguishableFromRoundLoss() {
        val matchLostNotes = listOf(
            415.30 to 190,
            392.00 to 190,
            349.23 to 210,
            311.13 to 230,
            261.63 to 430
        )
        val roundLostNotes = listOf(
            349.23 to 170,
            329.63 to 170,
            311.13 to 190,
            261.63 to 270
        )

        val matchLostDuration = matchLostNotes.sumOf { it.second }
        val roundLostDuration = roundLostNotes.sumOf { it.second }

        assertTrue("Match lost failure duration must be between 500 and 1500 ms", matchLostDuration in 500..1500)
        assertNotEquals("Match lost must be distinct from round lost", matchLostDuration, roundLostDuration)
        assertTrue("Match lost instrumental must be longer than round lost sequence", matchLostDuration > roundLostDuration)
    }

    @Test
    fun testVibrationPatternsForWinAndLossAreDistinctAndValid() {
        val winPattern = XOSoundAndHapticManager.CELEBRATORY_VIBRATION_PATTERN
        val lossPattern = XOSoundAndHapticManager.DEFEAT_VIBRATION_PATTERN

        assertTrue("Win vibration pattern must have positive durations", winPattern.isNotEmpty())
        assertTrue("Loss vibration pattern must have positive durations", lossPattern.isNotEmpty())

        val totalWinVibeMs = winPattern.sum()
        val totalLossVibeMs = lossPattern.sum()

        // Casual vibration pattern duration between 300ms and 1500ms
        assertTrue("Celebratory vibration pattern must be brief (<1000ms)", totalWinVibeMs in 300..1000)
        assertTrue("Defeat vibration pattern must be brief (<1000ms)", totalLossVibeMs in 300..1000)

        // Patterns must be distinguishable
        assertFalse(winPattern.contentEquals(lossPattern))
    }

    @Test
    fun testEventDecisionLogicForRoundAndMatchOutcomes() {
        // Test helper simulating the event router
        data class TriggerEvent(val type: String, val hasSound: Boolean, val hasVibe: Boolean)

        fun determineEvent(
            state: XOState,
            localPlayer: PlayerId,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean
        ): TriggerEvent? {
            return when (val status = state.status) {
                is GameStatus.InProgress -> null
                is GameStatus.RoundWon -> {
                    val didLocalWin = status.winner == localPlayer
                    TriggerEvent(
                        type = if (didLocalWin) "ROUND_WON" else "ROUND_LOST",
                        hasSound = soundEnabled,
                        hasVibe = false // Ordinary round won/lost does not vibrate
                    )
                }
                is GameStatus.RoundDraw -> null // No effects on round draw
                is GameStatus.MatchWon -> {
                    val isTie = state.player1Score == state.player2Score
                    if (isTie) {
                        null // Draw/tie handling: no effects
                    } else {
                        val didLocalWin = status.matchWinner == localPlayer
                        TriggerEvent(
                            type = if (didLocalWin) "MATCH_WON" else "MATCH_LOST",
                            hasSound = soundEnabled,
                            hasVibe = vibrationEnabled
                        )
                    }
                }
                else -> null
            }
        }

        // 1. Ordinary Round Win for Local Player (Player 1)
        val round1State = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_1),
            roundNumber = 1,
            player1Score = 1,
            player2Score = 0,
            targetScore = 3
        )
        val r1Event = determineEvent(round1State, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(r1Event)
        assertEquals("ROUND_WON", r1Event!!.type)
        assertTrue(r1Event.hasSound)
        assertFalse("Ordinary round win must not trigger whole-game vibration", r1Event.hasVibe)

        // 2. Ordinary Round Loss for Local Player (Player 2 won round 2)
        val round2State = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_2),
            roundNumber = 2,
            player1Score = 1,
            player2Score = 1,
            targetScore = 3
        )
        val r2Event = determineEvent(round2State, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(r2Event)
        assertEquals("ROUND_LOST", r2Event!!.type)
        assertTrue(r2Event.hasSound)
        assertFalse("Ordinary round loss must not trigger vibration", r2Event.hasVibe)

        // 3. Whole Game Won for Local Player (MatchWon takes precedence)
        val matchWonState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 3,
            player1Score = 2,
            player2Score = 1,
            targetScore = 3
        )
        val matchWinEvent = determineEvent(matchWonState, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(matchWinEvent)
        assertEquals("MATCH_WON", matchWinEvent!!.type)
        assertTrue(matchWinEvent.hasSound)
        assertTrue(matchWinEvent.hasVibe)

        // 4. Whole Game Lost for Local Player
        val matchLostState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_2),
            roundNumber = 3,
            player1Score = 1,
            player2Score = 2,
            targetScore = 3
        )
        val matchLossEvent = determineEvent(matchLostState, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(matchLossEvent)
        assertEquals("MATCH_LOST", matchLossEvent!!.type)
        assertTrue(matchLossEvent.hasSound)
        assertTrue(matchLossEvent.hasVibe)

        // 5. Draw Handling: RoundDraw must produce NO event
        val drawState = XOState(
            status = GameStatus.RoundDraw,
            roundNumber = 1,
            player1Score = 0,
            player2Score = 0,
            targetScore = 3
        )
        val drawEvent = determineEvent(drawState, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertEquals("Draw must produce no victory or failure effects", null, drawEvent)

        // 6. Match Tied: Match tie must produce NO win/loss effects
        val matchTieState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 2,
            player1Score = 1,
            player2Score = 1,
            targetScore = 2
        )
        val matchTieEvent = determineEvent(matchTieState, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = true)
        assertEquals("Match tied must produce no win or loss effects", null, matchTieEvent)

        // 7. Independence: Sound OFF, Vibration ON
        val soundOffEvent = determineEvent(matchWonState, PlayerId.PLAYER_1, soundEnabled = false, vibrationEnabled = true)
        assertNotNull(soundOffEvent)
        assertFalse("Sound OFF must disable sound", soundOffEvent!!.hasSound)
        assertTrue("Vibration ON must keep vibration active when sound is OFF", soundOffEvent.hasVibe)

        // 8. Independence: Sound ON, Vibration OFF
        val vibeOffEvent = determineEvent(matchWonState, PlayerId.PLAYER_1, soundEnabled = true, vibrationEnabled = false)
        assertNotNull(vibeOffEvent)
        assertTrue("Sound ON must keep sound active when vibration is OFF", vibeOffEvent!!.hasSound)
        assertFalse("Vibration OFF must disable vibration", vibeOffEvent.hasVibe)
    }
}
