package com.letrasacordes.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.letrasacordes.application.database.Cancion
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import com.google.mlkit.vision.text.Text as VisionText

// Se reutilizan las mismas expresiones regulares y lógica de normalización
private const val CHORD_PATTERN = """^((Do|Re|Mi|Fa|Sol|La|Si|[A-G]|B7)(#|b)?(m|maj|dim|aug|sus|add|M|7|9|11|13|6|5|4|2|b5|#9|#5|b9|sus4|sus2|-|-|–|—|/)?(?:[0-9b#])*(?:/[A-G](?:#|b)?)?)$"""
private val CHORD_REGEX = Regex(CHORD_PATTERN, RegexOption.IGNORE_CASE)
private val CHORD_CLEANUP_REGEX = Regex("(#|b)\\s+[-–—]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val INSTRUMENTAL_LABEL_REGEX = Regex("^(Introducci[oó0]n|Intr[o0]|Inicio|Puente|Final|Outr[o0]|Inter|Sol[o0]|Decoraciones|Remate|C[ií]rcul[o0]|Cicl[o0])\\.?:?\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

// Constantes para SharedPreferences
private const val PREFS_NAME = "app_preferences"
private const val KEY_SHOW_SCAN_WARNING = "show_scan_warning_edit" // Key distinta para editar

private fun normalizeChordForDisplay(chord: String): String {
    // Normalizar guiones
    var normalized = chord.replace('–', '-').replace('—', '-')
    
    // Convertir notación latina (Do, Re, Mi...) a notación inglesa (C, D, E...)
    normalized = when {
        normalized.startsWith("Do", ignoreCase = true) -> normalized.replaceFirst("Do", "C", true)
        normalized.startsWith("Re", ignoreCase = true) -> normalized.replaceFirst("Re", "D", true)
        normalized.startsWith("Mi", ignoreCase = true) -> normalized.replaceFirst("Mi", "E", true)
        normalized.startsWith("Fa", ignoreCase = true) -> normalized.replaceFirst("Fa", "F", true)
        normalized.startsWith("Sol", ignoreCase = true) -> normalized.replaceFirst("Sol", "G", true)
        normalized.startsWith("La", ignoreCase = true) -> normalized.replaceFirst("La", "A", true)
        normalized.startsWith("Si", ignoreCase = true) -> normalized.replaceFirst("Si", "B", true)
        else -> normalized
    }

    // Asegurar que la primera letra siempre sea mayúscula (para A-G)
    if (normalized.isNotEmpty() && normalized[0].isLowerCase()) {
        normalized = normalized.replaceFirstChar { it.uppercase() }
    }

    // Normalizar acordes menores usando 'm' minúscula
    if (normalized.contains("-")) {
        normalized = normalized.replaceFirst("-", "m")
    }
    
    return normalized
}

private fun isTokenChord(token: String): Boolean {
    if (CHORD_REGEX.matches(token)) return true
    if (token.contains("-") || token.contains("/")) {
        val parts = token.split(Regex("[-/]"))
        // Check if parts are empty or look like chords
        val allPartsAreChords = parts.all { it.isBlank() || CHORD_REGEX.matches(it) }
        // Ensure at least one part is a valid chord token (not just empty strings from split)
        if (allPartsAreChords && parts.any { it.isNotBlank() }) return true
    }
    return false
}

private fun isChordLine(line: String): Boolean {
    val correctedLine = line.trim().replace(CHORD_CLEANUP_REGEX, "$1-")
    if (correctedLine.isBlank()) return false
    val words = correctedLine.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    if (words.isEmpty()) return false
    val chordCount = words.count { isTokenChord(it) }
    val chordRatio = chordCount.toDouble() / words.size
    return chordRatio > 0.6
}

private fun mergeChordAndLyricLines(chordLines: List<VisionText.Line>, lyricLine: VisionText.Line): String {
    val lyricElements = lyricLine.elements.sortedBy { it.boundingBox?.left }
    if (lyricElements.isEmpty()) return ""
    val insertions = mutableMapOf<Int, MutableList<Pair<String, Int>>>()
    val allChordElements = chordLines.flatMap { it.elements }

    for (chordElement in allChordElements) {
        val rawChordText = chordElement.text.replace(CHORD_CLEANUP_REGEX, "$1-")
        if (!CHORD_REGEX.matches(rawChordText) && !isTokenChord(rawChordText)) continue
        val chordBox = chordElement.boundingBox ?: continue
        val chordCenter = chordBox.centerX()
        var targetElementIndex: Int? = null

        for ((index, element) in lyricElements.withIndex()) {
            val elementBox = element.boundingBox ?: continue
            if (chordCenter >= elementBox.left && chordCenter <= elementBox.right) {
                targetElementIndex = index
                break
            }
        }

        if (targetElementIndex == null) {
            targetElementIndex = lyricElements.indices.minByOrNull { i ->
                val lyricBox = lyricElements[i].boundingBox ?: return@minByOrNull Int.MAX_VALUE
                val distToLeft = abs(chordCenter - lyricBox.left)
                val distToRight = abs(chordCenter - lyricBox.right)
                minOf(distToLeft, distToRight)
            }
        }

        if (targetElementIndex == null) continue
        val targetElement = lyricElements[targetElementIndex]
        val targetElementBox = targetElement.boundingBox ?: continue
        val targetWord = targetElement.text
        val avgCharWidth = targetElementBox.width().toFloat() / targetWord.length.coerceAtLeast(1)
        val offset = (chordCenter - targetElementBox.left)
        val insertionIndexInWord = if (avgCharWidth > 0f) {
             (offset / avgCharWidth).toInt().coerceIn(0, targetWord.length)
        } else {
            0
        }
        val displayChord = normalizeChordForDisplay(rawChordText)
        val entry = insertions.getOrPut(targetElementIndex) { mutableListOf() }
        entry.add("[$displayChord]" to insertionIndexInWord)
    }

    val finalLyricParts = lyricElements.map { it.text }.toMutableList()
    for (i in finalLyricParts.indices.reversed()) {
        val wordInsertions = insertions[i] ?: continue
        val sortedWordInsertions = wordInsertions.sortedByDescending { it.second }
        var currentWord = finalLyricParts[i]
        for ((chord, index) in sortedWordInsertions) {
            currentWord = currentWord.substring(0, index) + chord + currentWord.substring(index)
        }
        finalLyricParts[i] = currentWord
    }
    return finalLyricParts.joinToString(" ")
}

private fun createImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "images/pic_edit.jpg")
    imageFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditarCancion(
    cancionId: Int,
    onNavegarAtras: () -> Unit,
    viewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    LaunchedEffect(cancionId) {
        viewModel.cargarCancion(cancionId)
    }

    val cancionState by viewModel.cancionSeleccionada.collectAsState()
    val cancion = cancionState

    // Contexto y Preferencias
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var titulo by remember(cancion) { mutableStateOf(cancion?.titulo ?: "") }
    var autor by remember(cancion) { mutableStateOf(cancion?.autor ?: "") }
    var ritmo by remember(cancion) { mutableStateOf(cancion?.ritmo ?: "") }
    var letraOriginal by remember(cancion) { mutableStateOf(cancion?.letraOriginal ?: "") }
    
    var isRhythmExpanded by remember { mutableStateOf(false) }
    var showScanWarningDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf<() -> Unit>({}) }
    var shouldAppendScannedText by remember { mutableStateOf(false) }

    val tieneAcordes = letraOriginal.contains("[") && letraOriginal.contains("]")
    val scope = rememberCoroutineScope()

    val rhythmOptions = listOf(
        "Balada 4/4", "Balada 6/8", "Rock", "Pop", "Bolero", 
        "Ranchera", "Cumbia", "Salsa", "Reggae", "Ska", 
        "Blues", "Jazz", "Bossa Nova", "Arpegio", 
        "Corrido", "Norteño", "Huapango", "Vals"
    )

    // ML Kit y Launcher (Duplicado de lógica de agregar, idealmente refactorizar en un Composable común o Helper)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText: VisionText ->
                        val allLines = visionText.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top }
                        val processedLines = mutableListOf<String>()
                        val pendingChordLines = mutableListOf<VisionText.Line>()

                        for (line in allLines) {
                            val lineText = line.text.trim()
                            val instrumentalMatch = INSTRUMENTAL_LABEL_REGEX.matchEntire(lineText)
                            if (instrumentalMatch != null) {
                                pendingChordLines.clear()
                                val labelRaw = instrumentalMatch.groupValues[1]
                                val upperLabel = labelRaw.uppercase().replace("0", "O")
                                val standardizedLabel = when {
                                    upperLabel.contains("INICIO") || upperLabel.startsWith("INTRO") -> "INTRO"
                                    upperLabel.startsWith("OUT") || upperLabel.startsWith("FIN") || upperLabel.startsWith("REM") -> "FINAL"
                                    upperLabel.startsWith("PUENTE") || upperLabel.startsWith("INTER") -> "PUENTE"
                                    upperLabel.startsWith("SOL") || upperLabel.startsWith("DEC") -> "SOLO"
                                    upperLabel.startsWith("CIR") || upperLabel.startsWith("CIC") -> "CIRCULO"
                                    else -> upperLabel
                                }
                                val content = instrumentalMatch.groupValues[2].trim()
                                val tokens = content.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
                                val hasChords = tokens.any { isTokenChord(it) }

                                if (hasChords) {
                                    val normalizedContent = tokens.joinToString(" ") { normalizeChordForDisplay(it) }
                                    processedLines.add("[$standardizedLabel]{$normalizedContent}")
                                }
                                continue
                            }
                            if (isChordLine(lineText)) {
                                pendingChordLines.add(line)
                            } else {
                                if (pendingChordLines.isNotEmpty()) {
                                    val mergedLine = mergeChordAndLyricLines(pendingChordLines, line)
                                    processedLines.add(mergedLine)
                                    pendingChordLines.clear()
                                } else {
                                    processedLines.add(lineText)
                                }
                            }
                        }

                        if (pendingChordLines.isNotEmpty()) {
                             val remainingChords = pendingChordLines.joinToString(" ") { chordLine ->
                                chordLine.text.split(WHITESPACE_REGEX)
                                    .filter { isTokenChord(it) }
                                    .joinToString(" ") { normalizeChordForDisplay(it) }
                            }.trim()
                            if (remainingChords.isNotBlank()) {
                                processedLines.add("{ $remainingChords }")
                            }
                        }
                        
                        val detectedText = processedLines.joinToString("\n")
                        
                        if (letraOriginal.isBlank() || !shouldAppendScannedText) {
                            letraOriginal = detectedText
                        } else {
                            letraOriginal += "\n\n" + detectedText
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(context, "Error al reconocer texto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            val exception = result.error
            if (exception != null) {
                Toast.makeText(context, "Recorte cancelado o fallido: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val cropOptions = CropImageContractOptions(it, CropImageOptions(initialCropWindowPaddingRatio = 0.1f))
                cropImageLauncher.launch(cropOptions)
            }
        }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri?.let {
                    val cropOptions = CropImageContractOptions(it, CropImageOptions(initialCropWindowPaddingRatio = 0.1f))
                    cropImageLauncher.launch(cropOptions)
                }
            } else {
                Toast.makeText(context, "Error al capturar imagen", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun launchCamera() {
        val uri = createImageUri(context)
        imageUri = uri
        takePictureLauncher.launch(uri)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun checkAndExecuteAction(action: () -> Unit) {
        val showWarning = sharedPreferences.getBoolean(KEY_SHOW_SCAN_WARNING, true)
        if (showWarning) {
            currentAction = action
            showScanWarningDialog = true
        } else {
            action()
        }
    }

    // --- DIÁLOGOS ---
    if (showScanWarningDialog) {
        var doNotShowAgain by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showScanWarningDialog = false },
            title = { Text("Importante", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("El escaneo reemplazará el texto actual de la letra.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Usa la mayor resolución posible.")
                    Text("• El sistema puede requerir edición manual.")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { doNotShowAgain = !doNotShowAgain }
                    ) {
                        Checkbox(
                            checked = doNotShowAgain,
                            onCheckedChange = { doNotShowAgain = it }
                        )
                        Text("No volver a mostrar", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (doNotShowAgain) {
                        sharedPreferences.edit().putBoolean(KEY_SHOW_SCAN_WARNING, false).apply()
                    }
                    showScanWarningDialog = false
                    currentAction()
                }) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanWarningDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de sobrescritura
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Texto existente") },
            text = { Text("Ya existe texto en el campo de letra. ¿Qué deseas hacer?") },
            confirmButton = {
                TextButton(onClick = {
                    shouldAppendScannedText = true
                    showOverwriteDialog = false
                    checkAndExecuteAction(currentAction)
                }) {
                    Text("Agregar al final")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    shouldAppendScannedText = false
                    showOverwriteDialog = false
                    checkAndExecuteAction(currentAction)
                }) {
                    Text("Reemplazar todo")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Canción") },
                navigationIcon = {
                    IconButton(onClick = onNavegarAtras) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar y regresar")
                    }
                },
                actions = {
                    IconButton(
                        enabled = cancion != null,
                        onClick = {
                            val cancionParaActualizar = cancion
                            if (cancionParaActualizar != null) {
                                // Aplicar normalización de acordes al guardar manualmente
                                val regexAcordes = Regex("\\[(.*?)\\]")
                                val letraNormalizada = letraOriginal.replace(regexAcordes) { match ->
                                    val acorde = match.groupValues[1]
                                    "[${normalizeChordForDisplay(acorde)}]"
                                }
                                
                                val cancionActualizada = cancionParaActualizar.copy(
                                    titulo = titulo,
                                    autor = autor.takeIf { it.isNotBlank() },
                                    ritmo = ritmo.takeIf { it.isNotBlank() },
                                    letraOriginal = letraNormalizada,
                                    tieneAcordes = tieneAcordes,
                                    ultimaEdicion = System.currentTimeMillis()
                                )
                                scope.launch {
                                    viewModel.actualizarCancion(cancionActualizada)
                                    onNavegarAtras()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar Cambios")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (cancion == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título de la canción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = autor,
                    onValueChange = { autor = it },
                    label = { Text("Autor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Selector de Ritmo (Ahora sí se guarda)
                ExposedDropdownMenuBox(
                    expanded = isRhythmExpanded,
                    onExpandedChange = { isRhythmExpanded = !isRhythmExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = ritmo,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ritmo sugerido") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRhythmExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isRhythmExpanded,
                        onDismissRequest = { isRhythmExpanded = false }
                    ) {
                        rhythmOptions.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion) },
                                onClick = {
                                    ritmo = opcion
                                    isRhythmExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)) {
                    OutlinedTextField(
                        value = letraOriginal,
                        onValueChange = { letraOriginal = it },
                        label = { Text("Letra y Acordes") },
                        modifier = Modifier.fillMaxSize(),
                        supportingText = { Text("Usa el formato [Am]Letra para los acordes") }
                    )
                    
                     Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                val action = { pickImageLauncher.launch("image/*") }
                                if (letraOriginal.isNotBlank()) {
                                    currentAction = action
                                    showOverwriteDialog = true
                                } else {
                                    shouldAppendScannedText = false
                                    checkAndExecuteAction(action)
                                }
                            },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.CropOriginal, contentDescription = "Galería")
                        }

                        FloatingActionButton(
                            onClick = {
                                val action = {
                                    when (PackageManager.PERMISSION_GRANTED) {
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                            launchCamera()
                                        }
                                        else -> {
                                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                }
                                if (letraOriginal.isNotBlank()) {
                                    currentAction = action
                                    showOverwriteDialog = true
                                } else {
                                    shouldAppendScannedText = false
                                    checkAndExecuteAction(action)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Cámara")
                        }
                    }
                }
            }
        }
    }
}
