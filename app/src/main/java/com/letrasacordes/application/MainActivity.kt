package com.letrasacordes.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letrasacordes.application.ui.theme.ApplicationTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import com.letrasacordes.application.database.Cancion
import androidx.compose.material3.CardDefaults // <-- AÑADE ESTA LÍNEA

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApplicationTheme {
                // Usamos un Surface como contenedor principal de la app
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Llamamos a nuestro nuevo Composable de la pantalla principal
                    NavegacionApp()
                }
            }
        }
    }
}

/**
 * El Composable principal que contiene la lógica y la UI de la pantalla de la lista de canciones.
 */
@OptIn(ExperimentalMaterial3Api::class) // Necesario para TopAppBar
@Composable
fun PantallaPrincipalCanciones(
    modifier: Modifier = Modifier,
    onAgregarCancionClick: () -> Unit,
    onConfiguracionClick: () -> Unit,
    onCancionClick: (Int) -> Unit,
    cancionesViewModel: CancionesViewModel = viewModel(factory = CancionesViewModel.Factory)
) {
    val textoBusqueda by cancionesViewModel.textoBusqueda.collectAsState()
    val listaCanciones by cancionesViewModel.canciones.collectAsState()

    // Scaffold nos da una estructura estándar con TopAppBar, FAB, etc.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Letras y Acordes") },
                actions = {
                    // Botón para agregar una nueva canción
                    IconButton(onClick = onAgregarCancionClick) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar canción")
                    }
                    // Botón para ir a la configuración
                    IconButton(onClick = onConfiguracionClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues -> // paddingValues contiene el espacio ocupado por la TopAppBar

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Aplicamos el padding para que el contenido no quede debajo de la barra
        ) {
            // 1. Barra de Búsqueda
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = cancionesViewModel::enTextoBusquedaCambiado,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Buscar por título, autor o letra...") },
                singleLine = true
            )

            // 2. Lista de Canciones
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(listaCanciones) { cancion ->
                    CancionItem(
                        cancion = cancion,
                        // Le añadimos un modificador para que sea clicable
                        modifier = Modifier.clickable { onCancionClick(cancion.id) }
                    )
                }
            }
        }
    }
}

/**
 * Un Composable que representa un único elemento (una fila) en la lista de canciones.
 */
@Composable
fun CancionItem(cancion: Cancion, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = cancion.titulo,
                style = MaterialTheme.typography.titleLarge
            )
            // Mostramos el autor solo si existe (no es nulo o vacío)
            if (!cancion.autor.isNullOrBlank()) {
                Text(
                    text = cancion.autor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// --- Preview para el diseñador de Android Studio ---

@Preview(showBackground = true)
@Composable
fun CancionItemPreview() {
    ApplicationTheme {
        CancionItem(
            cancion = Cancion(
                0,
                "Título de Ejemplo",
                "Autor de Ejemplo",
                "", true, "C", "", 0, 0
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaPrincipalPreview() {

    ApplicationTheme {
        // En el preview no tenemos un ViewModel real, por lo que no podemos
        // mostrar una lista dinámica, pero sí podemos ver la estructura.
        PantallaPrincipalCanciones(
            onAgregarCancionClick = {},
            onConfiguracionClick = {},
            onCancionClick = {}
        )
    }
}