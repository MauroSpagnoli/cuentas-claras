package app.cuentasclaras.feature.groups

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
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
 * Grafo de grupos: listado, creación y detalle (con miembros e invitaciones).
 *
 * El `NavHost` raíz del área autenticada lo monta `AuthenticatedNavHost`, que
 * encadena este grafo con el de gastos.
 *
 * @param currentUserId id del usuario con la sesión abierta; la UI lo usa para
 *   decidir sobre qué miembros puede actuar.
 * @param onSignOut cierre de sesión, resuelto por el grafo raíz.
 * @param onOpenExpenses abre el listado de gastos del grupo elegido.
 * @param onOpenBalances abre los balances y el plan de liquidación del grupo.
 */
fun NavGraphBuilder.groupsGraph(
    navController: NavHostController,
    currentUserId: String,
    onSignOut: () -> Unit,
    onOpenExpenses: (groupId: String) -> Unit,
    onOpenBalances: (groupId: String) -> Unit,
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
            onOpenExpenses = onOpenExpenses,
            onOpenBalances = onOpenBalances,
        )
    }
}
