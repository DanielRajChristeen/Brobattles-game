package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.example.core.engine.GameStatus
import com.example.core.engine.PlayerId
import com.example.core.registry.GameMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dedicated sound effects and match outcome audio manager for Snakes & Ladders.
 *
 * Provides:
 * - Realistic dice rolling / tumbling rattle sound (~800 ms)
 * - Light stick-figure running footstep hops (~35 ms)
 * - Distinct wooden ladder-climbing steps & creaks (~600 ms)
 * - Recognizable smooth descending snake slide (~650 ms)
 * - Short stick-figure celebratory winning animation sound (~450 ms)
 * - Full match Victory fanfare & Defeat theme
 *
 * Pre-rendered in-memory via 16-bit PCM and ingested into low-latency SoundPool
 * with static AudioTrack fallback, working completely offline.
 */
class SnakesAndLaddersSoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var rollSoundId = 0
    private var footstepSoundId = 0
    private var ladderSoundId = 0
    private var snakeSoundId = 0
    private var winningAnimSoundId = 0
    private var victorySoundId = 0
    private var defeatSoundId = 0

    // SoundPool active stream IDs for stopping/fading ongoing looping/streaming effects
    private var activeRollStreamId = 0
    private var activeLadderStreamId = 0
    private var activeSnakeStreamId = 0

    // AudioTrack fallbacks
    private var rollTrack: AudioTrack? = null
    private var footstepTrack: AudioTrack? = null
    private var ladderTrack: AudioTrack? = null
    private var snakeTrack: AudioTrack? = null
    private var winningAnimTrack: AudioTrack? = null
    private var victoryTrack: AudioTrack? = null
    private var defeatTrack: AudioTrack? = null

    private var isInitialized = false

    enum class SnakesOutcomeAudio {
        VICTORY,
        DEFEAT,
        NONE
    }

    companion object {
        const val SAMPLE_RATE = 22050

        /**
         * Synthesizes a realistic dice rattling and tumbling sound sequence (~800 ms).
         * Multi-tap wood/plastic hollow impacts with exponential damping.
         */
        fun generateDiceRoll(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.65): ByteArray {
            val durationMs = 800
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            // Impact timings (in ms) simulating the bounces of a rolling 3D die
            val tapMoments = listOf(
                0 to 480.0,
                65 to 620.0,
                130 to 450.0,
                195 to 720.0,
                260 to 520.0,
                325 to 660.0,
                390 to 480.0,
                460 to 580.0,
                540 to 460.0,
                630 to 520.0,
                720 to 440.0
            )

            for ((startMs, freq) in tapMoments) {
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val tapDurationSamples = (sampleRate * 0.045).toInt()
                for (i in 0 until tapDurationSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-75.0 * t)
                    val sample = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                    pcmShorts[sIdx] = (pcmShorts[sIdx] + sample).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                }
            }

            return shortsToBytes(pcmShorts)
        }

        /**
         * Synthesizes a light, crisp running footstep / hop tap (~35 ms).
         */
        fun generateFootstep(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.50): ByteArray {
            val durationMs = 35
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)
            val freq = 380.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-90.0 * t)
                val sample = sin(2.0 * PI * freq * t) * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = sample.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }

            return shortsToBytes(pcmShorts)
        }

        /**
         * Synthesizes distinct wooden ladder-climbing steps & creaks (~600 ms).
         * A rhythmic sequence of ascending resonant wooden steps.
         */
        fun generateLadderClimb(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 600
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            // Ascending wooden rung taps
            val rungs = listOf(
                0 to 300.0,
                95 to 360.0,
                190 to 420.0,
                285 to 490.0,
                380 to 580.0,
                480 to 680.0
            )

            for ((startMs, freq) in rungs) {
                val startSample = (sampleRate * (startMs / 1000.0)).toInt()
                val stepSamples = (sampleRate * 0.075).toInt()
                for (i in 0 until stepSamples) {
                    val sIdx = startSample + i
                    if (sIdx >= totalSamples) break
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-48.0 * t)
                    // Wooden harmonic overtone
                    val body = (sin(2.0 * PI * freq * t) * 0.75 + sin(4.0 * PI * freq * t) * 0.25)
                    val sample = body * envelope * volume * Short.MAX_VALUE
                    pcmShorts[sIdx] = (pcmShorts[sIdx] + sample).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                }
            }

            return shortsToBytes(pcmShorts)
        }

        /**
         * Synthesizes a recognizable smooth descending snake slide (~650 ms).
         * Descends smoothly in pitch from ~750 Hz down to ~220 Hz with subtle modulated resonance.
         */
        fun generateSnakeSlide(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.70): ByteArray {
            val durationMs = 650
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmShorts = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val progress = i.toDouble() / totalSamples
                val currentFreq = 750.0 - (530.0 * progress) // 750Hz down to 220Hz
                val t = i.toDouble() / sampleRate

                // Soft bell-like envelope with smooth fade-in and gentle release
                val attack = 0.05
                val release = 0.20
                val envelope = when {
                    progress < attack -> progress / attack
                    progress > 1.0 - release -> (1.0 - progress) / release
                    else -> 1.0
                }

                val vibrato = sin(2.0 * PI * 8.0 * t) * 12.0
                val wave = sin(2.0 * PI * (currentFreq + vibrato) * t)
                val sample = wave * envelope * volume * Short.MAX_VALUE
                pcmShorts[i] = sample.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
            }

            return shortsToBytes(pcmShorts)
        }

        /**
         * Synthesizes a short celebratory sound for the stick figure's winning animation (~450 ms).
         */
        fun generateStickFigureWinning(sampleRate: Int = SAMPLE_RATE, volume: Double = 0.75): ByteArray {
            val notes = listOf(
                523.25 to 80,  // C5
                659.25 to 80,  // E5
                783.99 to 100, // G5
                1046.50 to 190 // C6
            )
            return generateToneSequence(notes, sampleRate, volume)
        }

        /**
         * Synthesizes 16-bit PCM mono musical note sequences.
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
            return shortsToBytes(pcmShorts)
        }

        private fun shortsToBytes(pcmShorts: ShortArray): ByteArray {
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

        /**
         * Resolves the match outcome audio according to game mode and local player identity:
         * - VS Bot: Human win -> Victory; Bot win -> Defeat
         * - Local 1v1: Shared device celebrates the winner with Victory theme (never Defeat)
         * - Nearby: Winner device -> Victory; Loser device -> Defeat
         */
        fun resolveOutcomeAudio(
            status: GameStatus,
            mode: GameMode,
            localPlayer: PlayerId
        ): SnakesOutcomeAudio {
            val winner = (status as? GameStatus.MatchWon)?.matchWinner ?: return SnakesOutcomeAudio.NONE
            return when (mode) {
                GameMode.SOLO -> {
                    if (winner == PlayerId.PLAYER_1) SnakesOutcomeAudio.VICTORY else SnakesOutcomeAudio.DEFEAT
                }
                GameMode.LOCAL_1V1 -> {
                    SnakesOutcomeAudio.VICTORY
                }
                GameMode.BLUETOOTH_1V1 -> {
                    if (winner == localPlayer) SnakesOutcomeAudio.VICTORY else SnakesOutcomeAudio.DEFEAT
                }
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
            val rollPcm = generateDiceRoll()
            val footstepPcm = generateFootstep()
            val ladderPcm = generateLadderClimb()
            val snakePcm = generateSnakeSlide()
            val winningAnimPcm = generateStickFigureWinning()

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

            val victoryPcm = generateToneSequence(victoryNotes, volume = 0.75)
            val defeatPcm = generateToneSequence(defeatNotes, volume = 0.75)

            // AudioTrack Fallbacks
            rollTrack = buildStaticTrack(rollPcm, SAMPLE_RATE)
            footstepTrack = buildStaticTrack(footstepPcm, SAMPLE_RATE)
            ladderTrack = buildStaticTrack(ladderPcm, SAMPLE_RATE)
            snakeTrack = buildStaticTrack(snakePcm, SAMPLE_RATE)
            winningAnimTrack = buildStaticTrack(winningAnimPcm, SAMPLE_RATE)
            victoryTrack = buildStaticTrack(victoryPcm, SAMPLE_RATE)
            defeatTrack = buildStaticTrack(defeatPcm, SAMPLE_RATE)

            // SoundPool for low-latency playback
            try {
                val poolAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(poolAttributes)
                    .build()

                fun writeAndLoad(name: String, pcm: ByteArray): Int {
                    val file = File(context.cacheDir, name)
                    FileOutputStream(file).use { it.write(pcmToWav(pcm)) }
                    return pool.load(file.absolutePath, 1)
                }

                rollSoundId = writeAndLoad("snakes_dice_roll.wav", rollPcm)
                footstepSoundId = writeAndLoad("snakes_footstep.wav", footstepPcm)
                ladderSoundId = writeAndLoad("snakes_ladder.wav", ladderPcm)
                snakeSoundId = writeAndLoad("snakes_snake.wav", snakePcm)
                winningAnimSoundId = writeAndLoad("snakes_win_anim.wav", winningAnimPcm)
                victorySoundId = writeAndLoad("snakes_vic.wav", victoryPcm)
                defeatSoundId = writeAndLoad("snakes_def.wav", defeatPcm)

                soundPool = pool
            } catch (_: Exception) {
                soundPool = null
            }

            isInitialized = true
        } catch (_: Exception) {
            // JVM / Headless safe fallback
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

    private fun playTrack(track: AudioTrack?, isSoundEnabled: Boolean) {
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
     * Plays the dice rolling sound when the 3D dice begins rolling.
     */
    fun playDiceRoll(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopDiceRoll()
        val pool = soundPool
        if (pool != null && rollSoundId > 0) {
            try {
                activeRollStreamId = pool.play(rollSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(rollTrack, isSoundEnabled)
    }

    /**
     * Stops or fades the dice roll sound immediately when the dice animation finishes.
     */
    fun stopDiceRoll() {
        val pool = soundPool
        if (pool != null && activeRollStreamId != 0) {
            try {
                pool.stop(activeRollStreamId)
            } catch (_: Exception) {}
            activeRollStreamId = 0
        }
        try {
            if (rollTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                rollTrack?.stop()
                rollTrack?.reloadStaticData()
            }
        } catch (_: Exception) {}
    }

    /**
     * Plays a crisp stick-figure running footstep hop sound.
     */
    fun playFootstep(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && footstepSoundId > 0) {
            try {
                pool.play(footstepSoundId, 0.55f, 0.55f, 1, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(footstepTrack, isSoundEnabled)
    }

    /**
     * Plays the wooden ladder-climbing sound when a ladder climb begins.
     */
    fun playLadderClimb(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopLadderClimb()
        val pool = soundPool
        if (pool != null && ladderSoundId > 0) {
            try {
                activeLadderStreamId = pool.play(ladderSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(ladderTrack, isSoundEnabled)
    }

    /**
     * Stops the ladder climbing sound when the climb animation finishes.
     */
    fun stopLadderClimb() {
        val pool = soundPool
        if (pool != null && activeLadderStreamId != 0) {
            try {
                pool.stop(activeLadderStreamId)
            } catch (_: Exception) {}
            activeLadderStreamId = 0
        }
        try {
            if (ladderTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                ladderTrack?.stop()
                ladderTrack?.reloadStaticData()
            }
        } catch (_: Exception) {}
    }

    /**
     * Plays the snake sliding sound when sliding down a snake begins.
     */
    fun playSnakeSlide(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopSnakeSlide()
        val pool = soundPool
        if (pool != null && snakeSoundId > 0) {
            try {
                activeSnakeStreamId = pool.play(snakeSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(snakeTrack, isSoundEnabled)
    }

    /**
     * Stops the snake slide sound when sliding finishes.
     */
    fun stopSnakeSlide() {
        val pool = soundPool
        if (pool != null && activeSnakeStreamId != 0) {
            try {
                pool.stop(activeSnakeStreamId)
            } catch (_: Exception) {}
            activeSnakeStreamId = 0
        }
        try {
            if (snakeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                snakeTrack?.stop()
                snakeTrack?.reloadStaticData()
            }
        } catch (_: Exception) {}
    }

    /**
     * Plays the short stick-figure celebratory winning animation sound.
     */
    fun playStickFigureWinning(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        val pool = soundPool
        if (pool != null && winningAnimSoundId > 0) {
            try {
                pool.play(winningAnimSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(winningAnimTrack, isSoundEnabled)
    }

    /**
     * Plays the match Victory theme.
     */
    fun playVictory(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopAllSounds()
        val pool = soundPool
        if (pool != null && victorySoundId > 0) {
            try {
                pool.play(victorySoundId, 0.9f, 0.9f, 2, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(victoryTrack, isSoundEnabled)
    }

    /**
     * Plays the match Defeat theme.
     */
    fun playDefeat(isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        stopAllSounds()
        val pool = soundPool
        if (pool != null && defeatSoundId > 0) {
            try {
                pool.play(defeatSoundId, 0.9f, 0.9f, 2, 0, 1.0f)
                return
            } catch (_: Exception) {}
        }
        playTrack(defeatTrack, isSoundEnabled)
    }

    fun stopAllSounds() {
        stopDiceRoll()
        stopLadderClimb()
        stopSnakeSlide()
        listOf(winningAnimTrack, victoryTrack, defeatTrack).forEach {
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
        rollSoundId = 0
        footstepSoundId = 0
        ladderSoundId = 0
        snakeSoundId = 0
        winningAnimSoundId = 0
        victorySoundId = 0
        defeatSoundId = 0

        listOf(rollTrack, footstepTrack, ladderTrack, snakeTrack, winningAnimTrack, victoryTrack, defeatTrack).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
        rollTrack = null
        footstepTrack = null
        ladderTrack = null
        snakeTrack = null
        winningAnimTrack = null
        victoryTrack = null
        defeatTrack = null
        isInitialized = false
    }
}
