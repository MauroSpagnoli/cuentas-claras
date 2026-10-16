package app.cuentasclaras.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Balances de un grupo tal como los devuelve `GET /groups/{id}/balances`:
 * netos por miembro y deudas mínimas por pares.
 */
@Serializable
data class BalancesDto(
    val currency: String = "",
    val members: List<MemberBalanceDto> = emptyList(),
    val pairs: List<PairwiseDebtDto> = emptyList(),
)

/** Saldo neto de un miembro: positivo si le deben, negativo si debe. */
@Serializable
data class MemberBalanceDto(
    val userId: String = "",
    val displayName: String = "",
    val netMinor: Long = 0,
    val currency: String = "",
)

/** Deuda mínima: `fromUserId` le paga `amountMinor` a `toUserId`. */
@Serializable
data class PairwiseDebtDto(
    val fromUserId: String = "",
    val toUserId: String = "",
    val amountMinor: Long = 0,
)

/** Plan de liquidación devuelto por `GET /groups/{id}/settlement-plan`. */
@Serializable
data class SettlementPlanDto(
    val currency: String = "",
    val algorithm: String = "",
    val transactionCount: Int = 0,
    val steps: List<PairwiseDebtDto> = emptyList(),
)

/** Pago registrado devuelto por la API. */
@Serializable
data class SettlementDto(
    val id: String = "",
    val groupId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val amountMinor: Long = 0,
    val currency: String = "",
    val settledOn: String = "",
    val note: String? = null,
    val createdAt: String? = null,
)

/**
 * Cuerpo de `POST /groups/{id}/settlements`. La moneda es opcional: si falta se
 * usa la del grupo.
 */
@Serializable
data class CreateSettlementRequestDto(
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val currency: String? = null,
    val settledOn: String,
    val note: String? = null,
)
