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

    private val movePcm by lazy { generateTone(freqStart = 520.0, freqEnd = 660.0, durationMs = 22, amplitude = 0.14) }
    private val selectPcm by lazy { generateTone(freqStart = 760.0, freqEnd = 1040.0, durationMs = 45, amplitude = 0.18) }
    private val backPcm by lazy { generateTone(freqStart = 540.0, freqEnd = 370.0, durationMs = 36, amplitude = 0.15) }
    private val menuPcm by lazy { generateTone(freqStart = 640.0, freqEnd = 880.0, durationMs = 38, amplitude = 0.16) }

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
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
            val sample = (sin(phase) * amplitude * env * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}
