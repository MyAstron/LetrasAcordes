package com.letrasacordes.application.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para la entidad Cancion.
 * Aquí se definen todos los métodos para acceder a la base de datos.
 */
@Dao
interface CancionDao {

    @Insert
    suspend fun insertar(cancion: Cancion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarVarias(canciones: List<Cancion>)

    @Update
    suspend fun actualizar(cancion: Cancion)

    @Delete
    suspend fun eliminar(cancion: Cancion)

    @Query("SELECT * FROM canciones WHERE id = :id")
    fun obtenerCancionPorId(id: Int): Flow<Cancion?>

    @Query("SELECT * FROM canciones ORDER BY titulo ASC")
    fun obtenerTodasLasCanciones(): Flow<List<Cancion>>

    /**
     * Busca canciones cuyo título, autor o letra contengan el texto de búsqueda.
     */
    @Query("""
        SELECT * FROM canciones WHERE 
        titulo LIKE '%' || :query || '%' COLLATE NOCASE OR 
        autor LIKE '%' || :query || '%' COLLATE NOCASE OR 
        letraSinAcordes LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY titulo ASC
    """)
    fun buscarCanciones(query: String): Flow<List<Cancion>>

    /**
     * Obtiene una lista de canciones basada en un conjunto de IDs.
     * Se usa para mostrar las canciones de una categoría.
     */
    @Query("SELECT * FROM canciones WHERE id IN (:ids) ORDER BY titulo ASC")
    fun obtenerCancionesPorIds(ids: Set<Int>): Flow<List<Cancion>>
}
