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
        val widgetIds = categoryRepository.getSongIdsForCategory("WIDGET_SELECTION")
        
        val canciones = if (widgetIds.isEmpty()) {
            db.cancionDao().obtenerTodasLasCanciones().first().take(6)
        } else {
            db.cancionDao().obtenerCancionesPorIds(widgetIds.toSet()).first().take(6)
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF1A237E)))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (canciones.isEmpty()) {
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Lista vacía",
                            style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.5f)))
                        )
                    }
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        canciones.forEach { cancion -> SongItem(context, cancion) }
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
                .padding(vertical = 2.dp, horizontal = 4.dp)
                .background(ColorProvider(Color(0xFF283593)))
                .padding(4.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "♪",
                style = TextStyle(color = ColorProvider(Color(0xFF4FC3F7)), fontSize = 14.sp)
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = cancion.titulo.uppercase(),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 10.sp,
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
