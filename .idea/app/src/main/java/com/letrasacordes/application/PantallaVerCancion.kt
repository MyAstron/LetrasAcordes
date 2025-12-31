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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
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
    
    // Referencia local estable
    val cancionActual = cancionState

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Estados de configuración
    var semitonos by remember { mutableStateOf(0) }
    var mostrarAcordes by remember { mutableStateOf(true) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }

    // Estados de Auto-Scroll
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) } // Píxeles por ciclo

    val tonoOriginal = remember(cancionActual) {
        cancionActual?.let { TonalidadUtil.obtenerPrimerAcorde(it.letraOriginal) }
    }

    // Lógica del Auto-Scroll
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isActive) {
                // Desplazamos la pantalla suavemente
                scrollState.scrollBy(scrollSpeed)
                // Un pequeño delay para mantener ~50 FPS aprox y suavidad
                delay(20)
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

    // Usamos CompositionLocalProvider para cambiar la dirección del layout a RTL
    // Esto hace que el Drawer aparezca desde la derecha (End).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                // Volvemos a poner LTR para el contenido del Drawer para que el texto se lea bien
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Ajustes de Lectura",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Divider()

                        // --- SECCIÓN: INFORMACIÓN ---
                        if (cancionActual != null) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Información", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = buildString {
                                        append(cancionActual.titulo)
                                        if (!cancionActual.autor.isNullOrBlank()) append(" - ${cancionActual.autor}")
                                        if (!cancionActual.ritmo.isNullOrBlank()) append(" (${cancionActual.ritmo})")
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Divider()
                        }

                        // --- SECCIÓN: AUTO-SCROLL ---
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Auto-Scroll", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                FilledTonalIconButton(
                                    onClick = { 
                                        isAutoScrolling = !isAutoScrolling
                                        // Si activamos el play, cerramos el menú para ver la canción
                                        if (isAutoScrolling) {
                                            scope.launch { drawerState.close() }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isAutoScrolling) "Pausar" else "Iniciar"
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("Velocidad: ${String.format("%.1f", scrollSpeed)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = scrollSpeed,
                                onValueChange = { scrollSpeed = it },
                                valueRange = 0.5f..4.0f,
                                steps = 6, 
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Divider()

                        // --- SECCIÓN: TONALIDAD ---
                        if (cancionActual != null && cancionActual.tieneAcordes) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Tonalidad y Acordes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                     Text("Mostrar Acordes", style = MaterialTheme.typography.bodyMedium)
                                     Switch(checked = mostrarAcordes, onCheckedChange = { mostrarAcordes = it })
                                }

                                if (mostrarAcordes && tonoOriginal != null) {
                                     val tonoAbajo = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos - 1)
                                     val tonoActualTranspuesto = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos)
                                     val tonoArriba = TonalidadUtil.transponerAcorde(tonoOriginal, semitonos + 1)

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(onClick = { semitonos-- }) { Text("- $tonoAbajo") }
                                        Text(
                                            text = tonoActualTranspuesto,
                                            fontWeight = FontWeight.Bold, 
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        OutlinedButton(onClick = { semitonos++ }) { Text("+ $tonoArriba") }
                                    }
                                }
                            }
                            Divider()
                        }

                        // --- SECCIÓN: ACCIONES ---
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Acciones", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { 
                                    scope.launch { drawerState.close() }
                                    onNavegarAEditar(cancionId) 
                                },
                                enabled = cancionActual != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(18.dp))
                                Text("Editar Canción")
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { 
                                    scope.launch { drawerState.close() }
                                    mostrarDialogoEliminar = true 
                                },
                                enabled = cancionActual != null,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Eliminar Canción")
                            }
                        }
                    }
                }
            }
        ) {
            // Volvemos a poner LTR para el contenido principal
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                                // Botón para abrir el menú lateral (ahora desde la derecha)
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Tune, "Ajustes de visualización")
                                }
                            }
                        )
                    }
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
                                    .verticalScroll(scrollState) // Usamos el estado que controla el Auto-Scroll
                            ) {
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
                                
                                Spacer(modifier = Modifier.height(400.dp))
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}