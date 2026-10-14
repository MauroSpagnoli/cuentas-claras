package app.cuentasclaras.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object AuthRoutes {
    const val ARG_MODE = "mode"
    const val MODE_LOGIN = "login"
    const val MODE_REGISTER = "register"

    /** Patrón de ruta con el modo como argumento. */
    const val AUTH = "auth/{$ARG_MODE}"

    fun auth(mode: String): String = "auth/$mode"
}

/**
 * Grafo de navegación del área no autenticada: login ↔ registro.
 */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.auth(AuthRoutes.MODE_LOGIN),
        modifier = modifier,
    ) {
        composable(
            route = AuthRoutes.AUTH,
            arguments = listOf(
                navArgument(AuthRoutes.ARG_MODE) {
                    type = NavType.StringType
                    defaultValue = AuthRoutes.MODE_LOGIN
                },
            ),
        ) { entry ->
            val currentMode = entry.arguments?.getString(AuthRoutes.ARG_MODE).orEmpty()
            AuthRoute(
                onSwitchMode = {
                    val target = if (currentMode == AuthRoutes.MODE_REGISTER) {
                        AuthRoutes.MODE_LOGIN
                    } else {
                        AuthRoutes.MODE_REGISTER
                    }
                    navController.navigate(AuthRoutes.auth(target)) {
                        popUpTo(AuthRoutes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
