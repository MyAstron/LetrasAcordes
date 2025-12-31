package com.letrasacordes.application

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Definimos las rutas como constantes para evitar errores de tipeo
object Rutas {
    const val EASTER_EGG = "easter_egg" // La carta base (oculta)
    const val LISTA_CANCIONES = "lista_canciones"
    const val AGREGAR_CANCION = "agregar_cancion"
    const val CONFIGURACION = "configuracion"
    const val VER_CANCION = "ver_cancion/{cancionId}"

    // Función auxiliar para construir la ruta completa con el ID
    fun verCancionConId(id: Int) = "ver_cancion/$id"
}

@Composable
fun NavegacionApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        // Empezamos con el Easter Egg, pero este nos mandará inmediatamente a la lista
        startDestination = Rutas.EASTER_EGG 
    ) {
        
        // PANTALLA EASTER EGG (La base de la pila)
        composable(Rutas.EASTER_EGG) {
            // Usamos rememberSaveable para recordar si ya hicimos la redirección inicial
            // incluso si la configuración cambia, pero se reinicia si matan la app.
            var inicioRealizado by rememberSaveable { mutableStateOf(false) }

            if (!inicioRealizado) {
                // Si es la primera vez que entramos aquí (al abrir la app),
                // navegamos inmediatamente a la lista de canciones.
                LaunchedEffect(Unit) {
                    inicioRealizado = true
                    navController.navigate(Rutas.LISTA_CANCIONES) {
                        // No hacemos popUpTo para que el Easter Egg se quede en la pila (debajo)
                    }
                }
            } else {
                // Si volvemos aquí (dando atrás desde la lista), mostramos el mensaje.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { 
                            // Al hacer click, volvemos a poner la carta del menú encima
                            navController.navigate(Rutas.LISTA_CANCIONES) 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Me encontraste retrocediendo mucho.\nRegresa al menú.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Cada 'composable' es una pantalla en nuestro grafo de navegación
        composable(Rutas.LISTA_CANCIONES) {
            PantallaPrincipalCanciones(
                // Pasamos las funciones de navegación como parámetros
                onAgregarCancionClick = { navController.navigate(Rutas.AGREGAR_CANCION) },
                onConfiguracionClick = { navController.navigate(Rutas.CONFIGURACION) },
                onCancionClick = { cancionId ->
                    navController.navigate(Rutas.verCancionConId(cancionId))
                }
            )
        }

        composable(
            route = Rutas.VER_CANCION,
            arguments = listOf(navArgument("cancionId") { type = NavType.IntType })
        ) { backStackEntry ->
            // Extraemos el ID de los argumentos de la ruta
            val cancionId = backStackEntry.arguments?.getInt("cancionId") ?: 0
            PantallaVerCancion(
                cancionId = cancionId,
                onNavegarAtras = { navController.popBackStack() },
                onNavegarAEditar = { id ->
                    navController.navigate("editarCancion/$id")
                }
            )
        }

        composable(
            route = "editarCancion/{cancionId}",
            arguments = listOf(navArgument("cancionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val cancionId = backStackEntry.arguments?.getInt("cancionId") ?: return@composable
            PantallaEditarCancion(
                cancionId = cancionId,
                onNavegarAtras = { navController.popBackStack() }
            )
        }

        composable(Rutas.AGREGAR_CANCION) {
            PantallaAgregarCancion(
                onNavegarAtras = { navController.popBackStack() }
            )
        }

        composable(Rutas.CONFIGURACION) {
            PantallaConfiguracion(
                onNavegarAtras = { navController.popBackStack() }
            )
        }
    }
}