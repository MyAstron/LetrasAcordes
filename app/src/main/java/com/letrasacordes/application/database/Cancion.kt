package com.letrasacordes.application.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Define la tabla "canciones" en la base de datos.
 * Cada instancia de esta clase representa una fila en la tabla.
 */
@Entity(tableName = "canciones")
data class Cancion(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val titulo: String,

    val autor: String?,

    val ritmo: String?,

    val letraOriginal: String,

    val tieneAcordes: Boolean,

    val tonoOriginal: String?,

    val letraSinAcordes: String,

    val fechaCreacion: Long,

    val ultimaEdicion: Long,
)