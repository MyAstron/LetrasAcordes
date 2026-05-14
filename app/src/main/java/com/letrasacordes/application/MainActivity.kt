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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.theme.ApplicationTheme
import com.letrasacordes.application.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                        val safeUri = uri ?: return@LaunchedEffect
                        
                        if (safeUri.scheme == "app") {
                            // Deep link del widget - No procesar como archivo
                            return@LaunchedEffect
                        }

                        // Es un archivo .la o similar
                        try {
                            context.contentResolver.openInputStream(safeUri)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                val count = viewModel.importarCanciones(bytes)
                                Toast.makeText(context, "$count canciones importadas", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            // Solo mostramos el error si el esquema parece de archivo (content o file)
                            if (safeUri.scheme == "content" || safeUri.scheme == "file") {
                                Toast.makeText(context, "Error al importar archivo: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            e.printStackTrace()
                        }
                        _intentUri.value = null
                    }

                    NavegacionApp(
                        deepLinkUri = uri,
                        onDeepLinkHandled = { _intentUri.value = null }
                    )
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

    var cancionesAImprimir by remember { mutableStateOf<List<Cancion>>(emptyList()) }
    var imprimirConAcordes by remember { mutableStateOf(true) }
    var incluirIndice by remember { mutableStateOf(true) }
    var modoCompacto by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        cancionesViewModel.refrescarCategorias()
    }

    BackHandler {
        (context as? Activity)?.finish()
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { safeUri ->
            scope.launch {
                cancionesViewModel.generarPdf(cancionesAImprimir, imprimirConAcordes, incluirIndice, modoCompacto, safeUri)
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, safeUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir Cancionero"))
            }
        }
    }

    if (mostrarDialogoCrearCategoria) {
        DialogoCrearEditarCategoria(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
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
            categorias = categorias,
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
            cancionesIniciales = cancionesAImprimir,
            incluirAcordes = imprimirConAcordes,
            onIncluirAcordesChange = { imprimirConAcordes = it },
            incluirIndice = incluirIndice,
            onIncluirIndiceChange = { incluirIndice = it },
            modoCompacto = modoCompacto,
            onModoCompactoChange = { modoCompacto = it },
            onDismiss = { mostrarDialogoImprimir = false },
            onConfirm = { seleccionFinal ->
                cancionesAImprimir = seleccionFinal
                pdfLauncher.launch("Cancionero_Melodias.pdf")
                mostrarDialogoImprimir = false
            }
        )
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(AzulProfundo, AzulMedio, CianBrillante.copy(alpha = 0.5f))
    )

    val lazyListState = rememberLazyListState()
    val uniqueInitialLetters = remember(listaCanciones) {
        listaCanciones
            .map { it.titulo.first().uppercaseChar() }
            .filter { it.isLetter() }
            .distinct()
            .sorted()
    }
    
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    
    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        if (listaCanciones.isNotEmpty() && lazyListState.firstVisibleItemIndex < listaCanciones.size) {
            activeLetter = listaCanciones[lazyListState.firstVisibleItemIndex].titulo.first().uppercaseChar()
        }
    }

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

            val categoriasVisibles = remember(categorias) { 
                categorias.keys.filter { it != "WIDGET_SELECTION" && it != "LISTA_TEMPORAL_AUTO" }.toList()
            }

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
                    items(categoriasVisibles) { cat ->
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
            
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
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
                
                AlphabeticalIndex(
                    letters = uniqueInitialLetters,
                    activeLetter = activeLetter,
                    onLetterClick = { letter ->
                        scope.launch {
                            val index = listaCanciones.indexOfFirst { it.titulo.startsWith(letter, ignoreCase = true) }
                            if (index != -1) {
                                lazyListState.scrollToItem(index)
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun AlphabeticalIndex(
    letters: List<Char>,
    activeLetter: Char?,
    onLetterClick: (Char) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                color = if (letter == activeLetter) CianBrillante else Color.White.copy(alpha = 0.7f),
                fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable { onLetterClick(letter) }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCrearEditarCategoria(
    nombreOriginal: String? = null,
    idsCancionesActuales: List<Int> = emptyList(),
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, List<Int>>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Int>) -> Unit
) {
    var nombre by remember { mutableStateOf(nombreOriginal ?: "") }
    var cancionesEnLista by remember { mutableStateOf(todasLasCanciones.filter { it.id in idsCancionesActuales }) }
    var mostrarSelector by remember { mutableStateOf(false) }

    if (mostrarSelector) {
        DialogoSeleccionarCanciones(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
            seleccionadasActualmente = cancionesEnLista,
            permitirListas = false, // Punto 2: No permitir agregar listas en Nueva Categoría
            onDismiss = { mostrarSelector = false },
            onSongsAdded = { nuevas ->
                cancionesEnLista = (cancionesEnLista + nuevas).distinctBy { it.id }
                mostrarSelector = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = AzulProfundo,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (nombreOriginal == null) "Nueva Lista" else "Editar Lista", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = nombre, 
                    onValueChange = { nombre = it }, 
                    label = { Text("Nombre", color = Color.White.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CianBrillante, unfocusedBorderColor = Color.White.copy(alpha = 0.3f))
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("Canciones en esta lista:", color = CianBrillante, style = MaterialTheme.typography.labelLarge)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    if (cancionesEnLista.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay canciones", color = Color.White.copy(alpha = 0.4f))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(cancionesEnLista) { cancion ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(cancion.coverUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = android.R.drawable.ic_menu_gallery)
                                    )
                                    Text(cancion.titulo, color = Color.White, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { cancionesEnLista = cancionesEnLista.filter { it.id != cancion.id } }) {
                                        Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { mostrarSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CianBrillante, contentColor = AzulProfundo)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar Canciones", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar", color = Color.White) }
                    Button(
                        onClick = { onConfirm(nombre, cancionesEnLista.map { it.id }) },
                        enabled = nombre.isNotBlank() && cancionesEnLista.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CianBrillante, contentColor = AzulProfundo)
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun DialogoImprimir(
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, List<Int>>,
    cancionesIniciales: List<Cancion>,
    incluirAcordes: Boolean,
    onIncluirAcordesChange: (Boolean) -> Unit,
    incluirIndice: Boolean,
    onIncluirIndiceChange: (Boolean) -> Unit,
    modoCompacto: Boolean,
    onModoCompactoChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<Cancion>) -> Unit
) {
    var cancionesAImprimir by remember { mutableStateOf(cancionesIniciales) }
    var mostrarSelector by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var initialIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 64.dp.toPx() } // 56dp height + 8dp spacing

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val mutableList = cancionesAImprimir.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        cancionesAImprimir = mutableList
    }

    if (mostrarSelector) {
        DialogoSeleccionarCanciones(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
            seleccionadasActualmente = cancionesAImprimir,
            permitirListas = true,
            onDismiss = { mostrarSelector = false },
            onSongsAdded = { nuevas ->
                cancionesAImprimir = (cancionesAImprimir + nuevas).distinctBy { it.id }
                mostrarSelector = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = AzulProfundo,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Imprimir Cancionero (PDF)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirAcordesChange(!incluirAcordes) }.fillMaxWidth()) {
                        Checkbox(checked = incluirAcordes, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante, uncheckedColor = Color.White.copy(alpha = 0.6f)))
                        Text("Incluir Acordes", modifier = Modifier.padding(start = 8.dp), color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onIncluirIndiceChange(!incluirIndice) }.fillMaxWidth()) {
                        Checkbox(checked = incluirIndice, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante, uncheckedColor = Color.White.copy(alpha = 0.6f)))
                        Text("Incluir Índice", modifier = Modifier.padding(start = 8.dp), color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModoCompactoChange(!modoCompacto) }.fillMaxWidth()) {
                        Checkbox(checked = modoCompacto, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante, uncheckedColor = Color.White.copy(alpha = 0.6f)))
                        Text("Modo Compacto (Ahorrar papel)", modifier = Modifier.padding(start = 8.dp), color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    if (cancionesAImprimir.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay canciones seleccionadas", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(cancionesAImprimir, key = { _, cancion -> cancion.id }) { index, cancion ->
                                val isDraggingThis = draggedItemId == cancion.id
                                val translationY = if (isDraggingThis) {
                                    (initialIndex - index) * itemHeightPx + totalDragOffset
                                } else 0f

                                ItemOrdenablePdf(
                                    cancion = cancion,
                                    isDragging = isDraggingThis,
                                    isInteractionDisabled = draggedItemId != null && !isDraggingThis,
                                    modifier = Modifier
                                        .animateItem()
                                        .zIndex(if (isDraggingThis) 100f else 1f)
                                        .graphicsLayer { this.translationY = translationY },
                                    onDragStart = {
                                        draggedItemId = cancion.id
                                        initialIndex = index
                                        totalDragOffset = 0f
                                    },
                                    onDrag = { dragAmount ->
                                        totalDragOffset += dragAmount
                                        val currentIndex = cancionesAImprimir.indexOfFirst { it.id == draggedItemId }
                                        if (currentIndex != -1) {
                                            val targetIndex = (initialIndex + (totalDragOffset / itemHeightPx).roundToInt())
                                                .coerceIn(0, cancionesAImprimir.size - 1)
                                            if (targetIndex != currentIndex) moveItem(currentIndex, targetIndex)
                                        }
                                    },
                                    onDragEnd = {
                                        draggedItemId = null
                                        initialIndex = -1
                                        totalDragOffset = 0f
                                    },
                                    onRemove = {
                                        cancionesAImprimir = cancionesAImprimir.toMutableList().filter { it.id != cancion.id }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { mostrarSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CianBrillante, contentColor = AzulProfundo)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar Canciones / Listas", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar", color = Color.White) }
                    Button(
                        onClick = { onConfirm(cancionesAImprimir) },
                        enabled = cancionesAImprimir.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CianBrillante, contentColor = AzulProfundo)
                    ) { Text("Generar PDF", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ItemOrdenablePdf(
    cancion: Cancion,
    isDragging: Boolean,
    isInteractionDisabled: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().height(64.dp), // Aumentado ligeramente para la imagen
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = null,
                tint = if (isInteractionDisabled) Color.White.copy(alpha = 0.2f) else CianBrillante,
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(isInteractionDisabled) {
                        if (isInteractionDisabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .padding(4.dp)
            )
            
            Spacer(modifier = Modifier.width(4.dp))

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cancion.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cancion.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cancion.autor ?: "Autor desconocido",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DialogoSeleccionarCanciones(
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, List<Int>>,
    seleccionadasActualmente: List<Cancion>,
    permitirListas: Boolean = true,
    onDismiss: () -> Unit,
    onSongsAdded: (List<Cancion>) -> Unit
) {
    val idsInicialmenteSeleccionados = remember { seleccionadasActualmente.map { it.id }.toSet() }
    val seleccionadasTemporales = remember { mutableStateMapOf<Int, Boolean>() }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = AzulProfundo
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seleccionar para añadir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (permitirListas) {
                        val categoriasDisponibles = categorias.filter { entry ->
                            entry.key != "Todas" && entry.value.any { it !in idsInicialmenteSeleccionados }
                        }
                        
                        if (categoriasDisponibles.isNotEmpty()) {
                            item { Text("Listas:", modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = CianBrillante) }
                            items(categoriasDisponibles.keys.toList()) { cat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { 
                                        val idsEnLista = categorias[cat] ?: emptyList()
                                        val nuevas = todasLasCanciones.filter { it.id in idsEnLista && it.id !in idsInicialmenteSeleccionados }
                                        onSongsAdded(nuevas)
                                    }.padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlaylistAdd, null, tint = CianBrillante)
                                    Text(cat, modifier = Modifier.padding(start = 12.dp), color = Color.White)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.Add, null, tint = CianBrillante, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    
                    val cancionesDisponibles = todasLasCanciones.filter { it.id !in idsInicialmenteSeleccionados }
                    if (cancionesDisponibles.isNotEmpty()) {
                        item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f)) }
                        item { Text("Canciones:", modifier = Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = CianBrillante) }
                        items(cancionesDisponibles) { cancion ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    seleccionadasTemporales[cancion.id] = !(seleccionadasTemporales[cancion.id] ?: false) 
                                }.padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = seleccionadasTemporales.getOrDefault(cancion.id, false),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = CianBrillante)
                                )
                                
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(cancion.coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = android.R.drawable.ic_menu_gallery)
                                )
                                Text(cancion.titulo, modifier = Modifier.padding(start = 12.dp), color = Color.White)
                            }
                        }
                    } else if (!permitirListas || categorias.isEmpty()) {
                        item { 
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("¡No hay canciones disponibles!", color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cerrar", color = Color.White) }
                    Button(
                        onClick = { 
                            val cancionesAAnadir = todasLasCanciones.filter { seleccionadasTemporales[it.id] == true }
                            onSongsAdded(cancionesAAnadir)
                        },
                        enabled = seleccionadasTemporales.any { it.value },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CianBrillante, contentColor = AzulProfundo)
                    ) { Text("Añadir", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cancion.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CianBrillante),
                contentScale = ContentScale.Crop,
                error = painterResource(id = android.R.drawable.ic_menu_gallery),
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery)
            )
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