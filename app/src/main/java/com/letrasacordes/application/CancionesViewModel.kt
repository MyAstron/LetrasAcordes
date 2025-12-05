package com.letrasacordes.application
import com.letrasacordes.application.database.Cancion // ¡Asegúrate de importar tu entidad!

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.letrasacordes.application.database.CancionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CancionesViewModel(private val dao: CancionDao) : ViewModel() {

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda = _textoBusqueda.asStateFlow()

    val canciones: StateFlow<List<Cancion>> = textoBusqueda
        .debounce(500L) // Espera 500ms después de que el usuario deja de escribir
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.obtenerTodasLasCanciones()
            } else {
                dao.buscarCanciones(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Mantiene el flow activo 5s
            initialValue = emptyList() // Valor inicial mientras carga
        )

    fun enTextoBusquedaCambiado(nuevoTexto: String) {
        _textoBusqueda.value = nuevoTexto
    }
    /**
     * Nueva función para obtener una canción específica por su ID.
     */
    fun obtenerCancion(id: Int): StateFlow<Cancion?> {
        return dao.obtenerCancionPorId(id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

    suspend fun agregarCancion(
        titulo: String,
        autor: String?,
        letra: String,
        tieneAcordes: Boolean
    ) {
        // Creamos la instancia de la nueva canción (AHORA SÍ COINCIDE CON LA DATA CLASS)
        val timestamp = System.currentTimeMillis() // Momento actual en milisegundos

        // Lógica para extraer la letra sin acordes
        val letraLimpia = letra.replace(Regex("\\[.*?\\]"), "")

        // TODO: Lógica futura más robusta para extraer el primer acorde como tono original
        val primerAcorde = if (tieneAcordes) {
            letra.substringAfter("[").substringBefore("]")
        } else {
            null
        }
        // Creamos la instancia de la nueva canción
        val nuevaCancion = Cancion(
            titulo = titulo.trim(),
            autor = autor?.trim().takeIf { !it.isNullOrBlank() },
            letraOriginal = letra, // La letra completa con [acordes]
            tieneAcordes = tieneAcordes,
            tonoOriginal = if (tieneAcordes) primerAcorde?.takeIf { it.isNotBlank() } ?: "C" else null, // Placeholder
            letraSinAcordes = letraLimpia,
            fechaCreacion = timestamp,
            ultimaEdicion = timestamp
        )

        // Usamos el DAO para insertar el objeto en la base de datos
        dao.insertar(nuevaCancion)
    }

    // Factory para poder pasar el 'dao' al crear el ViewModel
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                val dao = (application as CancionarioApplication).database.cancionDao()
                return CancionesViewModel(dao) as T
            }
        }
    }
}
