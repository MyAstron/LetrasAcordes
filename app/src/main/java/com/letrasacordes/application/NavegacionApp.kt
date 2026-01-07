package com.letrasacordes.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
        startDestination = Rutas.EASTER_EGG // Iniciamos en la Carta 1 (Base de la pila)
    ) {
        // Carta 1: Easter Egg (Base)
        composable(Rutas.EASTER_EGG) {
            // Estado para controlar si es el inicio de la app o si hemos vuelto aquí voluntariamente
            val esInicio = rememberSaveable { mutableStateOf(true) }

            if (esInicio.value) {
                // Si es el arranque, navegamos INMEDIATAMENTE al Main (Carta 2)
                LaunchedEffect(Unit) {
                    esInicio.value = false
                    navController.navigate(Rutas.LISTA_CANCIONES) {
                        // No hacemos popUpTo para mantener el Easter Egg en la pila (debajo)
                    }
                }
            } else {
                // Si estamos aquí porque volvimos (popBackStack o navegación explícita), mostramos la pantalla
                EasterEggScreen(
                    onRegresarMenu = { 
                        navController.navigate(Rutas.LISTA_CANCIONES) {
                            popUpTo(Rutas.EASTER_EGG) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Carta 2: Main (Visible al usuario tras el rebote inicial)
        composable(Rutas.LISTA_CANCIONES) {
            PantallaPrincipalCanciones(
                onAgregarCancionClick = { navController.navigate(Rutas.AGREGAR_CANCION) },
                onConfiguracionClick = { navController.navigate(Rutas.CONFIGURACION) },
                onCancionClick = { cancionId ->
                    navController.navigate(Rutas.verCancionConId(cancionId))
                },
                onSecretBack = { 
                    // Esta acción permite volver a la Carta 1 programáticamente si se desea en el futuro
                    navController.popBackStack() 
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
