package com.letrasacordes.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditarCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()

    // 1. Carga la canción una sola vez o cuando el ID cambia.
    LaunchedEffect(cancionId) {
        viewModel.cargarCancion(cancionId)
    }
    // 2. Observa el estado de la canción desde el flujo estable del ViewModel.
    val cancion by viewModel.cancionSeleccionada.collectAsState()

    // 3. El estado local se actualiza de forma segura cuando la canción se carga.
    //    Usar `remember` con una clave en `cancion` asegura que el estado se reinicie
    //    solo cuando se carga una canción diferente.
    var titulo by remember(cancion) { mutableStateOf(cancion?.titulo ?: "") }
    var autor by remember(cancion) { mutableStateOf(cancion?.autor ?: "") }
    var letraOriginal by remember(cancion) { mutableStateOf(cancion?.letraOriginal ?: "") }
    var tieneAcordes by remember(cancion) { mutableStateOf(cancion?.tieneAcordes ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Canción") },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar y regresar")
                    }
                },
                actions = {
                    IconButton(
                        enabled = cancion != null, // Habilita el botón solo si la canción se ha cargado
                        onClick = {
                            val cancionParaActualizar = cancion
                            if (cancionParaActualizar != null) {
                                val cancionActualizada = cancionParaActualizar.copy(
                                    titulo = titulo,
                                    autor = autor.takeIf { it.isNotBlank() },
                                    letraOriginal = letraOriginal,
                                    tieneAcordes = tieneAcordes
                                )
                                scope.launch {
                                    viewModel.actualizarCancion(cancionActualizada)
                                    onNavegarAtras()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar Cambios")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (cancion == null) {
            // Muestra un indicador de carga mientras la canción es nula.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título de la canción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = autor,
                    onValueChange = { autor = it },
                    label = { Text("Autor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = letraOriginal,
                    onValueChange = { letraOriginal = it },
                    label = { Text("Letra y Acordes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    supportingText = { Text("Usa el formato [Am] para los acordes en línea.") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = tieneAcordes,
                        onCheckedChange = { tieneAcordes = it }
                    )
                    Text("La canción tiene acordes")
                }
            }
        }
    }
}
