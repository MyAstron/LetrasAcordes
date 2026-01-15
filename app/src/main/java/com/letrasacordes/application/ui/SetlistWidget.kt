package com.letrasacordes.application.ui

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.letrasacordes.application.MainActivity
import com.letrasacordes.application.database.AppDatabase
import kotlinx.coroutines.flow.first

class SetlistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val canciones = db.cancionDao().obtenerTodasLasCanciones().first().take(3)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabecera usando colores del tema por defecto (sustituye a la cinta amarilla)
                    Text(
                        text = "SETLIST PRÓX.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )

                    Spacer(GlanceModifier.height(8.dp))

                    if (canciones.isEmpty()) {
                        Text(
                            text = "No hay canciones",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    } else {
                        canciones.forEach { cancion ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(actionStartActivity<MainActivity>()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "•",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 18.sp
                                    ),
                                    modifier = GlanceModifier.width(12.dp)
                                )
                                Column {
                                    Text(
                                        text = cancion.titulo.uppercase(),
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class SetlistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SetlistWidget()
}
