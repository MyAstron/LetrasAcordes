package com.letrasacordes.application

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.theme.ApplicationTheme
import com.letrasacordes.application.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val _intentUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _intentUri.value = intent?.data

        setContent {
            ApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
                    val context = LocalContext.current
                    val uri by _intentUri

                    LaunchedEffect(uri) {
                        uri?.let { safeUri ->
                            try {
                                context.contentResolver.openInputStream(safeUri)?.use { inputStream ->
                                    val bytes = inputStream.readBytes()
                                    val count = viewModel.importarCanciones(bytes)
                                    Toast.makeText(context, "$count canciones importadas", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                                e.printStackTrace()
                            }
                            _intentUri.value = null
                        }
                    }

                    NavegacionApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
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
    onSecretBack: () -> Unit = {},
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
    var mostrarDialogoImprimir by remember { mutableStateOf(false) }

    val cancionesSeleccionadasParaImprimir = remember { mutableStateMapOf<Int, Boolean>() }
    var imprimirConAcordes by remember { mutableStateOf(true) }
    var incluirIndice by remember { mutableStateOf(true) }
    var modoCompacto by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    BackHandler {
        (context as? Activity)?.finish()
    }

    // Launcher para PDF con compartir inmediato
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { safeUri ->
            scope.launch {
                val cancionesAImprimir = todasLasCanciones.filter { cancion -> cancionesSeleccionadasParaImprimir.getOrDefault(cancion.id, false) }
                cancionesViewModel.generarPdf(cancionesAImprimir, imprimirConAcordes, incluirIndice, modoCompacto, safeUri)
                
                // Abrir menú de compartir inmediatamente después de generar
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, safeUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir Cancionero"))
            }
        }
    }

    // --- DIÁLOGOS DE CATEGORÍAS ---
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

    categoriaParaEditar?.let { nombreCategoria ->
        DialogoCrearEditarCategoria(
            nombreOriginal = nombreCategoria,
            idsCancionesActuales = categorias[nombreCategoria] ?: emptyList(),
            todasLasCanciones = todasLasCanciones,
            onDismiss = { categoriaParaEditar = null },
            onConfirm = { nombreNuevo, ids ->
                cancionesViewModel.actualizarCategoria(nombreCategoria, nombreNuevo, ids)
                categoriaParaEditar = null
            }
        )
    }

    categoriaParaEliminar?.let { nombreCategoria ->
        AlertDialog(
            onDismissRequest = { categoriaParaEliminar = null },
            title = { Text("Eliminar Categoría") },
            text = { Text("¿Estás seguro de que quieres eliminar la categoría '$nombreCategoria'?") },
            confirmButton = { 
                Button(
                    onClick = { cancionesViewModel.eliminarCategoria(nombreCategoria); categoriaParaEliminar = null }, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Eliminar", color = Color.White) } 
            },
            dismissButton = { TextButton(onClick = { categoriaParaEliminar = null }) { Text("Cancelar") } }
        )
    }

    if (mostrarDialogoImprimir) {
        DialogoImprimir(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
            cancionesSeleccionadas = cancionesSeleccionadasParaImprimir,
            incluirAcordes = imprimirConAcordes,
            onIncluirAcordesChange = { imprimirConAcordes = it },
            incluirIndice = incluirIndice,
            onIncluirIndiceChange = { incluirIndice = it },
            modoCompacto = modoCompacto,
            onModoCompactoChange = { modoCompacto = it },
            onDismiss = { mostrarDialogoImprimir = false },
            onConfirm = {
                pdfLauncher.launch("Cancionero_Melodias.pdf")
                mostrarDialogoImprimir = false
            }
        )
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(AzulProfundo, AzulMedio, CianBrillante.copy(alpha = 0.5f))
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarDialogoImprimir = true },
                containerColor = CianBrillante,
                contentColor = AzulProfundo
            ) {
                Icon(Icons.Default.Print, contentDescription = "Imprimir/Compartir")
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("MELODÍAS", fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White) 
                },
                actions = {
                    IconButton(onClick = onAgregarCancionClick) { 
                        Icon(Icons.Default.AddCircle, "Agregar", tint = Color.White) 
                    }
                    IconButton(onClick = onConfiguracionClick) { 
                        Icon(Icons.Default.Tune, "Ajustes", tint = Color.White) 
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = AzulProfundo,
        modifier = modifier.background(backgroundGradient)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(backgroundGradient)
        ) {
            // Buscador
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = cancionesViewModel::enTextoBusquedaCambiado,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("¿Qué quieres tocar hoy?", color = Color.White.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CianBrillante,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                singleLine = true
            )

            // Fila de Categorías con Edición
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
                            label = { Text("Todas") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CianBrillante, selectedLabelColor = AzulProfundo, labelColor = Color.White)
                        )
                    }
                    items(categorias.keys.toList()) { cat ->
                        if (isInEditMode) {
                            InputChip(
                                selected = false,
                                onClick = { categoriaParaEditar = cat },
                                label = { Text(cat) },
                                trailingIcon = { 
                                    IconButton(onClick = { categoriaParaEliminar = cat }, modifier = Modifier.size(18.dp)) { 
                                        Icon(Icons.Default.Close, null, tint = Color.Red) 
                                    } 
                                },
                                colors = InputChipDefaults.inputChipColors(containerColor = Color.White.copy(alpha = 0.2f), labelColor = Color.White)
                            )
                        } else {
                            FilterChip(
                                selected = categoriaSeleccionada == cat,
                                onClick = { cancionesViewModel.seleccionarCategoria(cat) },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CianBrillante, selectedLabelColor = AzulProfundo, labelColor = Color.White)
                            )
                        }
                    }
                }
                
                IconButton(onClick = { mostrarDialogoCrearCategoria = true }) { 
                    Icon(Icons.Default.CreateNewFolder, "Crear", tint = CianBrillante) 
                }
                
                if (categorias.isNotEmpty()) {
                    IconButton(onClick = { isInEditMode = !isInEditMode }) { 
                        Icon(
                            if (isInEditMode) Icons.Default.Check else Icons.Default.Edit, 
                            null, 
                            tint = if (isInEditMode) Color.Green else CianBrillante
                        ) 
                    }
                }
            }

            // Lista de canciones
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(listaCanciones) { index, cancion ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { 40 * (index + 1) }) + fadeIn()
                    ) {
                        CardPlantilla(cancion = cancion, onClick = { onCancionClick(cancion.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCrearEditarCategoria(
    nombreOriginal: String? = null,
    idsCancionesActuales: List<Int> = emptyList(),
    todasLasCanciones: List<Cancion>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Int>) -> Unit
) {
    var nombre by remember { mutableStateOf(nombreOriginal ?: "") }
    val seleccionadas = remember { mutableStateMapOf<Int, Boolean>().apply { idsCancionesActuales.forEach { put(it, true) } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nombreOriginal == null) "Nueva Categoría" else "Editar Categoría") },
        text = {
            Column {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                Text("Selecciona canciones:", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(todasLasCanciones) { cancion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { seleccionadas[cancion.id] = !(seleccionadas[cancion.id] ?: false) }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = seleccionadas.getOrDefault(cancion.id, false), onCheckedChange = null)
                            Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { 
                    val ids = seleccionadas.filter { it.value }.keys.toList()
                    onConfirm(nombre, ids) 
                }, 
                enabled = nombre.isNotBlank() && seleccionadas.any { it.value }
            ) { Text("Guardar") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoImprimir(
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, List<Int>>,
    cancionesSeleccionadas: MutableMap<Int, Boolean>,
    incluirAcordes: Boolean,
    onIncluirAcordesChange: (Boolean) -> Unit,
    incluirIndice: Boolean,
    onIncluirIndiceChange: (Boolean) -> Unit,
    modoCompacto: Boolean,
    onModoCompactoChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generar Cancionero (PDF)") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirAcordesChange(!incluirAcordes) }) {
                            Checkbox(checked = incluirAcordes, onCheckedChange = null)
                            Text("Incluir Acordes", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirIndiceChange(!incluirIndice) }) {
                            Checkbox(checked = incluirIndice, onCheckedChange = null)
                            Text("Incluir Índice", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModoCompactoChange(!modoCompacto) }) {
                            Checkbox(checked = modoCompacto, onCheckedChange = null)
                            Text("Modo Compacto (Ahorrar papel)", modifier = Modifier.padding(start = 8.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Listas rápidas:", style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(categorias.keys.toList()) { name ->
                    val ids = categorias[name] ?: emptyList()
                    val allSelected = ids.all { cancionesSeleccionadas.getOrDefault(it, false) }
                    Row(modifier = Modifier.fillMaxWidth().clickable { val newState = !allSelected; ids.forEach { cancionesSeleccionadas[it] = newState } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(state = if (allSelected) ToggleableState.On else if (ids.any { cancionesSeleccionadas.getOrDefault(it, false) }) ToggleableState.Indeterminate else ToggleableState.Off, onClick = null)
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)); Text("Canciones:", style = MaterialTheme.typography.labelLarge) }
                items(todasLasCanciones) { cancion ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { cancionesSeleccionadas[cancion.id] = !(cancion.id in cancionesSeleccionadas && cancionesSeleccionadas[cancion.id]!!) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cancionesSeleccionadas.getOrDefault(cancion.id, false), onCheckedChange = null)
                        Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = cancionesSeleccionadas.any { it.value }) { Text("Generar PDF") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CardPlantilla(cancion: Cancion, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(CianBrillante, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = AzulProfundo)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = cancion.titulo, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = cancion.autor ?: "Autor desconocido", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
            if (!cancion.ritmo.isNullOrBlank()) {
                Surface(color = AzulMedio, shape = RoundedCornerShape(8.dp)) {
                    Text(text = cancion.ritmo!!, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = CianBrillante)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}
