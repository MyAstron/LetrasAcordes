package com.letrasacordes.application

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument

// Definimos las rutas como constantes para evitar errores de tipeo
object Rutas {
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
        startDestination = Rutas.LISTA_CANCIONES // La pantalla con la que empieza la app
    ) {
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