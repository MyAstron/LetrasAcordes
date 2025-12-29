package com.letrasacordes.application

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Definimos las rutas como constantes para evitar errores de tipeo
object Rutas {
    const val EASTER_EGG = "easter_egg"
    const val LISTA_CANCIONES = "lista_canciones"
    const val AGREGAR_CANCION = "agregar_cancion"
    const val CONFIGURACION = "configuracion"
    const val VER_CANCION = "ver_cancion/{cancionId}"
    const val EDITAR_CANCION = "editarCancion/{cancionId}"

    // Función auxiliar para construir la ruta completa con el ID
    fun verCancionConId(id: Int) = "ver_cancion/$id"
    fun editarCancionConId(id: Int) = "editarCancion/$id"
}

@Composable
fun NavegacionApp(inicioRealizado: Boolean = false) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (inicioRealizado) Rutas.EASTER_EGG else Rutas.LISTA_CANCIONES
    ) {
        composable(Rutas.EASTER_EGG) {
            // Este fragmento estaba presente en el código decompilado.
            // Si tienes una pantalla o lógica específica para el Easter Egg, impleméntala aquí.
            // Por ahora, redirigiremos a la lista de canciones o mostraremos un placeholder
            // para mantener la consistencia con el código original restaurado.
            // Como no tenemos el código de 'EasterEggScreen', asumiremos que es una pantalla simple o redirección.
            PantallaPrincipalCanciones(
                onAgregarCancionClick = { navController.navigate(Rutas.AGREGAR_CANCION) },
                onConfiguracionClick = { navController.navigate(Rutas.CONFIGURACION) },
                onCancionClick = { cancionId ->
                    navController.navigate(Rutas.verCancionConId(cancionId))
                }
            )
        }

        composable(Rutas.LISTA_CANCIONES) {
            PantallaPrincipalCanciones(
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
            val cancionId = backStackEntry.arguments?.getInt("cancionId") ?: 0
            PantallaVerCancion(
                cancionId = cancionId,
                onNavegarAtras = { navController.popBackStack() },
                onNavegarAEditar = { id ->
                    navController.navigate(Rutas.editarCancionConId(id))
                }
            )
        }

        composable(
            route = Rutas.EDITAR_CANCION,
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