package com.letrasacordes.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.theme.ApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavegacionApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalCanciones(
    modifier: Modifier = Modifier,
    onAgregarCancionClick: () -> Unit,
    onConfiguracionClick: () -> Unit,
    onCancionClick: (Int) -> Unit,
    cancionesViewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val textoBusqueda by cancionesViewModel.textoBusqueda.collectAsState()
    val listaCanciones by cancionesViewModel.canciones.collectAsState()
    val todasLasCanciones by cancionesViewModel.todasLasCanciones.collectAsState()
    val categorias by cancionesViewModel.categorias.collectAsState()
    val categoriaSeleccionada by cancionesViewModel.categoriaSeleccionada.collectAsState()

    var mostrarDialogoCrearCategoria by remember { mutableStateOf(false) }
    var categoriaParaEditar by remember { mutableStateOf<String?>(null) }
    var categoriaParaEliminar by remember { mutableStateOf<String?>(null) }
    var isInEditMode by remember { mutableStateOf(false) }

    // --- DIÁLOGOS ---
    if (mostrarDialogoCrearCategoria) {
        DialogoCrearEditarCategoria(
            todasLasCanciones = todasLasCanciones,
            onDismiss = { mostrarDialogoCrearCategoria = false },
            onConfirm = { nombre, ids ->
                cancionesViewModel.guardarCategoria(nombre, ids)
                mostrarDialogoCrearCategoria = false
            }
        )
    }

    categoriaParaEditar?.let {
        DialogoCrearEditarCategoria(
            nombreOriginal = it,
            idsCancionesActuales = categorias[it] ?: emptySet(),
            todasLasCanciones = todasLasCanciones,
            onDismiss = { categoriaParaEditar = null },
            onConfirm = { nombreNuevo, ids ->
                cancionesViewModel.actualizarCategoria(it, nombreNuevo, ids)
                categoriaParaEditar = null
            }
        )
    }

    categoriaParaEliminar?.let {
        AlertDialog(
            onDismissRequest = { categoriaParaEliminar = null },
            title = { Text("Eliminar Categoría") },
            text = { Text("¿Estás seguro de que quieres eliminar la categoría '$it'?") },
            confirmButton = {
                Button(
                    onClick = {
                        cancionesViewModel.eliminarCategoria(it)
                        categoriaParaEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { categoriaParaEliminar = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Letras y Acordes") },
                actions = {
                    IconButton(onClick = onAgregarCancionClick) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar canción")
                    }
                    IconButton(onClick = onConfiguracionClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = cancionesViewModel::enTextoBusquedaCambiado,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Buscar por título, autor o letra...") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = categoriaSeleccionada == null,
                            onClick = { if (!isInEditMode) cancionesViewModel.seleccionarCategoria(null) },
                            label = { Text("Todas") }
                        )
                    }
                    items(categorias.keys.toList()) { nombreCategoria ->
                        if (isInEditMode) {
                            InputChip(
                                selected = false,
                                onClick = { categoriaParaEditar = nombreCategoria },
                                label = { Text(nombreCategoria) },
                                trailingIcon = {
                                    IconButton(onClick = { categoriaParaEliminar = nombreCategoria }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Categoría")
                                    }
                                }
                            )
                        } else {
                            FilterChip(
                                selected = categoriaSeleccionada == nombreCategoria,
                                onClick = { cancionesViewModel.seleccionarCategoria(nombreCategoria) },
                                label = { Text(nombreCategoria) }
                            )
                        }
                    }
                }
                // Botones de acción para categorías
                IconButton(onClick = { mostrarDialogoCrearCategoria = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Crear nueva categoría")
                }
                IconButton(onClick = { isInEditMode = !isInEditMode }) {
                    Icon(
                        if (isInEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isInEditMode) "Finalizar edición" else "Editar categorías"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(listaCanciones) { cancion ->
                    CancionItem(
                        cancion = cancion,
                        modifier = Modifier.clickable { onCancionClick(cancion.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCrearEditarCategoria(
    nombreOriginal: String? = null,
    idsCancionesActuales: Set<Int> = emptySet(),
    todasLasCanciones: List<Cancion>,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<Int>) -> Unit
) {
    var nombreCategoria by remember { mutableStateOf(nombreOriginal ?: "") }
    val cancionesSeleccionadas = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            idsCancionesActuales.forEach { put(it, true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nombreOriginal == null) "Crear Nueva Categoría" else "Editar Categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = nombreCategoria,
                    onValueChange = { nombreCategoria = it },
                    label = { Text("Nombre de la categoría") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    items(todasLasCanciones) { cancion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val isSelected = cancionesSeleccionadas[cancion.id] ?: false
                                    cancionesSeleccionadas[cancion.id] = !isSelected
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = cancionesSeleccionadas.getOrDefault(cancion.id, false),
                                onCheckedChange = { isSelected -> cancionesSeleccionadas[cancion.id] = isSelected }
                            )
                            Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val idsSeleccionados = cancionesSeleccionadas.filter { it.value }.keys
                    onConfirm(nombreCategoria, idsSeleccionados)
                },
                enabled = nombreCategoria.isNotBlank() && cancionesSeleccionadas.any { it.value }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}


@Composable
fun CancionItem(cancion: Cancion, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = cancion.titulo,
                style = MaterialTheme.typography.titleLarge
            )
            if (!cancion.autor.isNullOrBlank()) {
                Text(
                    text = cancion.autor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CancionItemPreview() {
    ApplicationTheme {
        CancionItem(
            cancion = Cancion(
                0,
                "Título de Ejemplo",
                "Autor de Ejemplo",
                "", true, "C", "", 0, 0
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaPrincipalPreview() {
    ApplicationTheme {
        PantallaPrincipalCanciones(
            onAgregarCancionClick = {},
            onConfiguracionClick = {},
            onCancionClick = {}
        )
    }
}
