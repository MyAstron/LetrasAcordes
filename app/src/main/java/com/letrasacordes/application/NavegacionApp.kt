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
import com.google.gson.Gson

object Rutas {
    const val EASTER_EGG = "easter_egg"
    const val LISTA_CANCIONES = "lista_canciones"
    const val AGREGAR_CANCION = "agregar_cancion"
    const val CONFIGURACION = "configuracion"
    const val VER_CANCION = "ver_cancion/{cancionId}"
    const val EDITAR_CANCION = "editarCancion/{cancionId}"
    const val MODO_PRESENTACION = "modo_presentacion/{categoria}?cancionIds={cancionIds}"
    const val VER_CANCION_PRESENTACION = "ver_cancion_presentacion/{cancionId}/{categoria}"

    fun verCancionConId(id: Int) = "ver_cancion/$id"
    fun editarCancionConId(id: Int) = "editarCancion/$id"
    fun modoPresentacionConCategoria(categoria: String, cancionIds: String? = null): String {
        return if (cancionIds != null) {
            "modo_presentacion/$categoria?cancionIds=$cancionIds"
        } else {
            "modo_presentacion/$categoria"
        }
    }
    fun verCancionPresentacion(id: Int, categoria: String) = "ver_cancion_presentacion/$id/$categoria"
}

@Composable
fun NavegacionApp(inicioRealizado: Boolean = false) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Rutas.EASTER_EGG
    ) {
        composable(Rutas.EASTER_EGG) {
            val esInicio = rememberSaveable { mutableStateOf(true) }

            if (esInicio.value) {
                LaunchedEffect(Unit) {
                    esInicio.value = false
                    navController.navigate(Rutas.LISTA_CANCIONES) {
                        // No hacemos popUpTo para mantener el Easter Egg en la pila (debajo)
                    }
                }
            } else {
                EasterEggScreen(
                    onRegresarMenu = {
                        navController.navigate(Rutas.LISTA_CANCIONES) {
                            popUpTo(Rutas.EASTER_EGG) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Rutas.LISTA_CANCIONES) {
            PantallaPrincipalCanciones(
                onAgregarCancionClick = { navController.navigate(Rutas.AGREGAR_CANCION) },
                onConfiguracionClick = { navController.navigate(Rutas.CONFIGURACION) },
                onCancionClick = { cancionId ->
                    navController.navigate(Rutas.verCancionConId(cancionId))
                },
                onSecretBack = {
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
                onNavegarAtras = { navController.popBackStack() },
                onIniciarPresentacion = { categoria, cancionIds ->
                    val gson = Gson()
                    val cancionIdsJson = gson.toJson(cancionIds)
                    navController.navigate(Rutas.modoPresentacionConCategoria(categoria, cancionIdsJson))
                }
            )
        }

        composable(
            route = Rutas.MODO_PRESENTACION,
            arguments = listOf(
                navArgument("categoria") { type = NavType.StringType },
                navArgument("cancionIds") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val categoria = backStackEntry.arguments?.getString("categoria") ?: "Todas"
            val cancionIdsJson = backStackEntry.arguments?.getString("cancionIds")
            val gson = Gson()
            val cancionIds = gson.fromJson(cancionIdsJson, Array<Int>::class.java)?.toList()

            PantallaModoPresentacion(
                categoria = categoria,
                cancionIds = cancionIds,
                onSalir = { navController.popBackStack() },
                onCancionClick = { id ->
                    navController.navigate(Rutas.verCancionPresentacion(id, categoria))
                }
            )
        }

        composable(
            route = Rutas.VER_CANCION_PRESENTACION,
            arguments = listOf(
                navArgument("cancionId") { type = NavType.IntType },
                navArgument("categoria") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val cancionId = backStackEntry.arguments?.getInt("cancionId") ?: 0
            val categoria = backStackEntry.arguments?.getString("categoria") ?: "Todas"
            PantallaVerCancionPresentacion(
                cancionId = cancionId,
                categoria = categoria,
                onRegresar = { navController.popBackStack() },
                onSiguienteCancion = { nuevaId ->
                    navController.navigate(Rutas.verCancionPresentacion(nuevaId, categoria)) {
                        popUpTo(Rutas.VER_CANCION_PRESENTACION) { inclusive = true }
                    }
                }
            )
        }
    }
}
