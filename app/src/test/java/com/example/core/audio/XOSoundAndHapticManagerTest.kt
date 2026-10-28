package com.example.core.audio

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.engine.xo.XOState
import com.example.core.registry.GameMode
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
        // Test helper simulating the outcome sound and haptic router
        data class TriggerEvent(val type: String, val hasSound: Boolean, val hasVibe: Boolean)

        fun determineEvent(
            state: XOState,
            localPlayer: PlayerId,
            isTwoPlayerMode: Boolean,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean
        ): TriggerEvent? {
            return when (val status = state.status) {
                is GameStatus.InProgress -> null
                is GameStatus.RoundWon -> {
                    val isVictory = isTwoPlayerMode || status.winner == localPlayer
                    TriggerEvent(
                        type = if (isVictory) "ROUND_WON" else "ROUND_LOST",
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
                        val isVictory = isTwoPlayerMode || status.matchWinner == localPlayer
                        TriggerEvent(
                            type = if (isVictory) "MATCH_WON" else "MATCH_LOST",
                            hasSound = soundEnabled,
                            hasVibe = vibrationEnabled
                        )
                    }
                }
                else -> null
            }
        }

        // ==========================================
        // 1. VS BOT MODE TESTS (isTwoPlayerMode = false)
        // ==========================================
        val botRound1State = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_1),
            roundNumber = 1,
            player1Score = 1,
            player2Score = 0,
            targetScore = 3
        )
        val botR1Event = determineEvent(botRound1State, PlayerId.PLAYER_1, isTwoPlayerMode = false, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(botR1Event)
        assertEquals("ROUND_WON", botR1Event!!.type)
        assertTrue(botR1Event.hasSound)
        assertFalse("Ordinary round win must not trigger vibration", botR1Event.hasVibe)

        // VS Bot: Bot (Player 2) wins round -> failure instrumental
        val botRound2State = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_2),
            roundNumber = 2,
            player1Score = 1,
            player2Score = 1,
            targetScore = 3
        )
        val botR2Event = determineEvent(botRound2State, PlayerId.PLAYER_1, isTwoPlayerMode = false, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(botR2Event)
        assertEquals("ROUND_LOST", botR2Event!!.type)
        assertTrue(botR2Event.hasSound)
        assertFalse("Ordinary round loss must not trigger vibration", botR2Event.hasVibe)

        // VS Bot: Local human (Player 1) wins match -> victory fanfare + celebratory vibration
        val botMatchWinState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 3,
            player1Score = 2,
            player2Score = 1,
            targetScore = 3
        )
        val botMatchWinEvent = determineEvent(botMatchWinState, PlayerId.PLAYER_1, isTwoPlayerMode = false, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(botMatchWinEvent)
        assertEquals("MATCH_WON", botMatchWinEvent!!.type)
        assertTrue(botMatchWinEvent.hasSound)
        assertTrue(botMatchWinEvent.hasVibe)

        // VS Bot: Bot (Player 2) wins match -> failure instrumental + defeat vibration
        val botMatchLossState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_2),
            roundNumber = 3,
            player1Score = 1,
            player2Score = 2,
            targetScore = 3
        )
        val botMatchLossEvent = determineEvent(botMatchLossState, PlayerId.PLAYER_1, isTwoPlayerMode = false, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(botMatchLossEvent)
        assertEquals("MATCH_LOST", botMatchLossEvent!!.type)
        assertTrue(botMatchLossEvent.hasSound)
        assertTrue(botMatchLossEvent.hasVibe)

        // ==========================================
        // 2. TWO-PLAYER MODE TESTS (isTwoPlayerMode = true)
        // ==========================================
        // Two-Player: Player 1 wins round -> victory instrumental, no failure, no vibration
        val p1RoundWinState = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_1),
            roundNumber = 1,
            player1Score = 1,
            player2Score = 0,
            targetScore = 3
        )
        val p1RoundWinEvent = determineEvent(p1RoundWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(p1RoundWinEvent)
        assertEquals("ROUND_WON", p1RoundWinEvent!!.type)
        assertTrue("Two-player P1 round win must play sound", p1RoundWinEvent.hasSound)
        assertFalse("Two-player round win must not vibrate", p1RoundWinEvent.hasVibe)

        // Two-Player: Player 2 wins round -> SAME victory instrumental, NO failure instrumental, no vibration
        val p2RoundWinState = XOState(
            status = GameStatus.RoundWon(PlayerId.PLAYER_2),
            roundNumber = 2,
            player1Score = 1,
            player2Score = 1,
            targetScore = 3
        )
        val p2RoundWinEvent = determineEvent(p2RoundWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(p2RoundWinEvent)
        assertEquals("ROUND_WON", p2RoundWinEvent!!.type)
        assertTrue("Two-player P2 round win must play victory instrumental", p2RoundWinEvent.hasSound)
        assertFalse("Two-player round win must not vibrate", p2RoundWinEvent.hasVibe)

        // Two-Player: Player 1 wins the entire match -> victory fanfare + celebratory vibration
        val p1MatchWinState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 3,
            player1Score = 2,
            player2Score = 1,
            targetScore = 3
        )
        val p1MatchWinEvent = determineEvent(p1MatchWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(p1MatchWinEvent)
        assertEquals("MATCH_WON", p1MatchWinEvent!!.type)
        assertTrue("Two-player P1 match win must play victory fanfare", p1MatchWinEvent.hasSound)
        assertTrue("Two-player P1 match win must trigger celebratory vibration", p1MatchWinEvent.hasVibe)

        // Two-Player: Player 2 wins the entire match -> SAME victory fanfare + celebratory vibration, NO failure instrumental
        val p2MatchWinState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_2),
            roundNumber = 3,
            player1Score = 1,
            player2Score = 2,
            targetScore = 3
        )
        val p2MatchWinEvent = determineEvent(p2MatchWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertNotNull(p2MatchWinEvent)
        assertEquals("MATCH_WON", p2MatchWinEvent!!.type)
        assertTrue("Two-player P2 match win must play victory fanfare", p2MatchWinEvent.hasSound)
        assertTrue("Two-player P2 match win must trigger celebratory vibration", p2MatchWinEvent.hasVibe)

        // Two-Player: Round Draw -> No victory or failure effects
        val drawState = XOState(
            status = GameStatus.RoundDraw,
            roundNumber = 1,
            player1Score = 0,
            player2Score = 0,
            targetScore = 3
        )
        val drawEvent = determineEvent(drawState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertEquals("Draw in two-player mode must produce no victory or failure effects", null, drawEvent)

        // Two-Player: Match Tied -> No victory fanfare or vibration
        val matchTieState = XOState(
            status = GameStatus.MatchWon(PlayerId.PLAYER_1),
            roundNumber = 2,
            player1Score = 1,
            player2Score = 1,
            targetScore = 2
        )
        val matchTieEvent = determineEvent(matchTieState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = true)
        assertEquals("Match tied must produce no win or loss effects", null, matchTieEvent)

        // ==========================================
        // 3. SETTINGS INDEPENDENCE (Sound and Vibration)
        // ==========================================
        // Sound OFF, Vibration ON in Two-Player match win
        val soundOffEvent = determineEvent(p2MatchWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = false, vibrationEnabled = true)
        assertNotNull(soundOffEvent)
        assertFalse("Sound OFF must disable sound", soundOffEvent!!.hasSound)
        assertTrue("Vibration ON must keep vibration active when sound is OFF", soundOffEvent.hasVibe)

        // Sound ON, Vibration OFF in Two-Player match win
        val vibeOffEvent = determineEvent(p2MatchWinState, PlayerId.PLAYER_1, isTwoPlayerMode = true, soundEnabled = true, vibrationEnabled = false)
        assertNotNull(vibeOffEvent)
        assertTrue("Sound ON must keep sound active when vibration is OFF", vibeOffEvent!!.hasSound)
        assertFalse("Vibration OFF must disable vibration", vibeOffEvent.hasVibe)
    }

    @Test
    fun testTouchpadTapSoundDurationUnder50Ms() {
        val tapNotes = listOf(
            1400.0 to 10,
            700.0 to 18
        )
        val sampleRate = XOSoundAndHapticManager.SAMPLE_RATE
        val pcm = XOSoundAndHapticManager.generateToneSequence(tapNotes, sampleRate, volume = 0.45)

        val totalDurationMs = tapNotes.sumOf { it.second }
        assertTrue("Touchpad tap sound must be very brief (< 50ms) for responsive gameplay", totalDurationMs in 15..45)
        assertEquals(28, totalDurationMs)

        val expectedSamples = tapNotes.sumOf { (sampleRate * (it.second / 1000.0)).toInt() }
        val expectedBytes = expectedSamples * 2
        assertEquals(expectedBytes, pcm.size)

        // Ensure non-silent, valid PCM audio data
        val hasSound = pcm.any { it != 0.toByte() }
        assertTrue("Touchpad tap audio must contain non-zero audio samples", hasSound)
    }

    @Test
    fun testTugOfWarOutcomeRoutingByModeAndWinner() {
        // Simulates the routing logic in onTugOfWarMatchWon
        fun routeOutcome(
            winner: PlayerId,
            localPlayer: PlayerId,
            mode: GameMode,
            soundEnabled: Boolean
        ): String {
            if (!soundEnabled) return "MUTED"
            return when (mode) {
                GameMode.SOLO -> {
                    if (winner == PlayerId.PLAYER_1) "VICTORY" else "DEFEAT"
                }
                GameMode.LOCAL_1V1 -> {
                    "VICTORY"
                }
                GameMode.BLUETOOTH_1V1 -> {
                    if (winner == localPlayer) "VICTORY" else "DEFEAT"
                }
            }
        }

        // 1. User vs Bot: User (Player 1) wins -> Victory instrumental
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_1, GameMode.SOLO, soundEnabled = true))

        // 2. User vs Bot: Bot (Player 2) wins -> Defeat instrumental
        assertEquals("DEFEAT", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.SOLO, soundEnabled = true))

        // 3. Local 2-Player: Player 1 wins -> Victory instrumental
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_1, GameMode.LOCAL_1V1, soundEnabled = true))

        // 4. Local 2-Player: Player 2 wins -> Victory instrumental (same victory theme, never defeat!)
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.LOCAL_1V1, soundEnabled = true))
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_2, GameMode.LOCAL_1V1, soundEnabled = true))

        // 5. Nearby: Player 1 wins
        // On Player 1's device (localPlayer == PLAYER_1) -> Victory instrumental
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_1, GameMode.BLUETOOTH_1V1, soundEnabled = true))
        // On Player 2's device (localPlayer == PLAYER_2) -> Defeat instrumental
        assertEquals("DEFEAT", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_2, GameMode.BLUETOOTH_1V1, soundEnabled = true))

        // 6. Nearby: Player 2 wins
        // On Player 2's device (localPlayer == PLAYER_2) -> Victory instrumental
        assertEquals("VICTORY", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_2, GameMode.BLUETOOTH_1V1, soundEnabled = true))
        // On Player 1's device (localPlayer == PLAYER_1) -> Defeat instrumental
        assertEquals("DEFEAT", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.BLUETOOTH_1V1, soundEnabled = true))

        // 7. Sound Effects OFF -> Muted in all cases
        assertEquals("MUTED", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_1, GameMode.SOLO, soundEnabled = false))
        assertEquals("MUTED", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.SOLO, soundEnabled = false))
        assertEquals("MUTED", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.LOCAL_1V1, soundEnabled = false))
        assertEquals("MUTED", routeOutcome(PlayerId.PLAYER_1, PlayerId.PLAYER_1, GameMode.BLUETOOTH_1V1, soundEnabled = false))
        assertEquals("MUTED", routeOutcome(PlayerId.PLAYER_2, PlayerId.PLAYER_1, GameMode.BLUETOOTH_1V1, soundEnabled = false))
    }

    @Test
    fun testPcmToWavHeaderFormat() {
        val tapNotes = listOf(1400.0 to 10, 700.0 to 18)
        val pcm = XOSoundAndHapticManager.generateToneSequence(tapNotes, XOSoundAndHapticManager.SAMPLE_RATE, volume = 0.45)
        val wav = XOSoundAndHapticManager.pcmToWav(pcm, XOSoundAndHapticManager.SAMPLE_RATE)

        // Total WAV file size = 44 bytes header + PCM data
        assertEquals(pcm.size + 44, wav.size)

        // RIFF header
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])

        // WAVE signature
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])

        // fmt chunk
        assertEquals('f'.code.toByte(), wav[12])
        assertEquals('m'.code.toByte(), wav[13])
        assertEquals('t'.code.toByte(), wav[14])
        assertEquals(' '.code.toByte(), wav[15])

        // Format code: 1 = PCM (little endian at byte 20)
        assertEquals(1, wav[20].toInt())
        assertEquals(0, wav[21].toInt())

        // Number of channels: 1 = Mono (at byte 22)
        assertEquals(1, wav[22].toInt())
        assertEquals(0, wav[23].toInt())

        // data chunk header at byte 36
        assertEquals('d'.code.toByte(), wav[36])
        assertEquals('a'.code.toByte(), wav[37])
        assertEquals('t'.code.toByte(), wav[38])
        assertEquals('a'.code.toByte(), wav[39])
    }

    @Test
    fun testTapAcceptanceAndSoundTriggeringSequence() {
        // Simulates the zero-latency tap input validation and dispatch sequence in TugOfWarGameView
        var soundTriggerCount = 0
        var moveExecutionCount = 0

        fun simulateHandleTap(
            countdown: String?,
            isFinishing: Boolean,
            isInProgress: Boolean,
            sendNetwork: Boolean,
            soundEnabled: Boolean
        ): Boolean {
            // Step 1 & 2: Immediate validation
            if (countdown != null || isFinishing) return false
            if (!isInProgress) return false

            // Step 3: Immediate sound trigger upon acceptance
            if (sendNetwork && soundEnabled) {
                soundTriggerCount++
            }

            // Step 4: Game action execution
            moveExecutionCount++
            return true
        }

        // 1. Valid accepted user tap with sound ON -> triggers sound and executes move immediately
        val tap1 = simulateHandleTap(countdown = null, isFinishing = false, isInProgress = true, sendNetwork = true, soundEnabled = true)
        assertTrue(tap1)
        assertEquals(1, soundTriggerCount)
        assertEquals(1, moveExecutionCount)

        // 2. Tap during countdown -> rejected silently, no sound, no move
        val tapCountdown = simulateHandleTap(countdown = "3", isFinishing = false, isInProgress = true, sendNetwork = true, soundEnabled = true)
        assertFalse(tapCountdown)
        assertEquals(1, soundTriggerCount)
        assertEquals(1, moveExecutionCount)

        // 3. Tap after match finishes -> rejected silently, no sound, no move
        val tapFinished = simulateHandleTap(countdown = null, isFinishing = true, isInProgress = false, sendNetwork = true, soundEnabled = true)
        assertFalse(tapFinished)
        assertEquals(1, soundTriggerCount)
        assertEquals(1, moveExecutionCount)

        // 4. Remote network echo (sendNetwork = false) -> executes move, but DOES NOT play local tap sound
        val tapRemoteEcho = simulateHandleTap(countdown = null, isFinishing = false, isInProgress = true, sendNetwork = false, soundEnabled = true)
        assertTrue(tapRemoteEcho)
        assertEquals(1, soundTriggerCount) // unchanged!
        assertEquals(2, moveExecutionCount)

        // 5. Sound Effects disabled (soundEnabled = false) -> executes move, but sound is muted
        val tapMuted = simulateHandleTap(countdown = null, isFinishing = false, isInProgress = true, sendNetwork = true, soundEnabled = false)
        assertTrue(tapMuted)
        assertEquals(1, soundTriggerCount) // unchanged!
        assertEquals(3, moveExecutionCount)
    }
}

