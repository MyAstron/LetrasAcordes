package com.letrasacordes.application

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConfiguracion(
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var mostrarDialogoExportar by remember { mutableStateOf(false) }
    val todasLasCanciones by viewModel.canciones.collectAsState()
    // CORRECCIÓN 1: Usar `mutableStateMapOf` para que Compose observe los cambios.
    val cancionesSeleccionadas = remember { mutableStateMapOf<Int, Boolean>() }

    val importadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val textoImportado = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                        if (!textoImportado.isNullOrBlank()) {
                            val cancionesImportadas = viewModel.importarCanciones(textoImportado)
                            snackbarHostState.showSnackbar("$cancionesImportadas canciones importadas con éxito")
                        } else {
                            snackbarHostState.showSnackbar("Error: El archivo está vacío o no se pudo leer.")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al importar el archivo: ${e.message}")
                    }
                }
            }
        }
    )

    val exportadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val cancionesAExportar = todasLasCanciones.filter { cancionesSeleccionadas.getOrDefault(it.id, false) }
                        val textoAExportar = viewModel.exportarCanciones(cancionesAExportar)
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(textoAExportar.toByteArray())
                        }
                        snackbarHostState.showSnackbar("${cancionesAExportar.size} canciones exportadas con éxito.")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al exportar el archivo: ${e.message}")
                    } finally {
                        // CORRECCIÓN 3: Limpiar siempre la selección después de terminar.
                        cancionesSeleccionadas.clear()
                    }
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Importar y Exportar") },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { importadorLauncher.launch("*/*") }) {
                Text("Importar Canciones desde Archivo")
            }

            Button(onClick = { mostrarDialogoExportar = true }) {
                Text("Exportar Canciones a Archivo")
            }
        }
    }

    if (mostrarDialogoExportar) {
        DialogoSeleccionExportar(
            canciones = todasLasCanciones,
            cancionesSeleccionadas = cancionesSeleccionadas, // Pasamos el mapa observable
            onDismiss = {
                mostrarDialogoExportar = false
                cancionesSeleccionadas.clear() // Limpiar al cancelar
            },
            onConfirm = {
                mostrarDialogoExportar = false
                exportadorLauncher.launch("cancionero.txt")
            }
        )
    }
}

@Composable
fun DialogoSeleccionExportar(
    canciones: List<Cancion>,
    // CORRECCIÓN 2: Recibir el mapa de estado observable en lugar de uno global.
    cancionesSeleccionadas: SnapshotStateMap<Int, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Canciones") },
        text = {
            LazyColumn {
                items(canciones) { cancion ->
                    val isChecked = cancionesSeleccionadas.getOrDefault(cancion.id, false)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cancionesSeleccionadas[cancion.id] = !isChecked
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { nuevoEstado ->
                                cancionesSeleccionadas[cancion.id] = nuevoEstado
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(cancion.titulo)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = cancionesSeleccionadas.any { it.value }
            ) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
