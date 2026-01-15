package com.letrasacordes.application

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.logic.SongTextFormatter
import com.letrasacordes.application.logic.TonalidadUtil
import com.letrasacordes.application.ui.theme.*
import com.letrasacordes.application.ui.MetronomeController
import com.letrasacordes.application.ui.ChordDiagram
import com.letrasacordes.application.logic.ChordDictionary
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
    val scope = rememberCoroutineScope()
    val sharedPreferences = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    val userProfile = remember { sharedPreferences.getString("user_profile", "GUITARRA") ?: "GUITARRA" }
    val isCantante = userProfile == "CANTANTE"

    LaunchedEffect(cancionId) { viewModel.cargarCancion(cancionId) }
    val cancionActual by viewModel.cancionSeleccionada.collectAsState()

    var semitonos by remember { mutableIntStateOf(0) }
    var mostrarAcordes by remember(isCantante) { mutableStateOf(!isCantante) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    var altoContraste by remember { mutableStateOf(false) }
    var mostrarRielDiagramas by remember { mutableStateOf(false) }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }
    
    val metronome = remember { MetronomeController() }
    var isMetronomeRunning by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(100) }

    val scrollState = rememberScrollState()

    LaunchedEffect(cancionActual) {
        cancionActual?.ritmo?.let {
            bpm = metronome.parseBpmFromRhythm(it) ?: metronome.estimateBpmFromStyle(it)
            metronome.setBpm(bpm)
        }
    }

    DisposableEffect(Unit) { onDispose { metronome.release() } }

    val backgroundBrush = if (altoContraste) Brush.verticalGradient(listOf(Color.Black, Color.Black)) 
                         else Brush.verticalGradient(listOf(AzulProfundo, AzulMedio))

    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isActive) {
                scrollState.scrollBy(scrollSpeed)
                delay(16)
            }
        }
    }

    if (mostrarConfirmarEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarEliminar = false },
            title = { Text("¿Eliminar canción?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = { scope.launch { cancionActual?.let { viewModel.eliminarCancion(it) }; onNavegarAtras() } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Eliminar", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { mostrarConfirmarEliminar = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(cancionActual?.titulo ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(cancionActual?.autor ?: "", color = CianBrillante, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                },
                actions = {
                    if (cancionActual?.tieneAcordes == true && !isCantante) {
                        IconButton(onClick = { mostrarRielDiagramas = !mostrarRielDiagramas }) {
                            Icon(Icons.Default.MenuBook, "Diagramas", tint = if(mostrarRielDiagramas) AcordeAmarillo else Color.White)
                        }
                    }
                    IconButton(onClick = { onNavegarAEditar(cancionId) }) { Icon(Icons.Default.Edit, "Editar", tint = Color.White) }
                    IconButton(onClick = { mostrarConfirmarEliminar = true }) { Icon(Icons.Default.Delete, "Eliminar", tint = Color.White.copy(alpha = 0.5f)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if(altoContraste) Color.Black else AzulProfundo)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(backgroundBrush)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)
            ) {
                AnimatedVisibility(visible = mostrarRielDiagramas && cancionActual?.tieneAcordes == true) {
                    val acordesUnicos = remember(cancionActual, semitonos) {
                        val texto = cancionActual?.letraOriginal ?: ""
                        val listaAcordes = mutableListOf<String>()
                        
                        // Regex mejorado para capturar [Acorde] o [Etiqueta]{Acordes}
                        val regexEspecial = Regex("\\[(.*?)\\](?:\\s*\\{(.*?)\\})?")
                        regexEspecial.findAll(texto).forEach { match ->
                            val contentCorchetes = match.groupValues[1]
                            val contentLlaves = match.groupValues[2]

                            if (contentLlaves.isNotBlank()) {
                                // Es [INTRO]{F G}, extraemos de las llaves
                                contentLlaves.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach {
                                    listaAcordes.add(TonalidadUtil.transponerAcorde(it, semitonos))
                                }
                            } else {
                                // Es [F] normal, verificamos si no es una etiqueta sola
                                if (!TonalidadUtil.esEtiquetaInstrumental(contentCorchetes)) {
                                    listaAcordes.add(TonalidadUtil.transponerAcorde(contentCorchetes, semitonos))
                                }
                            }
                        }
                        listaAcordes.distinct()
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(acordesUnicos) { acorde ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.width(100.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                    Text(acorde, fontWeight = FontWeight.Bold, color = Color.Black)
                                    val fingering = ChordDictionary.getFingering(acorde)
                                    if (fingering != null) Box(modifier = Modifier.size(70.dp)) { ChordDiagram(fingering = fingering) }
                                }
                            }
                        }
                    }
                }

                val lineas = if (cancionActual != null) {
                    SongTextFormatter.formatSongTextForDisplay(cancionActual!!.letraOriginal, semitonos, mostrarAcordes)
                } else emptyList()

                lineas.forEach { (acordes, letra) ->
                    if (acordes.isNotBlank() && mostrarAcordes) {
                        Text(text = acordes, color = if(altoContraste) Color.Yellow else AcordeAmarillo, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(text = letra, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(modifier = Modifier.height(180.dp))
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                AnimatedVisibility(visible = isMetronomeRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(16.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BPM: $bpm", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                        Slider(value = bpm.toFloat(), onValueChange = { bpm = it.toInt(); metronome.setBpm(bpm) }, valueRange = 40f..240f, modifier = Modifier.weight(1f))
                        IconButton(onClick = { isMetronomeRunning = false; metronome.stop() }) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    }
                }

                Box(
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)).padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (cancionActual?.tieneAcordes == true && !isCantante) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { semitonos-- }) { Icon(Icons.Default.Remove, null, tint = Color.White) }
                                val tonoVisual = TonalidadUtil.transponerAcorde(cancionActual?.tonoOriginal ?: "C", semitonos)
                                Text(tonoVisual, color = if(semitonos == 0) AcordeAmarillo else CianBrillante, fontWeight = FontWeight.Black)
                                IconButton(onClick = { semitonos++ }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                            }
                            VerticalDivider(modifier = Modifier.height(30.dp), color = Color.White.copy(alpha = 0.2f))
                        }
                        
                        IconButton(onClick = { isAutoScrolling = !isAutoScrolling }, modifier = Modifier.background(if(isAutoScrolling) CianBrillante else Color.Transparent, RoundedCornerShape(12.dp))) {
                            Icon(if(isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if(isAutoScrolling) AzulProfundo else Color.White)
                        }

                        IconButton(onClick = { isMetronomeRunning = !isMetronomeRunning; if(isMetronomeRunning) metronome.start(scope) else metronome.stop() }) {
                            Icon(Icons.Default.Timer, null, tint = if(isMetronomeRunning) AcordeAmarillo else Color.White)
                        }

                        VerticalDivider(modifier = Modifier.height(30.dp), color = Color.White.copy(alpha = 0.2f))

                        IconButton(onClick = { altoContraste = !altoContraste }) {
                            Icon(if(altoContraste) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = if(altoContraste) AcordeAmarillo else Color.White)
                        }
                    }
                }
            }
        }
    }
}
