
package com.letrasacordes.application

import androidx.compose.animation.core.copy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

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
import com.letrasacordes.application.database.Cancion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditarCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    val cancion by viewModel.obtenerCancion(cancionId).collectAsState()

    var titulo by remember { mutableStateOf("") }
    var autor by remember { mutableStateOf("") }
    // --- CORRECCIÓN 1: 'mutableStateOf' con 'f' minúscula ---
    var letraOriginal by remember { mutableStateOf("") }
    var tieneAcordes by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { cancion }
            .collect { cancionCargada ->
                if (cancionCargada != null) {
                    titulo = cancionCargada.titulo
                    autor = cancionCargada.autor ?: ""
                    letraOriginal = cancionCargada.letraOriginal
                    tieneAcordes = cancionCargada.tieneAcordes
                }
            }
    }

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
                        enabled = cancion != null,
                        onClick = {
                            cancion?.let { cancionParaActualizar ->
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
                singleLine = true,
                enabled = cancion != null
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = autor,
                onValueChange = { autor = it },
                label = { Text("Autor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = cancion != null
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = letraOriginal,
                onValueChange = { letraOriginal = it },
                label = { Text("Letra y Acordes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                supportingText = { Text("Usa el formato [Am] para los acordes en línea.") },
                // --- CORRECCIÓN 2: 'enabled' va dentro del paréntesis del TextField ---
                enabled = cancion != null
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = tieneAcordes,
                    onCheckedChange = { tieneAcordes = it },
                    // --- CORRECCIÓN 3: 'enabled' va dentro del paréntesis del Checkbox ---
                    enabled = cancion != null
                )
                Text("La canción tiene acordes")
            }
        }
    }
}
