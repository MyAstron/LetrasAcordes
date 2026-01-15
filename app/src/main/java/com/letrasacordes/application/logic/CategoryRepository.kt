package com.letrasacordes.application.logic

import android.content.Context

/**
 * Repositorio para gestionar las categorías (listas) de canciones.
 * Utiliza SharedPreferences para persistir los datos de forma local y ligera.
 * Se ha actualizado para usar String delimitado por comas para preservar el orden.
 */
class CategoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("song_categories", Context.MODE_PRIVATE)

    /**
     * Obtiene todas las categorías guardadas.
     * @return Un mapa donde la clave es el nombre de la categoría y el valor es una lista de IDs de canciones.
     */
    fun getAllCategories(): Map<String, List<Int>> {
        return prefs.all.mapValues { entry ->
            val value = entry.value
            when (value) {
                is String -> {
                    value.split(",").mapNotNull { it.toIntOrNull() }
                }
                is Set<*> -> {
                    // Compatibilidad con versiones anteriores que usaban StringSet
                    value.filterIsInstance<String>().mapNotNull { it.toIntOrNull() }
                }
                else -> emptyList()
            }
        }
    }

    /**
     * Guarda una nueva categoría o actualiza una existente.
     * @param name El nombre de la categoría.
     * @param songIds La lista de IDs de canciones para esta categoría.
     */
    fun saveCategory(name: String, songIds: List<Int>) {
        // Guardamos como String delimitado por comas para mantener el orden.
        val stringValue = songIds.joinToString(",")
        prefs.edit().putString(name, stringValue).apply()
    }

    /**
     * Elimina una categoría por su nombre.
     */
    fun deleteCategory(name: String) {
        prefs.edit().remove(name).apply()
    }

    /**
     * Obtiene los IDs de las canciones para una categoría específica.
     */
    fun getSongIdsForCategory(name: String): List<Int> {
        val value = prefs.getString(name, null)
        if (value != null) {
            return value.split(",").mapNotNull { it.toIntOrNull() }
        }
        // Fallback para el formato antiguo StringSet
        return prefs.getStringSet(name, emptySet())
            ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }
}
