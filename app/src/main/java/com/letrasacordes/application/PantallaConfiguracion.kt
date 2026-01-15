package com.letrasacordes.application

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.MicrophoneTunerController
import com.letrasacordes.application.ui.TunerResult
import com.letrasacordes.application.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConfiguracion(
    onNavegarAtras: () -> Unit,
    onIniciarPresentacion: (String) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }

    var userProfile by remember { mutableStateOf(sharedPreferences.getString("user_profile", "GUITARRA") ?: "GUITARRA") }
    
    var mostrarAfinador by remember { mutableStateOf(false) }
    var mostrarDialogoExportar by remember { mutableStateOf(false) }
    var mostrarDialogoPresentacion by remember { mutableStateOf(false) }

    val todasLasCanciones by viewModel.todasLasCanciones.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val cancionesSeleccionadas = remember { mutableStateMapOf<Int, Boolean>() }

    // Afinador Controller
    val tunerController = remember { MicrophoneTunerController() }
    var currentResult by remember { mutableStateOf<TunerResult?>(null) }
    val strings = listOf(
        Triple("6ta", "E", 82.41), Triple("5ta", "A", 110.00), Triple("4ta", "D", 146.83),
        Triple("3ra", "G", 196.00), Triple("2da", "B", 246.94), Triple("1ra", "E", 329.63)
    )
    var selectedFreq by remember { mutableStateOf<Double?>(null) }
    var selectedNoteName by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if (it) { mostrarAfinador = true; tunerController.start(scope) } 
    }

    DisposableEffect(Unit) {
        tunerController.onTunerUpdate = { currentResult = it }
        onDispose { tunerController.stop() }
    }

    val displayResult = remember(currentResult, selectedFreq) {
        val res = currentResult ?: return@remember null
        val target = selectedFreq ?: return@remember res
        if (res.frequency > 0) {
            val cents = (1200 * log2(res.frequency / target)).toInt()
            res.copy(noteName = selectedNoteName, centsOff = cents.coerceIn(-50, 50), isLocked = abs(cents) < 5)
        } else res.copy(noteName = selectedNoteName)
    }

    // Lanzadores
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { safeUri ->
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(safeUri)?.use { it.readBytes() }
                    if (bytes != null) viewModel.importarCanciones(bytes)
                } catch (e: Exception) { snackbarHostState.showSnackbar("Error al importar") }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { safeUri ->
            scope.launch {
                try {
                    val cancionesAExportar = todasLasCanciones.filter { cancionesSeleccionadas.getOrDefault(it.id, false) }
                    val bytes = viewModel.exportarCanciones(cancionesAExportar)
                    context.contentResolver.openOutputStream(safeUri)?.use { it.write(bytes) }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, safeUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir Respaldo"))
                } catch (e: Exception) { snackbarHostState.showSnackbar("Error al exportar") }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CONFIGURACIÓN", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavegarAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzulProfundo)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AzulProfundo, AzulMedio))).padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("TU PERFIL", color = CianBrillante, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileCard("GUITARRA", Icons.Default.MusicNote, userProfile == "GUITARRA", { userProfile = "GUITARRA"; sharedPreferences.edit().putString("user_profile", "GUITARRA").apply() }, Modifier.weight(1f))
                    ProfileCard("CANTANTE", Icons.Default.Mic, userProfile == "CANTANTE", { userProfile = "CANTANTE"; sharedPreferences.edit().putString("user_profile", "CANTANTE").apply() }, Modifier.weight(1f))
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HERRAMIENTAS", color = PlataBrillante, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        ToolButton(
                            text = if(mostrarAfinador) "Ocultar Afinador" else "Afinador para Guitarra", 
                            icon = if(mostrarAfinador) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            enabled = userProfile == "GUITARRA"
                        ) {
                            if (mostrarAfinador) { mostrarAfinador = false; tunerController.stop() }
                            else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    mostrarAfinador = true; tunerController.start(scope)
                                } else { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            }
                        }
                        
                        AnimatedVisibility(visible = mostrarAfinador && userProfile == "GUITARRA") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                                WaveformVisualizerReal(amplitude = displayResult?.amplitude ?: 0f, isLocked = displayResult?.isLocked ?: false)
                                Text(displayResult?.noteName ?: "--", fontSize = 56.sp, fontWeight = FontWeight.Black, color = if (displayResult?.isLocked == true) Color.Green else Color.White)
                                TunerGaugeReal(centsOff = displayResult?.centsOff ?: 0, isLocked = displayResult?.isLocked ?: false)
                                Text(if(displayResult != null && displayResult.noteName != "--") "${displayResult.centsOff} cents" else "Esperando señal...", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    strings.take(3).forEach { (l, n, f) -> StringButtonReal(n, l, selectedFreq == f) { selectedFreq = if(selectedFreq == f) null else f; selectedNoteName = n } }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    strings.takeLast(3).forEach { (l, n, f) -> StringButtonReal(n, l, selectedFreq == f) { selectedFreq = if(selectedFreq == f) null else f; selectedNoteName = n } }
                                }
                                Button(onClick = { mostrarAfinador = false; tunerController.stop() }, modifier = Modifier.padding(top = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF28B82))) {
                                    Icon(Icons.Default.MicOff, null, tint = Color.Black); Spacer(Modifier.width(8.dp)); Text("Detener", color = Color.Black)
                                }
                            }
                        }

                        ToolButton("Importar (.la / .txt)", Icons.Default.FileDownload) { importLauncher.launch(arrayOf("*/*")) }
                        ToolButton("Exportar Selección (.la)", Icons.Default.FileUpload) { mostrarDialogoExportar = true }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CianBrillante.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PresentToAll, null, tint = CianBrillante)
                            Spacer(Modifier.width(12.dp))
                            Text("MODO PRESENTACIÓN", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Este modo permite ver exclusivamente las canciones de una lista específica de forma inmersiva.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { mostrarDialogoPresentacion = true }, colors = ButtonDefaults.buttonColors(containerColor = CianBrillante), modifier = Modifier.fillMaxWidth()) {
                            Text("Configurar Presentación", color = AzulProfundo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (mostrarDialogoExportar) {
        DialogoExportacionAvanzado(todasLasCanciones, categorias, cancionesSeleccionadas, { mostrarDialogoExportar = false; cancionesSeleccionadas.clear() }, { mostrarDialogoExportar = false; exportLauncher.launch("repertorio.la") })
    }

    if (mostrarDialogoPresentacion) {
        var seleccionTemporal by remember { mutableStateOf<String?>(null) }
        val cancionesManuales = remember { mutableStateMapOf<Int, Boolean>() }
        
        AlertDialog(
            onDismissRequest = { mostrarDialogoPresentacion = false },
            containerColor = SurfaceDark,
            title = { Text("Modo Presentación", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (categorias.isNotEmpty()) {
                        Text("Selecciona la Lista para presentar:", color = Color.White.copy(alpha = 0.7f))
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(categorias.keys.toList()) { cat ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { seleccionTemporal = cat }.padding(vertical = 8.dp)) {
                                    RadioButton(selected = seleccionTemporal == cat, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = CianBrillante))
                                    Text(cat, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    } else {
                        Text("No tienes listas creadas. Selecciona las canciones para tu presentación:", color = Color.White.copy(alpha = 0.7f))
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(todasLasCanciones) { cancion ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { 
                                    cancionesManuales[cancion.id] = !(cancionesManuales[cancion.id] ?: false)
                                }.padding(vertical = 4.dp)) {
                                    Checkbox(checked = cancionesManuales.getOrDefault(cancion.id, false), onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante))
                                    Text(cancion.titulo, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                val habilitado = if (categorias.isNotEmpty()) seleccionTemporal != null else cancionesManuales.any { it.value }
                Button(
                    onClick = { 
                        if (categorias.isNotEmpty()) {
                            onIniciarPresentacion(seleccionTemporal!!)
                        } else {
                            val ids = cancionesManuales.filter { it.value }.keys.toList()
                            // Usamos un nombre específico para identificar que es una lista temporal
                            viewModel.guardarCategoria("LISTA_TEMPORAL_AUTO", ids)
                            onIniciarPresentacion("LISTA_TEMPORAL_AUTO")
                        }
                        mostrarDialogoPresentacion = false
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = CianBrillante),
                    enabled = habilitado
                ) { Text("Activar", color = AzulProfundo) } 
            },
            dismissButton = { TextButton(onClick = { mostrarDialogoPresentacion = false }) { Text("Cancelar", color = Color.White) } }
        )
    }
}

@Composable
fun WaveformVisualizerReal(amplitude: Float, isLocked: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val phase by infiniteTransition.animateFloat(0f, (2f * PI).toFloat(), infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "")
    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        val midY = size.height / 2
        val path = Path()
        path.moveTo(0f, midY)
        for (i in 0..60) {
            val x = i * (size.width / 60)
            val y = midY + sin((i.toFloat() / 60 * 10f + phase).toDouble()).toFloat() * (size.height * amplitude * 0.8f)
            path.lineTo(x, y)
        }
        drawPath(path, if (isLocked) Color.Green else CianBrillante, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun TunerGaugeReal(centsOff: Int, isLocked: Boolean) {
    val animatedCents by animateFloatAsState(centsOff.toFloat(), spring(stiffness = Spring.StiffnessLow), label = "")
    Canvas(modifier = Modifier.size(200.dp, 80.dp)) {
        val center = Offset(size.width / 2, size.height - 10f)
        val radius = size.width / 2 - 10.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.1f), 180f, 180f, false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
        val angle = 270f + (animatedCents.coerceIn(-50f, 50f) * 1.8f)
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val lineEnd = Offset(center.x + radius * cos(angleRad), center.y + radius * sin(angleRad))
        drawLine(if (isLocked) Color.Green else Color.Red, center, lineEnd, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 6.dp.toPx(), center = center)
    }
}

@Composable
fun StringButtonReal(note: String, label: String, active: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(60.dp).background(if (active) AzulMedio else Color.Transparent, CircleShape).border(2.dp, if (active) CianBrillante else Color.White.copy(alpha = 0.2f), CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(note, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        }
    }
}

@Composable
fun DialogoExportacionAvanzado(todasLasCanciones: List<Cancion>, categorias: Map<String, List<Int>>, seleccionadas: MutableMap<Int, Boolean>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Canciones (.la)", color = Color.White) },
        containerColor = SurfaceDark,
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                item { Text("Listas", color = CianBrillante, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                items(categorias.keys.toList()) { cat ->
                    val ids = categorias[cat] ?: emptyList()
                    val count = ids.count { seleccionadas[it] == true }
                    val state = when { count == 0 -> ToggleableState.Off; count == ids.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                    Row(modifier = Modifier.fillMaxWidth().clickable { val newState = state != ToggleableState.On; ids.forEach { seleccionadas[it] = newState } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(state = state, onClick = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante))
                        Text(cat, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f)) }
                item { Text("Canciones Individuales", color = CianBrillante, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                items(todasLasCanciones) { cancion ->
                    val isChecked = seleccionadas.getOrDefault(cancion.id, false)
                    Row(modifier = Modifier.fillMaxWidth().clickable { seleccionadas[cancion.id] = !isChecked }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = CianBrillante))
                        Text(cancion.titulo, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = seleccionadas.any { it.value }, colors = ButtonDefaults.buttonColors(containerColor = CianBrillante)) { Text("Exportar", color = AzulProfundo) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White) } }
    )
}

@Composable
fun ProfileCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val borderColor by animateColorAsState(if (isSelected) CianBrillante else Color.White.copy(alpha = 0.2f), label = "")
    val bgColor by animateColorAsState(if (isSelected) CianBrillante.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), label = "")
    Box(modifier = modifier.height(100.dp).background(bgColor, RoundedCornerShape(20.dp)).border(2.dp, borderColor, RoundedCornerShape(20.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (isSelected) CianBrillante else Color.White)
            Text(title, color = if (isSelected) CianBrillante else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun ToolButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = CianBrillante, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Icon(icon, null, tint = Color.White.copy(alpha = 0.3f))
    }
}
