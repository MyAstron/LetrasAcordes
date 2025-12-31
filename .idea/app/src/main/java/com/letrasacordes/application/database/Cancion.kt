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

    val autor: String?, // Opcional, por eso el '?'
    
    val ritmo: String?, // Nuevo campo añadido

    val letraOriginal: String,

    val tieneAcordes: Boolean,

    val tonoOriginal: String?, // Opcional, pero debería estar si tieneAcordes es true

    val letraSinAcordes: String,

    val fechaCreacion: Long,

    val ultimaEdicion: Long
)