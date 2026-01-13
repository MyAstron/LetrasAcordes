package com.letrasacordes.application.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

class TunerController {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentFrequency = 0.0
    private var job: Job? = null

    // Frecuencias Estándar (A440)
    companion object {
        const val FREQ_E2 = 82.41
        const val FREQ_A2 = 110.00
        const val FREQ_D3 = 146.83
        const val FREQ_G3 = 196.00
        const val FREQ_B3 = 246.94
        const val FREQ_E4 = 329.63
    }

    fun playNote(frequency: Double, scope: CoroutineScope) {
        // Si ya está sonando la misma nota, la detenemos (toggle)
        if (isPlaying && currentFrequency == frequency) {
            stop()
            return
        }
        
        // Si suena otra nota, detenemos la anterior y empezamos la nueva
        stop()
        
        currentFrequency = frequency
        isPlaying = true
        
        job = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            val buffer = ShortArray(bufferSize)
            val angularFrequency = 2.0 * Math.PI * frequency
            var phase = 0.0

            while (isActive && isPlaying) {
                for (i in buffer.indices) {
                    buffer[i] = (Short.MAX_VALUE * sin(phase)).toInt().toShort()
                    phase += angularFrequency / sampleRate
                    if (phase > 2.0 * Math.PI) {
                        phase -= 2.0 * Math.PI
                    }
                }
                audioTrack?.write(buffer, 0, bufferSize)
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignorar errores al liberar si ya estaba liberado
        }
        audioTrack = null
        currentFrequency = 0.0
    }

    fun isPlayingNote(frequency: Double): Boolean {
        return isPlaying && currentFrequency == frequency
    }
}
