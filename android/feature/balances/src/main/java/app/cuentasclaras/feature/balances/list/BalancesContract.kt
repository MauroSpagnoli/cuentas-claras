package app.cuentasclaras.feature.balances.list

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.MemberBalance
import app.cuentasclaras.domain.model.PairwiseDebt
import app.cuentasclaras.domain.model.SettlementPlan

/**
 * Estado de la pantalla de balances: netos por miembro, deudas por pares y plan
 * de liquidación de un grupo.
 */
data class BalancesUiState(
    val currency: String = "",
    val members: List<MemberBalance> = emptyList(),
    val pairs: List<PairwiseDebt> = emptyList(),
    val plan: SettlementPlan? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
) {

    /** Nombre del miembro, o `null` mientras no se conozcan los balances. */
    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName

    /** Pasos del plan de liquidación (deuda mínima por par). */
    val steps: List<PairwiseDebt> get() = plan?.steps.orEmpty()

    /** Hay algo que mostrar (netos o plan) aunque la última carga fallara. */
    val hasData: Boolean get() = members.isNotEmpty() || plan != null
}
