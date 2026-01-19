package com.letrasacordes.application.logic

import android.content.Context

/**
 * Repositorio para gestionar las categorías (listas) de canciones.
 * Utiliza SharedPreferences para persistir los datos de forma local y ligera.
 */
class CategoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("song_categories", Context.MODE_PRIVATE)

    fun getAllCategories(): Map<String, List<Int>> {
        return prefs.all.mapValues { entry ->
            val value = entry.value
            when (value) {
                is String -> {
                    value.split(",").mapNotNull { it.toIntOrNull() }
                }
                is Set<*> -> {
                    value.filterIsInstance<String>().mapNotNull { it.toIntOrNull() }
                }
                else -> emptyList()
            }
        }
    }

    fun saveCategory(name: String, songIds: List<Int>) {
        val stringValue = songIds.joinToString(",")
        // Usamos commit() en lugar de apply() para asegurar sincronía inmediata en lecturas posteriores
        prefs.edit().putString(name, stringValue).commit()
    }

    fun deleteCategory(name: String) {
        prefs.edit().remove(name).commit()
    }

    fun getSongIdsForCategory(name: String): List<Int> {
        val value = prefs.getString(name, null)
        if (value != null) {
            return value.split(",").mapNotNull { it.toIntOrNull() }
        }
        return prefs.getStringSet(name, emptySet())
            ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }
}
