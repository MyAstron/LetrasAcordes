package com.letrasacordes.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.MicrophoneTunerController
import com.letrasacordes.application.ui.TunerResult
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConfiguracion(
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }

    var mostrarDialogoExportar by remember { mutableStateOf(false) }
    var mostrarAfinador by remember { mutableStateOf(false) }
    
    // Estado del perfil del usuario
    var userProfile by remember { 
        mutableStateOf(sharedPreferences.getString("user_profile", "GUITARRA") ?: "GUITARRA") 
    }

    val todasLasCanciones by viewModel.todasLasCanciones.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val cancionesSeleccionadas = remember { mutableStateMapOf<Int, Boolean>() }

    val importadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val bytesImportados = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytesImportados != null && bytesImportados.isNotEmpty()) {
                            val cancionesImportadas = viewModel.importarCanciones(bytesImportados)
                            snackbarHostState.showSnackbar("$cancionesImportadas canciones importadas con éxito")
                        } else {
                            snackbarHostState.showSnackbar("Error: El archivo está vacío o no se pudo leer.")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al importar el archivo: ${e.message}")
                    }
                }
            }
        }
    )

    val exportadorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val cancionesAExportar = todasLasCanciones.filter { cancionesSeleccionadas.getOrDefault(it.id, false) }
                        val bytesAExportar = viewModel.exportarCanciones(cancionesAExportar)
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(bytesAExportar)
                        }
                        snackbarHostState.showSnackbar("${cancionesAExportar.size} canciones exportadas con éxito.")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al exportar el archivo: ${e.message}")
                    } finally {
                        cancionesSeleccionadas.clear()
                    }
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECCIÓN: PERFIL DE USUARIO
            Text(
                "Perfil de Usuario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Opción Guitarrista
                OutlinedCard(
                    onClick = { 
                        userProfile = "GUITARRA"
                        sharedPreferences.edit().putString("user_profile", "GUITARRA").apply()
                    },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (userProfile == "GUITARRA") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = userProfile == "GUITARRA")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Text("Guitarrista", style = MaterialTheme.typography.labelLarge)
                    }
                }
                
                // Opción Cantante
                OutlinedCard(
                    onClick = { 
                        userProfile = "CANTANTE"
                        sharedPreferences.edit().putString("user_profile", "CANTANTE").apply()
                    },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (userProfile == "CANTANTE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = userProfile == "CANTANTE")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Text("Cantante", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            
            Text(
                text = if (userProfile == "GUITARRA") 
                    "Modo completo: Acordes, metrónomo y afinador activos." 
                    else "Modo limpio: Solo letra, ideal para vocalistas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                "Herramientas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            // El afinador solo es relevante para guitarristas
            Button(
                onClick = { mostrarAfinador = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = userProfile == "GUITARRA"
            ) {
                Text("Afinador Guitarra Clásica")
            }

            HorizontalDivider()

            Text(
                "Respaldo y Datos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Button(
                onClick = { importadorLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Importar Canciones (.la/Txt)")
            }

            Button(
                onClick = { mostrarDialogoExportar = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exportar Canciones (.la)")
            }
        }
    }

    if (mostrarDialogoExportar) {
        DialogoExportacionAvanzado(
            todasLasCanciones = todasLasCanciones,
            categorias = categorias,
            cancionesSeleccionadas = cancionesSeleccionadas,
            onDismiss = {
                mostrarDialogoExportar = false
                cancionesSeleccionadas.clear()
            },
            onConfirm = {
                mostrarDialogoExportar = false
                exportadorLauncher.launch("cancionero.la")
            }
        )
    }

    if (mostrarAfinador) {
        DialogoAfinadorCromatico(onDismiss = { mostrarAfinador = false })
    }
}

// ... Resto de componentes (GuitarString, DialogoAfinadorCromatico, etc.) se mantienen igual
data class GuitarString(val label: String, val note: String, val freq: Double)

@Composable
fun DialogoAfinadorCromatico(onDismiss: () -> Unit) {
    val tunerController = remember { MicrophoneTunerController() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var isListening by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<TunerResult?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    
    val strings = listOf(
        GuitarString("6ta", "E", 82.41),
        GuitarString("5ta", "A", 110.00),
        GuitarString("4ta", "D", 146.83),
        GuitarString("3ra", "G", 196.00),
        GuitarString("2da", "B", 246.94),
        GuitarString("1ra", "E", 329.63)
    )
    
    var selectedString by remember { mutableStateOf<GuitarString?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                isListening = true
                tunerController.start(scope)
            } else {
                permissionDenied = true
            }
        }
    )

    DisposableEffect(tunerController) {
        tunerController.onTunerUpdate = { result ->
            currentResult = result
        }
        onDispose {
            tunerController.stop()
        }
    }

    val displayResult = remember(currentResult, selectedString) {
        val res = currentResult ?: return@remember null
        val target = selectedString ?: return@remember res
        
        if (res.frequency > 0) {
            val semitonesOff = 12 * log2(res.frequency / target.freq)
            val centsOff = (semitonesOff * 100).toInt()
            
            res.copy(
                noteName = target.note,
                centsOff = centsOff.coerceIn(-50, 50),
                isLocked = abs(centsOff) < 5
            )
        } else {
            res.copy(noteName = target.note)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Afinador para Guitarra") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isListening) {
                    TunerGaugeWithWave(result = displayResult ?: TunerResult(0.0, "--", 0, false, 0f))
                    
                    Text("Selecciona una cuerda (Opcional):", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        strings.take(3).forEach { guitarString ->
                            StringButton(
                                guitarString = guitarString,
                                isSelected = selectedString == guitarString,
                                onClick = {
                                    selectedString = if (selectedString == guitarString) null else guitarString
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        strings.takeLast(3).forEach { guitarString ->
                            StringButton(
                                guitarString = guitarString,
                                isSelected = selectedString == guitarString,
                                onClick = {
                                    selectedString = if (selectedString == guitarString) null else guitarString
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isListening = false
                            tunerController.stop()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Detener")
                    }
                } else {
                    Text(
                        "Usa el micrófono para afinar tu guitarra clásica.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    if (permissionDenied) {
                        Text(
                            "Se requiere permiso de micrófono.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                isListening = true
                                tunerController.start(scope)
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun StringButton(guitarString: GuitarString, isSelected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(guitarString.note, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(guitarString.label, fontSize = 10.sp)
        }
    }
}

@Composable
fun TunerGaugeWithWave(result: TunerResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformVisualizer(amplitude = result.amplitude, isLocked = result.isLocked)
        
        Text(
            text = result.noteName,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (result.isLocked) Color.Green else MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val primaryColor = MaterialTheme.colorScheme.primary
        
        Canvas(modifier = Modifier.size(200.dp, 80.dp)) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val bottomY = height - 5f
            val radius = width / 2 - 10f

            drawArc(
                color = Color.Gray.copy(alpha = 0.3f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(centerX - radius, bottomY - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 15f, cap = StrokeCap.Round)
            )
            
            val angleOffset = (result.centsOff / 50.0) * 45.0
            val needleAngle = 270.0 + angleOffset
            val needleRad = Math.toRadians(needleAngle)
            
            val needleLength = radius - 5f
            val needleX = centerX + needleLength * cos(needleRad).toFloat()
            val needleY = bottomY + needleLength * sin(needleRad).toFloat()

            drawLine(
                color = if (result.isLocked) Color.Green else Color.Red,
                start = Offset(centerX, bottomY),
                end = Offset(needleX, needleY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            
            drawCircle(
                color = primaryColor,
                radius = 8f,
                center = Offset(centerX, bottomY)
            )
        }
        
        Text(
            text = if (result.frequency > 0) "${result.centsOff} cents" else "--",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WaveformVisualizer(amplitude: Float, isLocked: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val waveColor = if (isLocked) Color.Green else primaryColor

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)) {
        val width = size.width
        val height = size.height
        val midY = height / 2
        val path = Path()
        
        path.moveTo(0f, midY)
        
        val segments = 50
        val step = width / segments
        
        val visualHeight = height * amplitude * 0.8f
        
        for (i in 0..segments) {
            val x = i * step
            val relativeX = i.toFloat() / segments
            val y = midY + sin((relativeX * 10f + phase).toDouble()).toFloat() * visualHeight
            path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun DialogoExportacionAvanzado(
    todasLasCanciones: List<Cancion>,
    categorias: Map<String, Set<Int>>,
    cancionesSeleccionadas: MutableMap<Int, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Canciones") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text("Listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(categorias.keys.toList()) { nombreCategoria ->
                    val idsEnCategoria = categorias[nombreCategoria] ?: emptySet()
                    val cancionesSeleccionadasEnCategoria = idsEnCategoria.count { cancionesSeleccionadas.getOrDefault(it, false) }

                    val checkboxState = when {
                        cancionesSeleccionadasEnCategoria == 0 -> ToggleableState.Off
                        cancionesSeleccionadasEnCategoria == idsEnCategoria.size && idsEnCategoria.isNotEmpty() -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = checkboxState != ToggleableState.On
                                idsEnCategoria.forEach { id -> cancionesSeleccionadas[id] = newState }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(state = checkboxState, onClick = null)
                        Text(nombreCategoria, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

                item {
                    Text("Canciones Individuales", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(todasLasCanciones) { cancion ->
                    val isChecked = cancionesSeleccionadas.getOrDefault(cancion.id, false)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cancionesSeleccionadas[cancion.id] = !isChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                        Text(cancion.titulo, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = cancionesSeleccionadas.any { it.value }
            ) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}