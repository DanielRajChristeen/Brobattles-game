package com.example.core.audio

import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakesAndLaddersSoundManagerTest {

    @Test
    fun testDiceRollSoundDurationAround800Ms() {
        val sampleRate = SnakesAndLaddersSoundManager.SAMPLE_RATE
        val pcm = SnakesAndLaddersSoundManager.generateDiceRoll(sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Dice roll duration must be between 700 and 900 ms, got $durationMs ms", durationMs in 700..900)
        assertTrue("Dice roll audio must contain non-zero samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testFootstepSoundDurationUnder50Ms() {
        val sampleRate = SnakesAndLaddersSoundManager.SAMPLE_RATE
        val pcm = SnakesAndLaddersSoundManager.generateFootstep(sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Footstep duration must be under 50 ms for crisp animation synchronization, got $durationMs ms", durationMs in 20..50)
        assertTrue("Footstep audio must contain non-zero samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testLadderClimbSoundDurationAround600Ms() {
        val sampleRate = SnakesAndLaddersSoundManager.SAMPLE_RATE
        val pcm = SnakesAndLaddersSoundManager.generateLadderClimb(sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Ladder climb duration must be between 500 and 700 ms, got $durationMs ms", durationMs in 500..700)
        assertTrue("Ladder climb audio must contain non-zero samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testSnakeSlideSoundDurationAround650Ms() {
        val sampleRate = SnakesAndLaddersSoundManager.SAMPLE_RATE
        val pcm = SnakesAndLaddersSoundManager.generateSnakeSlide(sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Snake slide duration must be between 550 and 750 ms, got $durationMs ms", durationMs in 550..750)
        assertTrue("Snake slide audio must contain non-zero samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testStickFigureWinningAnimationSoundDurationUnder600Ms() {
        val sampleRate = SnakesAndLaddersSoundManager.SAMPLE_RATE
        val pcm = SnakesAndLaddersSoundManager.generateStickFigureWinning(sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Stick figure celebratory animation sound must be short (~450 ms), got $durationMs ms", durationMs in 350..550)
        assertTrue("Winning animation audio must contain non-zero samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testPcmToWavHeaderFormat() {
        val pcm = SnakesAndLaddersSoundManager.generateFootstep()
        val wav = SnakesAndLaddersSoundManager.pcmToWav(pcm, SnakesAndLaddersSoundManager.SAMPLE_RATE)

        assertEquals("WAV size must equal PCM size + 44 header bytes", pcm.size + 44, wav.size)
        val riff = String(wav.copyOfRange(0, 4), Charsets.US_ASCII)
        val wave = String(wav.copyOfRange(8, 12), Charsets.US_ASCII)
        val fmt = String(wav.copyOfRange(12, 16), Charsets.US_ASCII)
        val data = String(wav.copyOfRange(36, 40), Charsets.US_ASCII)

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)
        assertEquals("data", data)
    }

    // -------------------------------------------------------------
    // Match Outcome Sound Classification Tests
    // -------------------------------------------------------------

    @Test
    fun testSoloModeHumanWinPlaysVictoryBotWinPlaysDefeat() {
        val humanWon = GameStatus.MatchWon(PlayerId.PLAYER_1)
        val botWon = GameStatus.MatchWon(PlayerId.PLAYER_2)

        val humanWinAudio = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = humanWon,
            mode = GameMode.SOLO,
            localPlayer = PlayerId.PLAYER_1
        )
        val botWinAudio = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = botWon,
            mode = GameMode.SOLO,
            localPlayer = PlayerId.PLAYER_1
        )

        assertEquals("Human win in Solo mode must play Victory theme", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.VICTORY, humanWinAudio)
        assertEquals("Bot win in Solo mode must play Defeat theme for the user", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.DEFEAT, botWinAudio)
    }

    @Test
    fun testLocal1v1ModeAlwaysPlaysVictoryThemeForWinner() {
        val p1Won = GameStatus.MatchWon(PlayerId.PLAYER_1)
        val p2Won = GameStatus.MatchWon(PlayerId.PLAYER_2)

        val p1WinAudio = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p1Won,
            mode = GameMode.LOCAL_1V1,
            localPlayer = PlayerId.PLAYER_1
        )
        val p2WinAudio = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p2Won,
            mode = GameMode.LOCAL_1V1,
            localPlayer = PlayerId.PLAYER_1
        )

        assertEquals("Local 1v1 P1 win must play Victory theme", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.VICTORY, p1WinAudio)
        assertEquals("Local 1v1 P2 win must play Victory theme (never Defeat)", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.VICTORY, p2WinAudio)
    }

    @Test
    fun testNearbyMultiplayerCorrectClassificationPerDevice() {
        val p1Won = GameStatus.MatchWon(PlayerId.PLAYER_1)
        val p2Won = GameStatus.MatchWon(PlayerId.PLAYER_2)

        // When Player 1 wins:
        val p1DeviceWhenP1Wins = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p1Won,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = PlayerId.PLAYER_1
        )
        val p2DeviceWhenP1Wins = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p1Won,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = PlayerId.PLAYER_2
        )

        assertEquals("Player 1's device must play Victory when P1 wins", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.VICTORY, p1DeviceWhenP1Wins)
        assertEquals("Player 2's device must play Defeat when P1 wins", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.DEFEAT, p2DeviceWhenP1Wins)

        // When Player 2 wins:
        val p2DeviceWhenP2Wins = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p2Won,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = PlayerId.PLAYER_2
        )
        val p1DeviceWhenP2Wins = SnakesAndLaddersSoundManager.resolveOutcomeAudio(
            status = p2Won,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = PlayerId.PLAYER_1
        )

        assertEquals("Player 2's device must play Victory when P2 wins", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.VICTORY, p2DeviceWhenP2Wins)
        assertEquals("Player 1's device must play Defeat when P2 wins", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.DEFEAT, p1DeviceWhenP2Wins)
    }

    @Test
    fun testInProgressGameReturnsNone() {
        val inProgress = GameStatus.InProgress
        for (mode in GameMode.entries) {
            val outcome = SnakesAndLaddersSoundManager.resolveOutcomeAudio(inProgress, mode, PlayerId.PLAYER_1)
            assertEquals("In-progress game must produce no outcome audio", SnakesAndLaddersSoundManager.SnakesOutcomeAudio.NONE, outcome)
        }
    }
}
