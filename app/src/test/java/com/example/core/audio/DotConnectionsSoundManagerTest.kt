package com.example.core.audio

import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotConnectionsSoundManagerTest {

    @Test
    fun testConnectSoundDurationWithin25To50Ms() {
        val sampleRate = DotConnectionsSoundManager.SAMPLE_RATE
        val pcm = DotConnectionsSoundManager.generateConnectSound(sampleRate = sampleRate)

        // 16-bit mono = 2 bytes per sample
        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Connect sound duration must be between 25 and 50 ms, got $durationMs ms", durationMs in 25..50)
        assertTrue("Connect sound must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testSingleBoxSoundDurationWithin200To300Ms() {
        val sampleRate = DotConnectionsSoundManager.SAMPLE_RATE
        val pcm = DotConnectionsSoundManager.generateSingleBoxSound(sampleRate = sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Single box sound duration must be between 200 and 300 ms, got $durationMs ms", durationMs in 200..300)
        assertTrue("Single box sound must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testDoubleBoxSoundDurationWithin300To450Ms() {
        val sampleRate = DotConnectionsSoundManager.SAMPLE_RATE
        val pcm = DotConnectionsSoundManager.generateDoubleBoxSound(sampleRate = sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Double box sound duration must be between 300 and 450 ms, got $durationMs ms", durationMs in 300..450)
        assertTrue("Double box sound must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testVictoryAndDefeatAudioDurations() {
        val sampleRate = DotConnectionsSoundManager.SAMPLE_RATE
        val victoryPcm = DotConnectionsSoundManager.generateVictoryFanfare(sampleRate = sampleRate)
        val defeatPcm = DotConnectionsSoundManager.generateDefeatCadence(sampleRate = sampleRate)
        val drawPcm = DotConnectionsSoundManager.generateDrawChime(sampleRate = sampleRate)

        val victoryDurationMs = ((victoryPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val defeatDurationMs = ((defeatPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val drawDurationMs = ((drawPcm.size / 2) * 1000.0 / sampleRate).toInt()

        assertTrue("Victory fanfare duration should be around 1100 ms, got $victoryDurationMs", victoryDurationMs in 1000..1200)
        assertTrue("Defeat cadence duration should be around 1100 ms, got $defeatDurationMs", defeatDurationMs in 1000..1200)
        assertTrue("Draw chime duration should be around 750 ms, got $drawDurationMs", drawDurationMs in 700..850)
    }

    @Test
    fun testPcmToWavHeaderFormat() {
        val pcm = DotConnectionsSoundManager.generateConnectSound()
        val wav = DotConnectionsSoundManager.pcmToWav(pcm, DotConnectionsSoundManager.SAMPLE_RATE)

        assertEquals("WAV size must be PCM size + 44 header bytes", pcm.size + 44, wav.size)
        val riff = String(wav.copyOfRange(0, 4), Charsets.US_ASCII)
        val wave = String(wav.copyOfRange(8, 12), Charsets.US_ASCII)
        val fmt = String(wav.copyOfRange(12, 16), Charsets.US_ASCII)
        val data = String(wav.copyOfRange(36, 40), Charsets.US_ASCII)

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)
        assertEquals("data", data)
    }

    @Test
    fun testSoloModeOutcomeClassification() {
        // Player 1 (user) wins against bot
        val p1WinOutcome = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_1,
            isDraw = false,
            mode = GameMode.SOLO,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.VICTORY, p1WinOutcome)

        // Bot (Player 2) wins against user
        val botWinOutcome = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_2,
            isDraw = false,
            mode = GameMode.SOLO,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DEFEAT, botWinOutcome)

        // Draw in Solo mode
        val drawOutcome = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = null,
            isDraw = true,
            mode = GameMode.SOLO,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DRAW, drawOutcome)
    }

    @Test
    fun testLocal1v1ModeAlwaysCelebratesWinner() {
        // In local mode on a shared phone, both Player 1 and Player 2 wins celebrate the victor
        val p1Win = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_1,
            isDraw = false,
            mode = GameMode.LOCAL_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.VICTORY, p1Win)

        val p2Win = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_2,
            isDraw = false,
            mode = GameMode.LOCAL_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.VICTORY, p2Win)

        val draw = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = null,
            isDraw = true,
            mode = GameMode.LOCAL_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DRAW, draw)
    }

    @Test
    fun testNearbyMultiplayerOutcomeClassification() {
        // Device A is Player 1, Player 1 wins -> VICTORY on Device A
        val devAWins = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_1,
            isDraw = false,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.VICTORY, devAWins)

        // Device B is Player 2, Player 1 wins -> DEFEAT on Device B
        val devBLoses = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_1,
            isDraw = false,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = DotPlayer.PLAYER_2
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DEFEAT, devBLoses)

        // Device B is Player 2, Player 2 wins -> VICTORY on Device B
        val devBWins = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_2,
            isDraw = false,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = DotPlayer.PLAYER_2
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.VICTORY, devBWins)

        // Device A is Player 1, Player 2 wins -> DEFEAT on Device A
        val devALoses = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = DotPlayer.PLAYER_2,
            isDraw = false,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DEFEAT, devALoses)

        // Draw in Nearby multiplayer
        val nearbyDraw = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = null,
            isDraw = true,
            mode = GameMode.BLUETOOTH_1V1,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.DRAW, nearbyDraw)
    }

    @Test
    fun testInProgressGameReturnsNone() {
        val noneOutcome = DotConnectionsSoundManager.resolveOutcomeAudio(
            winner = null,
            isDraw = false,
            mode = GameMode.SOLO,
            localPlayer = DotPlayer.PLAYER_1
        )
        assertEquals(DotConnectionsSoundManager.DotOutcomeAudio.NONE, noneOutcome)
    }
}
