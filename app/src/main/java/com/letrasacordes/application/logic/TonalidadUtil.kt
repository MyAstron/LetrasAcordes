package com.letrasacordes.application.logic

import java.util.Locale

object TonalidadUtil {

    // Escala cromática con sostenidos
    private val escalaSostenidos = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    // Escala cromática con bemoles
    private val escalaBemoles = listOf(
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    )

    // Mapa para normalizar
    private val mapaNormalizacion = mapOf(
        "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#"
    )

    // Palabras clave que identifican secciones instrumentales
    private val etiquetasInstrumentales = setOf(
        "INTRO", "INTRODUCCION", "INICIO",
        "FINAL", "FIN", "OUTRO", "REMATE",
        "PUENTE", "INTER", "INTERLUDIO",
        "SOLO", "REQUINTO", "DECORACIONES",
        "CIRCULO", "CICLO", "CORO", "VERSO"
    )

    /**
     * Verifica si una cadena es una etiqueta instrumental (ej: INTRO, CORO).
     */
    fun esEtiquetaInstrumental(texto: String): Boolean {
        val upper = texto.uppercase(Locale.ROOT).trim()
        return etiquetasInstrumentales.any { etiqueta ->
            upper.startsWith(etiqueta)
        }
    }

    /**
     * Transpone un acorde un número determinado de semitonos.
     */
    fun transponerAcorde(acorde: String, semitonos: Int): String {
        if (acorde.isBlank()) return ""

        val regex = Regex("([A-G][#b]?)(.*)")
        val match = regex.find(acorde) ?: return acorde

        val notaBase = match.groupValues[1]
        val modificador = match.groupValues[2]

        val notaNormalizada = normalizarNota(notaBase)
        val indiceActual = escalaSostenidos.indexOf(notaNormalizada)

        if (indiceActual == -1) return acorde

        var nuevoIndice = (indiceActual + semitonos) % 12
        if (nuevoIndice < 0) {
            nuevoIndice += 12
        }

        val nuevaNota = escalaSostenidos[nuevoIndice]
        return nuevaNota + modificador
    }

    /**
     * Busca el primer acorde real de la canción.
     */
    fun obtenerPrimerAcorde(texto: String): String? {
        val regexCorchetes = Regex("\\[(.*?)\\]")
        val matches = regexCorchetes.findAll(texto)

        for (match in matches) {
            val contenidoRaw = match.groupValues[1]
            if (!esEtiquetaInstrumental(contenidoRaw)) {
                if (contenidoRaw.uppercase(Locale.ROOT).trim().matches(Regex("^[A-G][#b]?.*"))) {
                    return contenidoRaw
                }
            }
        }

        val regexLlaves = Regex("\\{([A-G][#b]?.*?)\\}")
        val matchLlaves = regexLlaves.find(texto)

        if (matchLlaves != null) {
            val contenidoLlaves = matchLlaves.groupValues[1].trim()
            val primerToken = contenidoLlaves.split(Regex("\\s+")).firstOrNull()

            if (primerToken != null && primerToken.matches(Regex("^[A-G][#b]?.*"))) {
                return primerToken
            }
        }

        return null
    }

    private fun normalizarNota(nota: String): String {
        return mapaNormalizacion[nota] ?: nota
    }
}
