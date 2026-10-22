package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Manages electronic audio synthesis and haptic feedback for Tic Tac Toe (XO).
 *
 * All audio tones are synthesized natively in-memory via 16-bit PCM AudioTrack,
 * requiring zero network calls, zero external asset dependencies, and minimal memory (<200KB).
 */
class XOSoundAndHapticManager(private val context: Context) {

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

    private var beepTrack: AudioTrack? = null
    private var roundWonTrack: AudioTrack? = null
    private var roundLostTrack: AudioTrack? = null
    private var matchWonTrack: AudioTrack? = null
    private var matchLostTrack: AudioTrack? = null

    private var isInitialized = false

    companion object {
        const val SAMPLE_RATE = 22050

        // Celebratory vibration pattern for Whole Game Won: fast energetic rhythm
        val CELEBRATORY_VIBRATION_PATTERN = longArrayOf(0, 80, 60, 90, 60, 220)

        // Defeat vibration pattern for Whole Game Lost: deep double rumble
        val DEFEAT_VIBRATION_PATTERN = longArrayOf(0, 200, 100, 320)

        /**
         * Synthesizes a PCM 16-bit mono little-endian byte array from note frequencies and durations.
         * Public for unit-testing verification of duration and audio synthesis without hardware.
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
                    // Harmonic overtone synthesis for clean retro electronic timbre
                    val wave = (sin(2.0 * PI * freq * t) * 0.82 + sin(4.0 * PI * freq * t) * 0.18) * envelope * volume * Short.MAX_VALUE
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
    }

    init {
        initializeAudio()
    }

    @Synchronized
    private fun initializeAudio() {
        if (isInitialized) return
        try {
            // 1. Placement Beep: Short, subtle electronic beep (~90 ms, 880 Hz)
            val beepPcm = generateToneSequence(
                listOf(880.0 to 90),
                SAMPLE_RATE,
                volume = 0.50
            )
            beepTrack = buildStaticTrack(beepPcm, SAMPLE_RATE)

            // 2. Round Won: Cheerful ascending electronic jingle (~700 ms)
            // C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
            val roundWonPcm = generateToneSequence(
                listOf(
                    523.25 to 130,
                    659.25 to 130,
                    783.99 to 150,
                    1046.50 to 290
                ),
                SAMPLE_RATE,
                volume = 0.65
            )
            roundWonTrack = buildStaticTrack(roundWonPcm, SAMPLE_RATE)

            // 3. Round Lost: Descending electronic failure sequence (~800 ms)
            // F4 (349Hz), E4 (330Hz), Eb4 (311Hz), C4 (261Hz)
            val roundLostPcm = generateToneSequence(
                listOf(
                    349.23 to 170,
                    329.63 to 170,
                    311.13 to 190,
                    261.63 to 270
                ),
                SAMPLE_RATE,
                volume = 0.65
            )
            roundLostTrack = buildStaticTrack(roundLostPcm, SAMPLE_RATE)

            // 4. Whole Game Won: Triumphant electronic fanfare (~1250 ms, distinguishable from round win)
            // G4, C5, E5, G5, E5, G5, high C6 chord
            val matchWonPcm = generateToneSequence(
                listOf(
                    392.00 to 110,
                    523.25 to 110,
                    659.25 to 110,
                    783.99 to 160,
                    659.25 to 110,
                    783.99 to 150,
                    1046.50 to 500
                ),
                SAMPLE_RATE,
                volume = 0.70
            )
            matchWonTrack = buildStaticTrack(matchWonPcm, SAMPLE_RATE)

            // 5. Whole Game Lost: Distinct dramatic failure sequence (~1250 ms, distinguishable from round loss)
            // Ab4, G4, F4, Eb4, low C4
            val matchLostPcm = generateToneSequence(
                listOf(
                    415.30 to 190,
                    392.00 to 190,
                    349.23 to 210,
                    311.13 to 230,
                    261.63 to 430
                ),
                SAMPLE_RATE,
                volume = 0.70
            )
            matchLostTrack = buildStaticTrack(matchLostPcm, SAMPLE_RATE)

            isInitialized = true
        } catch (_: Exception) {
            // Audio initialization safe fallback for test / headless environments
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
        } catch (_: Exception) {
            // Playback error handling
        }
    }

    /**
     * Plays a short, clean electronic placement beep (50–150 ms) for valid moves.
     */
    fun playPlacementBeep(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        play(beepTrack, isSoundEnabled)
    }

    /**
     * Plays a cheerful electronic victory jingle (~0.7s) on an ordinary round win.
     */
    fun playRoundWon(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopAllSounds()
        play(roundWonTrack, isSoundEnabled)
    }

    /**
     * Plays a descending electronic failure sequence (~0.8s) on an ordinary round loss.
     */
    fun playRoundLost(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopAllSounds()
        play(roundLostTrack, isSoundEnabled)
    }

    /**
     * Plays the whole-game victory fanfare and triggers celebratory vibration.
     * Takes precedence over ordinary round win.
     */
    fun playMatchWon(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isSoundEnabled) {
            stopAllSounds()
            play(matchWonTrack, isSoundEnabled)
        }
        if (isVibrationEnabled) {
            vibratePattern(CELEBRATORY_VIBRATION_PATTERN)
        }
    }

    /**
     * Plays the whole-game failure instrumental and triggers defeat vibration.
     * Takes precedence over ordinary round loss.
     */
    fun playMatchLost(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isSoundEnabled) {
            stopAllSounds()
            play(matchLostTrack, isSoundEnabled)
        }
        if (isVibrationEnabled) {
            vibratePattern(DEFEAT_VIBRATION_PATTERN)
        }
    }

    /**
     * Immediately pauses and mutes all Tic Tac Toe sound effects.
     */
    fun stopAllSounds() {
        listOf(beepTrack, roundWonTrack, roundLostTrack, matchWonTrack, matchLostTrack).forEach { track ->
            try {
                if (track?.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.playbackHeadPosition = 0
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Immediately cancels all sound and vibration effects.
     */
    fun stopAll() {
        stopAllSounds()
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun vibratePattern(timings: LongArray) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        } catch (_: Exception) {
            // Safe vibration fallback
        }
    }

    /**
     * Releases audio hardware resources to prevent memory or track leaks.
     */
    fun release() {
        stopAll()
        listOf(beepTrack, roundWonTrack, roundLostTrack, matchWonTrack, matchLostTrack).forEach { track ->
            try {
                track?.release()
            } catch (_: Exception) {}
        }
        beepTrack = null
        roundWonTrack = null
        roundLostTrack = null
        matchWonTrack = null
        matchLostTrack = null
        isInitialized = false
    }
}
