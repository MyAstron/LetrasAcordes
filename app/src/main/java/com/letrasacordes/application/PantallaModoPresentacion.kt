package com.letrasacordes.application

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
    onSalir: () -> Unit,
    onCancionClick: (Int) -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val todasLasCanciones by viewModel.todasLasCanciones.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    
    var cancionesPresentacion by remember { mutableStateOf<List<Cancion>>(emptyList()) }
    
    LaunchedEffect(categoria, todasLasCanciones, categorias) {
        val ids = categorias[categoria]
        val listaFiltrada = if (ids != null) {
            val mapaCanciones = todasLasCanciones.associateBy { it.id }
            ids.mapNotNull { mapaCanciones[it] }
        } else {
            todasLasCanciones
        }
        cancionesPresentacion = listaFiltrada
    }

    val lazyListState = rememberLazyListState()
    
    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var initialIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 92.dp.toPx() }

    // Función unificada para salir y limpiar si es necesario
    val salirYLimpiar = {
        if (categoria == "LISTA_TEMPORAL_AUTO") {
            viewModel.eliminarCategoria(categoria)
        }
        onSalir()
    }

    // Manejar el botón de atrás físico del dispositivo
    BackHandler {
        salirYLimpiar()
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
                    // Espacio vacío para balancear el icono de la derecha
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
                            }
                            .clickable { if (draggedItemId == null) onCancionClick(cancion.id) },
                        onDragStart = {
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
        modifier = modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, 
                contentDescription = "Reordenar", 
                tint = if (isInteractionDisabled) Color.White.copy(alpha = 0.2f) else Color.Yellow,
                modifier = Modifier
                    .size(40.dp)
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
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cancion.titulo, 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = cancion.autor ?: "Autor desconocido", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            
            Box(
                modifier = Modifier.size(32.dp).background(Color.Yellow.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.Yellow, modifier = Modifier.size(18.dp))
            }
        }
    }
}
