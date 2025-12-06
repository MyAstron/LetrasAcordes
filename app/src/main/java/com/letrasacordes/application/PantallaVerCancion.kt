package com.letrasacordes.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.logic.TonalidadUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVerCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    onNavegarAEditar: (Int) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    // SOLUCIÓN 1: Usar un estado local para la canción para evitar el bucle de recomposición.
    var cancionActual by remember { mutableStateOf<Cancion?>(null) }

    var semitonos by remember { mutableStateOf(0) }
    var mostrarAcordes by remember { mutableStateOf(true) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }

    // SOLUCIÓN 2: Usar LaunchedEffect para cargar los datos solo cuando cancionId cambie.
    LaunchedEffect(cancionId) {
        viewModel.obtenerCancion(cancionId).collect { cancionDesdeDb ->
            cancionActual = cancionDesdeDb
        }
    }

    val tonoOriginal = remember(cancionActual) {
        cancionActual?.let { TonalidadUtil.obtenerPrimerAcorde(it.letraOriginal) }
    }

    if (mostrarDialogoEliminar && cancionActual != null) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEliminar = false },
            title = { Text("Confirmar Eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar permanentemente '${cancionActual!!.titulo}'? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            // Se usa '!!' porque ya hemos comprobado que no es nulo en el 'if'.
                            viewModel.eliminarCancion(cancionActual!!)
                            mostrarDialogoEliminar = false
                            onNavegarAtras()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEliminar = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cancionActual?.titulo ?: "Cargando...", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cancionActual?.tieneAcordes == true) {
                            Switch(
                                checked = mostrarAcordes,
                                onCheckedChange = { mostrarAcordes = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Acordes", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (cancionActual != null && cancionActual!!.tieneAcordes && mostrarAcordes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (tonoOriginal != null) {
                                val tonoAbajo = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos - 1)
                                val tonoActualTranspuesto = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos)
                                val tonoArriba = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos + 1)

                                FilledTonalButton(onClick = { if (semitonos > -12) semitonos-- }, modifier = Modifier.size(48.dp)) {
                                    Text(tonoAbajo, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = tonoActualTranspuesto,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(50.dp)
                                )
                                FilledTonalButton(onClick = { if (semitonos < 12) semitonos++ }, modifier = Modifier.size(48.dp)) {
                                    Text(tonoArriba, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { mostrarDialogoEliminar = true }, enabled = cancionActual != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar Canción")
                        }
                        IconButton(onClick = { onNavegarAEditar(cancionId) }, enabled = cancionActual != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar Canción")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // SOLUCIÓN 3: Mostrar un indicador de carga mientras cancionActual es nulo.
        if (cancionActual == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = cancionActual?.autor ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val lineasDeCancion = remember(cancionActual, semitonos, mostrarAcordes) {
                    cancionActual?.let {
                        procesarLetraParaVista(it.letraOriginal, semitonos, mostrarAcordes, it.tieneAcordes)
                    } ?: emptyList()
                }

                lineasDeCancion.forEach { (lineaDeAcordes, lineaDeLetra) ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (lineaDeAcordes.isNotBlank()) {
                            Text(
                                text = lineaDeAcordes,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Text(
                            text = lineaDeLetra,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun procesarLetraParaVista(
    letraOriginal: String,
    semitonos: Int,
    mostrarAcordes: Boolean,
    tieneAcordes: Boolean
): List<Pair<String, String>> {
    val lineasProcesadas = mutableListOf<Pair<String, String>>()
    // SOLUCIÓN 4: Usar "raw strings" para las expresiones regulares.
    val regexAcordes = Regex("""\[.*?\]""")

    letraOriginal.lines().forEach { lineaOriginal ->
        if (!tieneAcordes || !mostrarAcordes) {
            val letraLimpia = lineaOriginal.replace(regexAcordes, "")
            lineasProcesadas.add(Pair("", letraLimpia))
        } else {
            val lineaDeAcordes = StringBuilder()
            val lineaDeLetra = StringBuilder()
            var indiceActual = 0
            val regex = Regex("""\[(.*?)\]""")
            val matches = regex.findAll(lineaOriginal)

            matches.forEach { match ->
                val acordeOriginal = match.groupValues[1]
                val acordeTranspuesto = TonalidadUtil.transponerAcorde(acordeOriginal, semitonos)
                val textoEntreAcordes = lineaOriginal.substring(indiceActual, match.range.first)
                lineaDeLetra.append(textoEntreAcordes)
                val espacios = textoEntreAcordes.length
                lineaDeAcordes.append(" ".repeat(espacios))
                lineaDeAcordes.append(acordeTranspuesto)
                indiceActual = match.range.last + 1
            }

            lineaDeLetra.append(lineaOriginal.substring(indiceActual))
            lineasProcesadas.add(Pair(lineaDeAcordes.toString(), lineaDeLetra.toString()))
        }
    }
    return lineasProcesadas
}
