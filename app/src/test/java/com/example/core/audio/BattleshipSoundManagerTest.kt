package com.example.core.audio

import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleshipSoundManagerTest {

    @Test
    fun testPlacementAndRotationSoundDurations() {
        val sampleRate = BattleshipSoundManager.SAMPLE_RATE
        val placePcm = BattleshipSoundManager.generatePlacementClick(sampleRate = sampleRate)
        val rotatePcm = BattleshipSoundManager.generateRotationRatchet(sampleRate = sampleRate)

        val placeMs = ((placePcm.size / 2) * 1000.0 / sampleRate).toInt()
        val rotateMs = ((rotatePcm.size / 2) * 1000.0 / sampleRate).toInt()

        assertTrue("Placement sound should be between 30 and 55 ms, got $placeMs ms", placeMs in 30..55)
        assertTrue("Rotation sound should be between 45 and 75 ms, got $rotateMs ms", rotateMs in 45..75)
    }

    @Test
    fun testCombatSoundDurations() {
        val sampleRate = BattleshipSoundManager.SAMPLE_RATE
        val launchPcm = BattleshipSoundManager.generateMissileLaunch(sampleRate = sampleRate)
        val hitPcm = BattleshipSoundManager.generateHitExplosion(sampleRate = sampleRate)
        val missPcm = BattleshipSoundManager.generateWaterSplash(sampleRate = sampleRate)
        val sunkPcm = BattleshipSoundManager.generateShipSunkDetonation(sampleRate = sampleRate)
        val sonarPcm = BattleshipSoundManager.generateSonarPing(sampleRate = sampleRate)

        val launchMs = ((launchPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val hitMs = ((hitPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val missMs = ((missPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val sunkMs = ((sunkPcm.size / 2) * 1000.0 / sampleRate).toInt()
        val sonarMs = ((sonarPcm.size / 2) * 1000.0 / sampleRate).toInt()

        assertTrue("Missile launch should be around 180 ms, got $launchMs", launchMs in 150..220)
        assertTrue("Hit explosion should be around 320 ms, got $hitMs", hitMs in 280..360)
        assertTrue("Water splash should be around 220 ms, got $missMs", missMs in 180..260)
        assertTrue("Ship sunk detonation should be around 550 ms, got $sunkMs", sunkMs in 480..620)
        assertTrue("Sonar ping should be around 350 ms, got $sonarMs", sonarMs in 300..400)
    }

    @Test
    fun testPcmToWavHeader() {
        val pcm = BattleshipSoundManager.generatePlacementClick()
        val wav = BattleshipSoundManager.pcmToWav(pcm, BattleshipSoundManager.SAMPLE_RATE)

        assertEquals("WAV size must be PCM size + 44 header bytes", pcm.size + 44, wav.size)
        val riff = String(wav.copyOfRange(0, 4), Charsets.US_ASCII)
        val wave = String(wav.copyOfRange(8, 12), Charsets.US_ASCII)
        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
    }

    @Test
    fun testOutcomeClassificationAcrossModes() {
        // Solo: P1 wins -> VICTORY; Bot wins -> DEFEAT
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.VICTORY,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_1, GameMode.SOLO, BattleshipPlayer.PLAYER_1)
        )
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.DEFEAT,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_2, GameMode.SOLO, BattleshipPlayer.PLAYER_1)
        )

        // Local 1v1: Pass-the-phone always celebrates winner
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.VICTORY,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_1, GameMode.LOCAL_1V1, BattleshipPlayer.PLAYER_1)
        )
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.VICTORY,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_2, GameMode.LOCAL_1V1, BattleshipPlayer.PLAYER_1)
        )

        // WiFi Play (Bluetooth 1v1): Winner device gets VICTORY, Loser gets DEFEAT
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.VICTORY,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_1, GameMode.BLUETOOTH_1V1, BattleshipPlayer.PLAYER_1)
        )
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.DEFEAT,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_2, GameMode.BLUETOOTH_1V1, BattleshipPlayer.PLAYER_1)
        )
        assertEquals(
            BattleshipSoundManager.BattleshipOutcomeAudio.VICTORY,
            BattleshipSoundManager.resolveOutcomeAudio(BattleshipPlayer.PLAYER_2, GameMode.BLUETOOTH_1V1, BattleshipPlayer.PLAYER_2)
        )
    }
}
