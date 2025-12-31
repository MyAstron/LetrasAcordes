package com.letrasacordes.application

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.theme.ApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Usamos un estado observable para la URI del intent
    private val _intentUri = mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar con el intent de arranque
        _intentUri.value = intent?.data
        
        setContent {
            ApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
                    val scope = rememberCoroutineScope()
                    // Observamos el estado mutable que actualizaremos desde onNewIntent
                    val currentUri by _intentUri

                    // Usamos una clave única basada en la URI para reiniciar el efecto solo cuando cambie el archivo
                    LaunchedEffect(currentUri) {
                        currentUri?.let { uri ->
                            try {
                                contentResolver.openInputStream(uri)?.use { stream ->
                                    // CORRECCIÓN: Leemos como bytes para soportar GZIP
                                    val datosImportados = stream.readBytes()
                                    if (datosImportados.isNotEmpty()) {
                                        val count = viewModel.importarCanciones(datosImportados)
                                        Toast.makeText(this@MainActivity, "$count canciones importadas", Toast.LENGTH_LONG).show()
                                        // Limpiamos el intent para no reimportar al rotar pantalla
                                        intent.data = null
                                        _intentUri.value = null
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    NavegacionApp()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Actualizamos el intent de la actividad y nuestro estado observable
        setIntent(intent)
        _intentUri.value = intent.data
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
    // YA NO NECESITAMOS LA LÓGICA DE BACKHANDLER
    // El sistema de navegación manejará el retroceso y nos llevará al Easter Egg automáticamente

    val textoBusqueda by cancionesViewModel.textoBusqueda.collectAsState()
    val listaCanciones by cancionesViewModel.canciones.collectAsState()
    val todasLasCanciones by cancionesViewModel.todasLasCanciones.collectAsState()
    val categorias by cancionesViewModel.categorias.collectAsState()
    val categoriaSeleccionada by cancionesViewModel.categoriaSeleccionada.collectAsState()

    // Ordenar listas alfabéticamente ignorando mayúsculas/minúsculas
    val listaCancionesOrdenadas = remember(listaCanciones) {
        listaCanciones.sortedBy { it.titulo.lowercase() }
    }
    val todasLasCancionesOrdenadas = remember(todasLasCanciones) {
        todasLasCanciones.sortedBy { it.titulo.lowercase() }
    }

    var mostrarDialogoCrearCategoria by remember { mutableStateOf(false) }
    var categoriaParaEditar by remember { mutableStateOf<String?>(null) }
    var categoriaParaEliminar by remember { mutableStateOf<String?>(null) }
    var isInEditMode by remember { mutableStateOf(false) }
    var mostrarDialogoImprimir by remember { mutableStateOf(false) }

    val cancionesSeleccionadasParaImprimir = remember { mutableStateMapOf<Int, Boolean>() }
    var imprimirConAcordes by remember { mutableStateOf(true) }
    var imprimirIndice by remember { mutableStateOf(true) }
    var modoCompacto by remember { mutableStateOf(true) } // NUEVO ESTADO PARA MODO COMPACTO (Default: true)

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            // CORRECCIÓN: Usar sintaxis Kotlin [key] == true en lugar de getOrDefault
            val cancionesAImprimir = todasLasCancionesOrdenadas.filter { cancion -> 
                cancionesSeleccionadasParaImprimir[cancion.id] == true 
            }
            // Pasamos el parámetro 'compactMode' también
            cancionesViewModel.generarPdf(cancionesAImprimir, imprimirConAcordes, imprimirIndice, modoCompacto, it)
        }
    }

    // --- DIÁLOGOS ---
    if (mostrarDialogoCrearCategoria) {
        DialogoCrearEditarCategoria(
            todasLasCanciones = todasLasCancionesOrdenadas,
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
            todasLasCanciones = todasLasCancionesOrdenadas,
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
            confirmButton = { Button(onClick = { cancionesViewModel.eliminarCategoria(it); categoriaParaEliminar = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { categoriaParaEliminar = null }) { Text("Cancelar") } }
        )
    }

    if (mostrarDialogoImprimir) {
        DialogoImprimir(
            todasLasCanciones = todasLasCancionesOrdenadas,
            categorias = categorias,
            cancionesSeleccionadas = cancionesSeleccionadasParaImprimir,
            incluirIndice = imprimirIndice,
            onIncluirIndiceChange = { imprimirIndice = it },
            incluirAcordes = imprimirConAcordes,
            onIncluirAcordesChange = { imprimirConAcordes = it },
            compactMode = modoCompacto,
            onCompactModeChange = { modoCompacto = it },
            onDismiss = { mostrarDialogoImprimir = false },
            onConfirm = {
                pdfLauncher.launch("Cancionero.pdf")
                mostrarDialogoImprimir = false
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogoImprimir = true }) {
                Icon(Icons.Default.Print, contentDescription = "Imprimir Cancionero")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Letras y Acordes") },
                actions = {
                    IconButton(onClick = onAgregarCancionClick) { Icon(Icons.Default.ControlPoint, "Agregar canción") }
                    IconButton(onClick = onConfiguracionClick) { Icon(Icons.Default.Settings, "Configuración") }
                }
            )
        },
        bottomBar = {
            // Texto de derechos de autor en la parte inferior
            Text(
                text = "2025 © ${stringResource(id = R.string.desarrollador_nombre)}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = cancionesViewModel::enTextoBusquedaCambiado,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Buscar por título, autor o letra...") },
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = categoriaSeleccionada == null, onClick = { if (!isInEditMode) cancionesViewModel.seleccionarCategoria(null) }, label = { Text("Todas") }) }
                    items(categorias.keys.toList()) { nombreCategoria ->
                        if (isInEditMode) {
                            InputChip(
                                selected = false,
                                onClick = { categoriaParaEditar = nombreCategoria },
                                label = { Text(nombreCategoria) },
                                trailingIcon = { IconButton(onClick = { categoriaParaEliminar = nombreCategoria }) { Icon(Icons.Default.Close, "Eliminar Categoría") } }
                            )
                        } else {
                            FilterChip(selected = categoriaSeleccionada == nombreCategoria, onClick = { cancionesViewModel.seleccionarCategoria(nombreCategoria) }, label = { Text(nombreCategoria) })
                        }
                    }
                }
                IconButton(onClick = { mostrarDialogoCrearCategoria = true }) { Icon(Icons.Default.CreateNewFolder, "Crear categoría") }
                
                // Botón de editar categorías: visible solo si hay categorías
                if (categorias.isNotEmpty()) {
                    IconButton(onClick = { isInEditMode = !isInEditMode }) { 
                        Icon(if (isInEditMode) Icons.Default.Check else Icons.Default.Edit, 
                             if (isInEditMode) "Finalizar edición" else "Editar categorías") 
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(listaCancionesOrdenadas) { cancion ->
                    CancionItem(cancion = cancion, modifier = Modifier.clickable { onCancionClick(cancion.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCrearEditarCategoria(nombreOriginal: String? = null, idsCancionesActuales: Set<Int> = emptySet(), todasLasCanciones: List<Cancion>, onDismiss: () -> Unit, onConfirm: (String, Set<Int>) -> Unit) {
    var nombreCategoria by remember { mutableStateOf(nombreOriginal ?: "") }
    val cancionesSeleccionadas = remember { mutableStateMapOf<Int, Boolean>().apply { idsCancionesActuales.forEach { put(it, true) } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nombreOriginal == null) "Crear Nueva Categoría" else "Editar Categoría") },
        text = { 
            Column { 
                OutlinedTextField(value = nombreCategoria, onValueChange = { nombreCategoria = it }, label = { Text("Nombre de la categoría") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                LazyColumn { 
                    items(todasLasCanciones) { cancion -> 
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    val isSelected = cancionesSeleccionadas[cancion.id] == true
                                    cancionesSeleccionadas[cancion.id] = !isSelected 
                                }, 
                            verticalAlignment = Alignment.CenterVertically
                        ) { 
                            // CORRECCIÓN: Usar [key] == true
                            Checkbox(
                                checked = cancionesSeleccionadas[cancion.id] == true, 
                                onCheckedChange = { isSelected -> cancionesSeleccionadas[cancion.id] = isSelected }
                            )
                            Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp)) 
                        } 
                    } 
                } 
            } 
        },
        confirmButton = { Button(onClick = { onConfirm(nombreCategoria, cancionesSeleccionadas.filter { it.value }.keys) }, enabled = nombreCategoria.isNotBlank() && cancionesSeleccionadas.any { it.value }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoImprimir(
    todasLasCanciones: List<Cancion>, 
    categorias: Map<String, Set<Int>>, 
    cancionesSeleccionadas: MutableMap<Int, Boolean>, 
    incluirAcordes: Boolean, 
    onIncluirAcordesChange: (Boolean) -> Unit, 
    incluirIndice: Boolean,
    onIncluirIndiceChange: (Boolean) -> Unit,
    compactMode: Boolean, // NUEVO PARÁMETRO
    onCompactModeChange: (Boolean) -> Unit, // NUEVO CALLBACK
    onDismiss: () -> Unit, 
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imprimir Cancionero (.pdf)") },
        text = {
            Column {
                // Opción: Incluir Acordes
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirAcordesChange(!incluirAcordes) }) {
                    Checkbox(checked = incluirAcordes, onCheckedChange = null)
                    Text("Incluir Acordes", modifier = Modifier.padding(start = 8.dp))
                }
                
                // Opción: Incluir Índice
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirIndiceChange(!incluirIndice) }) {
                    Checkbox(checked = incluirIndice, onCheckedChange = null)
                    Text("Incluir Índice", modifier = Modifier.padding(start = 8.dp))
                }
                
                // Opción: Modo Compacto
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCompactModeChange(!compactMode) }) {
                    Checkbox(checked = compactMode, onCheckedChange = null)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Modo Compacto")
                        Text(
                            text = if (compactMode) "Canciones seguidas (Ahorra papel)" else "Una canción por página",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item { Text("Listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                    items(categorias.keys.toList()) { nombreCategoria ->
                        val idsEnCategoria = categorias[nombreCategoria] ?: emptySet()
                        val cancionesSeleccionadasEnCategoria = idsEnCategoria.count { cancionesSeleccionadas[it] == true }
                        val checkboxState = when {
                            cancionesSeleccionadasEnCategoria == 0 -> ToggleableState.Off
                            cancionesSeleccionadasEnCategoria == idsEnCategoria.size && idsEnCategoria.isNotEmpty() -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        Row(modifier = Modifier.fillMaxWidth().clickable { val newState = checkboxState != ToggleableState.On; idsEnCategoria.forEach { id -> cancionesSeleccionadas[id] = newState } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(state = checkboxState, onClick = null)
                            Text(nombreCategoria, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }
                    item { Text("Canciones Individuales", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                    items(todasLasCanciones) { cancion ->
                        val isChecked = cancionesSeleccionadas[cancion.id] == true
                        Row(modifier = Modifier.fillMaxWidth().clickable { cancionesSeleccionadas[cancion.id] = !isChecked }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = cancionesSeleccionadas.any { it.value }) { Text("Imprimir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CancionItem(cancion: Cancion, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = cancion.titulo, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!cancion.autor.isNullOrBlank()) {
                    Text(text = cancion.autor, style = MaterialTheme.typography.bodyMedium)
                }
                
                // Muestra el tono original si existe
                if (cancion.tonoOriginal != null) {
                    Text(
                        text = " • ${cancion.tonoOriginal}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Muestra el ritmo si existe
                if (!cancion.ritmo.isNullOrBlank()) {
                    Text(
                        text = " • ${cancion.ritmo}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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
                id = 0, 
                titulo = "Título de Ejemplo", 
                autor = "Autor de Ejemplo", 
                ritmo = "Balada",
                letraOriginal = "", 
                tieneAcordes = true, 
                tonoOriginal = "C", 
                letraSinAcordes = "", 
                fechaCreacion = 0, 
                ultimaEdicion = 0
            )
        ) 
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaPrincipalPreview() {
    ApplicationTheme { PantallaPrincipalCanciones(onAgregarCancionClick = {}, onConfiguracionClick = {}, onCancionClick = {}) }
}
