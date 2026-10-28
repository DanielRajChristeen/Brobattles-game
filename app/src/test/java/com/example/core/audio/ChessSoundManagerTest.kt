package com.example.core.audio

import com.example.core.engine.chess.ChessGameStatus
import com.example.core.engine.chess.PieceColor
import com.example.core.registry.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSoundManagerTest {

    @Test
    fun testWoodenMoveTapDurationWithin50To80Ms() {
        val sampleRate = ChessSoundManager.SAMPLE_RATE
        val pcm = ChessSoundManager.generateWoodTap(isCapture = false, sampleRate = sampleRate)

        // 16-bit mono = 2 bytes per sample
        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Wooden move sound duration must be between 50 and 80 ms, got $durationMs ms", durationMs in 50..80)
        assertTrue("Wooden move sound must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testWoodenCaptureTapDurationWithin70To100Ms() {
        val sampleRate = ChessSoundManager.SAMPLE_RATE
        val pcm = ChessSoundManager.generateWoodTap(isCapture = true, sampleRate = sampleRate)

        val sampleCount = pcm.size / 2
        val durationMs = (sampleCount * 1000.0 / sampleRate).toInt()

        assertTrue("Wooden capture sound duration must be between 70 and 100 ms, got $durationMs ms", durationMs in 70..100)
        assertTrue("Wooden capture sound must contain non-zero audio samples", pcm.any { it != 0.toByte() })
    }

    @Test
    fun testPcmToWavHeaderFormat() {
        val pcm = ChessSoundManager.generateWoodTap(isCapture = false)
        val wav = ChessSoundManager.pcmToWav(pcm, ChessSoundManager.SAMPLE_RATE)

        assertEquals("WAV size must be PCM size + 44 header bytes", pcm.size + 44, wav.size)
        // Verify standard RIFF WAVE header bytes
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
    fun testVictoryAndDefeatAudioDurations() {
        val victoryNotes = listOf(
            Pair(392.00, 110),
            Pair(523.25, 110),
            Pair(659.25, 110),
            Pair(783.99, 160),
            Pair(659.25, 110),
            Pair(783.99, 150),
            Pair(1046.50, 400)
        )
        val defeatNotes = listOf(
            Pair(415.30, 170),
            Pair(392.00, 170),
            Pair(349.23, 190),
            Pair(311.13, 210),
            Pair(261.63, 380)
        )
        val victoryPcm = ChessSoundManager.generateToneSequence(victoryNotes)
        val defeatPcm = ChessSoundManager.generateToneSequence(defeatNotes)

        val victoryDuration = victoryNotes.sumOf { it.second }
        val defeatDuration = defeatNotes.sumOf { it.second }

        assertTrue("Victory duration must be between 800 and 1500 ms", victoryDuration in 800..1500)
        assertTrue("Defeat duration must be between 800 and 1500 ms", defeatDuration in 800..1500)
        assertTrue("Victory audio must be non-empty", victoryPcm.any { it != 0.toByte() })
        assertTrue("Defeat audio must be non-empty", defeatPcm.any { it != 0.toByte() })
    }

    // -------------------------------------------------------------
    // Checkmate Vibration targeting rules tests
    // -------------------------------------------------------------

    @Test
    fun testCheckmateVibrationInSoloModeOnlyForLosingHuman() {
        val checkmateWhiteWins = ChessGameStatus.Checkmate(PieceColor.WHITE)

        // Human was White -> Human won -> Do NOT vibrate!
        val shouldVibrateOnWin = ChessSoundManager.shouldVibrateOnGameEnd(
            status = checkmateWhiteWins,
            mode = GameMode.SOLO,
            isHumanWinnerInSolo = true,
            isLocalWinnerInNearby = false
        )
        assertFalse("Human winner device must NOT vibrate on checkmate victory", shouldVibrateOnWin)

        // Human was Black (or Bot won) -> Human lost -> Must vibrate!
        val shouldVibrateOnLoss = ChessSoundManager.shouldVibrateOnGameEnd(
            status = checkmateWhiteWins,
            mode = GameMode.SOLO,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        assertTrue("Losing human player device MUST vibrate on checkmate defeat", shouldVibrateOnLoss)
    }

    @Test
    fun testCheckmateVibrationInLocal1v1ModeFiresOnceOnSharedDevice() {
        val checkmateStatus = ChessGameStatus.Checkmate(PieceColor.BLACK)

        val shouldVibrate = ChessSoundManager.shouldVibrateOnGameEnd(
            status = checkmateStatus,
            mode = GameMode.LOCAL_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        assertTrue("Local 1v1 shared device must vibrate once on checkmate", shouldVibrate)
    }

    @Test
    fun testCheckmateVibrationInNearbyModeOnlyForLosingDevice() {
        val checkmateStatus = ChessGameStatus.Checkmate(PieceColor.WHITE)

        // Local device won -> Must NOT vibrate
        val shouldVibrateWinner = ChessSoundManager.shouldVibrateOnGameEnd(
            status = checkmateStatus,
            mode = GameMode.BLUETOOTH_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = true
        )
        assertFalse("Winning device in Nearby mode must NOT vibrate", shouldVibrateWinner)

        // Local device lost -> MUST vibrate
        val shouldVibrateLoser = ChessSoundManager.shouldVibrateOnGameEnd(
            status = checkmateStatus,
            mode = GameMode.BLUETOOTH_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        assertTrue("Losing device in Nearby mode MUST vibrate", shouldVibrateLoser)
    }

    @Test
    fun testCheckmateVibrationDoesNotTriggerOnStalemateOrDraw() {
        val stalemate = ChessGameStatus.Stalemate
        val drawFifty = ChessGameStatus.DrawByFiftyMoves
        val drawMaterial = ChessGameStatus.DrawByInsufficientMaterial

        for (status in listOf(stalemate, drawFifty, drawMaterial)) {
            for (mode in GameMode.entries) {
                val vibrate = ChessSoundManager.shouldVibrateOnGameEnd(
                    status = status,
                    mode = mode,
                    isHumanWinnerInSolo = false,
                    isLocalWinnerInNearby = false
                )
                assertFalse("Stalemate and draw must never trigger checkmate vibration in mode $mode", vibrate)
            }
        }
    }

    // -------------------------------------------------------------
    // Outcome Audio Routing tests
    // -------------------------------------------------------------

    @Test
    fun testOutcomeAudioSoloModeHumanWinVsBotWin() {
        val checkmate = ChessGameStatus.Checkmate(PieceColor.WHITE)

        val winAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmate,
            mode = GameMode.SOLO,
            isHumanWinnerInSolo = true,
            isLocalWinnerInNearby = false
        )
        assertEquals(ChessSoundManager.MatchOutcomeAudio.VICTORY, winAudio)

        val lossAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmate,
            mode = GameMode.SOLO,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        assertEquals(ChessSoundManager.MatchOutcomeAudio.DEFEAT, lossAudio)
    }

    @Test
    fun testOutcomeAudioLocal1v1AlwaysCelebratesWinner() {
        val checkmateWhite = ChessGameStatus.Checkmate(PieceColor.WHITE)
        val checkmateBlack = ChessGameStatus.Checkmate(PieceColor.BLACK)

        val p1WinAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmateWhite,
            mode = GameMode.LOCAL_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        val p2WinAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmateBlack,
            mode = GameMode.LOCAL_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )

        assertEquals("Local 1v1 P1 win must play Victory audio", ChessSoundManager.MatchOutcomeAudio.VICTORY, p1WinAudio)
        assertEquals("Local 1v1 P2 win must play Victory audio", ChessSoundManager.MatchOutcomeAudio.VICTORY, p2WinAudio)
    }

    @Test
    fun testOutcomeAudioNearbyMultiplayer() {
        val checkmate = ChessGameStatus.Checkmate(PieceColor.WHITE)

        val localWinAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmate,
            mode = GameMode.BLUETOOTH_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = true
        )
        val localLossAudio = ChessSoundManager.resolveOutcomeAudio(
            status = checkmate,
            mode = GameMode.BLUETOOTH_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )

        assertEquals("Nearby local winner must hear Victory audio", ChessSoundManager.MatchOutcomeAudio.VICTORY, localWinAudio)
        assertEquals("Nearby local loser must hear Defeat audio", ChessSoundManager.MatchOutcomeAudio.DEFEAT, localLossAudio)
    }

    @Test
    fun testOutcomeAudioStalemateAndDrawPlaysDrawChime() {
        val stalemate = ChessGameStatus.Stalemate
        val drawFifty = ChessGameStatus.DrawByFiftyMoves

        val stalemateAudio = ChessSoundManager.resolveOutcomeAudio(
            status = stalemate,
            mode = GameMode.SOLO,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )
        val drawAudio = ChessSoundManager.resolveOutcomeAudio(
            status = drawFifty,
            mode = GameMode.BLUETOOTH_1V1,
            isHumanWinnerInSolo = false,
            isLocalWinnerInNearby = false
        )

        assertEquals(ChessSoundManager.MatchOutcomeAudio.DRAW, stalemateAudio)
        assertEquals(ChessSoundManager.MatchOutcomeAudio.DRAW, drawAudio)
    }
}
