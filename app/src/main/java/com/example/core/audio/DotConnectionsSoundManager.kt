package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.registry.GameMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dedicated sound effects and match outcome audio manager for Dot Connections.
 *
 * Provides:
 * - Ultra low-latency dot-connecting snap sound (~35 ms)
 * - Satisfying single box-completion chime (~240 ms)
 * - Celebratory double box-completion chime (~360 ms)
 * - Resonant victory fanfare for match wins (~1.1 s)
 * - Solemn defeat cadence for match losses (~1.1 s)
 * - Neutral harmonic chime for draws (~0.75 s)
 *
 * Audio waveforms are pre-rendered in-memory via 16-bit PCM and ingested into SoundPool
 * with static AudioTrack fallback, working completely offline with zero lag.
 */
class DotConnectionsSoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var connectSoundId = 0
    private var singleBoxSoundId = 0
    private var doubleBoxSoundId = 0

    // AudioTracks for match outcomes and fallbacks
    private var connectTrack: AudioTrack? = null
    private var singleBoxTrack: AudioTrack? = null
    private var doubleBoxTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null
    private var defeatTrack: AudioTrack? = null
    private var drawTrack: AudioTrack? = null

    private var isInitialized = false

    enum class DotOutcomeAudio {
        VICTORY,
        DEFEAT,
        DRAW,
        NONE
    }

    companion object {
        const val SAMPLE_RATE = 22050

        /**
         * Resolves the correct outcome audio based on game outcome, mode, and local player.
         *
         * - VS Bot: Player 1 wins -> VICTORY, Bot wins -> DEFEAT
         * - Local 1v1: Player 1 wins -> VICTORY, Player 2 wins -> VICTORY (always celebrate winner)
         * - Nearby: Winner device -> VICTORY, Loser device -> DEFEAT
         * - Draw -> DRAW
         */
        fun resolveOutcomeAudio(
            winner: DotPlayer?,
            isDraw: Boolean,
            mode: GameMode,
            localPlayer: DotPlayer
        ): DotOutcomeAudio {
            if (isDraw) return DotOutcomeAudio.DRAW
            if (winner == null) return DotOutcomeAudio.NONE
            return when (mode) {
                GameMode.SOLO -> if (winner == DotPlayer.PLAYER_1) DotOutcomeAudio.VICTORY else DotOutcomeAudio.DEFEAT
                GameMode.LOCAL_1V1 -> DotOutcomeAudio.VICTORY
                GameMode.BLUETOOTH_1V1 -> if (winner == localPlayer) DotOutcomeAudio.VICTORY else DotOutcomeAudio.DEFEAT
            }
        }

        /**
         * Synthesizes an ultra low-latency connecting sound (~35 ms).
         * A crisp dual-frequency click transient with steep exponential damping.
         */
        fun generateConnectSound(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 35
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val baseFreq = 950.0
            val dropFreq = 480.0
            val decayRate = 95.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = baseFreq + (dropFreq - baseFreq) * (i.toDouble() / totalSamples)
                val envelope = exp(-decayRate * t)
                val wave = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = wave.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }

            return pcmShortsToBytes(pcmShorts)
        }

        /**
         * Synthesizes a bright, satisfying chime when a single box is completed (~240 ms).
         * Two ascending bell tones (C5 -> G5) with resonant decay.
         */
        fun generateSingleBoxSound(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.75): ByteArray {
            val durationMs = 240
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val notes = listOf(
                0 to (523.25 to 90), // C5
                90 to (783.99 to 150) // G5
            )

            for ((startMs, pair) in notes) {
                val (freq, noteDurMs) = pair
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * (noteDurMs / 1000.0)).toInt()

                for (i in 0 until noteSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-22.0 * t)
                    // Fundamental + subtle 2nd harmonic for bell warmth
                    val sample = (sin(2.0 * PI * freq * t) * 0.75 + sin(2.0 * PI * freq * 2.0 * t) * 0.25) *
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
         * Synthesizes a distinct celebratory double-box completion cue (~360 ms).
         * Ascending major triad chime (C5 -> E5 -> C6) acknowledging the double completion
         * in a cohesive musical arc without harsh clipping.
         */
        fun generateDoubleBoxSound(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.75): ByteArray {
            val durationMs = 360
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val notes = listOf(
                0 to (523.25 to 80),   // C5
                80 to (659.25 to 80),  // E5
                160 to (1046.50 to 200) // C6
            )

            for ((startMs, pair) in notes) {
                val (freq, noteDurMs) = pair
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * (noteDurMs / 1000.0)).toInt()

                for (i in 0 until noteSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-18.0 * t)
                    val sample = (sin(2.0 * PI * freq * t) * 0.75 + sin(2.0 * PI * freq * 2.0 * t) * 0.25) *
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
         * Synthesizes a triumphant victory fanfare (~1.1 s).
         * G4 -> C5 -> E5 -> G5 with full acoustic sustain.
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
         * Synthesizes a solemn minor defeat cadence (~1.1 s).
         * F4 -> Eb4 -> D4 -> C4 descending.
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

        /**
         * Synthesizes a neutral harmonic chime for draws (~750 ms).
         * C5 -> G4 -> C5.
         */
        fun generateDrawChime(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 750
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            val notes = listOf(
                0 to (523.25 to 200),
                200 to (392.00 to 200),
                400 to (523.25 to 350)
            )

            for ((startMs, pair) in notes) {
                val (freq, noteDurMs) = pair
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * (noteDurMs / 1000.0)).toInt()

                for (i in 0 until noteSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val decay = if (startMs >= 400) 8.0 else 15.0
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

                val connectPcm = generateConnectSound()
                val singleBoxPcm = generateSingleBoxSound()
                val doubleBoxPcm = generateDoubleBoxSound()
                val victoryPcm = generateVictoryFanfare()
                val defeatPcm = generateDefeatCadence()
                val drawPcm = generateDrawChime()

                // Cache wav files for SoundPool ingestion
                val cacheDir = File(context.cacheDir, "audio_dot_connections").apply { mkdirs() }

                fun writeAndLoad(name: String, pcm: ByteArray): Int {
                    val file = File(cacheDir, name)
                    FileOutputStream(file).use { it.write(pcmToWav(pcm)) }
                    return soundPool?.load(file.absolutePath, 1) ?: 0
                }

                connectSoundId = writeAndLoad("connect.wav", connectPcm)
                singleBoxSoundId = writeAndLoad("single_box.wav", singleBoxPcm)
                doubleBoxSoundId = writeAndLoad("double_box.wav", doubleBoxPcm)

                // Static AudioTracks as resilient fallback & outcome channels
                connectTrack = createStaticTrack(connectPcm)
                singleBoxTrack = createStaticTrack(singleBoxPcm)
                doubleBoxTrack = createStaticTrack(doubleBoxPcm)
                victoryTrack = createStaticTrack(victoryPcm)
                defeatTrack = createStaticTrack(defeatPcm)
                drawTrack = createStaticTrack(drawPcm)

                isInitialized = true
            } catch (_: Exception) {
                // Graceful fallback
            }
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

    /**
     * Triggers the dot-connecting sound immediately with zero delay.
     */
    fun playConnect(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        try {
            if (connectSoundId != 0 && soundPool != null) {
                soundPool?.play(connectSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
            } else {
                connectTrack?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (_: Exception) {
            // Ignore playback error
        }
    }

    /**
     * Triggers the box-completion cue.
     * Uses the single box chime for 1 box, and the richer ascending triad chime for 2 boxes.
     */
    fun playBoxCompletion(boxCount: Int, isSoundEnabled: Boolean) {
        if (!isSoundEnabled || boxCount <= 0) return
        try {
            if (boxCount >= 2) {
                if (doubleBoxSoundId != 0 && soundPool != null) {
                    soundPool?.play(doubleBoxSoundId, 0.95f, 0.95f, 2, 0, 1.0f)
                } else {
                    doubleBoxTrack?.let {
                        it.stop()
                        it.reloadStaticData()
                        it.play()
                    }
                }
            } else {
                if (singleBoxSoundId != 0 && soundPool != null) {
                    soundPool?.play(singleBoxSoundId, 0.90f, 0.90f, 2, 0, 1.0f)
                } else {
                    singleBoxTrack?.let {
                        it.stop()
                        it.reloadStaticData()
                        it.play()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore playback error
        }
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

    fun playDraw(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        try {
            drawTrack?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (_: Exception) {}
    }

    fun playOutcome(outcome: DotOutcomeAudio, isSoundEnabled: Boolean) {
        when (outcome) {
            DotOutcomeAudio.VICTORY -> playVictory(isSoundEnabled)
            DotOutcomeAudio.DEFEAT -> playDefeat(isSoundEnabled)
            DotOutcomeAudio.DRAW -> playDraw(isSoundEnabled)
            DotOutcomeAudio.NONE -> {}
        }
    }

    fun stopAllSounds() {
        try {
            soundPool?.autoPause()
            connectTrack?.pause()
            singleBoxTrack?.pause()
            doubleBoxTrack?.pause()
            victoryTrack?.pause()
            defeatTrack?.pause()
            drawTrack?.pause()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            connectTrack?.release()
            singleBoxTrack?.release()
            doubleBoxTrack?.release()
            victoryTrack?.release()
            defeatTrack?.release()
            drawTrack?.release()
            connectTrack = null
            singleBoxTrack = null
            doubleBoxTrack = null
            victoryTrack = null
            defeatTrack = null
            drawTrack = null
        } catch (_: Exception) {}
    }
}
