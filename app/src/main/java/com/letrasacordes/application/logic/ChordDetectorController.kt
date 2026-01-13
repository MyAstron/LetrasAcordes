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
        // Reducimos el sample rate a 11025Hz para duplicar la resolución en las frecuencias bajas (guitarra)
        private const val SAMPLE_RATE = 11025 
        private const val FFT_SIZE = 4096 // Resolución de ~2.7Hz por bin. Ideal para cuerdas graves.
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        
        // Fórmulas de acordes con prioridad de "Popularidad"
        // El primer valor es el sufijo, el segundo la fórmula, y el tercero el "Bono de Popularidad"
        private val CHORD_DEFINITIONS = listOf(
            Triple("", listOf(0, 4, 7), 0.20),      // Mayor (Prioridad Máxima)
            Triple("m", listOf(0, 3, 7), 0.18),     // Menor (Prioridad Alta)
            Triple("7", listOf(0, 4, 7, 10), 0.10), // Séptima
            Triple("maj7", listOf(0, 4, 7, 11), 0.05),
            Triple("m7", listOf(0, 3, 7, 10), 0.05),
            Triple("dim", listOf(0, 3, 6), 0.02),
            Triple("dim7", listOf(0, 3, 6, 9), 0.01),
            Triple("m7b5", listOf(0, 3, 6, 10), 0.01),
            Triple("aug", listOf(0, 4, 8), 0.01),
            Triple("sus4", listOf(0, 5, 7), 0.05),
            Triple("sus2", listOf(0, 2, 7), 0.05)
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
                    val rms = calculateRMS(audioBuffer)
                    if (rms > 350) { // Un poco más sensible
                        for (i in 0 until FFT_SIZE) {
                            val window = 0.5 * (1 - cos(2.0 * PI * i / (FFT_SIZE - 1)))
                            real[i] = audioBuffer[i].toDouble() * window
                            imag[i] = 0.0
                        }

                        fft(real, imag)

                        val magnitudes = DoubleArray(FFT_SIZE / 2)
                        for (i in 0 until FFT_SIZE / 2) {
                            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
                        }

                        val detectedNotes = findProminentNotes(magnitudes)
                        if (detectedNotes.isNotEmpty()) {
                            val chord = matchChord(detectedNotes)
                            if (chord != null) {
                                withContext(Dispatchers.Main) {
                                    val amplitude = (rms.toFloat() / 5000f).coerceIn(0f, 1f)
                                    onChordDetected?.invoke(PolyphonicTunerResult(chord, 1.0f, amplitude))
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onChordDetected?.invoke(PolyphonicTunerResult("--", 0f, 0f))
                        }
                    }
                }
                delay(80) // Respuesta ligeramente más rápida
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
        for (s in buffer) sum += s.toDouble() * s.toDouble()
        return sqrt(sum / buffer.size)
    }

    private fun findProminentNotes(magnitudes: DoubleArray): Set<Int> {
        val noteEnergies = DoubleArray(12) { 0.0 }
        val binFreqStep = SAMPLE_RATE.toDouble() / FFT_SIZE

        // Guitarra: E2 (82Hz) hasta armónicos altos útiles (~1200Hz)
        val minBin = (75 / binFreqStep).toInt()
        val maxBin = (1200 / binFreqStep).toInt()

        for (bin in minBin..maxBin) {
            val freq = bin * binFreqStep
            val mag = magnitudes[bin]
            
            // Filtro de ruido por bin
            if (mag > 300) { 
                val semitonesFromA4 = 12 * log2(freq / 440.0)
                val noteIndex = (((semitonesFromA4 + 69).roundToInt() % 12) + 12) % 12
                
                // Aplicamos un peso: las frecuencias fundamentales (más bajas) 
                // suelen ser más importantes que los armónicos altos para identificar la nota
                val weight = if (freq < 400) 1.2 else 0.8
                noteEnergies[noteIndex] += mag * weight
            }
        }

        val maxEnergy = noteEnergies.maxOrNull() ?: 0.0
        if (maxEnergy < 600) return emptySet()

        // Umbral dinámico: capturamos notas que tengan al menos el 35% de la energía de la nota dominante
        return noteEnergies.indices.filter { noteEnergies[it] > maxEnergy * 0.35 }.toSet()
    }

    private fun matchChord(detectedNoteIndices: Set<Int>): String? {
        if (detectedNoteIndices.isEmpty()) return null

        val candidates = mutableListOf<Pair<String, Double>>()

        for (root in 0..11) {
            for ((suffix, formula, popularityBonus) in CHORD_DEFINITIONS) {
                val requiredNotes = formula.map { (root + it) % 12 }.toSet()
                val intersection = detectedNoteIndices.intersect(requiredNotes)
                
                // Coincidencia básica
                val matchRatio = intersection.size.toDouble() / requiredNotes.size
                
                if (matchRatio >= 0.70) { // Bajamos un poco el ratio mínimo para compensar ruidos
                    val extraNotes = (detectedNoteIndices - requiredNotes).size
                    
                    // Calculamos puntuación final:
                    // Ratio base + Bono de popularidad - Penalización por notas "basura" detectadas
                    val score = matchRatio + popularityBonus - (extraNotes * 0.08)
                    
                    candidates.add((NOTE_NAMES[root] + suffix) to score)
                }
            }
        }
        
        // Retornamos el acorde con mayor puntuación. 
        // Gracias al popularityBonus, ante la duda (ej: entre C y Cmaj7), 
        // el sistema preferirá el acorde más común (C) a menos que la 7ma sea muy clara.
        return candidates.maxByOrNull { it.second }?.first
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n <= 1) return
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
