package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.core.engine.chess.ChessGameStatus
import com.example.core.registry.GameMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-fidelity wooden piece sound synthesis and haptic feedback manager for Chess.
 *
 * Provides:
 * - Natural wooden piece placement/movement sound (light wooden tap/thud, ~65 ms)
 * - Distinct wooden capture sound (slightly heavier double-knock impact, ~85 ms)
 * - Regal ascending victory fanfare for match wins (~1.1 s)
 * - Solemn minor defeat cadence for match losses (~1.1 s)
 * - Neutral harmonic chime for draws and stalemates (~0.75 s)
 * - Targeted checkmate vibration for the losing opponent
 *
 * Audio is pre-rendered in-memory via 16-bit PCM and ingested into low-latency SoundPool
 * with static AudioTrack fallback, requiring zero external network calls or bulky assets.
 */
class ChessSoundManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private var soundPool: SoundPool? = null
    private var moveSoundId = 0
    private var captureSoundId = 0

    private var moveTrack: AudioTrack? = null
    private var captureTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null
    private var defeatTrack: AudioTrack? = null
    private var drawTrack: AudioTrack? = null

    private var isInitialized = false

    enum class MatchOutcomeAudio {
        VICTORY,
        DEFEAT,
        DRAW,
        NONE
    }

    companion object {
        const val SAMPLE_RATE = 22050

        // Distinct checkmate vibration pattern: alert rumble for the losing opponent (~510 ms total)
        val CHECKMATE_VIBRATION_PATTERN = longArrayOf(0, 160, 90, 260)

        // Subtle tactile tap for piece moves (~25 ms)
        const val MOVE_VIBRATION_DURATION_MS = 25L

        /**
         * Synthesizes natural, acoustic wooden piece movement or capture sounds using
         * impulse transient click modeling combined with exponential decaying wood cavity resonance.
         */
        fun generateWoodTap(
            isCapture: Boolean = false,
            sampleRate: Int = SAMPLE_RATE,
            volume: Double = 0.75
        ): ByteArray {
            val durationMs = if (isCapture) 85 else 65
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            // Frequencies tuned to mimic physical hardwood chess pieces on a wooden board
            val baseFreq = if (isCapture) 220.0 else 290.0
            val overtoneFreq = if (isCapture) 460.0 else 640.0
            val clickFreq = if (isCapture) 950.0 else 1250.0

            val decayRate = if (isCapture) 45.0 else 58.0
            val clickWindowSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-decayRate * t)

                // Initial crisp wooden impact click transient
                val clickEnvelope = if (i < clickWindowSamples) {
                    (1.0 - i.toDouble() / clickWindowSamples)
                } else 0.0
                val click = sin(2.0 * PI * clickFreq * t) * clickEnvelope * 0.55

                // Resonant body of the wooden piece and board
                val body = (sin(2.0 * PI * baseFreq * t) * 0.72 + sin(2.0 * PI * overtoneFreq * t) * 0.28)

                val wave = (body + click) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }

            val bytes = ByteArray(pcmShorts.size * 2)
            for (i in pcmShorts.indices) {
                val s = pcmShorts[i].toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        /**
         * Synthesizes 16-bit PCM mono musical note sequences for outcomes (Victory, Defeat, Draw).
         */
        fun generateToneSequence(
            notes: List<Pair<Double, Int>>,
            sampleRate: Int = SAMPLE_RATE,
            volume: Double = 0.65
        ): ByteArray {
            val totalSamples = notes.sumOf { (sampleRate * (it.second / 1000.0)).toInt() }
            val pcmShorts = ShortArray(totalSamples)
            var offset = 0
            for ((freq, durationMs) in notes) {
                val count = (sampleRate * (durationMs / 1000.0)).toInt()
                val attack = (sampleRate * 0.008).toInt().coerceAtLeast(1)
                val release = (sampleRate * 0.025).toInt().coerceAtLeast(1)
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val envelope = when {
                        i < attack -> i.toDouble() / attack
                        i > count - release -> (count - i).toDouble() / release
                        else -> 1.0
                    }
                    val wave = (sin(2.0 * PI * freq * t) * 0.8 + sin(4.0 * PI * freq * t) * 0.2) * envelope * volume * Short.MAX_VALUE
                    pcmShorts[offset + i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                }
                offset += count
            }
            val bytes = ByteArray(pcmShorts.size * 2)
            for (i in pcmShorts.indices) {
                val s = pcmShorts[i].toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        /**
         * Converts raw 16-bit mono PCM into a standard WAV byte array for SoundPool loading.
         */
        fun pcmToWav(pcmData: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = 1; header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2; header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (pcmData.size and 0xff).toByte()
            header[41] = ((pcmData.size shr 8) and 0xff).toByte()
            header[42] = ((pcmData.size shr 16) and 0xff).toByte()
            header[43] = ((pcmData.size shr 24) and 0xff).toByte()
            return header + pcmData
        }

        /**
         * Evaluates whether checkmate vibration should fire on this device.
         * Only the losing opponent's device vibrates.
         * Winning devices never vibrate on checkmate.
         * Draws and non-checkmate outcomes never vibrate.
         */
        fun shouldVibrateOnGameEnd(
            status: ChessGameStatus,
            mode: GameMode,
            isHumanWinnerInSolo: Boolean,
            isLocalWinnerInNearby: Boolean
        ): Boolean {
            if (status !is ChessGameStatus.Checkmate) return false
            return when (mode) {
                GameMode.SOLO -> !isHumanWinnerInSolo // Bot checkmates human -> human device vibrates
                GameMode.LOCAL_1V1 -> true // Shared device vibrates once for the losing player
                GameMode.BLUETOOTH_1V1 -> !isLocalWinnerInNearby // Losing remote player's local device vibrates
            }
        }

        /**
         * Resolves the correct musical outcome theme based on game mode and winner:
         * - Solo: Human win -> Victory; Bot win -> Defeat
         * - Local 1v1: Celebrates the winner with Victory theme on the shared device
         * - Nearby: Local win -> Victory; Local loss -> Defeat
         * - Stalemate/Draw: Draw chime
         */
        fun resolveOutcomeAudio(
            status: ChessGameStatus,
            mode: GameMode,
            isHumanWinnerInSolo: Boolean,
            isLocalWinnerInNearby: Boolean
        ): MatchOutcomeAudio {
            if (!status.isGameOver) return MatchOutcomeAudio.NONE
            return when (status) {
                is ChessGameStatus.Checkmate -> {
                    when (mode) {
                        GameMode.SOLO -> if (isHumanWinnerInSolo) MatchOutcomeAudio.VICTORY else MatchOutcomeAudio.DEFEAT
                        GameMode.LOCAL_1V1 -> MatchOutcomeAudio.VICTORY
                        GameMode.BLUETOOTH_1V1 -> if (isLocalWinnerInNearby) MatchOutcomeAudio.VICTORY else MatchOutcomeAudio.DEFEAT
                    }
                }
                else -> MatchOutcomeAudio.DRAW
            }
        }
    }

    init {
        initializeAudio()
    }

    @Synchronized
    private fun initializeAudio() {
        if (isInitialized) return
        try {
            // 1. Wooden Move Sound PCM (~65 ms, crisp piece placement tap)
            val movePcm = generateWoodTap(isCapture = false, sampleRate = SAMPLE_RATE, volume = 0.70)
            moveTrack = buildStaticTrack(movePcm, SAMPLE_RATE)

            // 2. Wooden Capture Sound PCM (~85 ms, richer wooden impact)
            val capturePcm = generateWoodTap(isCapture = true, sampleRate = SAMPLE_RATE, volume = 0.80)
            captureTrack = buildStaticTrack(capturePcm, SAMPLE_RATE)

            // 3. Victory Fanfare (~1.1 s, bright ascending royal theme)
            val victoryNotes = listOf(
                Pair(392.00, 110), // G4
                Pair(523.25, 110), // C5
                Pair(659.25, 110), // E5
                Pair(783.99, 160), // G5
                Pair(659.25, 110), // E5
                Pair(783.99, 150), // G5
                Pair(1046.50, 400) // C6
            )
            victoryTrack = buildStaticTrack(generateToneSequence(victoryNotes, SAMPLE_RATE, volume = 0.70), SAMPLE_RATE)

            // 4. Defeat Cadence (~1.1 s, solemn minor descending theme)
            val defeatNotes = listOf(
                Pair(415.30, 170), // Ab4
                Pair(392.00, 170), // G4
                Pair(349.23, 190), // F4
                Pair(311.13, 210), // Eb4
                Pair(261.63, 380)  // C4
            )
            defeatTrack = buildStaticTrack(generateToneSequence(defeatNotes, SAMPLE_RATE, volume = 0.70), SAMPLE_RATE)

            // 5. Draw Chime (~0.75 s, peaceful neutral cadence)
            val drawNotes = listOf(
                Pair(329.63, 140), // E4
                Pair(392.00, 140), // G4
                Pair(493.88, 190), // B4
                Pair(587.33, 280)  // D5
            )
            drawTrack = buildStaticTrack(generateToneSequence(drawNotes, SAMPLE_RATE, volume = 0.65), SAMPLE_RATE)

            // Low-latency SoundPool for instantaneous wooden tap playback
            try {
                val poolAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(poolAttributes)
                    .build()

                val moveWav = pcmToWav(movePcm, SAMPLE_RATE)
                val moveCache = File(context.cacheDir, "chess_wood_move.wav")
                FileOutputStream(moveCache).use { it.write(moveWav) }
                moveSoundId = pool.load(moveCache.absolutePath, 1)

                val capWav = pcmToWav(capturePcm, SAMPLE_RATE)
                val capCache = File(context.cacheDir, "chess_wood_capture.wav")
                FileOutputStream(capCache).use { it.write(capWav) }
                captureSoundId = pool.load(capCache.absolutePath, 1)

                soundPool = pool
            } catch (_: Exception) {
                soundPool = null
                moveSoundId = 0
                captureSoundId = 0
            }

            isInitialized = true
        } catch (_: Exception) {
            // Safe fallback for JVM / test environments
        }
    }

    private fun buildStaticTrack(pcmBytes: ByteArray, sampleRate: Int): AudioTrack? {
        return try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(pcmBytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcmBytes, 0, pcmBytes.size)
            track
        } catch (_: Exception) {
            null
        }
    }

    private fun play(track: AudioTrack?, isSoundEnabled: Boolean) {
        if (!isSoundEnabled || track == null) return
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.pause()
                track.flush()
                track.playbackHeadPosition = 0
                track.play()
            }
        } catch (_: Exception) {}
    }

    /**
     * Plays a realistic wooden chess move sound once for a successfully committed move.
     * Uses slightly heavier wooden capture sound when capturing a piece.
     */
    fun playMoveSound(isCapture: Boolean = false, isSoundEnabled: Boolean = true) {
        if (!isSoundEnabled) return
        try {
            val pool = soundPool
            val sId = if (isCapture) captureSoundId else moveSoundId
            if (pool != null && sId > 0) {
                pool.play(sId, 0.85f, 0.85f, 1, 0, 1.0f)
                return
            }
        } catch (_: Exception) {}

        // Fallback to static AudioTrack
        val track = if (isCapture) captureTrack else moveTrack
        play(track, isSoundEnabled)
    }

    /**
     * Plays the triumphant royal victory fanfare when the local player wins.
     */
    fun playVictory(isSoundEnabled: Boolean = true) {
        if (!isSoundEnabled) return
        stopAllSounds()
        play(victoryTrack, isSoundEnabled)
    }

    /**
     * Plays the solemn defeat theme when the local player loses to bot or remote opponent.
     */
    fun playDefeat(isSoundEnabled: Boolean = true) {
        if (!isSoundEnabled) return
        stopAllSounds()
        play(defeatTrack, isSoundEnabled)
    }

    /**
     * Plays the peaceful draw chime when game ends in stalemate or draw.
     */
    fun playDraw(isSoundEnabled: Boolean = true) {
        if (!isSoundEnabled) return
        stopAllSounds()
        play(drawTrack, isSoundEnabled)
    }

    /**
     * Triggers distinct checkmate vibration pattern on the losing opponent's device.
     */
    fun vibrateCheckmate(isVibrationEnabled: Boolean = true) {
        if (!isVibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(CHECKMATE_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(CHECKMATE_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Triggers subtle tactile vibration feedback for piece placement.
     */
    fun vibrateMove(isVibrationEnabled: Boolean = true) {
        if (!isVibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(MOVE_VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(MOVE_VIBRATION_DURATION_MS)
            }
        } catch (_: Exception) {}
    }

    fun stopAllSounds() {
        listOf(moveTrack, captureTrack, victoryTrack, defeatTrack, drawTrack).forEach {
            try {
                if (it?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                    it.reloadStaticData()
                }
            } catch (_: Exception) {}
        }
    }

    fun release() {
        stopAllSounds()
        try {
            soundPool?.release()
        } catch (_: Exception) {}
        soundPool = null
        moveSoundId = 0
        captureSoundId = 0

        listOf(moveTrack, captureTrack, victoryTrack, defeatTrack, drawTrack).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
        moveTrack = null
        captureTrack = null
        victoryTrack = null
        defeatTrack = null
        drawTrack = null
        isInitialized = false
    }
}
