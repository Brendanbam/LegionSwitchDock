package com.legion.switchdock.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.legion.switchdock.data.DockPreferences
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

object SwitchAudioFeedback {
    private val executor = Executors.newSingleThreadExecutor()
    private const val SAMPLE_RATE = 44100

    // Short Switch-style "tick" when moving between tiles, brighter chirps for select / menu, a falling tone for back.
    // Long and loud enough to hear in glasses speakers and TVs (the old 22 ms move tick was nearly inaudible).
    private val movePcm by lazy { generateTone(freqStart = 880.0, freqEnd = 1180.0, durationMs = 55, amplitude = 0.32) }
    private val selectPcm by lazy { generateTone(freqStart = 760.0, freqEnd = 1240.0, durationMs = 80, amplitude = 0.36) }
    private val backPcm by lazy { generateTone(freqStart = 620.0, freqEnd = 380.0, durationMs = 70, amplitude = 0.32) }
    private val menuPcm by lazy { generateTone(freqStart = 660.0, freqEnd = 990.0, durationMs = 70, amplitude = 0.34) }

    fun playMove(context: Context) = playIfEnabled(context, movePcm)
    fun playSelect(context: Context) = playIfEnabled(context, selectPcm)
    fun playBack(context: Context) = playIfEnabled(context, backPcm)
    fun playMenu(context: Context) = playIfEnabled(context, menuPcm)

    private fun playIfEnabled(context: Context, pcm: ShortArray) {
        if (!DockPreferences(context).uiSoundsEnabled) return
        executor.execute {
            runCatching {
                val byteCount = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            // Game audio follows the media volume, so a muted / silent-mode tablet still plays hub sounds
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
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 15L)
                track.stop()
                track.release()
            }
        }
    }

    private fun generateTone(
        freqStart: Double,
        freqEnd: Double,
        durationMs: Int,
        amplitude: Double
    ): ShortArray {
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(sampleCount)
        var phase = 0.0
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleCount
            val freq = freqStart + (freqEnd - freqStart) * t
            // Smooth attack and exponential decay envelope
            val attack = (i.toDouble() / (sampleCount * 0.12)).coerceAtMost(1.0)
            val decay = (1.0 - t) * (1.0 - t)
            val env = attack * decay
            phase += 2.0 * PI * freq / SAMPLE_RATE
            // A little second harmonic makes it read as a "tick" rather than a pure beep
            val sample = ((sin(phase) + 0.35 * sin(2 * phase)) / 1.35 * amplitude * env * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}
