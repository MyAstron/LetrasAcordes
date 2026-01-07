package com.letrasacordes.application.ui

import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * Controlador para manejar la lógica del metrónomo.
 * Usa SoundPool para baja latencia y Corrutinas para el timing.
 */
class MetronomeController {
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var isPlaying = false
    private var bpm = 120
    private var job: Job? = null
    
    // Callback para notificar el beat (para el LED visual)
    var onBeat: (() -> Unit)? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        // Cargamos un sonido de click sintético o recurso.
        // Como no tenemos un archivo mp3, generaremos un tono simple si es posible,
        // o asumiremos que se cargará un recurso. Para simplificar sin archivos extra,
        // la mejor opción en Android puro sin archivos es ToneGenerator, pero SoundPool es mejor para ritmo.
        // Para este ejemplo, simularemos la carga. En una app real, pondrías R.raw.click.
        // Como no puedo agregar archivos binarios, usaremos ToneGenerator en la implementación visual
        // o asumiremos que el desarrollador agregará el sonido.
        //
        // CAMBIO DE ESTRATEGIA: Para no depender de un archivo .mp3/.wav que no puedo crear,
        // usaré la clase ToneGenerator directamente en el bucle, que viene en Android.
    }
    
    // Usaremos ToneGenerator en lugar de SoundPool para evitar necesitar un archivo de recurso
    private val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)

    fun start(scope: CoroutineScope) {
        if (isPlaying) return
        isPlaying = true
        
        job = scope.launch(Dispatchers.Default) {
            while (isActive && isPlaying) {
                val intervalMs = (60000.0 / bpm).toLong()
                val startTime = System.currentTimeMillis()
                
                // Reproducir sonido
                toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 50)
                
                // Notificar a la UI (en el hilo principal)
                withContext(Dispatchers.Main) {
                    onBeat?.invoke()
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                val delayTime = intervalMs - elapsedTime
                
                if (delayTime > 0) {
                    delay(delayTime)
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        job = null
    }

    fun setBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(30, 300)
    }
    
    fun getBpm(): Int = bpm

    fun isRunning(): Boolean = isPlaying

    fun release() {
        stop()
        toneGenerator.release()
        soundPool?.release()
    }
    
    /**
     * Intenta extraer el BPM de un string de ritmo (ej: "Balada 120", "Rock 140bpm").
     * Si no encuentra número, devuelve null.
     */
    fun parseBpmFromRhythm(rhythm: String?): Int? {
        if (rhythm.isNullOrBlank()) return null
        val regex = Regex("(\\d{2,3})")
        val match = regex.find(rhythm)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Devuelve un BPM sugerido por defecto según el estilo si no hay número explícito.
     */
    fun estimateBpmFromStyle(style: String?): Int {
        if (style.isNullOrBlank()) return 120
        val s = style.lowercase()
        return when {
            s.contains("balada") -> 70
            s.contains("rock") -> 130
            s.contains("pop") -> 120
            s.contains("bolero") -> 90
            s.contains("ranchera") -> 110
            s.contains("cumbia") -> 95
            s.contains("salsa") -> 180
            s.contains("reggae") -> 75
            s.contains("ska") -> 150
            s.contains("blues") -> 60
            s.contains("jazz") -> 110
            s.contains("bossa") -> 130
            s.contains("arpegio") -> 80
            s.contains("corrido") -> 140
            s.contains("norteño") -> 130
            s.contains("huapango") -> 140
            s.contains("vals") -> 160
            else -> 120
        }
    }
}
