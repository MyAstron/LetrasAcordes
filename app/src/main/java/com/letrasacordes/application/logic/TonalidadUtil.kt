package com.letrasacordes.application.logic

import java.util.Locale

object TonalidadUtil {

    // Escala cromática con sostenidos
    private val escalaSostenidos = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    // Escala cromática con bemoles (aunque usamos principalmente la de sostenidos para calcular)
    private val escalaBemoles = listOf(
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    )

    // Un mapa para normalizar los acordes a su versión con sostenidos para facilitar el cálculo
    private val mapaNormalizacion = mapOf(
        "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#"
    )

    // Palabras clave que identifican secciones instrumentales y NO son acordes
    private val etiquetasInstrumentales = setOf(
        "INTRO", "INTRODUCCION", "INICIO",
        "FINAL", "FIN", "OUTRO", "REMATE",
        "PUENTE", "INTER", "INTERLUDIO",
        "SOLO", "REQINTO", "DECORACIONES",
        "CIRCULO", "CICLO", "CORO", "VERSO"
    )

    /**
     * Transpone un acorde un número determinado de semitonos.
     * @param acorde El acorde a transponer (ej: "Am", "G7", "C/E").
     * @param semitonos El número de semitonos a mover (positivo para subir, negativo para bajar).
     * @return El acorde transpuesto.
     */
    fun transponerAcorde(acorde: String, semitonos: Int): String {
        if (acorde.isBlank()) return ""

        // Regex para separar la nota base (A-G, opcional # o b) del resto (m, 7, sus4, etc.)
        val regex = Regex("([A-G][#b]?)(.*)")
        val match = regex.find(acorde) ?: return acorde

        val notaBase = match.groupValues[1]
        val modificador = match.groupValues[2]

        val notaNormalizada = normalizarNota(notaBase)
        val indiceActual = escalaSostenidos.indexOf(notaNormalizada)

        if (indiceActual == -1) return acorde

        // Cálculo circular de índices (Módulo 12)
        // La lógica del APK usaba operaciones a nivel de bits para manejar negativos,
        // aquí usamos una forma más legible de Kotlin que hace lo mismo:
        var nuevoIndice = (indiceActual + semitonos) % 12
        if (nuevoIndice < 0) {
            nuevoIndice += 12
        }

        val nuevaNota = escalaSostenidos[nuevoIndice]

        return nuevaNota + modificador
    }

    /**
     * Busca el primer acorde real de la canción.
     * 1. Revisa etiquetas entre corchetes [Acorde]. Ignora [INTRO], [CORO], etc.
     * 2. Si no encuentra, revisa bloques entre llaves {Acorde Acorde}.
     */
    fun obtenerPrimerAcorde(texto: String): String? {
        val regexCorchetes = Regex("\\[(.*?)\\]")
        val matches = regexCorchetes.findAll(texto)

        // 1. Buscar en los corchetes [...]
        for (match in matches) {
            val contenidoRaw = match.groupValues[1] // Ej: "G" o "INTRO"
            val contenidoUpper = contenidoRaw.uppercase(Locale.ROOT).trim()

            // Verificamos si es una etiqueta instrumental conocida
            val esEtiquetaInstrumental = etiquetasInstrumentales.any { etiqueta ->
                contenidoUpper.startsWith(etiqueta)
            }

            if (!esEtiquetaInstrumental) {
                // Si NO es etiqueta, verificamos si parece un acorde musical (Empieza con A-G)
                if (contenidoUpper.matches(Regex("^[A-G][#b]?.*"))) {
                    return contenidoRaw // Devolvemos el acorde original
                }
            }
        }

        // 2. Fallback: Buscar en bloques instrumentales {...}
        // El APK usa regex para encontrar cosas como {G D Em} y tomar el primero.
        val regexLlaves = Regex("\\{([A-G][#b]?.*?)\\}")
        val matchLlaves = regexLlaves.find(texto)

        if (matchLlaves != null) {
            val contenidoLlaves = matchLlaves.groupValues[1].trim()
            // Tomamos el primer "token" separado por espacios
            val primerToken = contenidoLlaves.split(Regex("\\s+")).firstOrNull()

            if (primerToken != null && primerToken.matches(Regex("^[A-G][#b]?.*"))) {
                return primerToken
            }
        }

        return null
    }

    /**
     * Normaliza una nota a su equivalente con sostenido si es un bemol.
     * Ej: "Db" se convierte en "C#".
     */
    private fun normalizarNota(nota: String): String {
        return mapaNormalizacion[nota] ?: nota
    }
}