package com.letrasacordes.application.ui

import android.Manifest
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
    val centsOff: Int, // Desviación en cents (-50 a +50)
    val isLocked: Boolean, // Si la señal es estable
    val amplitude: Float // Amplitud normalizada (0.0 a 1.0) para visualización
)

class MicrophoneTunerController {
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var job: Job? = null
    
    // Callback para actualizaciones de UI
    var onTunerUpdate: ((TunerResult) -> Unit)? = null

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val A4_FREQ = 440.0
        
        // Notas cromáticas
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }

    @SuppressLint("MissingPermission") // Se debe chequear permiso antes de llamar start()
    fun start(scope: CoroutineScope) {
        if (isListening) return
        
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return
        }

        // Buffer para procesar
        val bufferSize = maxOf(minBufferSize, 4096)
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

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
                        // Normalizar amplitud para la UI (aproximado)
                        val normalizedAmp = (rms / 5000.0).toFloat().coerceIn(0f, 1f)
                        
                        if (rms > 300) { // Umbral de silencio un poco más bajo para visualización
                            val frequency = detectPitch(bufferDouble, readResult, SAMPLE_RATE)
                            
                            if (frequency > 60 && frequency < 1000) {
                                val result = calculateNote(frequency, normalizedAmp)
                                withContext(Dispatchers.Main) {
                                    onTunerUpdate?.invoke(result)
                                }
                            } else {
                                // Aún mandamos actualización de amplitud aunque no haya nota clara
                                withContext(Dispatchers.Main) {
                                    onTunerUpdate?.invoke(TunerResult(0.0, "--", 0, false, normalizedAmp))
                                }
                            }
                        } else {
                            // Silencio
                            withContext(Dispatchers.Main) {
                                onTunerUpdate?.invoke(TunerResult(0.0, "--", 0, false, 0f))
                            }
                        }
                    }
                    delay(30) // Más rápido para visualización fluida
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun stop() {
        isListening = false
        job?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignorar
        }
        audioRecord = null
    }

    private fun calculateRMS(buffer: DoubleArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return kotlin.math.sqrt(sum / length)
    }

    private fun detectPitch(buffer: DoubleArray, length: Int, sampleRate: Int): Double {
        val n = length
        val bestLagSearchStart = sampleRate / 1000
        val bestLagSearchEnd = sampleRate / 60
        
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
        
        if (bestLag > 0) {
            return sampleRate.toDouble() / bestLag
        }
        
        return 0.0
    }

    private fun calculateNote(frequency: Double, amplitude: Float): TunerResult {
        val semitonesFromA4 = 12 * log2(frequency / A4_FREQ)
        val noteIndexRaw = (semitonesFromA4 + 69).roundToInt()
        
        val noteIndex = (noteIndexRaw % 12)
        val normalizedIndex = if (noteIndex < 0) noteIndex + 12 else noteIndex
        
        val noteName = NOTE_NAMES[normalizedIndex]
        
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
}
