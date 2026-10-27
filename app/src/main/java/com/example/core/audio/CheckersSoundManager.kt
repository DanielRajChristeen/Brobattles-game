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
 * Audio synthesis and haptic feedback manager for Checkers.
 *
 * Synthesizes crisp, punchy audio tones directly in-memory via 16-bit PCM AudioTrack:
 * - Coin change/move click
 * - Capture pop
 * - King promotion regal fanfare
 * - Match victory fanfare
 * - Defeat minor melody
 * - Draw neutral chime
 */
class CheckersSoundManager(private val context: Context) {

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

    private var coinMoveTrack: AudioTrack? = null
    private var captureTrack: AudioTrack? = null
    private var kingCrownedTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null
    private var defeatTrack: AudioTrack? = null
    private var drawTrack: AudioTrack? = null

    private var isInitialized = false

    companion object {
        const val SAMPLE_RATE = 22050

        val VICTORY_VIBRATION_PATTERN = longArrayOf(0, 80, 60, 90, 60, 220)
        val DEFEAT_VIBRATION_PATTERN = longArrayOf(0, 180, 80, 280)
        val KING_CROWN_VIBRATION_PATTERN = longArrayOf(0, 50, 40, 60, 40, 140)

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
    }

    init {
        initializeAudio()
    }

    @Synchronized
    private fun initializeAudio() {
        if (isInitialized) return
        try {
            // Coin move / tap: crisp wooden click
            val coinNotes = listOf(
                Pair(480.0, 20),
                Pair(640.0, 35)
            )
            coinMoveTrack = createAudioTrack(generateToneSequence(coinNotes, volume = 0.55))

            // Capture sound: bouncy punchy snap
            val captureNotes = listOf(
                Pair(320.0, 30),
                Pair(480.0, 40),
                Pair(760.0, 60)
            )
            captureTrack = createAudioTrack(generateToneSequence(captureNotes, volume = 0.7))

            // King promotion sound: regal ascending royal fanfare
            val kingNotes = listOf(
                Pair(523.25, 60),  // C5
                Pair(659.25, 60),  // E5
                Pair(783.99, 80),  // G5
                Pair(1046.50, 160) // C6
            )
            kingCrownedTrack = createAudioTrack(generateToneSequence(kingNotes, volume = 0.75))

            // Victory sound: triumphant major fanfare
            val victoryNotes = listOf(
                Pair(523.25, 80),  // C5
                Pair(523.25, 80),  // C5
                Pair(659.25, 120), // E5
                Pair(783.99, 140), // G5
                Pair(1046.50, 300) // C6
            )
            victoryTrack = createAudioTrack(generateToneSequence(victoryNotes, volume = 0.8))

            // Defeat sound: descending sorrowful minor notes
            val defeatNotes = listOf(
                Pair(440.0, 120), // A4
                Pair(392.0, 120), // G4
                Pair(349.23, 140), // F4
                Pair(329.63, 260)  // E4
            )
            defeatTrack = createAudioTrack(generateToneSequence(defeatNotes, volume = 0.7))

            // Draw sound: peaceful neutral two-tone
            val drawNotes = listOf(
                Pair(523.25, 100),
                Pair(659.25, 180)
            )
            drawTrack = createAudioTrack(generateToneSequence(drawNotes, volume = 0.6))

            isInitialized = true
        } catch (_: Exception) {
            // AudioTrack creation fallback
        }
    }

    private fun createAudioTrack(data: ByteArray): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack(
            attributes,
            format,
            data.size,
            AudioTrack.MODE_STATIC,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(data, 0, data.size)
        return track
    }

    private fun playTrack(track: AudioTrack?, soundEnabled: Boolean) {
        if (!soundEnabled || track == null) return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
                track.reloadStaticData()
            }
            track.play()
        } catch (_: Exception) {}
    }

    fun playCoinMove(soundEnabled: Boolean = true) {
        playTrack(coinMoveTrack, soundEnabled)
    }

    fun playCapture(soundEnabled: Boolean = true) {
        playTrack(captureTrack, soundEnabled)
    }

    fun playKingPromotion(soundEnabled: Boolean = true) {
        playTrack(kingCrownedTrack, soundEnabled)
    }

    fun playVictory(soundEnabled: Boolean = true) {
        playTrack(victoryTrack, soundEnabled)
    }

    fun playDefeat(soundEnabled: Boolean = true) {
        playTrack(defeatTrack, soundEnabled)
    }

    fun playDraw(soundEnabled: Boolean = true) {
        playTrack(drawTrack, soundEnabled)
    }

    fun vibrateMove(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (_: Exception) {}
    }

    fun vibrateCapture(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 40, 30, 80), -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateKingPromotion(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(KING_CROWN_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(KING_CROWN_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateVictory(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(VICTORY_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VICTORY_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateDefeat(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(DEFEAT_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DEFEAT_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    fun stopAllSounds() {
        listOf(coinMoveTrack, captureTrack, kingCrownedTrack, victoryTrack, defeatTrack, drawTrack).forEach {
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
        listOf(coinMoveTrack, captureTrack, kingCrownedTrack, victoryTrack, defeatTrack, drawTrack).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
        coinMoveTrack = null
        captureTrack = null
        kingCrownedTrack = null
        victoryTrack = null
        defeatTrack = null
        drawTrack = null
        isInitialized = false
    }
}
