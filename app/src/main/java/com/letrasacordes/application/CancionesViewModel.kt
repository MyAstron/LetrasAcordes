package com.letrasacordes.application

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.gson.Gson
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.database.CancionDao
import com.letrasacordes.application.logic.CategoryRepository
import com.letrasacordes.application.logic.PdfGenerator
import com.letrasacordes.application.logic.TonalidadUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val LINE_BREAK_REPLACEMENT = "<br>"
private const val FIELD_SEPARATOR = "|||"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CancionesViewModel(
    private val dao: CancionDao, 
    private val categoryRepository: CategoryRepository,
    private val pdfGenerator: PdfGenerator,
    private val context: Context
) : ViewModel() {

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda = _textoBusqueda.asStateFlow()

    private val _categoriaSeleccionada = MutableStateFlow<String?>(null)
    val categoriaSeleccionada = _categoriaSeleccionada.asStateFlow()

    private val _categorias = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val categorias = _categorias.asStateFlow()

    private val _cancionId = MutableStateFlow<Int?>(null)
    val cancionSeleccionada: StateFlow<Cancion?> = _cancionId
        .filterNotNull()
        .flatMapLatest { id -> dao.obtenerCancionPorId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun cargarCancion(id: Int) {
        _cancionId.value = id
    }

    init {
        refrescarCategorias()
    }
    
    val todasLasCanciones: StateFlow<List<Cancion>> = dao.obtenerTodasLasCanciones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canciones: StateFlow<List<Cancion>> = combine(
        textoBusqueda.debounce(300L),
        _categoriaSeleccionada
    ) { query, category ->
        val idsCancionesCategoria = if (category != null) categoryRepository.getSongIdsForCategory(category) else null

        when {
            idsCancionesCategoria != null -> {
                if (query.isBlank()) dao.obtenerCancionesPorIds(idsCancionesCategoria.toSet())
                else dao.buscarCancionesPorIds(idsCancionesCategoria.toSet(), query)
            }
            query.isNotBlank() -> dao.buscarCanciones(query)
            else -> dao.obtenerTodasLasCanciones()
        }
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun enTextoBusquedaCambiado(nuevoTexto: String) { _textoBusqueda.value = nuevoTexto }
    fun seleccionarCategoria(nombre: String?) { _categoriaSeleccionada.value = nombre }
    fun refrescarCategorias() { _categorias.value = categoryRepository.getAllCategories() }
    
    fun guardarEnCategoria(nombre: String, id: Int) {
        val current = categoryRepository.getSongIdsForCategory(nombre).toMutableSet()
        current.add(id)
        categoryRepository.saveCategory(nombre, current.toList())
        refrescarCategorias()
    }
    
    fun quitarDeCategoria(nombre: String, id: Int) {
        val current = categoryRepository.getSongIdsForCategory(nombre).toMutableSet()
        current.remove(id)
        categoryRepository.saveCategory(nombre, current.toList())
        refrescarCategorias()
    }

    fun guardarCategoria(nombre: String, idsCanciones: List<Int>) { categoryRepository.saveCategory(nombre, idsCanciones); refrescarCategorias() }
    fun eliminarCategoria(nombre: String) { categoryRepository.deleteCategory(nombre); if (_categoriaSeleccionada.value == nombre) _categoriaSeleccionada.value = null; refrescarCategorias() }
    fun actualizarCategoria(nombreOriginal: String, nombreNuevo: String, idsCanciones: List<Int>) { if (nombreOriginal != nombreNuevo) categoryRepository.deleteCategory(nombreOriginal); categoryRepository.saveCategory(nombreNuevo, idsCanciones); refrescarCategorias(); if (_categoriaSeleccionada.value == nombreOriginal) _categoriaSeleccionada.value = nombreNuevo }

    fun generarPdf(canciones: List<Cancion>, withChords: Boolean, includeIndex: Boolean, compactMode: Boolean, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { pdfGenerator.generateSongbookPdf(canciones, withChords, includeIndex, compactMode, uri) }
    }

    suspend fun agregarCancion(titulo: String, autor: String?, ritmo: String?, letra: String, tieneAcordes: Boolean) {
        val timestamp = System.currentTimeMillis()
        val letraLimpia = letra.replace(Regex("(\\[.*?\\]|\\{.*?\\})"), "")
        val primerAcorde = if (tieneAcordes) TonalidadUtil.obtenerPrimerAcorde(letra) else null
        val nuevaCancion = Cancion(titulo = titulo.trim(), autor = autor?.trim().takeIf { !it.isNullOrBlank() }, ritmo = ritmo?.trim().takeIf { !it.isNullOrBlank() }, letraOriginal = letra, tieneAcordes = tieneAcordes, tonoOriginal = if (tieneAcordes) (primerAcorde ?: "C") else null, letraSinAcordes = letraLimpia, fechaCreacion = timestamp, ultimaEdicion = timestamp)
        dao.insertar(nuevaCancion)
    }

    suspend fun actualizarCancion(cancion: Cancion) {
        val letraLimpia = cancion.letraOriginal.replace(Regex("(\\[.*?\\]|\\{.*?\\})"), "")
        val tieneAcordesActual = cancion.letraOriginal.contains("[") && cancion.letraOriginal.contains("]")
        val primerAcorde = if (tieneAcordesActual) TonalidadUtil.obtenerPrimerAcorde(cancion.letraOriginal) else null
        val cancionActualizada = cancion.copy(letraSinAcordes = letraLimpia, tieneAcordes = tieneAcordesActual, tonoOriginal = if (tieneAcordesActual) (primerAcorde ?: "C") else null, ultimaEdicion = System.currentTimeMillis())
        dao.actualizar(cancionActualizada)
    }

    suspend fun eliminarCancion(cancion: Cancion) { dao.eliminar(cancion) }

    fun exportarCanciones(canciones: List<Cancion>): ByteArray {
        val textoPlano = canciones.joinToString("\n") { cancion ->
            listOf(
                cancion.id,
                cancion.titulo,
                cancion.autor ?: "",
                cancion.ritmo ?: "",
                cancion.letraOriginal.replace("\n", LINE_BREAK_REPLACEMENT),
                cancion.tieneAcordes,
                cancion.tonoOriginal ?: "",
                cancion.letraSinAcordes.replace("\n", LINE_BREAK_REPLACEMENT),
                cancion.fechaCreacion,
                cancion.ultimaEdicion
                // No incluimos coverUrl ni noBuscarPortada para mantener el respaldo limpio de datos de internet
            ).joinToString(FIELD_SEPARATOR)
        }
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip -> gzip.write(textoPlano.toByteArray(StandardCharsets.UTF_8)) }
        return bos.toByteArray()
    }

    suspend fun importarCanciones(datosArchivo: ByteArray): Int {
        val textoImportado = try { GZIPInputStream(ByteArrayInputStream(datosArchivo)).bufferedReader(StandardCharsets.UTF_8).use { it.readText() } } catch (e: Exception) { try { String(datosArchivo, StandardCharsets.UTF_8) } catch (e2: Exception) { return 0 } }
        val cancionesAImportar = textoImportado.lines().filter { it.isNotBlank() }.mapNotNull {
            try {
                val campos = it.split(FIELD_SEPARATOR)
                if (campos.size >= 9) {
                    val ritmoIndex = if(campos.size >= 10) 3 else -1
                    val offset = if(campos.size >= 10) 1 else 0
                    Cancion(
                        id = campos[0].toInt(),
                        titulo = campos[1],
                        autor = campos[2].takeIf { it.isNotEmpty() },
                        ritmo = if (ritmoIndex != -1) campos[ritmoIndex].takeIf { it.isNotEmpty() } else null,
                        letraOriginal = campos[3 + offset].replace(LINE_BREAK_REPLACEMENT, "\n"),
                        tieneAcordes = campos[4 + offset].toBoolean(),
                        tonoOriginal = campos[5 + offset].takeIf { it.isNotEmpty() },
                        letraSinAcordes = campos[6 + offset].replace(LINE_BREAK_REPLACEMENT, "\n"),
                        fechaCreacion = campos[7 + offset].toLong(),
                        ultimaEdicion = campos[8 + offset].toLong()
                    )
                } else null
            } catch (e: Exception) { null }
        }

        if (cancionesAImportar.isEmpty()) return 0

        // Obtener canciones existentes para emparejar por título y autor
        val cancionesExistentes = dao.obtenerTodasLasCancionesSync()

        // Helper para generar una llave única por título y autor
        fun obtenerLlave(titulo: String, autor: String?): String {
            val t = titulo.trim().lowercase()
            val a = autor?.trim()?.lowercase() ?: ""
            return "$t|$a"
        }

        val mapaExistentes = cancionesExistentes.associateBy { obtenerLlave(it.titulo, it.autor) }

        var nuevasAgregadas = 0
        var existentesActualizadas = 0

        cancionesAImportar.forEach { cancionImportada ->
            val llave = obtenerLlave(cancionImportada.titulo, cancionImportada.autor)
            val cancionExistente = mapaExistentes[llave]

            if (cancionExistente != null) {
                // Si la canción ya existe y la importada es más reciente, se actualizan sus datos manteniendo el ID local
                if (cancionImportada.ultimaEdicion > cancionExistente.ultimaEdicion) {
                    val cancionActualizada = cancionImportada.copy(
                        id = cancionExistente.id
                    )
                    dao.actualizar(cancionActualizada)
                    existentesActualizadas++
                }
            } else {
                // Si la canción no existe, la insertamos con id = 0 para evitar conflictos con IDs locales autogenerados
                val cancionNueva = cancionImportada.copy(id = 0)
                dao.insertar(cancionNueva)
                nuevasAgregadas++
            }
        }

        return nuevasAgregadas + existentesActualizadas
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as CancionarioApplication
                val dao = application.database.cancionDao()
                val categoryRepository = CategoryRepository(application.applicationContext)
                val pdfGenerator = PdfGenerator(application.applicationContext)
                return CancionesViewModel(dao, categoryRepository, pdfGenerator, application.applicationContext) as T
            }
        }
    }
}


