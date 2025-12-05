package com.letrasacordes.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.logic.TonalidadUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVerCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val cancionState by viewModel.obtenerCancion(cancionId).collectAsState()
    var semitonos by remember { mutableStateOf(0) }
    var mostrarAcordes by remember { mutableStateOf(true) } // Por defecto los mostramos

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Usamos el estado para mostrar el título de la canción
                    Text(cancionState?.titulo ?: "Cargando...", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        },
        // Floating Action Buttons para la transposición
        floatingActionButton = {
            if (cancionState?.tieneAcordes == true && mostrarAcordes) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(onClick = { semitonos-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bajar Tono") // <-- ¡Listo!
                    }
                    FloatingActionButton(onClick = { semitonos++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Subir Tono")
                    }
                }
            }
        }
    ) { paddingValues ->
        val cancion = cancionState
        if (cancion == null) {
            // Muestra un indicador de carga mientras la canción llega de la BD
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Información de la canción
                Text(
                    text = cancion.autor ?: "Autor desconocido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Toggle para mostrar/ocultar acordes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = mostrarAcordes,
                        onCheckedChange = { mostrarAcordes = it },
                        enabled = cancion.tieneAcordes // Solo se puede activar si la canción tiene acordes
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Mostrar acordes")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Letra de la canción
                val letraProcesada = remember(cancion.letraOriginal, semitonos, mostrarAcordes) {
                    procesarLetra(cancion.letraOriginal, semitonos, mostrarAcordes)
                }
                Text(
                    text = letraProcesada,
                    fontFamily = FontFamily.Monospace, // Fuente monoespaciada para alinear acordes
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * Función que toma la letra original y la transforma para su visualización.
 */
private fun procesarLetra(letraOriginal: String, semitonos: Int, mostrarAcordes: Boolean): String {
    if (!mostrarAcordes) {
        // Si no se muestran acordes, simplemente los eliminamos
        return letraOriginal.replace(Regex("\\[.*?\\]"), "")
    }

    // Usamos una expresión regular para encontrar todos los acordes [Acorde]
    val regex = Regex("\\[(.*?)\\]")
    return regex.replace(letraOriginal) { matchResult ->
        val acordeOriginal = matchResult.groupValues[1]
        // Transponemos el acorde usando nuestro TonalidadUtil
        val acordeTranspuesto = TonalidadUtil.transponerAcorde(acordeOriginal, semitonos)
        "[$acordeTranspuesto]" // Devolvemos el acorde transpuesto con sus corchetes
    }
}
