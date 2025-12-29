package com.letrasacordes.application

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.logic.SongTextFormatter
import com.letrasacordes.application.logic.TonalidadUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVerCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    onNavegarAEditar: (Int) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    LaunchedEffect(cancionId) {
        viewModel.cargarCancion(cancionId)
    }

    val cancionState by viewModel.cancionSeleccionada.collectAsState()
    
    // Referencia estable local
    val cancionActual = cancionState

    val scope = rememberCoroutineScope()
    var semitonos by remember { mutableIntStateOf(0) }
    var mostrarAcordes by remember { mutableStateOf(true) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }
    var mostrarAjustes by remember { mutableStateOf(false) }

    // Estado de Auto-Scroll
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    val scrollState = rememberScrollState()

    // Lógica de Auto-Scroll
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isActive) {
                scrollState.scrollBy(scrollSpeed) 
                delay(16) // ~60 FPS
            }
        }
    }

    val tonoOriginal = remember(cancionActual) {
        cancionActual?.let { TonalidadUtil.obtenerPrimerAcorde(it.letraOriginal) }
    }
    
    // Bottom Sheet de Ajustes
    if (mostrarAjustes && cancionActual != null) {
        ModalBottomSheet(
            onDismissRequest = { mostrarAjustes = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp) // Padding extra para la barra de navegación del sistema
            ) {
                Text(
                    text = "Ajustes de Lectura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Sección: Información
                Text(
                    text = "Información",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${cancionActual.titulo} - ${cancionActual.autor ?: "Desconocido"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!cancionActual.ritmo.isNullOrBlank()) {
                    Text(
                        text = "(${cancionActual.ritmo})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Sección: Auto-Scroll
                Text(
                    text = "Auto-Scroll",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledIconButton(
                        onClick = { 
                            isAutoScrolling = !isAutoScrolling 
                            if (isAutoScrolling) {
                                mostrarAjustes = false
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isAutoScrolling) "Pausar" else "Iniciar"
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Velocidad: ${String.format("%.1f", scrollSpeed)}")
                }
                Slider(
                    value = scrollSpeed,
                    onValueChange = { scrollSpeed = it },
                    valueRange = 0.5f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Sección: Tonalidad y Acordes
                if (cancionActual.tieneAcordes) {
                    Text(
                        text = "Tonalidad y Acordes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mostrar Acordes")
                        Switch(
                            checked = mostrarAcordes,
                            onCheckedChange = { mostrarAcordes = it }
                        )
                    }
                    
                    if (mostrarAcordes && tonoOriginal != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tonoAbajo = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos - 1)
                            val tonoActualTranspuesto = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos)
                            val tonoArriba = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos + 1)

                            OutlinedButton(
                                onClick = { semitonos-- },
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text("- $tonoAbajo")
                            }
                            
                            Text(
                                text = tonoActualTranspuesto,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            
                            OutlinedButton(
                                onClick = { semitonos++ },
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text("+ $tonoArriba")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                // Sección: Acciones
                Text(
                    text = "Acciones",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        mostrarAjustes = false
                        onNavegarAEditar(cancionId) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editar Canción")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        mostrarAjustes = false
                        mostrarDialogoEliminar = true 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = MaterialTheme.shapes.large,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar Canción")
                }
            }
        }
    }

    if (mostrarDialogoEliminar && cancionActual != null) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEliminar = false },
            title = { Text("Confirmar Eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar permanentemente '${cancionActual.titulo}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.eliminarCancion(cancionActual)
                            onNavegarAtras()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogoEliminar = false }) { Text("Cancelar") } }
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
                },
                actions = {
                    IconButton(onClick = { mostrarAjustes = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Ajustes de Lectura")
                    }
                }
            )
        }
        // FAB y BottomBar eliminados para maximizar espacio de lectura
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cancionActual != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = cancionActual.autor ?: "", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val lineasDeCancion = remember(cancionActual, semitonos, mostrarAcordes) {
                        SongTextFormatter.formatSongTextForDisplay(cancionActual.letraOriginal, semitonos, mostrarAcordes)
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
                    Spacer(modifier = Modifier.height(300.dp)) // Espacio extra para scroll
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
