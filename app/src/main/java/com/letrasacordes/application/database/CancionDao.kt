package com.letrasacordes.application.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para la entidad Cancion.
 * Aquí se definen todos los métodos para acceder a la base de datos.
 */
@Dao
interface CancionDao {

    /**
     * Inserta una nueva canción en la tabla. Si ya existe una canción con la misma
     * clave primaria, la operación fallará.
     * La anotación 'suspend' indica que debe ser llamada desde una corrutina.
     */
    @Insert
    suspend fun insertar(cancion: Cancion)

    /**
     * Actualiza una canción existente. Room usa la clave primaria para encontrar la canción.
     */
    @Update
    suspend fun actualizar(cancion: Cancion)

    /**
     * Elimina una canción de la tabla.
     */
    @Delete
    suspend fun eliminar(cancion: Cancion)

    /**
     * Obtiene una canción específica por su ID.
     * Flow<Cancion?> emitirá un nuevo valor cada vez que los datos de esa canción cambien.
     * Es nullable (?) por si no se encuentra una canción con ese ID.
     */
    @Query("SELECT * FROM canciones WHERE id = :id")
    fun obtenerCancionPorId(id: Int): Flow<Cancion?>

    /**
     * Obtiene todas las canciones ordenadas por título.
     * Flow<List<Cancion>> es un flujo de datos que se actualiza automáticamente en la UI
     * cuando los datos de la tabla de canciones cambian (se añade, edita o borra una canción).
     */
    @Query("SELECT * FROM canciones ORDER BY titulo ASC")
    fun obtenerTodasLasCanciones(): Flow<List<Cancion>>

    /**
     * Busca canciones cuyo título, autor o letra contengan el texto de búsqueda.
     * La búsqueda no distingue mayúsculas de minúsculas (COLLATE NOCASE).
     * Utilizamos 'letraSinAcordes' para que la búsqueda en la letra sea más limpia.
     * Los '%' son comodines que significan 'cualquier cadena de texto'.
     */
    @Query("""
        SELECT * FROM canciones WHERE 
        titulo LIKE '%' || :query || '%' COLLATE NOCASE OR 
        autor LIKE '%' || :query || '%' COLLATE NOCASE OR 
        letraSinAcordes LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY titulo ASC
    """)
    fun buscarCanciones(query: String): Flow<List<Cancion>>
}
