package app.cuentasclaras.domain.repository

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.GroupBalances
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.model.SettlementPlan
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de balances y pagos de un grupo.
 *
 * A diferencia de grupos y gastos, aquí el backend es la fuente de verdad: los
 * balances, las deudas por pares y el plan de liquidación son cálculos derivados
 * que cambian con cada gasto o pago, así que no se cachean en Room (la Fase 8 se
 * encarga del offline-first).
 */
interface BalanceRepository {

    /** Netos por miembro y deudas mínimas por pares del grupo. */
    suspend fun loadBalances(groupId: String): AppResult<GroupBalances>

    /** Plan de liquidación: transferencias mínimas que dejan el grupo a cero. */
    suspend fun loadSettlementPlan(groupId: String): AppResult<SettlementPlan>

    /** Historia de pagos del grupo, paginada por cursor contra la red. */
    fun observeSettlements(groupId: String): Flow<PagingData<Settlement>>

    /** Registra un pago entre dos miembros del grupo. */
    suspend fun registerSettlement(groupId: String, draft: SettlementDraft): AppResult<Settlement>
}
