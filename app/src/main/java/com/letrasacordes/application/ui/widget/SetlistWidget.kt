package com.letrasacordes.application.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.letrasacordes.application.MainActivity
import com.letrasacordes.application.database.AppDatabase
import com.letrasacordes.application.logic.CategoryRepository
import kotlinx.coroutines.flow.first

class SetlistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val categoryRepository = CategoryRepository(context)
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val categoryName = sharedPreferences.getString("widget_category", "Todas") ?: "Todas"
        
        val canciones = when (categoryName) {
            "Todas" -> db.cancionDao().obtenerTodasLasCanciones().first()
            "Favoritos" -> db.cancionDao().obtenerCancionesPorIds(categoryRepository.getSongIdsForCategory("Favoritos").toSet()).first()
            else -> db.cancionDao().obtenerCancionesPorIds(categoryRepository.getSongIdsForCategory(categoryName).toSet()).first()
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF1A237E)))
                    .padding(8.dp)
            ) {
                // Botón de Presentación Dinámico
                val presentacionIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("app://presentacion/$categoryName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(ColorProvider(Color(0xFFFBC02D)))
                        .padding(8.dp)
                        .clickable(actionStartActivity(presentacionIntent)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "▶ PRESENTACIÓN: ${categoryName.uppercase()}",
                        style = TextStyle(
                            color = ColorProvider(Color.Black),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (canciones.isEmpty()) {
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Lista vacía",
                            style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.5f)))
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(canciones) { cancion -> 
                            SongItem(context, cancion) 
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SongItem(context: Context, cancion: com.letrasacordes.application.database.Cancion) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("app://cancion/${cancion.id}")
            putExtra("cancionId", cancion.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(ColorProvider(Color(0xFF283593)))
                .padding(8.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "♪",
                style = TextStyle(color = ColorProvider(Color(0xFF4FC3F7)), fontSize = 16.sp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = cancion.titulo.uppercase(),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

class SetlistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SetlistWidget()
}
