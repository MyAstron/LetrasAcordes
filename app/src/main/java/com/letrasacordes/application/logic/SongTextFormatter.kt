package com.letrasacordes.application.logic

import kotlin.text.Regex

object SongTextFormatter {

    /**
     * Formatea el texto de una canción para mostrarlo en pantalla o PDF.* Convierte líneas tipo "[C]Hola [G]Mundo" en pares de líneas (Acordes / Letra).
     */
    fun formatSongTextForDisplay(
        letraOriginal: String,
        semitonos: Int,
        withChords: Boolean
    ): List<Pair<String, String>> {
        val lineasProcesadas = mutableListOf<Pair<String, String>>()

        // Regex para limpiar acordes [...] y bloques {...} si el usuario no los quiere
        val regexAcordesLimpieza = Regex("(\\[.*?\\]|\\{.*?\\})")

        // Regex para detectar líneas instrumentales completas: [INTRO]{G D}
        val regexLineaInstrumental = Regex("^\\[(.*?)\\]\\s*\\{(.*?)\\}\$")

        letraOriginal.lines().forEach { lineaOriginal ->
            val lineaTrim = lineaOriginal.trim()

            // Si el usuario desactivó los acordes, devolvemos solo la letra limpia
            if (!withChords) {
                lineasProcesadas.add(Pair("", lineaOriginal.replace(regexAcordesLimpieza, "").trim()))
                return@forEach
            }

            // 1. Caso especial: Línea Instrumental (ej: [INTRO]{G D})
            val matchInstrumental = regexLineaInstrumental.find(lineaTrim)
            if (matchInstrumental != null) {
                val etiquetaRaw = matchInstrumental.groupValues[1] // ej: INTRO
                val contenidoRaw = matchInstrumental.groupValues[2] // ej: G D

                // Formateamos la etiqueta: INTRO -> Intro
                val etiquetaVisual = etiquetaRaw.lowercase().replaceFirstChar { it.uppercase() }

                // Transponemos los acordes
                val contenidoTranspuesto = contenidoRaw.split(Regex("\\s+")).joinToString(" ") { token ->
                    TonalidadUtil.transponerAcorde(token, semitonos)
                }

                val lineaVisual = "$etiquetaVisual: $contenidoTranspuesto"
                // Agregamos como "línea de acordes" (primer par) para que se pinte de azul/color
                lineasProcesadas.add(Pair(lineaVisual, ""))
                return@forEach
            }

            // 2. Caso genérico: Bloques {...} mezclados (ej: interludios)
            if (lineaOriginal.contains("{")) {
                val lineaTranspuesta = transponerBloquesInstrumentales(lineaOriginal, semitonos)
                lineasProcesadas.add(Pair(lineaTranspuesta, ""))
                return@forEach
            }

            // 3. Procesamiento estándar de acordes [Acorde]Letra
            val lineaDeAcordes = StringBuilder()
            val lineaDeLetra = StringBuilder()
            var indiceActual = 0

            val regex = Regex("\\[(.*?)\\]")
            val matches = regex.findAll(lineaOriginal)

            if (!matches.any()) {
                // Si es solo texto sin acordes
                lineasProcesadas.add(Pair("", lineaOriginal))
                return@forEach
            }

            matches.forEach { match ->
                val acordeOriginal = match.groupValues[1]
                val acordeTranspuesto = TonalidadUtil.transponerAcorde(acordeOriginal, semitonos)

                val textoEntreAcordes = lineaOriginal.substring(indiceActual, match.range.first)
                lineaDeLetra.append(textoEntreAcordes)

                // Calcular espacios para alinear el acorde exactamente sobre la sílaba
                if (indiceActual > 0 || textoEntreAcordes.isNotEmpty()) {
                    lineaDeAcordes.append(" ".repeat(textoEntreAcordes.length))
                }

                lineaDeAcordes.append(acordeTranspuesto)
                indiceActual = match.range.last + 1
            }

            // Agregar el resto de la letra después del último acorde
            if (indiceActual < lineaOriginal.length) {
                lineaDeLetra.append(lineaOriginal.substring(indiceActual))
            }

            lineasProcesadas.add(Pair(lineaDeAcordes.toString(), lineaDeLetra.toString()))
        }
        return lineasProcesadas
    }

    private fun transponerBloquesInstrumentales(linea: String, semitonos: Int): String {
        // Busca contenido dentro de llaves {G D Em} y transpone
        val regexLlaves = Regex("\\{(.*?)\\}")
        return regexLlaves.replace(linea) { matchResult ->
            val contenido = matchResult.groupValues[1]
            val acordesTranspuestos = contenido.split(" ").joinToString(" ") { token ->
                TonalidadUtil.transponerAcorde(token, semitonos)
            }
            // Devolvemos solo los acordes transpuestos, quitando las llaves para visualización limpia
            acordesTranspuestos
        }
    }
}
