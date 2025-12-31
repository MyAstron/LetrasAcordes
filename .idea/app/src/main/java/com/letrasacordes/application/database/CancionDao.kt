package com.letrasacordes.application.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    // Modificado para ordenar alfabéticamente, con números y símbolos al final.
    @Query("""
        SELECT * FROM canciones 
        ORDER BY 
            CASE 
                WHEN SUBSTR(TRIM(titulo), 1, 1) GLOB '[A-Za-z]' THEN 1
                ELSE 2 
            END, 
            titulo COLLATE NOCASE ASC
    """)
    fun obtenerTodasLasCanciones(): Flow<List<Cancion>>

    // Modificado para ordenar alfabéticamente, con números y símbolos al final.
    @Query("""
        SELECT * FROM canciones WHERE 
        titulo LIKE '%' || :query || '%' COLLATE NOCASE OR 
        autor LIKE '%' || :query || '%' COLLATE NOCASE OR 
        letraSinAcordes LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY 
            CASE 
                WHEN SUBSTR(TRIM(titulo), 1, 1) GLOB '[A-Za-z]' THEN 1
                ELSE 2 
            END, 
            titulo COLLATE NOCASE ASC
    """)
    fun buscarCanciones(query: String): Flow<List<Cancion>>

    // Modificado para ordenar alfabéticamente, con números y símbolos al final.
    @Query("""
        SELECT * FROM canciones WHERE id IN (:ids) 
        ORDER BY 
            CASE 
                WHEN SUBSTR(TRIM(titulo), 1, 1) GLOB '[A-Za-z]' THEN 1
                ELSE 2 
            END, 
            titulo COLLATE NOCASE ASC
    """)
    fun obtenerCancionesPorIds(ids: Set<Int>): Flow<List<Cancion>>

    // Modificado para ordenar alfabéticamente, con números y símbolos al final.
    @Query("""
        SELECT * FROM canciones WHERE id IN (:ids) AND (
            titulo LIKE '%' || :query || '%' COLLATE NOCASE OR 
            autor LIKE '%' || :query || '%' COLLATE NOCASE OR 
            letraSinAcordes LIKE '%' || :query || '%' COLLATE NOCASE
        )
        ORDER BY 
            CASE 
                WHEN SUBSTR(TRIM(titulo), 1, 1) GLOB '[A-Za-z]' THEN 1
                ELSE 2 
            END, 
            titulo COLLATE NOCASE ASC
    """)
    fun buscarCancionesPorIds(ids: Set<Int>, query: String): Flow<List<Cancion>>
}
