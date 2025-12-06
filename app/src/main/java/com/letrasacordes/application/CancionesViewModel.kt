package com.letrasacordes.application

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.letrasacordes.application.database.Cancion
import com.letrasacordes.application.database.CancionDao
import com.letrasacordes.application.logic.CategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

private const val LINE_BREAK_REPLACEMENT = "<br>"
private const val FIELD_SEPARATOR = "|||"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CancionesViewModel(private val dao: CancionDao, private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda = _textoBusqueda.asStateFlow()

    private val _categoriaSeleccionada = MutableStateFlow<String?>(null)
    val categoriaSeleccionada = _categoriaSeleccionada.asStateFlow()

    private val _categorias = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val categorias = _categorias.asStateFlow()

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
                if (query.isBlank()) {
                    dao.obtenerCancionesPorIds(idsCancionesCategoria)
                } else {
                    dao.obtenerCancionesPorIds(idsCancionesCategoria).map { canciones ->
                        canciones.filter { it.titulo.contains(query, ignoreCase = true) || it.autor?.contains(query, ignoreCase = true) ?: false }
                    }
                }
            }
            query.isNotBlank() -> dao.buscarCanciones(query)
            else -> dao.obtenerTodasLasCanciones()
        }
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun enTextoBusquedaCambiado(nuevoTexto: String) {
        _textoBusqueda.value = nuevoTexto
    }

    fun seleccionarCategoria(nombre: String?) {
        _categoriaSeleccionada.value = nombre
    }

    private fun refrescarCategorias() {
        _categorias.value = categoryRepository.getAllCategories()
    }

    fun guardarCategoria(nombre: String, idsCanciones: Set<Int>) {
        categoryRepository.saveCategory(nombre, idsCanciones)
        refrescarCategorias()
    }

    fun eliminarCategoria(nombre: String) {
        categoryRepository.deleteCategory(nombre)
        if (_categoriaSeleccionada.value == nombre) {
            _categoriaSeleccionada.value = null
        }
        refrescarCategorias()
    }

    fun actualizarCategoria(nombreOriginal: String, nombreNuevo: String, idsCanciones: Set<Int>) {
        if (nombreOriginal != nombreNuevo) {
            categoryRepository.deleteCategory(nombreOriginal)
        }
        categoryRepository.saveCategory(nombreNuevo, idsCanciones)
        refrescarCategorias()
        if (_categoriaSeleccionada.value == nombreOriginal) {
            _categoriaSeleccionada.value = nombreNuevo
        }
    }

    fun obtenerCancion(id: Int): StateFlow<Cancion?> {
        return dao.obtenerCancionPorId(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    suspend fun agregarCancion(
        titulo: String,
        autor: String?,
        letra: String,
        tieneAcordes: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        val letraLimpia = letra.replace(Regex("\\[.*?\\]"), "")
        val primerAcorde = if (tieneAcordes) letra.substringAfter("[").substringBefore("]") else null
        val nuevaCancion = Cancion(
            titulo = titulo.trim(),
            autor = autor?.trim().takeIf { !it.isNullOrBlank() },
            letraOriginal = letra,
            tieneAcordes = tieneAcordes,
            tonoOriginal = if (tieneAcordes) primerAcorde?.takeIf { it.isNotBlank() } ?: "C" else null,
            letraSinAcordes = letraLimpia,
            fechaCreacion = timestamp,
            ultimaEdicion = timestamp
        )
        dao.insertar(nuevaCancion)
    }

    suspend fun actualizarCancion(cancion: Cancion) {
        val letraLimpia = cancion.letraOriginal.replace(Regex("\\[.*?\\]"), "")
        val primerAcorde = if (cancion.tieneAcordes) cancion.letraOriginal.substringAfter("[").substringBefore("]").takeIf { it.isNotBlank() } ?: "C" else null
        val cancionActualizada = cancion.copy(
            letraSinAcordes = letraLimpia,
            tonoOriginal = primerAcorde,
            ultimaEdicion = System.currentTimeMillis()
        )
        dao.actualizar(cancionActualizada)
    }

    suspend fun eliminarCancion(cancion: Cancion) {
        dao.eliminar(cancion)
    }

    fun exportarCanciones(canciones: List<Cancion>): String {
        return canciones.joinToString("\n") { cancion ->
            listOf(
                cancion.id,
                cancion.titulo,
                cancion.autor ?: "",
                cancion.letraOriginal.replace("\n", LINE_BREAK_REPLACEMENT),
                cancion.tieneAcordes,
                cancion.tonoOriginal ?: "",
                cancion.letraSinAcordes.replace("\n", LINE_BREAK_REPLACEMENT),
                cancion.fechaCreacion,
                cancion.ultimaEdicion
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    suspend fun importarCanciones(textoImportado: String): Int {
        val cancionesAImportar = textoImportado.lines().filter { it.isNotBlank() }.mapNotNull {
            try {
                val campos = it.split(FIELD_SEPARATOR)
                if (campos.size == 9) {
                    Cancion(
                        id = campos[0].toInt(),
                        titulo = campos[1],
                        autor = campos[2].takeIf { it.isNotEmpty() },
                        letraOriginal = campos[3].replace(LINE_BREAK_REPLACEMENT, "\n"),
                        tieneAcordes = campos[4].toBoolean(),
                        tonoOriginal = campos[5].takeIf { it.isNotEmpty() },
                        letraSinAcordes = campos[6].replace(LINE_BREAK_REPLACEMENT, "\n"),
                        fechaCreacion = campos[7].toLong(),
                        ultimaEdicion = campos[8].toLong()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (cancionesAImportar.isNotEmpty()) {
            dao.insertarVarias(cancionesAImportar)
        }
        return cancionesAImportar.size
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as CancionarioApplication
                val dao = application.database.cancionDao()
                val categoryRepository = CategoryRepository(application.applicationContext)
                return CancionesViewModel(dao, categoryRepository) as T
            }
        }
    }
}
