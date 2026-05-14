package com.letrasacordes.application

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaModoPresentacion(
    categoria: String,
    cancionIds: List<Int>?,
    onSalir: () -> Unit,
    onCancionClick: (Int) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val todasLasCanciones by viewModel.todasLasCanciones.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    
    var cancionesPresentacion by remember { mutableStateOf<List<Cancion>>(emptyList()) }
    
    BackHandler {
        Toast.makeText(context, "Para salir presiona el botón indicado", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(categoria, cancionIds, todasLasCanciones, categorias) {
        if (todasLasCanciones.isEmpty()) return@LaunchedEffect

        val idsParaUsar = cancionIds ?: categorias[categoria]

        if (idsParaUsar != null) {
            val mapaCanciones = todasLasCanciones.associateBy { it.id }
            cancionesPresentacion = idsParaUsar.mapNotNull { mapaCanciones[it] }
        }
    }

    val lazyListState = rememberLazyListState()
    
    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var initialIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 92.dp.toPx() }

    val salirYLimpiar = {
        if (categoria == "LISTA_TEMPORAL_AUTO") {
            viewModel.eliminarCategoria(categoria)
        }
        onSalir()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val mutableList = cancionesPresentacion.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        cancionesPresentacion = mutableList
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayTitle = if (categoria == "LISTA_TEMPORAL_AUTO") "MI LISTA" else categoria.uppercase()
                        Text(displayTitle, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                        Text("ORDENA LAS CANCIONES", style = MaterialTheme.typography.labelSmall, color = Color.Yellow)
                    }
                },
                navigationIcon = {
                    Spacer(modifier = Modifier.size(48.dp))
                },
                actions = {
                    IconButton(onClick = salirYLimpiar) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Salir", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cancionesPresentacion.isEmpty() && categoria != "Todas") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Yellow)
                        Spacer(Modifier.height(16.dp))
                        Text("Cargando lista...", color = Color.Yellow, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(cancionesPresentacion, key = { _, cancion -> cancion.id }) { index, cancion ->
                        val isDraggingThis = draggedItemId == cancion.id
                        val elevation by animateDpAsState(if (isDraggingThis) 16.dp else 0.dp, label = "elevation")
                        val scale by animateFloatAsState(if (isDraggingThis) 1.05f else 1f, label = "scale")
                        
                        val translationY = if (isDraggingThis) {
                            (initialIndex - index) * itemHeightPx + totalDragOffset
                        } else 0f

                        ItemPresentacion(
                            cancion = cancion,
                            isDragging = isDraggingThis,
                            isInteractionDisabled = draggedItemId != null && !isDraggingThis,
                            elevation = elevation,
                            modifier = Modifier
                                .animateItem() 
                                .zIndex(if (isDraggingThis) 100f else 1f)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.alpha = if (draggedItemId != null && !isDraggingThis) 0.6f else 1f
                                }
                                .clickable(enabled = draggedItemId == null) { onCancionClick(cancion.id) },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedItemId = cancion.id
                                initialIndex = index
                                totalDragOffset = 0f
                            },
                            onDrag = { dragAmount ->
                                totalDragOffset += dragAmount
                                
                                val currentIndex = cancionesPresentacion.indexOfFirst { it.id == draggedItemId }
                                if (currentIndex != -1) {
                                    val targetIndex = (initialIndex + (totalDragOffset / itemHeightPx).roundToInt())
                                        .coerceIn(0, cancionesPresentacion.size - 1)
                                    
                                    if (targetIndex != currentIndex) {
                                        moveItem(currentIndex, targetIndex)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedItemId = null
                                initialIndex = -1
                                totalDragOffset = 0f
                                viewModel.guardarCategoria(categoria, cancionesPresentacion.map { it.id })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemPresentacion(
    cancion: Cancion, 
    isDragging: Boolean,
    isInteractionDisabled: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
        ),
        border = if (isDragging) BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, 
                contentDescription = "Reordenar", 
                tint = if (isInteractionDisabled) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(isInteractionDisabled) {
                        if (isInteractionDisabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cancion.titulo, 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cancion.autor ?: "Autor desconocido", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (!isDragging) {
                Icon(
                    Icons.Default.MusicNote, 
                    null, 
                    tint = Color.Yellow.copy(alpha = 0.6f), 
                    modifier = Modifier.size(20.dp).padding(4.dp)
                )
            }
        }
    }
}
