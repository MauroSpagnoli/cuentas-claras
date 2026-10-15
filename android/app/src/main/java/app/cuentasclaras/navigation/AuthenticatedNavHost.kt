package app.cuentasclaras.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import app.cuentasclaras.feature.expenses.expensesGraph
import app.cuentasclaras.feature.groups.GroupsRoutes
import app.cuentasclaras.feature.groups.groupsGraph

/**
 * Grafo del área autenticada: encadena el grafo de grupos con el de gastos en
 * un único `NavHost`.
 *
 * Los dos grafos comparten prefijo (`groups/...` y `groups/{groupId}/expenses`),
 * pero el número de segmentos los hace inequívocos.
 *
 * @param currentUserId usuario con la sesión abierta; los destinos lo necesitan
 *   para decidir qué puede hacer (crear/editar/borrar) y a quién presuponer
 *   como pagador.
 * @param onSignOut cierre de sesión, resuelto por `CuentasClarasRoot`.
 */
@Composable
fun AuthenticatedNavHost(
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
        groupsGraph(
            navController = navController,
            currentUserId = currentUserId,
            onSignOut = onSignOut,
            onOpenExpenses = { groupId -> navController.navigate(ExpensesRoutes.list(groupId)) },
        )

        expensesGraph(
            navController = navController,
            currentUserId = currentUserId,
        )
    }
}
