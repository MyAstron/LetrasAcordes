package com.letrasacordes.application.logic

object ChordDictionary {

    /**
     * Obtiene la digitación para un acorde dado.
     * Devuelve una lista de 6 enteros representando los trastes de la 6ta a la 1ra cuerda.
     * -1 indica que la cuerda no se toca (X).
     * 0 indica cuerda al aire.
     */
    fun getFingering(chordName: String): List<Int>? {
        // Normalización básica para quitar inversiones complejas si no están en el mapa
        // Ej: "D/F#" -> intentamos buscar "D/F#", si no, buscamos "D"
        var target = chordName.trim()
        
        // Mapeo directo
        if (fingeringMap.containsKey(target)) return fingeringMap[target]

        // Intentar simplificar inversiones (slash chords)
        if (target.contains("/")) {
            val baseChord = target.substringBefore("/")
            if (fingeringMap.containsKey(baseChord)) return fingeringMap[baseChord]
        }

        // Intentar mapear de Latino a Inglés (por si acaso)
        val englishName = latinToEnglish(target)
        if (fingeringMap.containsKey(englishName)) return fingeringMap[englishName]

        return null
    }

    private fun latinToEnglish(chord: String): String {
        var s = chord
        s = s.replace("Do", "C")
        s = s.replace("Re", "D")
        s = s.replace("Mi", "E")
        s = s.replace("Fa", "F")
        s = s.replace("Sol", "G")
        s = s.replace("La", "A")
        s = s.replace("Si", "B")
        return s
    }

    // Mapa de digitaciones: Lista de 6 Ints [6ta, 5ta, 4ta, 3ra, 2da, 1ra]
    // -1 = X (Mute), 0 = Open
    private val fingeringMap = mapOf(
        // MAYORES
        "C" to listOf(-1, 3, 2, 0, 1, 0),
        "C#" to listOf(-1, 4, 6, 6, 6, 4), // Forma de A en traste 4
        "Db" to listOf(-1, 4, 6, 6, 6, 4),
        "D" to listOf(-1, -1, 0, 2, 3, 2),
        "D#" to listOf(-1, -1, 1, 3, 4, 3), // Forma de D subida
        "Eb" to listOf(-1, 6, 5, 3, 4, 3), // Forma de C movida o A en traste 6 (-1,6,8,8,8,6) -> Usaremos A shape traste 6
        "E" to listOf(0, 2, 2, 1, 0, 0),
        "F" to listOf(1, 3, 3, 2, 1, 1),
        "F#" to listOf(2, 4, 4, 3, 2, 2),
        "Gb" to listOf(2, 4, 4, 3, 2, 2),
        "G" to listOf(3, 2, 0, 0, 0, 3),
        "G#" to listOf(4, 6, 6, 5, 4, 4),
        "Ab" to listOf(4, 6, 6, 5, 4, 4),
        "A" to listOf(-1, 0, 2, 2, 2, 0),
        "A#" to listOf(-1, 1, 3, 3, 3, 1),
        "Bb" to listOf(-1, 1, 3, 3, 3, 1),
        "B" to listOf(-1, 2, 4, 4, 4, 2),

        // MENORES
        "Cm" to listOf(-1, 3, 5, 5, 4, 3), // Forma de Am en traste 3
        "C#m" to listOf(-1, 4, 6, 6, 5, 4),
        "Dbm" to listOf(-1, 4, 6, 6, 5, 4),
        "Dm" to listOf(-1, -1, 0, 2, 3, 1),
        "D#m" to listOf(-1, -1, 1, 3, 4, 2),
        "Ebm" to listOf(-1, 6, 8, 8, 7, 6),
        "Em" to listOf(0, 2, 2, 0, 0, 0),
        "Fm" to listOf(1, 3, 3, 1, 1, 1),
        "F#m" to listOf(2, 4, 4, 2, 2, 2),
        "Gbm" to listOf(2, 4, 4, 2, 2, 2),
        "Gm" to listOf(3, 5, 5, 3, 3, 3),
        "G#m" to listOf(4, 6, 6, 4, 4, 4),
        "Abm" to listOf(4, 6, 6, 4, 4, 4),
        "Am" to listOf(-1, 0, 2, 2, 1, 0),
        "A#m" to listOf(-1, 1, 3, 3, 2, 1),
        "Bbm" to listOf(-1, 1, 3, 3, 2, 1),
        "Bm" to listOf(-1, 2, 4, 4, 3, 2),

        // SEPTIMAS (Dominante 7)
        "C7" to listOf(-1, 3, 2, 3, 1, 0),
        "D7" to listOf(-1, -1, 0, 2, 1, 2),
        "E7" to listOf(0, 2, 0, 1, 0, 0),
        "F7" to listOf(1, 3, 1, 2, 1, 1),
        "G7" to listOf(3, 2, 0, 0, 0, 1),
        "A7" to listOf(-1, 0, 2, 0, 2, 0),
        "B7" to listOf(-1, 2, 1, 2, 0, 2)
    )
}
