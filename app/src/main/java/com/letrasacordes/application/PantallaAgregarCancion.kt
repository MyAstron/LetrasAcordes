package com.letrasacordes.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAgregarCancion(
    onNavegarAtras: () -> Unit,
    // Usamos el mismo ViewModel para centralizar la lógica de la base de datos
    cancionesViewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    // 1. Estados para manejar los inputs del usuario
    var titulo by remember { mutableStateOf("") }
    var autor by remember { mutableStateOf("") }
    var letra by remember { mutableStateOf("") }
    var tieneAcordes by remember { mutableStateOf(true) } // Por defecto, asumimos que sí

    // Estado para la validación del formulario
    val esFormularioValido = titulo.isNotBlank() && letra.isNotBlank()

    // Scope para llamar a la función suspendida del ViewModel
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar Nueva Canción") },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    // Botón de Guardar en la barra superior
                    IconButton(
                        onClick = {
                            scope.launch {
                                cancionesViewModel.agregarCancion(
                                    titulo = titulo,
                                    autor = autor,
                                    letra = letra,
                                    tieneAcordes = tieneAcordes
                                )
                                // Después de guardar, regresamos a la pantalla anterior
                                onNavegarAtras()
                            }
                        },
                        // El botón solo está activo si el formulario es válido
                        enabled = esFormularioValido
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Guardar Canción"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // 2. Contenido del Formulario
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                // Hacemos que la columna sea scrollable para que el teclado no oculte los campos
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio entre elementos
        ) {
            // Campo para el Título
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título de la canción (obligatorio)") },
                singleLine = true,
                isError = titulo.isBlank() // Mostramos error si está vacío (opcional)
            )

            // Campo para el Autor
            OutlinedTextField(
                value = autor,
                onValueChange = { autor = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Autor (opcional)") },
                singleLine = true
            )

            // Campo para la Letra (grande)
            OutlinedTextField(
                value = letra,
                onValueChange = { letra = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp), // Altura considerable para la letra
                label = { Text("Letra y Acordes (obligatorio)") },
                // Mostramos un texto de ayuda sobre la sintaxis
                placeholder = {
                    Text(
                        "Escribe aquí la letra.\nUsa corchetes para los acordes, ej: [Am]Te co[G]nozco.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            // Checkbox para los acordes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = tieneAcordes,
                    onCheckedChange = { tieneAcordes = it }
                )
                Text("La letra tiene acordes")
            }
        }
    }
}
