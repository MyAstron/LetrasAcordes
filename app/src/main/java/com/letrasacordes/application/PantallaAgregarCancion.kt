package com.letrasacordes.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.letrasacordes.application.logic.ChordDetectorController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import com.google.mlkit.vision.text.Text as VisionText

// Lógica de normalización y detección (se mantiene igual)
private const val CHORD_PATTERN = """^((Do|Re|Mi|Fa|Sol|La|Si|[A-G]|B7)(#|b)?(m|maj|dim|aug|sus|add|M|7|9|11|13|6|5|4|2|b5|#9|#5|b9|sus4|sus2|-|-|–|—|/)?(?:[0-9b#])*(?:/[A-G](?:#|b)?)?)$"""
private val CHORD_REGEX = Regex(CHORD_PATTERN, RegexOption.IGNORE_CASE)
private val CHORD_CLEANUP_REGEX = Regex("(#|b)\\s+[-–—]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val INSTRUMENTAL_LABEL_REGEX = Regex("^(Introducci[oó0]n|Intr[o0]|Inicio|Puente|Final|Outr[o0]|Inter|Sol[o0]|Decoraciones|Remate|C[ií]rcul[o0]|Cicl[o0])\\.?:?\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private const val PREFS_NAME = "app_preferences"
private const val KEY_SHOW_SCAN_WARNING = "show_scan_warning"
private const val KEY_SHOW_TUTORIAL = "show_tutorial"

private fun normalizeChordForDisplay(chord: String): String {
    var normalized = chord.replace('–', '-').replace('—', '-')
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
    if (normalized.isNotEmpty() && normalized[0].isLowerCase()) {
        normalized = normalized.replaceFirstChar { it.uppercase() }
    }
    if (normalized.contains("-")) {
        normalized = normalized.replaceFirst("-", "m")
    }
    return normalized
}

private fun isTokenChord(token: String): Boolean {
    if (CHORD_REGEX.matches(token)) return true
    if (token.contains("-") || token.contains("/")) {
        val parts = token.split(Regex("[-/]"))
        val allPartsAreChords = parts.all { it.isBlank() || CHORD_REGEX.matches(it) }
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
    val imageFile = File(context.cacheDir, "images/pic.jpg")
    imageFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAgregarCancion(
    onNavegarAtras: () -> Unit,
    cancionesViewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var titulo by remember { mutableStateOf("") }
    var autor by remember { mutableStateOf("") }
    var ritmo by remember { mutableStateOf("") }
    var letraValue by remember { mutableStateOf(TextFieldValue("")) }
    
    var isRhythmExpanded by remember { mutableStateOf(false) }
    var showScanWarningDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_TUTORIAL, true)) }
    var shouldAppendScannedText by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf<() -> Unit>({}) }
    
    // Estados de Notas Musicales (TECLADO)
    var showNotesBar by remember { mutableStateOf(false) }
    var selectedRootNote by remember { mutableStateOf<String?>(null) }
    val roots = listOf("C", "D", "E", "F", "G", "A", "B")
    val variations = listOf("", "m", "7", "m7", "maj7", "#", "#m", "b", "bm")

    // Estados de Transcripción (MODO PRUEBAS)
    var isDetectingChords by remember { mutableStateOf(false) }
    val chordDetector = remember { ChordDetectorController() }
    var currentDetectedChordLabel by remember { mutableStateOf("--") }
    
    val scope = rememberCoroutineScope()

    // Lógica de inserción
    fun insertarAcorde(acorde: String) {
        val textoAcorde = "[$acorde]"
        val currentText = letraValue.text
        val selection = letraValue.selection
        val newText = StringBuilder(currentText).insert(selection.start, textoAcorde).toString()
        letraValue = TextFieldValue(text = newText, selection = TextRange(selection.start + textoAcorde.length))
        selectedRootNote = null
    }

    // Lógica Mic (Visual)
    DisposableEffect(isDetectingChords) {
        if (isDetectingChords) {
            chordDetector.onChordDetected = { result -> currentDetectedChordLabel = result.chordName }
            chordDetector.start(scope)
        } else { chordDetector.stop(); currentDetectedChordLabel = "--" }
        onDispose { chordDetector.stop() }
    }

    // ML Kit y Lanzadores
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
                                val upperLabel = instrumentalMatch.groupValues[1].uppercase().replace("0", "O")
                                val standardizedLabel = when {
                                    upperLabel.contains("INICIO") || upperLabel.startsWith("INTRO") -> "INTRO"
                                    upperLabel.startsWith("OUT") || upperLabel.startsWith("FIN") || upperLabel.startsWith("REM") -> "FINAL"
                                    else -> upperLabel
                                }
                                val content = instrumentalMatch.groupValues[2].trim()
                                val tokens = content.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
                                if (tokens.any { isTokenChord(it) }) {
                                    val normalizedContent = tokens.joinToString(" ") { normalizeChordForDisplay(it) }
                                    processedLines.add("[$standardizedLabel]{$normalizedContent}")
                                }
                                continue
                            }
                            if (isChordLine(lineText)) pendingChordLines.add(line) else {
                                if (pendingChordLines.isNotEmpty()) {
                                    processedLines.add(mergeChordAndLyricLines(pendingChordLines, line))
                                    pendingChordLines.clear()
                                } else processedLines.add(lineText)
                            }
                        }
                        if (pendingChordLines.isNotEmpty()) {
                             val remaining = pendingChordLines.joinToString(" ") { cl ->
                                cl.text.split(WHITESPACE_REGEX).filter { isTokenChord(it) }.joinToString(" ") { normalizeChordForDisplay(it) }
                            }
                            if (remaining.isNotBlank()) processedLines.add("{ $remaining }")
                        }
                        val detectedText = processedLines.joinToString("\n")
                        letraValue = if (letraValue.text.isBlank() || !shouldAppendScannedText) TextFieldValue(detectedText) else TextFieldValue(letraValue.text + "\n\n" + detectedText)
                    }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { cropImageLauncher.launch(CropImageContractOptions(it, CropImageOptions(initialCropWindowPaddingRatio = 0.1f))) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
        if (success) imageUri?.let { cropImageLauncher.launch(CropImageContractOptions(it, CropImageOptions(initialCropWindowPaddingRatio = 0.1f))) }
    }

    fun launchCamera() { val uri = createImageUri(context); imageUri = uri; takePictureLauncher.launch(uri) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) currentAction() else Toast.makeText(context, "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    fun checkAndExecuteAction(action: () -> Unit) {
        if (sharedPreferences.getBoolean(KEY_SHOW_SCAN_WARNING, true)) { currentAction = action; showScanWarningDialog = true } else action()
    }

    if (showTutorialDialog) {
        TutorialDialog(onDismiss = { showTutorialDialog = false }, onFinishWithPreference = { doNotShow ->
            if (doNotShow) sharedPreferences.edit().putBoolean(KEY_SHOW_TUTORIAL, false).apply()
            showTutorialDialog = false
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar Nueva Canción") },
                navigationIcon = { IconButton(onClick = onNavegarAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar") } },
                actions = {
                    IconButton(
                        onClick = {
                            val letraNormalizada = letraValue.text.replace(Regex("\\[(.*?)\\]")) { "[${normalizeChordForDisplay(it.groupValues[1])}]" }
                            scope.launch { cancionesViewModel.agregarCancion(titulo, autor, ritmo, letraNormalizada, letraNormalizada.contains("[")); onNavegarAtras() }
                        },
                        enabled = titulo.isNotBlank() && letraValue.text.isNotBlank()
                    ) { Icon(Icons.Default.Check, "Guardar") }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = autor, onValueChange = { autor = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // Selector de Ritmo (Dropdown)
                ExposedDropdownMenuBox(expanded = isRhythmExpanded, onExpandedChange = { isRhythmExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = ritmo, onValueChange = {}, readOnly = true, label = { Text("Ritmo sugerido") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRhythmExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = isRhythmExpanded, onDismissRequest = { isRhythmExpanded = false }) {
                        listOf("Balada", "Rock", "Pop", "Bolero", "Cumbia", "Salsa", "Arpegio", "Vals").forEach { op ->
                            DropdownMenuItem(text = { Text(op) }, onClick = { ritmo = op; isRhythmExpanded = false })
                        }
                    }
                }

                // CUADRO DE LETRA CON PANEL INTERNO
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    OutlinedTextField(
                        value = letraValue,
                        onValueChange = { letraValue = it },
                        modifier = Modifier.fillMaxSize(),
                        label = { Text("Letra y Acordes") }
                    )

                    // PANEL DE CONTROL FIJO AL FONDO DEL CUADRO
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .fillMaxWidth(0.95f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 4.dp
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 1. Carril de Notas Musicales (Si está activo)
                            if (showNotesBar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedRootNote == null) {
                                        roots.forEach { note ->
                                            TextButton(onClick = { selectedRootNote = note }, contentPadding = PaddingValues(4.dp)) {
                                                Text(note, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            }
                                        }
                                    } else {
                                        IconButton(onClick = { selectedRootNote = null }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.ArrowBack, null, tint = Color.Red)
                                        }
                                        variations.forEach { variant ->
                                            TextButton(onClick = { insertarAcorde("$selectedRootNote$variant") }, contentPadding = PaddingValues(4.dp)) {
                                                Text(if (variant.isEmpty()) "M" else variant, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }

                            // 2. Fila de Botones de Herramientas
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showNotesBar = !showNotesBar }) {
                                    Icon(Icons.Default.MusicNote, "Teclado Musical", tint = if (showNotesBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = {
                                    val act = { pickImageLauncher.launch("image/*") }
                                    if (letraValue.text.isNotBlank()) { currentAction = act; showOverwriteDialog = true } else act()
                                }) { Icon(Icons.Default.CropOriginal, "Galería", tint = MaterialTheme.colorScheme.primary) }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = {
                                    val act = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                                        else { currentAction = { launchCamera() }; requestPermissionLauncher.launch(Manifest.permission.CAMERA) }
                                    }
                                    if (letraValue.text.isNotBlank()) { currentAction = act; showOverwriteDialog = true } else act()
                                }) { Icon(Icons.Default.PhotoCamera, "Cámara", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }

            // Detección Polifónica (Overlay Pruebas)
            if (isDetectingChords) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { isDetectingChords = false }, contentAlignment = Alignment.Center) {
                    Card(modifier = Modifier.size(200.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Analizando Polifonía:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(currentDetectedChordLabel, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Toca para detener", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit, onFinishWithPreference: (Boolean) -> Unit) {
    // Se mantiene igual...
}
