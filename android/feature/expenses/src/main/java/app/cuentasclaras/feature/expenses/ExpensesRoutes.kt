package app.cuentasclaras.feature.expenses

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.cuentasclaras.feature.expenses.detail.ExpenseDetailEntry
import app.cuentasclaras.feature.expenses.form.ExpenseFormEntry
import app.cuentasclaras.feature.expenses.list.ExpensesListEntry

/**
 * Rutas del grafo de gastos, con el id del grupo en la ruta porque un mismo
 * usuario puede tener abiertos los gastos de varios grupos.
 */
object ExpensesRoutes {

    /** Id del grupo; es la clave del `SavedStateHandle`. */
    const val ARG_GROUP_ID = "groupId"

    /** Id del gasto dentro del grupo. */
    const val ARG_EXPENSE_ID = "expenseId"

    /**
     * Id del usuario con la sesión abierta. No viaja en la ruta: el destino lo
     * recibe como valor por defecto para que el formulario sepa a quién
     * presuponer como pagador.
     */
    const val ARG_CURRENT_USER_ID = "currentUserId"

    const val LIST = "groups/{$ARG_GROUP_ID}/expenses"
    const val CREATE = "groups/{$ARG_GROUP_ID}/expenses/new"
    const val DETAIL = "groups/{$ARG_GROUP_ID}/expenses/{$ARG_EXPENSE_ID}"
    const val EDIT = "groups/{$ARG_GROUP_ID}/expenses/{$ARG_EXPENSE_ID}/edit"

    fun list(groupId: String): String = "groups/$groupId/expenses"

    fun create(groupId: String): String = "groups/$groupId/expenses/new"

    fun detail(groupId: String, expenseId: String): String = "groups/$groupId/expenses/$expenseId"

    fun edit(groupId: String, expenseId: String): String = "groups/$groupId/expenses/$expenseId/edit"
}

/**
 * Grafo de gastos de un grupo: listado, detalle y formulario de alta/edición.
 *
 * El destino de creación se declara **antes** que el de detalle a propósito:
 * `.../expenses/new` encaja también con `groups/{groupId}/expenses/{expenseId}`,
 * y Navigation resuelve el empate por orden de registro en el grafo.
 *
 * @param currentUserId usuario con la sesión abierta; se inyecta en el detalle
 *   y en el formulario a través de los argumentos por defecto del destino.
 */
fun NavGraphBuilder.expensesGraph(
    navController: NavHostController,
    currentUserId: String,
) {
    composable(
        route = ExpensesRoutes.LIST,
        arguments = listOf(
            navArgument(ExpensesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
        ),
    ) {
        ExpensesListEntry(
            onBack = { navController.popBackStack() },
            onOpenExpense = { groupId, expenseId ->
                navController.navigate(ExpensesRoutes.detail(groupId, expenseId))
            },
            onCreateExpense = { groupId ->
                navController.navigate(ExpensesRoutes.create(groupId))
            },
        )
    }

    composable(
        route = ExpensesRoutes.CREATE,
        arguments = listOf(
            navArgument(ExpensesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
            navArgument(ExpensesRoutes.ARG_CURRENT_USER_ID) {
                type = NavType.StringType
                defaultValue = currentUserId
            },
        ),
    ) {
        ExpenseFormEntry(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }

    composable(
        route = ExpensesRoutes.DETAIL,
        arguments = listOf(
            navArgument(ExpensesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
            navArgument(ExpensesRoutes.ARG_EXPENSE_ID) { type = NavType.StringType },
            navArgument(ExpensesRoutes.ARG_CURRENT_USER_ID) {
                type = NavType.StringType
                defaultValue = currentUserId
            },
        ),
    ) {
        ExpenseDetailEntry(
            onBack = { navController.popBackStack() },
            onEdit = { groupId, expenseId ->
                navController.navigate(ExpensesRoutes.edit(groupId, expenseId))
            },
        )
    }

    composable(
        route = ExpensesRoutes.EDIT,
        arguments = listOf(
            navArgument(ExpensesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
            navArgument(ExpensesRoutes.ARG_EXPENSE_ID) { type = NavType.StringType },
            navArgument(ExpensesRoutes.ARG_CURRENT_USER_ID) {
                type = NavType.StringType
                defaultValue = currentUserId
            },
        ),
    ) {
        ExpenseFormEntry(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}
