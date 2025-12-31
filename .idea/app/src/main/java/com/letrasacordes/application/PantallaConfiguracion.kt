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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
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
    val todasLasCanciones by viewModel.todasLasCanciones.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val cancionesSeleccionadas = remember { mutableStateMapOf<Int, Boolean>() }

    // Importador actualizado para leer Bytes (GZIP)
    val importadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        // Leemos Bytes para soportar GZIP
                        val datosImportados = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (datosImportados != null && datosImportados.isNotEmpty()) {
                            val cancionesImportadas = viewModel.importarCanciones(datosImportados)
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

    // Exportador actualizado para escribir Bytes (GZIP)
    val exportadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val cancionesAExportar = todasLasCanciones.filter { cancionesSeleccionadas.getOrDefault(it.id, false) }
                        
                        // Obtenemos los bytes comprimidos del ViewModel
                        val datosAExportar = viewModel.exportarCanciones(cancionesAExportar)
                        
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(datosAExportar)
                        }
                        snackbarHostState.showSnackbar("${cancionesAExportar.size} canciones exportadas")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al exportar el archivo: ${e.message}")
                    } finally {
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
                Text("Importar Canciones (.book)")
            }

            Button(onClick = { mostrarDialogoExportar = true }) {
                Text("Exportar Canciones a .book")
            }
        }
    }

    if (mostrarDialogoExportar) {
        DialogoExportacionAvanzado(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
            cancionesSeleccionadas = cancionesSeleccionadas,
            onDismiss = {
                mostrarDialogoExportar = false
                cancionesSeleccionadas.clear()
            },
            onConfirm = {
                mostrarDialogoExportar = false
                exportadorLauncher.launch("cancionero.book (1)")
            }
        )
    }
}

@Composable
fun DialogoExportacionAvanzado(
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, Set<Int>>,
    cancionesSeleccionadas: MutableMap<Int, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Canciones") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text("Listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(categorias.keys.toList()) { nombreCategoria ->
                    val idsEnCategoria = categorias[nombreCategoria] ?: emptySet()
                    val cancionesSeleccionadasEnCategoria = idsEnCategoria.count { cancionesSeleccionadas.getOrDefault(it, false) }

                    val checkboxState = when {
                        cancionesSeleccionadasEnCategoria == 0 -> ToggleableState.Off
                        cancionesSeleccionadasEnCategoria == idsEnCategoria.size && idsEnCategoria.isNotEmpty() -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = checkboxState != ToggleableState.On
                                idsEnCategoria.forEach { id -> cancionesSeleccionadas[id] = newState }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(state = checkboxState, onClick = null)
                        Text(nombreCategoria, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

                item {
                    Text("Canciones Individuales", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(todasLasCanciones) { cancion ->
                    val isChecked = cancionesSeleccionadas.getOrDefault(cancion.id, false)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cancionesSeleccionadas[cancion.id] = !isChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                        Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
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