package com.letrasacordes.application.logic

object TonalidadUtil {

    // Escala cromática con sostenidos
    private val escalaSostenidos = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )
    // Escala cromática con bemoles
    private val escalaBemoles = listOf(
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    )

    // Un mapa para normalizar los acordes a su versión con sostenidos
    // Ej: "Db" -> "C#", "Gbm" -> "F#m"
    private val mapaNormalizacion = mapOf(
        "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#"
    )

    /**
     * Transpone un acorde un número determinado de semitonos.
     * @param acorde El acorde a transponer (ej: "Am", "G7", "C/E").
     * @param semitonos El número de semitonos a mover (positivo para subir, negativo para bajar).
     * @return El acorde transpuesto.
     */
    fun transponerAcorde(acorde: String, semitonos: Int): String {
        if (acorde.isBlank()) return ""

        val regex = "([A-G][#b]?)(.*)".toRegex()
        val match = regex.find(acorde) ?: return acorde // Si no es un acorde válido, lo devolvemos tal cual

        val notaBase = match.groupValues[1]
        val modificador = match.groupValues[2]

        val notaNormalizada = normalizarNota(notaBase)
        val indiceActual = escalaSostenidos.indexOf(notaNormalizada)

        if (indiceActual == -1) return acorde // No debería pasar si la regex funcionó

        // Calculamos el nuevo índice en la escala (un círculo de 12 notas)
        val nuevoIndice = (indiceActual + semitonos).mod(12)
        val nuevaNota = escalaSostenidos[nuevoIndice]

        return nuevaNota + modificador
    }
    fun obtenerPrimerAcorde(texto: String): String? {
        val regex = Regex("\\[(.*?)\\]")
        return regex.find(texto)?.groupValues?.get(1)
    }

    /**
     * Normaliza una nota a su equivalente con sostenido si es un bemol.
     * Ej: "Db" se convierte en "C#".
     */
    private fun normalizarNota(nota: String): String {
        return mapaNormalizacion[nota] ?: nota
    }
}