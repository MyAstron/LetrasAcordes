package com.letrasacordes.application

/**
 * Un objeto singleton para manejar toda la lógica de transposición de acordes.
 * Utiliza la escala cromática para subir o bajar tonos.
 */
object ChordTransposer {    // Escala cromática con sostenidos. Usamos esta como base para la transposición.
    private val chromaticScaleSharp = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    // Escala cromática con bemoles para encontrar equivalencias.
    private val chromaticScaleFlat = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Transpone un único acorde un número determinado de semitonos.
     *
     * @param chord El acorde a transponer (ej: "Am", "G/B", "F#sus4").
     * @param semitones El número de semitonos a mover (positivo para subir, negativo para bajar).
     * @return El acorde transpuesto.
     */
    fun transposeChord(chord: String, semitones: Int): String {
        if (chord.isBlank()) return ""

        // Regex para separar la nota base de sus modificadores y el bajo (si existe).
        // Grupo 1: Nota (C, G#, Db)
        // Grupo 2: Modificadores (m, 7, sus4, etc.)
        // Grupo 3: (Opcional) Bajo de inversión (/B, /F#)
        val regex = "^([A-G][#b]?)([^/]*)(/([A-G][#b]?))?".toRegex()
        val match = regex.find(chord) ?: return chord // Si no coincide, devuelve el acorde original

        val (rootNote, modifiers, _, bassNoteWithSlash) = match.destructured

        val transposedRoot = transposeNote(rootNote, semitones)
        val transposedBass = if (bassNoteWithSlash.isNotEmpty()) {
            // El bajo viene con '/', por ejemplo "/C#". Hay que quitarle la barra.
            val bassNote = bassNoteWithSlash.substring(1)
            "/${transposeNote(bassNote, semitones)}"
        } else {
            ""
        }

        return "$transposedRoot$modifiers$transposedBass"
    }

    /**
     * Transpone una nota musical (ej: "C", "F#", "Bb") un número de semitonos.
     *
     * @param note La nota a transponer.
     * @param semitones El número de semitonos a mover.
     * @return La nota transpuesta, usando sostenidos por defecto.
     */
    private fun transposeNote(note: String, semitones: Int): String {
        // Encontrar el índice de la nota en cualquiera de las dos escalas.
        val indexSharp = chromaticScaleSharp.indexOf(note)
        val indexFlat = chromaticScaleFlat.indexOf(note)

        val currentIndex = if (indexSharp != -1) indexSharp else indexFlat

        if (currentIndex == -1) return note // Si la nota no se encuentra, devolverla tal cual.

        // Calcular el nuevo índice con vuelta circular usando el módulo.
        // El `+ chromaticScaleSharp.size` maneja los semitonos negativos.
        val newIndex = (currentIndex + semitones % 12 + chromaticScaleSharp.size) % chromaticScaleSharp.size

        return chromaticScaleSharp[newIndex]
    }
}
