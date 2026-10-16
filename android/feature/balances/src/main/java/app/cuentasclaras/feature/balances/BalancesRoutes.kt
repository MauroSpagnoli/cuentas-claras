package app.cuentasclaras.feature.balances

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.cuentasclaras.feature.balances.form.SettlementFormEntry
import app.cuentasclaras.feature.balances.history.SettlementHistoryEntry
import app.cuentasclaras.feature.balances.list.BalancesEntry

/**
 * Rutas del grafo de balances, con el id del grupo porque un mismo usuario puede
 * tener abiertos los balances de varios grupos.
 *
 * El formulario de pago admite tres argumentos opcionales por query
 * (`fromUserId`, `toUserId`, `amountMinor`) para prerrellenar la sugerencia del
 * plan de liquidación; cuando se registra un pago a mano llegan vacíos.
 */
object BalancesRoutes {

    /** Id del grupo; es la clave del `SavedStateHandle`. */
    const val ARG_GROUP_ID = "groupId"

    /**
     * Id del usuario con la sesión abierta. No viaja en la ruta: el destino lo
     * recibe como valor por defecto para presuponerlo como pagador.
     */
    const val ARG_CURRENT_USER_ID = "currentUserId"

    /** Miembro que paga (viene prefijado al registrar una deuda sugerida). */
    const val ARG_FROM_USER_ID = "fromUserId"

    /** Miembro que cobra. */
    const val ARG_TO_USER_ID = "toUserId"

    /** Importe en unidades mínimas de la deuda sugerida. */
    const val ARG_AMOUNT_MINOR = "amountMinor"

    const val BALANCES = "groups/{$ARG_GROUP_ID}/balances"

    const val SETTLEMENTS = "groups/{$ARG_GROUP_ID}/balances/settlements"

    const val NEW_PAYMENT =
        "groups/{$ARG_GROUP_ID}/balances/payments/new" +
            "?$ARG_FROM_USER_ID={$ARG_FROM_USER_ID}" +
            "&$ARG_TO_USER_ID={$ARG_TO_USER_ID}" +
            "&$ARG_AMOUNT_MINOR={$ARG_AMOUNT_MINOR}"

    fun balances(groupId: String): String = "groups/$groupId/balances"

    /** Ruta de la historia de pagos del grupo. */
    fun settlements(groupId: String): String = "groups/$groupId/balances/settlements"

    /**
     * Ruta del formulario de pago. Los opcionales se añaden solo cuando se
     * conoce el valor para no ensuciar la ruta en un alta manual.
     */
    fun newPayment(
        groupId: String,
        fromUserId: String? = null,
        toUserId: String? = null,
        amountMinor: Long? = null,
    ): String {
        val base = "groups/$groupId/balances/payments/new"
        if (fromUserId == null && toUserId == null && amountMinor == null) return base
        return base +
            "?$ARG_FROM_USER_ID=${fromUserId.orEmpty()}" +
            "&$ARG_TO_USER_ID=${toUserId.orEmpty()}" +
            "&$ARG_AMOUNT_MINOR=${amountMinor ?: 0L}"
    }
}

/**
 * Grafo de balances de un grupo: balances netos, deudas por pares y plan de
 * liquidación, más el formulario para registrar un pago (opcionalmente
 * prellenado desde una sugerencia del plan).
 *
 * @param currentUserId usuario con la sesión abierta; el formulario lo usa para
 *   presuponer quién paga cuando no viene de una sugerencia.
 */
fun NavGraphBuilder.balancesGraph(
    navController: NavHostController,
    currentUserId: String,
) {
    composable(
        route = BalancesRoutes.BALANCES,
        arguments = listOf(
            navArgument(BalancesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
        ),
    ) {
        BalancesEntry(
            currentUserId = currentUserId,
            onBack = { navController.popBackStack() },
            onRegisterPayment = { groupId, fromUserId, toUserId, amountMinor ->
                navController.navigate(
                    BalancesRoutes.newPayment(groupId, fromUserId, toUserId, amountMinor),
                )
            },
            onNewPayment = { groupId -> navController.navigate(BalancesRoutes.newPayment(groupId)) },
            onOpenHistory = { groupId -> navController.navigate(BalancesRoutes.settlements(groupId)) },
        )
    }

    composable(
        route = BalancesRoutes.SETTLEMENTS,
        arguments = listOf(
            navArgument(BalancesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
        ),
    ) {
        SettlementHistoryEntry(
            onBack = { navController.popBackStack() },
            onRegisterPayment = { groupId ->
                navController.navigate(BalancesRoutes.newPayment(groupId))
            },
        )
    }

    composable(
        route = BalancesRoutes.NEW_PAYMENT,
        arguments = listOf(
            navArgument(BalancesRoutes.ARG_GROUP_ID) { type = NavType.StringType },
            navArgument(BalancesRoutes.ARG_CURRENT_USER_ID) {
                type = NavType.StringType
                defaultValue = currentUserId
            },
            navArgument(BalancesRoutes.ARG_FROM_USER_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(BalancesRoutes.ARG_TO_USER_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(BalancesRoutes.ARG_AMOUNT_MINOR) {
                type = NavType.LongType
                defaultValue = 0L
            },
        ),
    ) {
        SettlementFormEntry(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}
