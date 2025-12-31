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
    // Ej: "Db" to "C#", "Gbm" to "F#m"
    private val mapaNormalizacion = mapOf(
        "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#"
    )

    // Palabras clave que identifican secciones instrumentales y NO son acordes de la canción
    private val etiquetasInstrumentales = setOf(
        "INTRO", "INTRODUCCION", "INICIO", 
        "FINAL", "FIN", "OUTRO", "REMATE",
        "PUENTE", "INTER", "INTERLUDIO", 
        "SOLO", "REQINTO", "DECORACIONES",
        "CIRCULO", "CICLO", "CORO", "VERSO" // Agregué Coro y Verso por si acaso se usan como etiquetas
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

    /**
     * Busca el primer acorde real de la canción, ignorando etiquetas como [INTRO], [CORO], etc.
     * Si encuentra una etiqueta como [INTRO]{G D}, intentará extraer el primer acorde dentro de las llaves.
     */
    fun obtenerPrimerAcorde(texto: String): String? {
        val regexCorchetes = Regex("\\[(.*?)\\]")
        val matches = regexCorchetes.findAll(texto)

        for (match in matches) {
            val contenido = match.groupValues[1].uppercase().trim()
            
            // 1. Si NO es una etiqueta instrumental conocida, asumimos que es un acorde (ej: [G])
            // Verificamos si parece un acorde (empieza con A-G)
            if (!etiquetasInstrumentales.any { contenido.startsWith(it) }) {
                // Validación extra: debe parecer un acorde musical
                if (contenido.matches(Regex("^[A-G][#b]?.*"))) {
                    return match.groupValues[1] // Devolvemos el contenido original sin uppercase
                }
            }
            
            // 2. Si ES una etiqueta instrumental, miramos si viene seguida de acordes en llaves {}
            // Ejemplo: [INTRO]{G D Em}
            // El regex global buscará esto, pero aquí estamos iterando match por match de corchetes.
            // Tendríamos que ver el texto INMEDIATAMENTE después de este match.
        }

        // Si no encontramos acordes normales en corchetes, busquemos bloques instrumentales { ... }
        // Muchas veces el formato es [INTRO]{G D} o simplemente {G D}
        val regexLlaves = Regex("\\{([A-G][#b]?.*?)\\}")
        val matchLlaves = regexLlaves.find(texto)
        if (matchLlaves != null) {
            val contenidoLlaves = matchLlaves.groupValues[1].trim()
            // Tomamos la primera "palabra" dentro de las llaves, que debería ser el primer acorde
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