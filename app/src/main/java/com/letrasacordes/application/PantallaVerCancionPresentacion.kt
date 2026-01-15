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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun PantallaVerCancionPresentacion(
    cancionId: Int,
    categoria: String,
    onRegresar: () -> Unit,
    onSiguienteCancion: (Int) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPreferences = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    val userProfile = remember { sharedPreferences.getString("user_profile", "GUITARRA") ?: "GUITARRA" }
    val isGuitarrista = userProfile == "GUITARRA"

    LaunchedEffect(cancionId) { viewModel.cargarCancion(cancionId) }
    val cancionActual by viewModel.cancionSeleccionada.collectAsState()
    val categorias by viewModel.categorias.collectAsState()

    var semitonos by remember { mutableIntStateOf(0) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    var mostrarRielDiagramas by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isActive) {
                scrollState.scrollBy(scrollSpeed)
                delay(16)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(cancionActual?.titulo ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(cancionActual?.autor ?: "", color = Color.Yellow, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onRegresar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                },
                actions = {
                    if (cancionActual?.tieneAcordes == true && isGuitarrista) {
                        IconButton(onClick = { mostrarRielDiagramas = !mostrarRielDiagramas }) {
                            Icon(Icons.Default.MenuBook, "Diagramas", tint = if(mostrarRielDiagramas) Color.Yellow else Color.White)
                        }
                    }
                    
                    IconButton(onClick = {
                        val ids = if (categoria == "Todas") {
                            emptyList<Int>() 
                        } else {
                            categorias[categoria] ?: emptyList()
                        }
                        
                        if (ids.isNotEmpty()) {
                            val currentIndex = ids.indexOf(cancionId)
                            if (currentIndex != -1 && currentIndex < ids.size - 1) {
                                onSiguienteCancion(ids[currentIndex + 1])
                            } else if (currentIndex == ids.size - 1) {
                                onSiguienteCancion(ids[0])
                            }
                        }
                    }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Siguiente", tint = Color.White) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)
            ) {
                AnimatedVisibility(visible = mostrarRielDiagramas && cancionActual?.tieneAcordes == true && isGuitarrista) {
                    val acordesUnicos = remember(cancionActual, semitonos) {
                        val texto = cancionActual?.letraOriginal ?: ""
                        val listaAcordes = mutableListOf<String>()
                        
                        val regexEspecial = Regex("\\[(.*?)\\](?:\\s*\\{(.*?)\\})?")
                        regexEspecial.findAll(texto).forEach { match ->
                            val contentCorchetes = match.groupValues[1]
                            val contentLlaves = match.groupValues[2]

                            if (contentLlaves.isNotBlank()) {
                                contentLlaves.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach {
                                    listaAcordes.add(TonalidadUtil.transponerAcorde(it, semitonos))
                                }
                            } else {
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
                    SongTextFormatter.formatSongTextForDisplay(cancionActual!!.letraOriginal, semitonos, isGuitarrista)
                } else emptyList()

                lineas.forEach { (acordes, letra) ->
                    if (acordes.isNotBlank() && isGuitarrista) {
                        Text(text = acordes, color = Color.Yellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(text = letra, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(modifier = Modifier.height(180.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (cancionActual?.tieneAcordes == true && isGuitarrista) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { semitonos-- }) { Icon(Icons.Default.Remove, null, tint = Color.White) }
                            val tonoVisual = TonalidadUtil.transponerAcorde(cancionActual?.tonoOriginal ?: "C", semitonos)
                            Text(tonoVisual, color = Color.Yellow, fontWeight = FontWeight.Black)
                            IconButton(onClick = { semitonos++ }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                        }
                        VerticalDivider(modifier = Modifier.height(30.dp), color = Color.White.copy(alpha = 0.2f))
                    }
                    
                    if (!cancionActual?.ritmo.isNullOrBlank()) {
                        Text(cancionActual?.ritmo ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        VerticalDivider(modifier = Modifier.height(30.dp), color = Color.White.copy(alpha = 0.2f))
                    }

                    IconButton(onClick = { isAutoScrolling = !isAutoScrolling }, modifier = Modifier.background(if(isAutoScrolling) Color.Yellow.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                        Icon(if(isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if(isAutoScrolling) Color.Yellow else Color.White)
                    }
                }
            }
        }
    }
}
