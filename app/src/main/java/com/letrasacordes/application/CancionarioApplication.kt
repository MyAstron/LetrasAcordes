package com.letrasacordes.application

import android.app.Application
import com.letrasacordes.application.database.AppDatabase

class CancionarioApplication : Application() {
    // Usamos 'lazy' para que la base de datos solo se cree cuando se necesite por primera vez.
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
