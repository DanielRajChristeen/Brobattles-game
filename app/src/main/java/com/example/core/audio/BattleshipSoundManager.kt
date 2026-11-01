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
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.registry.GameMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dedicated sound and haptic manager for Battleship.
 *
 * Provides:
 * - Ship placement mechanical click (~40 ms)
 * - Ship rotation ratchet sound (~60 ms)
 * - Missile launch whoosh / whistle (~180 ms)
 * - Hit explosion blast (~320 ms)
 * - Miss water splash (~220 ms)
 * - Heavy ship sunk detonation (~550 ms)
 * - Sonar ping handover cue (~350 ms)
 * - Victory fanfare (~1.1 s)
 * - Defeat cadence (~1.1 s)
 *
 * Pre-rendered in-memory via 16-bit PCM and ingested into low-latency SoundPool
 * with static AudioTrack fallback.
 */
class BattleshipSoundManager(private val context: Context) {

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
    private var placeSoundId = 0
    private var rotateSoundId = 0
    private var launchSoundId = 0
    private var hitSoundId = 0
    private var missSoundId = 0
    private var sunkSoundId = 0
    private var sonarSoundId = 0

    // AudioTracks for fallbacks and long musical outcome themes
    private var placeTrack: AudioTrack? = null
    private var rotateTrack: AudioTrack? = null
    private var launchTrack: AudioTrack? = null
    private var hitTrack: AudioTrack? = null
    private var missTrack: AudioTrack? = null
    private var sunkTrack: AudioTrack? = null
    private var sonarTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null
    private var defeatTrack: AudioTrack? = null

    private var isInitialized = false

    enum class BattleshipOutcomeAudio {
        VICTORY,
        DEFEAT,
        NONE
    }

    companion object {
        const val SAMPLE_RATE = 22050

        fun resolveOutcomeAudio(
            winner: BattleshipPlayer?,
            mode: GameMode,
            localPlayer: BattleshipPlayer
        ): BattleshipOutcomeAudio {
            if (winner == null) return BattleshipOutcomeAudio.NONE
            return when (mode) {
                GameMode.SOLO -> if (winner == BattleshipPlayer.PLAYER_1) BattleshipOutcomeAudio.VICTORY else BattleshipOutcomeAudio.DEFEAT
                GameMode.LOCAL_1V1 -> BattleshipOutcomeAudio.VICTORY // Always celebrate winner in pass-the-phone
                GameMode.BLUETOOTH_1V1 -> if (winner == localPlayer) BattleshipOutcomeAudio.VICTORY else BattleshipOutcomeAudio.DEFEAT
            }
        }

        /**
         * Crisp mechanical click for placing a ship on the deployment grid (~40 ms).
         */
        fun generatePlacementClick(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 40
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 1200.0 - 600.0 * (i.toDouble() / totalSamples)
                val envelope = exp(-80.0 * t)
                val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Quick mechanical ratchet sound for rotating ship orientation (~60 ms).
         */
        fun generateRotationRatchet(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 60
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val clicks = listOf(0 to 800.0, 20 to 1100.0, 40 to 1400.0)
            for ((startMs, freq) in clicks) {
                val startIdx = (sampleRate * (startMs / 1000.0)).toInt()
                val clickSamples = (sampleRate * 0.015).toInt()
                for (i in 0 until clickSamples) {
                    val idx = startIdx + i
                    if (idx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-120.0 * t)
                    val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                    pcmShorts[idx] = (pcmShorts[idx] + wave).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                }
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Missile launch whoosh / whistle (~180 ms).
         */
        fun generateMissileLaunch(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.65): ByteArray {
            val durationMs = 180
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                // Ascending whistle 400Hz to 1100Hz with noise shimmer
                val freq = 400.0 + 700.0 * (i.toDouble() / totalSamples)
                val envelope = if (i < totalSamples / 3) {
                    (i.toDouble() / (totalSamples / 3))
                } else {
                    exp(-15.0 * (t - 0.06))
                }
                val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Punchy explosion blast on hit (~320 ms).
         */
        fun generateHitExplosion(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.85): ByteArray {
            val durationMs = 320
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            var seed = 12345
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                // Low rumbling bass + filtered noise
                val bass = sin(2.0 * PI * 110.0 * t) * exp(-18.0 * t)
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val noise = ((seed.toDouble() / 0x7fffffff) * 2.0 - 1.0) * exp(-24.0 * t)
                val wave = (bass * 0.65 + noise * 0.35) * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Water splash sound on miss (~220 ms).
         */
        fun generateWaterSplash(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.65): ByteArray {
            val durationMs = 220
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                // Descending water bloop/plop + gentle splash
                val freq = 650.0 - 320.0 * (i.toDouble() / totalSamples)
                val envelope = exp(-20.0 * t)
                val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Heavy deep naval detonation when a ship is sunk (~550 ms).
         */
        fun generateShipSunkDetonation(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.90): ByteArray {
            val durationMs = 550
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            var seed = 98765
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val subBass = sin(2.0 * PI * 65.0 * t) * exp(-8.0 * t)
                val midBass = sin(2.0 * PI * 130.0 * t) * exp(-14.0 * t)
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val noise = ((seed.toDouble() / 0x7fffffff) * 2.0 - 1.0) * exp(-16.0 * t)

                val wave = (subBass * 0.50 + midBass * 0.30 + noise * 0.20) * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Sonar ping for radar / handover (~350 ms).
         */
        fun generateSonarPing(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 350
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val freq = 1760.0 // A6 pure sonar ping
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-12.0 * t)
                val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Triumphant victory fanfare (~1.1 s).
         */
        fun generateVictoryFanfare(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.75): ByteArray {
            val durationMs = 1100
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val notes = listOf(
                0 to (392.00 to 160),  // G4
                160 to (523.25 to 160), // C5
                320 to (659.25 to 160), // E5
                480 to (783.99 to 620)  // G5
            )

            for ((startMs, pair) in notes) {
                val (freq, noteDurMs) = pair
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * (noteDurMs / 1000.0)).toInt()

                for (i in 0 until noteSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val decay = if (freq >= 780.0) 4.5 else 14.0
                    val envelope = exp(-decay * t)
                    val sample = (sin(2.0 * PI * freq * t) * 0.70 + sin(2.0 * PI * freq * 2.0 * t) * 0.30) *
                        envelope * volume * Short.MAX_VALUE
                    pcmShorts[sIdx] = (pcmShorts[sIdx] + sample).coerceIn(
                        Short.MIN_VALUE.toDouble(),
                        Short.MAX_VALUE.toDouble()
                    ).toInt().toShort()
                }
            }
            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Solemn defeat cadence (~1.1 s).
         */
        fun generateDefeatCadence(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 1100
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val notes = listOf(
                0 to (349.23 to 220),  // F4
                220 to (311.13 to 220), // Eb4
                440 to (293.66 to 220), // D4
                660 to (261.63 to 440)  // C4
            )

            for ((startMs, pair) in notes) {
                val (freq, noteDurMs) = pair
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * (noteDurMs / 1000.0)).toInt()

                for (i in 0 until noteSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val decay = if (startMs >= 660) 6.0 else 12.0
                    val envelope = exp(-decay * t)
                    val sample = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                    pcmShorts[sIdx] = (pcmShorts[sIdx] + sample).coerceIn(
                        Short.MIN_VALUE.toDouble(),
                        Short.MAX_VALUE.toDouble()
                    ).toInt().toShort()
                }
            }
            return pcmShortsToBytes(pcmShorts)
        }

        private fun pcmShortsToBytes(shorts: ShortArray): ByteArray {
            val bytes = ByteArray(shorts.size * 2)
            for (i in shorts.indices) {
                val s = shorts[i].toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        fun pcmToWav(pcmData: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
            val totalAudioLen = pcmData.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * 2

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1
            header[21] = 0
            header[22] = 1
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2
            header[33] = 0
            header[34] = 16
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            return header + pcmData
        }
    }

    init {
        initAudioAsync()
    }

    private fun initAudioAsync() {
        Thread {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                soundPool = SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(attrs)
                    .build()

                val placePcm = generatePlacementClick()
                val rotatePcm = generateRotationRatchet()
                val launchPcm = generateMissileLaunch()
                val hitPcm = generateHitExplosion()
                val missPcm = generateWaterSplash()
                val sunkPcm = generateShipSunkDetonation()
                val sonarPcm = generateSonarPing()
                val victoryPcm = generateVictoryFanfare()
                val defeatPcm = generateDefeatCadence()

                val cacheDir = File(context.cacheDir, "audio_battleship").apply { mkdirs() }

                fun writeAndLoad(name: String, pcm: ByteArray): Int {
                    val file = File(cacheDir, name)
                    FileOutputStream(file).use { it.write(pcmToWav(pcm)) }
                    return soundPool?.load(file.absolutePath, 1) ?: 0
                }

                placeSoundId = writeAndLoad("place.wav", placePcm)
                rotateSoundId = writeAndLoad("rotate.wav", rotatePcm)
                launchSoundId = writeAndLoad("launch.wav", launchPcm)
                hitSoundId = writeAndLoad("hit.wav", hitPcm)
                missSoundId = writeAndLoad("miss.wav", missPcm)
                sunkSoundId = writeAndLoad("sunk.wav", sunkPcm)
                sonarSoundId = writeAndLoad("sonar.wav", sonarPcm)

                placeTrack = createStaticTrack(placePcm)
                rotateTrack = createStaticTrack(rotatePcm)
                launchTrack = createStaticTrack(launchPcm)
                hitTrack = createStaticTrack(hitPcm)
                missTrack = createStaticTrack(missPcm)
                sunkTrack = createStaticTrack(sunkPcm)
                sonarTrack = createStaticTrack(sonarPcm)
                victoryTrack = createStaticTrack(victoryPcm)
                defeatTrack = createStaticTrack(defeatPcm)

                isInitialized = true
            } catch (_: Exception) {}
        }.start()
    }

    private fun createStaticTrack(pcmData: ByteArray): AudioTrack? {
        return try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmData.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcmData, 0, pcmData.size)
            track
        } catch (_: Exception) {
            null
        }
    }

    fun playPlacement(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibrateTap(30)
        }
        if (!isSoundEnabled) return
        try {
            if (placeSoundId != 0 && soundPool != null) {
                soundPool?.play(placeSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
            } else {
                placeTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playRotation(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibrateTap(25)
        }
        if (!isSoundEnabled) return
        try {
            if (rotateSoundId != 0 && soundPool != null) {
                soundPool?.play(rotateSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
            } else {
                rotateTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playLaunch(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        try {
            if (launchSoundId != 0 && soundPool != null) {
                soundPool?.play(launchSoundId, 0.75f, 0.75f, 1, 0, 1.0f)
            } else {
                launchTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playHit(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibratePattern(longArrayOf(0, 100, 50, 150))
        }
        if (!isSoundEnabled) return
        try {
            if (hitSoundId != 0 && soundPool != null) {
                soundPool?.play(hitSoundId, 0.95f, 0.95f, 2, 0, 1.0f)
            } else {
                hitTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playMiss(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibrateTap(35)
        }
        if (!isSoundEnabled) return
        try {
            if (missSoundId != 0 && soundPool != null) {
                soundPool?.play(missSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
            } else {
                missTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playShipSunk(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibratePattern(longArrayOf(0, 150, 60, 200, 60, 250))
        }
        if (!isSoundEnabled) return
        try {
            if (sunkSoundId != 0 && soundPool != null) {
                soundPool?.play(sunkSoundId, 1.0f, 1.0f, 3, 0, 1.0f)
            } else {
                sunkTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playSonarPing(isSoundEnabled: Boolean, isVibrationEnabled: Boolean) {
        if (isVibrationEnabled) {
            vibrateTap(40)
        }
        if (!isSoundEnabled) return
        try {
            if (sonarSoundId != 0 && soundPool != null) {
                soundPool?.play(sonarSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
            } else {
                sonarTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun playVictory(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        try {
            victoryTrack?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (_: Exception) {}
    }

    fun playDefeat(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        try {
            defeatTrack?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (_: Exception) {}
    }

    fun playOutcome(outcome: BattleshipOutcomeAudio, isSoundEnabled: Boolean) {
        when (outcome) {
            BattleshipOutcomeAudio.VICTORY -> playVictory(isSoundEnabled)
            BattleshipOutcomeAudio.DEFEAT -> playDefeat(isSoundEnabled)
            BattleshipOutcomeAudio.NONE -> {}
        }
    }

    private fun vibrateTap(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    fun stopAllSounds() {
        try {
            soundPool?.autoPause()
            placeTrack?.pause()
            rotateTrack?.pause()
            launchTrack?.pause()
            hitTrack?.pause()
            missTrack?.pause()
            sunkTrack?.pause()
            sonarTrack?.pause()
            victoryTrack?.pause()
            defeatTrack?.pause()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            placeTrack?.release()
            rotateTrack?.release()
            launchTrack?.release()
            hitTrack?.release()
            missTrack?.release()
            sunkTrack?.release()
            sonarTrack?.release()
            victoryTrack?.release()
            defeatTrack?.release()
            placeTrack = null
            rotateTrack = null
            launchTrack = null
            hitTrack = null
            missTrack = null
            sunkTrack = null
            sonarTrack = null
            victoryTrack = null
            defeatTrack = null
        } catch (_: Exception) {}
    }
}
