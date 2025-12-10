package com.letrasacordes.application.logic

object SongTextFormatter {

    /**
     * Formatea el texto de una canción con acordes en línea [Am] a un formato de dos líneas
     * (una para acordes, otra para la letra) para su visualización.
     *
     * @param letraOriginal La letra completa de la canción con acordes en formato [Acorde].
     * @param semitonos El número de semitonos para transponer los acordes (0 si no se transpone).
     * @param withChords Si es `false`, simplemente limpiará los acordes de la letra.
     * @return Una lista de pares, donde cada par contiene la línea de acordes y la línea de letra correspondiente.
     */
    fun formatSongTextForDisplay(
        letraOriginal: String,
        semitonos: Int,
        withChords: Boolean
    ): List<Pair<String, String>> {
        val lineasProcesadas = mutableListOf<Pair<String, String>>()
        val regexAcordes = Regex("\\[.*?\\]")

        letraOriginal.lines().forEach { lineaOriginal ->
            // Si no queremos acordes, simplemente limpiamos la línea y continuamos.
            if (!withChords) {
                lineasProcesadas.add(Pair("", lineaOriginal.replace(regexAcordes, "")))
                return@forEach
            }

            val lineaDeAcordes = StringBuilder()
            val lineaDeLetra = StringBuilder()
            var indiceActual = 0

            val regex = Regex("\\[(.*?)\\]")
            val matches = regex.findAll(lineaOriginal)

            // Si no hay acordes en esta línea, la añadimos tal cual y continuamos.
            if (!matches.any()) {
                lineasProcesadas.add(Pair("", lineaOriginal))
                return@forEach
            }

            // Procesamos cada acorde encontrado en la línea
            matches.forEach { match ->
                val acordeOriginal = match.groupValues[1]
                val acordeTranspuesto = TonalidadUtil.transponerAcorde(acordeOriginal, semitonos)

                // Añadimos el texto de la letra que va antes del acorde
                val textoEntreAcordes = lineaOriginal.substring(indiceActual, match.range.first)
                lineaDeLetra.append(textoEntreAcordes)

                // Añadimos espacios a la línea de acordes para mantener la alineación
                lineaDeAcordes.append(" ".repeat(textoEntreAcordes.length))

                // Añadimos el acorde y actualizamos la posición
                lineaDeAcordes.append(acordeTranspuesto)
                indiceActual = match.range.last + 1
            }

            // Añadimos el resto del texto de la letra que va después del último acorde
            lineaDeLetra.append(lineaOriginal.substring(indiceActual))

            lineasProcesadas.add(Pair(lineaDeAcordes.toString(), lineaDeLetra.toString()))
        }
        return lineasProcesadas
    }
}
