package com.letrasacordes.application.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.letrasacordes.application.logic.ChordDictionary

@Composable
fun ChordDiagramDialog(chordName: String, onDismiss: () -> Unit) {
    val fingering = ChordDictionary.getFingering(chordName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = chordName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (fingering != null) {
                    ChordDiagram(fingering = fingering)
                } else {
                    Text("No hay diagrama disponible para este acorde.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun ChordDiagram(fingering: List<Int>) {
    // 6 cuerdas, 4 o 5 trastes
    // fingering: [E, A, D, G, B, e] -> [6ta, 5ta, ... 1ra]
    // Valores: -1 (X), 0 (Open), 1..N (Traste)

    // Necesitamos determinar el traste inicial (offset) si el acorde se toca muy agudo
    val frets = fingering.filter { it > 0 }
    val minFret = if (frets.isNotEmpty()) frets.minOrNull() ?: 1 else 1
    val maxFret = if (frets.isNotEmpty()) frets.maxOrNull() ?: 1 else 1
    
    // Si el acorde cabe en los primeros 4 trastes, baseFret = 1.
    // Si no, baseFret = minFret
    val baseFret = if (maxFret <= 5) 1 else minFret
    val numFretsToShow = 5

    Canvas(modifier = Modifier.size(150.dp, 180.dp)) {
        val width = size.width
        val height = size.height
        val padding = 20.dp.toPx()
        
        val effectiveWidth = width - 2 * padding
        val effectiveHeight = height - 2 * padding
        
        val stringGap = effectiveWidth / 5
        val fretGap = effectiveHeight / numFretsToShow

        // Dibujar Cuerdas (Verticales)
        for (i in 0..5) {
            val x = padding + i * stringGap
            drawLine(
                color = Color.Black,
                start = Offset(x, padding),
                end = Offset(x, padding + effectiveHeight),
                strokeWidth = if (i % 5 == 0) 3f else 2f // Bordes un poco más gruesos
            )
        }

        // Dibujar Trastes (Horizontales)
        // La cejuela (traste 0) se dibuja más gruesa si baseFret es 1
        val nutThickness = if (baseFret == 1) 8f else 2f
        
        drawLine(
            color = Color.Black,
            start = Offset(padding, padding),
            end = Offset(padding + effectiveWidth, padding),
            strokeWidth = nutThickness
        )

        for (i in 1..numFretsToShow) {
            val y = padding + i * fretGap
            drawLine(
                color = Color.Black,
                start = Offset(padding, y),
                end = Offset(padding + effectiveWidth, y),
                strokeWidth = 2f
            )
        }

        // Dibujar puntos y marcadores X/O
        // fingering va de 6ta (izquierda) a 1ra (derecha)
        fingering.forEachIndexed { index, fret ->
            val x = padding + index * stringGap
            
            if (fret == -1) {
                // Dibujar X encima de la cejuela
                drawX(center = Offset(x, padding - 15f))
            } else if (fret == 0) {
                // Dibujar O encima de la cejuela
                drawCircle(
                    color = Color.Black,
                    center = Offset(x, padding - 15f),
                    radius = 6f,
                    style = Stroke(width = 2f)
                )
            } else {
                // Dibujar punto en el traste correspondiente
                // Ajustar por baseFret
                val relativeFret = fret - baseFret + 1
                if (relativeFret in 1..numFretsToShow) {
                    val y = padding + (relativeFret * fretGap) - (fretGap / 2)
                    drawCircle(
                        color = Color.Black,
                        center = Offset(x, y),
                        radius = 10f
                    )
                }
            }
        }
        
        // Si baseFret > 1, dibujar el número del traste a la izquierda
        if (baseFret > 1) {
             // Esto es simplificado, en un Canvas real necesitaríamos un text painter o similar,
             // pero Compose Canvas no tiene drawText directo fácilmente sin contexto nativo.
             // Para simplificar, dibujaremos palitos o asumiremos que el usuario entiende la posición relativa.
             // Opcionalmente podemos dibujar un indicador visual simple.
        }
    }
}

fun DrawScope.drawX(center: Offset) {
    val size = 8f
    drawLine(
        color = Color.Black,
        start = Offset(center.x - size, center.y - size),
        end = Offset(center.x + size, center.y + size),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.Black,
        start = Offset(center.x + size, center.y - size),
        end = Offset(center.x - size, center.y + size),
        strokeWidth = 2f
    )
}
