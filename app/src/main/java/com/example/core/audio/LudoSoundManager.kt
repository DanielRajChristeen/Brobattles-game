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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance audio synthesis and haptic feedback manager for Ludo.
 *
 * Preloads short, punchy sound effects into Android SoundPool with dedicated channels
 * for zero-latency, non-blocking playback synchronized with step-by-step soldier hopping.
 *
 * Includes fallback AudioTrack synthesis if SoundPool is unavailable.
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

    private var soundPool: SoundPool? = null
    private var rollSoundId = 0
    private var hopSoundId = 0
    private var captureSoundId = 0
    private var finishSoundId = 0
    private var victorySoundId = 0

    // Round-robin pool of AudioTracks for low-latency soldier hopping fallback
    private val hopTrackPool = ArrayList<AudioTrack>()
    private var hopTrackIndex = 0
    private var diceRollTrack: AudioTrack? = null
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
                val attack = (sampleRate * 0.005).toInt().coerceAtLeast(1)
                val release = (sampleRate * 0.020).toInt().coerceAtLeast(1)
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val envelope = when {
                        i < attack -> i.toDouble() / attack
                        i > count - release -> (count - i).toDouble() / release
                        else -> 1.0
                    }
                    val wave = (sin(2.0 * PI * freq * t) * 0.85 + sin(3.0 * PI * freq * t) * 0.15) * envelope * volume * Short.MAX_VALUE
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
    }

    init {
        initializeAudio()
    }

    @Synchronized
    private fun initializeAudio() {
        if (isInitialized) return
        try {
            // 1. Soldier hop sound: short, crisp, responsive pop
            val hopNotes = listOf(
                Pair(540.0, 20),
                Pair(750.0, 35)
            )
            val hopPcm = generateToneSequence(hopNotes, volume = 0.6)

            // 2. Dice roll sound
            val rollNotes = listOf(
                Pair(520.0, 30),
                Pair(620.0, 30),
                Pair(480.0, 35),
                Pair(720.0, 40),
                Pair(550.0, 50)
            )
            val rollPcm = generateToneSequence(rollNotes, volume = 0.5)

            // 3. Capture sound
            val captureNotes = listOf(
                Pair(280.0, 40),
                Pair(420.0, 50),
                Pair(630.0, 80),
                Pair(350.0, 60)
            )
            val capturePcm = generateToneSequence(captureNotes, volume = 0.7)

            // 4. Home finish sound
            val finishNotes = listOf(
                Pair(523.25, 60),
                Pair(659.25, 60),
                Pair(783.99, 90),
                Pair(1046.50, 140)
            )
            val finishPcm = generateToneSequence(finishNotes, volume = 0.7)

            // 5. Match victory fanfare
            val victoryNotes = listOf(
                Pair(523.25, 100),
                Pair(523.25, 100),
                Pair(523.25, 100),
                Pair(659.25, 200),
                Pair(783.99, 150),
                Pair(1046.50, 350)
            )
            val victoryPcm = generateToneSequence(victoryNotes, volume = 0.8)

            // Try initializing SoundPool with preloaded WAV files for zero latency
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val pool = SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(attributes)
                    .build()

                fun writeAndLoad(fileName: String, pcm: ByteArray): Int {
                    val file = File(context.cacheDir, fileName)
                    FileOutputStream(file).use { it.write(pcmToWav(pcm)) }
                    return pool.load(file.absolutePath, 1)
                }

                hopSoundId = writeAndLoad("ludo_hop.wav", hopPcm)
                rollSoundId = writeAndLoad("ludo_roll.wav", rollPcm)
                captureSoundId = writeAndLoad("ludo_cap.wav", capturePcm)
                finishSoundId = writeAndLoad("ludo_fin.wav", finishPcm)
                victorySoundId = writeAndLoad("ludo_vic.wav", victoryPcm)

                soundPool = pool
            } catch (_: Exception) {
                soundPool = null
            }

            // AudioTrack Fallbacks
            repeat(4) {
                hopTrackPool.add(createAudioTrack(hopPcm))
            }
            diceRollTrack = createAudioTrack(rollPcm)
            captureTrack = createAudioTrack(capturePcm)
            homeFinishTrack = createAudioTrack(finishPcm)
            victoryTrack = createAudioTrack(victoryPcm)

            isInitialized = true
        } catch (_: Exception) {
            // Audio setup fallback
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

    fun playSoldierHop(soundEnabled: Boolean = true) {
        if (!soundEnabled) return
        val pool = soundPool
        if (pool != null && hopSoundId > 0) {
            try {
                val streamId = pool.play(hopSoundId, 0.75f, 0.75f, 1, 0, 1.0f)
                if (streamId != 0) return
            } catch (_: Exception) {}
        }
        // Fallback: round-robin audio track pool to avoid driver stop/reload stalls
        if (hopTrackPool.isNotEmpty()) {
            try {
                val track = hopTrackPool[hopTrackIndex % hopTrackPool.size]
                hopTrackIndex++
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                    track.reloadStaticData()
                }
                track.play()
            } catch (_: Exception) {}
        }
    }

    fun playTokenMove(soundEnabled: Boolean = true) {
        playSoldierHop(soundEnabled)
    }

    fun playDiceRoll(soundEnabled: Boolean = true) {
        if (!soundEnabled) return
        val pool = soundPool
        if (pool != null && rollSoundId > 0) {
            try {
                val streamId = pool.play(rollSoundId, 0.7f, 0.7f, 1, 0, 1.0f)
                if (streamId != 0) return
            } catch (_: Exception) {}
        }
        playTrackFallback(diceRollTrack)
    }

    fun playCapture(soundEnabled: Boolean = true) {
        if (!soundEnabled) return
        val pool = soundPool
        if (pool != null && captureSoundId > 0) {
            try {
                val streamId = pool.play(captureSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
                if (streamId != 0) return
            } catch (_: Exception) {}
        }
        playTrackFallback(captureTrack)
    }

    fun playHomeFinish(soundEnabled: Boolean = true) {
        if (!soundEnabled) return
        val pool = soundPool
        if (pool != null && finishSoundId > 0) {
            try {
                val streamId = pool.play(finishSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
                if (streamId != 0) return
            } catch (_: Exception) {}
        }
        playTrackFallback(homeFinishTrack)
    }

    fun playVictory(soundEnabled: Boolean = true) {
        if (!soundEnabled) return
        val pool = soundPool
        if (pool != null && victorySoundId > 0) {
            try {
                val streamId = pool.play(victorySoundId, 0.9f, 0.9f, 1, 0, 1.0f)
                if (streamId != 0) return
            } catch (_: Exception) {}
        }
        playTrackFallback(victoryTrack)
    }

    private fun playTrackFallback(track: AudioTrack?) {
        if (track == null) return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
                track.reloadStaticData()
            }
            track.play()
        } catch (_: Exception) {}
    }

    fun vibrateHop(vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    fun vibrateMove(vibrationEnabled: Boolean = true) {
        vibrateHop(vibrationEnabled)
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
        try {
            soundPool?.autoPause()
        } catch (_: Exception) {}
        listOf(diceRollTrack, captureTrack, homeFinishTrack, victoryTrack).forEach {
            try {
                if (it?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                    it.reloadStaticData()
                }
            } catch (_: Exception) {}
        }
        hopTrackPool.forEach {
            try {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
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
        listOf(diceRollTrack, captureTrack, homeFinishTrack, victoryTrack).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
        hopTrackPool.forEach {
            try {
                it.release()
            } catch (_: Exception) {}
        }
        hopTrackPool.clear()
        diceRollTrack = null
        captureTrack = null
        homeFinishTrack = null
        victoryTrack = null
        isInitialized = false
    }
}
