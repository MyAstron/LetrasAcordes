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
        // Regex para limpiar acordes [...] y bloques {...}
        val regexAcordesLimpieza = Regex("(\\[.*?\\]|\\{.*?\\})")
        
        // Regex para detectar líneas instrumentales completas: [INTRO]{G D} o [PUENTE] {Am}
        val regexLineaInstrumental = Regex("^\\[(.*?)\\]\\s*\\{(.*?)\\}$")

        letraOriginal.lines().forEach { lineaOriginal ->
            val lineaTrim = lineaOriginal.trim()

            // Si no queremos acordes, limpiamos todo
            if (!withChords) {
                lineasProcesadas.add(Pair("", lineaOriginal.replace(regexAcordesLimpieza, "").trim()))
                return@forEach
            }

            // 1. Caso especial: Línea Instrumental (ej: [INTRO]{G D})
            // Queremos que se vea "Intro: G D" y que esté en el estilo de acordes (azul/resaltado)
            val matchInstrumental = regexLineaInstrumental.find(lineaTrim)
            if (matchInstrumental != null) {
                val etiquetaRaw = matchInstrumental.groupValues[1] // ej: INTRO
                val contenidoRaw = matchInstrumental.groupValues[2] // ej: G D

                // Formateamos la etiqueta: INTRO -> Intro
                val etiquetaVisual = etiquetaRaw.lowercase().replaceFirstChar { it.uppercase() }
                
                // Transponemos los acordes dentro de las llaves
                val contenidoTranspuesto = contenidoRaw.split(Regex("\\s+")).joinToString(" ") { token ->
                    TonalidadUtil.transponerAcorde(token, semitonos)
                }

                // Construimos la línea visual: "Intro: G D"
                val lineaVisual = "$etiquetaVisual: $contenidoTranspuesto"

                // Agregamos al PRIMER elemento del par (línea de acordes) para que salga en azul
                lineasProcesadas.add(Pair(lineaVisual, ""))
                return@forEach
            }

            // 2. Caso genérico: Bloques {...} mezclados o sin etiqueta previa
            // (Mantenemos la lógica anterior por seguridad, pero poniéndolo en acordes si es solo bloques)
            if (lineaOriginal.contains("{")) {
                 val lineaTranspuesta = transponerBloquesInstrumentales(lineaOriginal, semitonos)
                 // Si la línea tiene formato de bloque, la tratamos como acordes para resaltarla
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
                // Si es solo texto sin acordes, va a la línea de letra (segundo elemento)
                lineasProcesadas.add(Pair("", lineaOriginal))
                return@forEach
            }

            matches.forEach { match ->
                val acordeOriginal = match.groupValues[1]
                val acordeTranspuesto = TonalidadUtil.transponerAcorde(acordeOriginal, semitonos)

                val textoEntreAcordes = lineaOriginal.substring(indiceActual, match.range.first)
                lineaDeLetra.append(textoEntreAcordes)

                // Espaciado para alinear el acorde sobre la letra
                val espacios = " ".repeat(textoEntreAcordes.length.coerceAtLeast(1))
                
                if (indiceActual == 0 && textoEntreAcordes.isEmpty()) {
                   // No añadir espacios extra al inicio
                } else {
                   lineaDeAcordes.append(" ".repeat(textoEntreAcordes.length))
                }

                lineaDeAcordes.append(acordeTranspuesto)
                indiceActual = match.range.last + 1
            }

            lineaDeLetra.append(lineaOriginal.substring(indiceActual))

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
            // Retiramos las llaves para la visualización si se desea, 
            // o las dejamos si el usuario quiere ver { }. 
            // Dado que pediste "Intro: acordes", asumiré que quieres los acordes limpios sin llaves
            // en este contexto de visualización puramente instrumental.
            acordesTranspuestos 
        }
    }
}