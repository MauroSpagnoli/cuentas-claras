package app.cuentasclaras.domain.model

/**
 * Saldo neto de un miembro dentro de un grupo.
 *
 * [netMinor] está en unidades mínimas de la moneda: positivo cuando al miembro
 * le deben dinero, negativo cuando es él quien debe.
 */
data class MemberBalance(
    val userId: String,
    val displayName: String,
    val netMinor: Long,
    val currency: String,
)

/**
 * Deuda mínima entre dos miembros: [fromUserId] le paga [amountMinor] a
 * [toUserId]. Es la unidad con la que se construyen los balances por pares y el
 * plan de liquidación.
 */
data class PairwiseDebt(
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
)

/** Balances de un grupo: netos por miembro y deudas mínimas por pares. */
data class GroupBalances(
    val currency: String,
    val members: List<MemberBalance>,
    val pairs: List<PairwiseDebt>,
)

/**
 * Plan de liquidación: el conjunto de transferencias que deja a todos los
 * miembros a cero. [algorithm] es el que usó el backend (`MIN_EXACT` o
 * `GREEDY`) y [transactionCount] el número de transferencias del plan.
 */
data class SettlementPlan(
    val currency: String,
    val algorithm: String,
    val transactionCount: Int,
    val steps: List<PairwiseDebt>,
)
