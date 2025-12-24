package com.letrasacordes.application.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La clase principal de la base de datos de la aplicación.
 * Esta clase une las entidades (tablas) y los DAOs.
 *
 * version = 2: Se incrementó la versión tras agregar el campo 'ritmo'.
 *
 * exportSchema = false: Para este proyecto, no necesitamos exportar el esquema
 *                       de la base de datos a un archivo JSON.
 */
@Database(entities = [Cancion::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Declara un método abstracto que devolverá una instancia de nuestro CancionDao.
     * Room se encargará de generar la implementación de este método.
     */
    abstract fun cancionDao(): CancionDao

    /**
     * El 'companion object' nos permite crear un método estático para obtener la
     * instancia de la base de datos (patrón Singleton).
     */
    companion object {
        /**
         * La anotación @Volatile asegura que el valor de INSTANCE siempre esté
         * actualizado y sea el mismo para todos los hilos de ejecución.
         * Esto es crucial para evitar crear dos instancias de la base de datos
         * al mismo tiempo por error.
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia Singleton de la base de datos.
         * Si la instancia no existe, la crea de forma segura (thread-safe).
         *
         * @param context El contexto de la aplicación, necesario para que Room
         *                pueda construir la base de datos.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Si la instancia ya existe, la retornamos.
            // Si no, entramos en un bloque sincronizado para crearla.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "letras_y_acordes_database" // Nombre del archivo de la base de datos
                )
                // Permitimos migraciones destructivas para facilitar el desarrollo.
                // ADVERTENCIA: Esto borrará los datos existentes si cambia el esquema.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                // Devolvemos la instancia recién creada
                instance
            }
        }
    }
}