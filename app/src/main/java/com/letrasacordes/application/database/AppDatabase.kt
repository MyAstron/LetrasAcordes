package com.letrasacordes.application.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Cancion::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cancionDao(): CancionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canciones ADD COLUMN coverUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canciones ADD COLUMN noBuscarPortada INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreamos la tabla canciones sin las columnas coverUrl, previewUrl y noBuscarPortada
                db.execSQL("""
                    CREATE TABLE canciones_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL,
                        autor TEXT,
                        ritmo TEXT,
                        letraOriginal TEXT NOT NULL,
                        tieneAcordes INTEGER NOT NULL,
                        tonoOriginal TEXT,
                        letraSinAcordes TEXT NOT NULL,
                        fechaCreacion INTEGER NOT NULL,
                        ultimaEdicion INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO canciones_new (id, titulo, autor, ritmo, letraOriginal, tieneAcordes, tonoOriginal, letraSinAcordes, fechaCreacion, ultimaEdicion)
                    SELECT id, titulo, autor, ritmo, letraOriginal, tieneAcordes, tonoOriginal, letraSinAcordes, fechaCreacion, ultimaEdicion FROM canciones
                """)
                db.execSQL("DROP TABLE canciones")
                db.execSQL("ALTER TABLE canciones_new RENAME TO canciones")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "letras_y_acordes_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
