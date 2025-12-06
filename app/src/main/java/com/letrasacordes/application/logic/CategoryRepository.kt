package com.letrasacordes.application.logic

import android.content.Context

/**
 * Repositorio para gestionar las categorías (listas) de canciones.
 * Utiliza SharedPreferences para persistir los datos de forma local y ligera.
 */
class CategoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("song_categories", Context.MODE_PRIVATE)

    /**
     * Obtiene todas las categorías guardadas.
     * @return Un mapa donde la clave es el nombre de la categoría y el valor es un conjunto de IDs de canciones.
     */
    fun getAllCategories(): Map<String, Set<Int>> {
        return prefs.all.mapValues { entry ->
            (entry.value as? Set<String>)
                ?.mapNotNull { it.toIntOrNull() } // Convierte los IDs de String a Int
                ?.toSet() ?: emptySet()
        }
    }

    /**
     * Guarda una nueva categoría o actualiza una existente.
     * @param name El nombre de la categoría.
     * @param songIds El conjunto de IDs de canciones para esta categoría.
     */
    fun saveCategory(name: String, songIds: Set<Int>) {
        // SharedPreferences solo guarda Set<String>, así que convertimos los Int a String.
        val stringIds = songIds.map { it.toString() }.toSet()
        prefs.edit().putStringSet(name, stringIds).apply()
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
    fun getSongIdsForCategory(name: String): Set<Int> {
        return prefs.getStringSet(name, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }
}
