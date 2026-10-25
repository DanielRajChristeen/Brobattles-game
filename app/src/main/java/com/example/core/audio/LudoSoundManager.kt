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
 * Manages electronic audio synthesis and haptic feedback for Ludo.
 * Synthesizes all sounds in-memory using 16-bit PCM AudioTrack.
 */
class LudoSoundManager(private val context: Context) {

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

    private var diceRollTrack: AudioTrack? = null
    private var moveTrack: AudioTrack? = null
    private var captureTrack: AudioTrack? = null
    private var homeFinishTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null

    private var isInitialized = false

    companion object {
        const val SAMPLE_RATE = 22050

        val CELEBRATORY_VIBRATION_PATTERN = longArrayOf(0, 80, 60, 90, 60, 220)
        val CAPTURE_VIBRATION_PATTERN = longArrayOf(0, 50, 40, 100)

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
            // Dice roll rattling sound
            val rollNotes = listOf(
                Pair(520.0, 30),
                Pair(620.0, 30),
                Pair(480.0, 35),
                Pair(720.0, 40),
                Pair(550.0, 50)
            )
            val rollBytes = generateToneSequence(rollNotes, volume = 0.5)
            diceRollTrack = createAudioTrack(rollBytes)

            // Token step tap sound
            val moveNotes = listOf(
                Pair(440.0, 25),
                Pair(660.0, 45)
            )
            val moveBytes = generateToneSequence(moveNotes, volume = 0.55)
            moveTrack = createAudioTrack(moveBytes)

            // Capture sound: bouncy upbeat boing
            val captureNotes = listOf(
                Pair(280.0, 40),
                Pair(420.0, 50),
                Pair(630.0, 80),
                Pair(350.0, 60)
            )
            val captureBytes = generateToneSequence(captureNotes, volume = 0.7)
            captureTrack = createAudioTrack(captureBytes)

            // Home finish sound: bright celebratory chime
            val finishNotes = listOf(
                Pair(523.25, 60), // C5
                Pair(659.25, 60), // E5
                Pair(783.99, 90), // G5
                Pair(1046.50, 140) // C6
            )
            val finishBytes = generateToneSequence(finishNotes, volume = 0.7)
            homeFinishTrack = createAudioTrack(finishBytes)

            // Whole match victory fanfare
            val victoryNotes = listOf(
                Pair(523.25, 100), // C5
                Pair(523.25, 100), // C5
                Pair(523.25, 100), // C5
                Pair(659.25, 200), // E5
                Pair(783.99, 150), // G5
                Pair(1046.50, 350) // C6
            )
            val victoryBytes = generateToneSequence(victoryNotes, volume = 0.8)
            victoryTrack = createAudioTrack(victoryBytes)

            isInitialized = true
        } catch (_: Exception) {
            // AudioTrack creation failure fallback
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

    fun playDiceRoll(soundEnabled: Boolean = true) {
        playTrack(diceRollTrack, soundEnabled)
    }

    fun playTokenMove(soundEnabled: Boolean = true) {
        playTrack(moveTrack, soundEnabled)
    }

    fun playCapture(soundEnabled: Boolean = true) {
        playTrack(captureTrack, soundEnabled)
    }

    fun playHomeFinish(soundEnabled: Boolean = true) {
        playTrack(homeFinishTrack, soundEnabled)
    }

    fun playVictory(soundEnabled: Boolean = true) {
        playTrack(victoryTrack, soundEnabled)
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
                vibrator?.vibrate(VibrationEffect.createWaveform(CAPTURE_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(CAPTURE_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateVictory(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(CELEBRATORY_VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(CELEBRATORY_VIBRATION_PATTERN, -1)
            }
        } catch (_: Exception) {}
    }

    fun stopAllSounds() {
        listOf(diceRollTrack, moveTrack, captureTrack, homeFinishTrack, victoryTrack).forEach {
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
        listOf(diceRollTrack, moveTrack, captureTrack, homeFinishTrack, victoryTrack).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
        diceRollTrack = null
        moveTrack = null
        captureTrack = null
        homeFinishTrack = null
        victoryTrack = null
        isInitialized = false
    }
}
