package app.cuentasclaras.feature.groups

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cuentasclaras.feature.groups.create.CreateGroupEntry
import app.cuentasclaras.feature.groups.detail.GroupDetailEntry
import app.cuentasclaras.feature.groups.list.GroupsListEntry

/** Rutas del grafo de grupos (mismo estilo que `AuthRoutes`). */
object GroupsRoutes {
    /** Nombre del argumento con el id del grupo; es la clave del `SavedStateHandle`. */
    const val ARG_GROUP_ID = "groupId"

    const val LIST = "groups"
    const val CREATE = "groups/create"
    const val DETAIL = "groups/{$ARG_GROUP_ID}"

    fun detail(groupId: String): String = "groups/$groupId"
}

/**
 * Grafo de navegación del área autenticada: listado de grupos, creación y
 * detalle (con miembros e invitaciones).
 *
 * @param currentUserId id del usuario con la sesión abierta; la UI lo usa para
 *   decidir sobre qué miembros puede actuar.
 * @param onSignOut cierre de sesión, resuelto por el grafo raíz.
 */
@Composable
fun GroupsNavHost(
    currentUserId: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = GroupsRoutes.LIST,
        modifier = modifier,
    ) {
        composable(GroupsRoutes.LIST) {
            GroupsListEntry(
                onOpenGroup = { groupId -> navController.navigate(GroupsRoutes.detail(groupId)) },
                onCreateGroup = { navController.navigate(GroupsRoutes.CREATE) },
                onSignOut = onSignOut,
            )
        }

        composable(GroupsRoutes.CREATE) {
            CreateGroupEntry(onBack = { navController.popBackStack() })
        }

        composable(
            route = GroupsRoutes.DETAIL,
            arguments = listOf(navArgument(GroupsRoutes.ARG_GROUP_ID) { type = NavType.StringType }),
        ) {
            GroupDetailEntry(
                currentUserId = currentUserId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
