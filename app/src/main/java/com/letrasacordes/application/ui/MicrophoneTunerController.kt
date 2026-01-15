package com.letrasacordes.application.ui

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class TunerResult(
    val frequency: Double,
    val noteName: String,
    val centsOff: Int,
    val isLocked: Boolean,
    val amplitude: Float,
    val targetFreq: Double? = null // Frecuencia objetivo si se seleccionó una cuerda
)

class MicrophoneTunerController {
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var job: Job? = null
    
    var onTunerUpdate: ((TunerResult) -> Unit)? = null
    var targetFrequency: Double? = null // Frecuencia de la cuerda seleccionada

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val A4_FREQ = 440.0
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isListening) return
        
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) return

        val bufferSize = maxOf(minBufferSize, 8192) // Buffer más grande para mejor resolución en graves
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, // Cambiado a MIC estándar para compatibilidad
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return

            audioRecord?.startRecording()
            isListening = true

            job = scope.launch(Dispatchers.Default) {
                val buffer = ShortArray(bufferSize)
                val bufferDouble = DoubleArray(bufferSize)
                
                while (isActive && isListening) {
                    val readResult = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    
                    if (readResult > 0) {
                        for (i in 0 until readResult) {
                            bufferDouble[i] = buffer[i].toDouble()
                        }
                        
                        val rms = calculateRMS(bufferDouble, readResult)
                        val normalizedAmp = (rms / 2000.0).toFloat().coerceIn(0f, 1f)
                        
                        if (rms > 50) { // Umbral muy sensible
                            val frequency = detectPitch(bufferDouble, readResult, SAMPLE_RATE)
                            
                            if (frequency > 50 && frequency < 1500) {
                                val result = calculateNote(frequency, normalizedAmp)
                                withContext(Dispatchers.Main) {
                                    onTunerUpdate?.invoke(result)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onTunerUpdate?.invoke(TunerResult(0.0, "--", 0, false, normalizedAmp, targetFrequency))
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onTunerUpdate?.invoke(TunerResult(0.0, "--", 0, false, 0f, targetFrequency))
                            }
                        }
                    }
                    delay(40)
                }
            }
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        isListening = false
        job?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    private fun calculateRMS(buffer: DoubleArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) sum += buffer[i] * buffer[i]
        return kotlin.math.sqrt(sum / length)
    }

    private fun detectPitch(buffer: DoubleArray, length: Int, sampleRate: Int): Double {
        // Autocorrelación simple (YIN o similar sería mejor, pero esta es funcional)
        val n = length
        val bestLagSearchStart = sampleRate / 1500
        val bestLagSearchEnd = sampleRate / 50
        
        var bestCorrelation = -1.0
        var bestLag = -1

        for (lag in bestLagSearchStart until minOf(bestLagSearchEnd, n / 2)) {
            var correlation = 0.0
            for (i in 0 until (n - lag)) {
                correlation += buffer[i] * buffer[i + lag]
            }
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        return if (bestLag > 0) sampleRate.toDouble() / bestLag else 0.0
    }

    private fun calculateNote(frequency: Double, amplitude: Float): TunerResult {
        // Si hay una frecuencia objetivo, calculamos cents relativo a ella
        val target = targetFrequency
        if (target != null) {
            val cents = 1200 * log2(frequency / target)
            val noteName = getNoteNameFromFreq(target)
            return TunerResult(
                frequency = frequency,
                noteName = noteName,
                centsOff = cents.roundToInt(),
                isLocked = abs(cents) < 5,
                amplitude = amplitude,
                targetFreq = target
            )
        }

        // Si no hay objetivo, usamos la nota cromática más cercana
        val semitonesFromA4 = 12 * log2(frequency / A4_FREQ)
        val noteIndexRaw = (semitonesFromA4 + 69).roundToInt()
        val noteIndex = (noteIndexRaw % 12).let { if (it < 0) it + 12 else it }
        val noteName = NOTE_NAMES[noteIndex]
        val idealFreq = A4_FREQ * 2.0.pow((noteIndexRaw - 69) / 12.0)
        val cents = 1200 * log2(frequency / idealFreq)
        
        return TunerResult(
            frequency = frequency,
            noteName = noteName,
            centsOff = cents.roundToInt(),
            isLocked = abs(cents) < 5,
            amplitude = amplitude
        )
    }

    private fun getNoteNameFromFreq(freq: Double): String {
        val semitonesFromA4 = 12 * log2(freq / A4_FREQ)
        val noteIndexRaw = (semitonesFromA4 + 69).roundToInt()
        val noteIndex = (noteIndexRaw % 12).let { if (it < 0) it + 12 else it }
        return NOTE_NAMES[noteIndex]
    }
}
