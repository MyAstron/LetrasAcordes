package com.letrasacordes.application.logic

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Resultado de la detección de un acorde.
 */
data class PolyphonicTunerResult(
    val chordName: String,
    val probability: Float,
    val amplitude: Float
)

/**
 * Controlador especializado en detección polifónica de acordes (Guitarra).
 * Utiliza FFT para análisis espectral y búsqueda de patrones de notas.
 */
class ChordDetectorController {
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var job: Job? = null
    
    var onChordDetected: ((PolyphonicTunerResult) -> Unit)? = null

    companion object {
        private const val SAMPLE_RATE = 22050 // Bajamos sample rate para mejor resolución en bajos con FFT pequeña
        private const val FFT_SIZE = 4096 // Suficiente resolución (~5Hz por bin)
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        
        // Fórmulas de acordes (Intervalos respecto a la raíz)
        private val CHORD_FORMULAS = mapOf(
            "" to listOf(0, 4, 7),      // Mayor
            "m" to listOf(0, 3, 7),     // Menor
            "7" to listOf(0, 4, 7, 10), // Séptima
            "maj7" to listOf(0, 4, 7, 11),
            "m7" to listOf(0, 3, 7, 10)
        )
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isListening) return
        
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize <= 0) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(bufferSize, FFT_SIZE * 2)
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return

        audioRecord?.startRecording()
        isListening = true

        job = scope.launch(Dispatchers.Default) {
            val audioBuffer = ShortArray(FFT_SIZE)
            val real = DoubleArray(FFT_SIZE)
            val imag = DoubleArray(FFT_SIZE)
            
            while (isActive && isListening) {
                val read = audioRecord?.read(audioBuffer, 0, FFT_SIZE) ?: 0
                if (read == FFT_SIZE) {
                    // 1. Calcular RMS para ignorar fondo
                    val rms = calculateRMS(audioBuffer)
                    if (rms > 400) {
                        // 2. Preparar para FFT (Ventana de Hanning para suavizar)
                        for (i in 0 until FFT_SIZE) {
                            val window = 0.5 * (1 - cos(2.0 * PI * i / (FFT_SIZE - 1)))
                            real[i] = audioBuffer[i].toDouble() * window
                            imag[i] = 0.0
                        }

                        // 3. Ejecutar FFT
                        fft(real, imag)

                        // 4. Analizar Espectro y Notas
                        val magnitudes = DoubleArray(FFT_SIZE / 2)
                        for (i in 0 until FFT_SIZE / 2) {
                            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
                        }

                        val detectedNotes = findProminentNotes(magnitudes)
                        if (detectedNotes.isNotEmpty()) {
                            val chord = matchChord(detectedNotes)
                            if (chord != null) {
                                withContext(Dispatchers.Main) {
                                    onChordDetected?.invoke(PolyphonicTunerResult(chord, 1.0f, (rms/5000f).coerceIn(0f, 1f)))
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onChordDetected?.invoke(PolyphonicTunerResult("--", 0f, 0f))
                        }
                    }
                }
                delay(100) // Procesar unas 10 veces por segundo para estabilidad
            }
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

    private fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0
        for (s in buffer) sum += s * s
        return sqrt(sum / buffer.size)
    }

    /**
     * Encuentra las notas musicales con más energía en el espectro.
     */
    private fun findProminentNotes(magnitudes: DoubleArray): Set<Int> {
        val noteEnergies = DoubleArray(12) { 0.0 }
        val binFreqStep = SAMPLE_RATE.toDouble() / FFT_SIZE

        // Solo analizamos el rango fundamental de la guitarra (80Hz a 1000Hz)
        val minBin = (80 / binFreqStep).toInt()
        val maxBin = (1000 / binFreqStep).toInt()

        for (bin in minBin..maxBin) {
            val freq = bin * binFreqStep
            val mag = magnitudes[bin]
            if (mag > 500) { // Umbral de magnitud
                val semitonesFromA4 = 12 * log2(freq / 440.0)
                val noteIndex = ((semitonesFromA4 + 69).roundToInt() % 12 + 12) % 12
                noteEnergies[noteIndex] += mag
            }
        }

        // Devolvemos los índices de las notas que superan un porcentaje de la energía máxima
        val maxEnergy = noteEnergies.maxOrNull() ?: 0.0
        if (maxEnergy < 1000) return emptySet()

        return noteEnergies.indices.filter { noteEnergies[it] > maxEnergy * 0.6 }.toSet()
    }

    /**
     * Compara las notas detectadas contra el diccionario de fórmulas.
     */
    private fun matchChord(detectedNoteIndices: Set<Int>): String? {
        if (detectedNoteIndices.isEmpty()) return null

        // Probamos cada nota detectada como posible raíz del acorde
        for (root in detectedNoteIndices) {
            for ((suffix, formula) in CHORD_FORMULAS) {
                val requiredNotes = formula.map { (root + it) % 12 }.toSet()
                
                // Si todas las notas de la fórmula están presentes en las detectadas
                if (detectedNoteIndices.containsAll(requiredNotes)) {
                    return NOTE_NAMES[root] + suffix
                }
            }
        }
        
        // Si no es un acorde claro, devolvemos la nota más fuerte (monofónico fallback)
        return null 
    }

    /**
     * Implementación básica de FFT Radix-2
     */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempRe = re[i]; re[i] = re[j]; re[j] = tempRe
                val tempIm = im[i]; im[i] = im[j]; im[j] = tempIm
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Butterfly computations
        var len = 2
        while (len <= n) {
            val angle = 2.0 * PI / len
            val wLenRe = cos(angle)
            val wLenIm = -sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len *= 2
        }
    }
}
