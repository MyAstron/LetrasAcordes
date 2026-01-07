package com.letrasacordes.application

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.logic.SongTextFormatter
import com.letrasacordes.application.logic.TonalidadUtil
import com.letrasacordes.application.ui.ChordDiagramDialog
import com.letrasacordes.application.ui.MetronomeController
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
    val context = LocalContext.current
    
    // Control de Wakelock (Mantener pantalla encendida)
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(cancionId) {
        viewModel.cargarCancion(cancionId)
    }

    val cancionState by viewModel.cancionSeleccionada.collectAsState()
    val cancionActual = cancionState

    val scope = rememberCoroutineScope()
    var semitonos by remember { mutableIntStateOf(0) }
    var mostrarAcordes by remember { mutableStateOf(true) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }
    var mostrarAjustes by remember { mutableStateOf(false) }

    var acordeSeleccionado by remember { mutableStateOf<String?>(null) }
    
    // Modo Escenario (Alto Contraste)
    var modoEscenario by remember { mutableStateOf(false) }

    // Estado de Auto-Scroll
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    val scrollState = rememberScrollState()

    // Metrónomo
    val metronomeController = remember { MetronomeController() }
    var isMetronomeVisible by remember { mutableStateOf(false) }
    var metronomeBpm by remember { mutableIntStateOf(120) }
    var isMetronomePlaying by remember { mutableStateOf(false) }
    var beatIndicator by remember { mutableStateOf(false) }
    
    // Configurar ritmo inicial del metrónomo
    LaunchedEffect(cancionActual) {
        if (cancionActual != null) {
            val ritmo = cancionActual.ritmo
            val bpm = metronomeController.parseBpmFromRhythm(ritmo) ?: metronomeController.estimateBpmFromStyle(ritmo)
            metronomeBpm = bpm
            metronomeController.setBpm(bpm)
        }
    }
    
    // Callback visual del beat
    DisposableEffect(metronomeController) {
        metronomeController.onBeat = {
            beatIndicator = true
        }
        onDispose {
            metronomeController.release()
        }
    }
    
    // Apagar indicador visual después de un momento
    LaunchedEffect(beatIndicator) {
        if (beatIndicator) {
            delay(100)
            beatIndicator = false
        }
    }

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
    
    val lineasDeCancion = remember(cancionActual, semitonos, mostrarAcordes) {
        if (cancionActual != null) {
            SongTextFormatter.formatSongTextForDisplay(cancionActual.letraOriginal, semitonos, mostrarAcordes)
        } else {
            emptyList()
        }
    }

    val acordesUnicos = remember(lineasDeCancion) {
        lineasDeCancion.flatMap { (lineaAcordes, _) ->
            val textoParaAnalizar = if (lineaAcordes.contains(":")) {
                lineaAcordes.substringAfter(":")
            } else {
                lineaAcordes
            }
            textoParaAnalizar.split("\\s+".toRegex())
        }
        .filter { it.isNotBlank() && it.matches(Regex("^[A-G].*")) }
        .distinct()
        .sorted()
    }
    
    // Definición de colores según el Modo Escenario
    val backgroundColor = if (modoEscenario) Color.Black else MaterialTheme.colorScheme.background
    val textColor = if (modoEscenario) Color.White else MaterialTheme.colorScheme.onBackground
    val chordColor = if (modoEscenario) Color.Yellow else MaterialTheme.colorScheme.primary
    val titleColor = if (modoEscenario) Color.White else MaterialTheme.colorScheme.onBackground
    val iconColor = if (modoEscenario) Color.White else MaterialTheme.colorScheme.onSurface
    
    // Colores Metrónomo
    val metronomeSurfaceColor = if (modoEscenario) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant
    val metronomeContentColor = if (modoEscenario) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

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
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Ajustes de ${cancionActual.titulo}, ${cancionActual.autor ?: " "}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Sección: Modo Escenario
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { modoEscenario = !modoEscenario },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = modoEscenario, onCheckedChange = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Modo Escenario (Alto Contraste)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Fondo negro, letras blancas y acordes amarillos para mejor visibilidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Sección: Información
                Text(
                    text = "Información",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                    
                    if (mostrarAcordes) {
                        if (tonoOriginal != null) {
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

                        if (acordesUnicos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Ver Diagramas:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(acordesUnicos) { acorde ->
                                    AssistChip(
                                        onClick = { acordeSeleccionado = acorde },
                                        label = { Text(acorde, fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
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

    if (acordeSeleccionado != null) {
        ChordDiagramDialog(
            chordName = acordeSeleccionado!!,
            onDismiss = { acordeSeleccionado = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = cancionActual?.titulo ?: "Cargando...", 
                        maxLines = 1,
                        color = titleColor
                    ) 
                },
                navigationIcon = { 
                    IconButton(onClick = onNavegarAtras) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = iconColor) 
                    } 
                },
                actions = {
                    // Botón Metrónomo
                    IconButton(onClick = { isMetronomeVisible = !isMetronomeVisible }) {
                        Icon(Icons.Default.Timer, contentDescription = "Metrónomo", tint = iconColor)
                    }
                    IconButton(onClick = { mostrarAjustes = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Ajustes de Lectura", tint = iconColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = titleColor,
                    actionIconContentColor = iconColor,
                    navigationIconContentColor = iconColor
                )
            )
        },
        containerColor = backgroundColor
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
                            text = cancionActual.ritmo ?: "",
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Light,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    lineasDeCancion.forEach { (lineaDeAcordes, lineaDeLetra) ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            if (lineaDeAcordes.isNotBlank()) {
                                Text(
                                    text = lineaDeAcordes,
                                    color = chordColor,
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
                                lineHeight = 22.sp,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(300.dp))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
            // Barra Flotante del Metrónomo (Alineada abajo)
            AnimatedVisibility(
                visible = isMetronomeVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = metronomeSurfaceColor,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Play/Pause
                        IconButton(onClick = {
                            if (isMetronomePlaying) {
                                metronomeController.stop()
                                isMetronomePlaying = false
                            } else {
                                metronomeController.start(scope)
                                isMetronomePlaying = true
                            }
                        }) {
                            Icon(
                                imageVector = if (isMetronomePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isMetronomePlaying) "Detener" else "Iniciar",
                                tint = metronomeContentColor
                            )
                        }

                        // Controles BPM
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                metronomeBpm = (metronomeBpm - 5).coerceAtLeast(30)
                                metronomeController.setBpm(metronomeBpm)
                            }) {
                                Icon(Icons.Default.Remove, "Disminuir BPM", tint = metronomeContentColor)
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$metronomeBpm",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = metronomeContentColor
                                )
                                Text(
                                    text = "BPM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metronomeContentColor.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(onClick = {
                                metronomeBpm = (metronomeBpm + 5).coerceAtMost(300)
                                metronomeController.setBpm(metronomeBpm)
                            }) {
                                Icon(Icons.Default.Add, "Aumentar BPM", tint = metronomeContentColor)
                            }
                        }
                        
                        // Indicador Visual (LED)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (beatIndicator) Color.Red else Color.Gray,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        
                        // Cerrar
                        IconButton(onClick = {
                            metronomeController.stop()
                            isMetronomePlaying = false
                            isMetronomeVisible = false
                        }) {
                            Icon(Icons.Default.Close, "Cerrar Metrónomo", tint = metronomeContentColor)
                        }
                    }
                }
            }
        }
    }
}
