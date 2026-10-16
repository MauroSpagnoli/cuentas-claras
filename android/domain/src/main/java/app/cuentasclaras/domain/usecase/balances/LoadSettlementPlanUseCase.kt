package app.cuentasclaras.domain.usecase.balances

import app.cuentasclaras.domain.model.SettlementPlan
import app.cuentasclaras.domain.repository.BalanceRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Carga desde la red el plan de liquidación (transferencias mínimas) del grupo. */
class LoadSettlementPlanUseCase @Inject constructor(
    private val repository: BalanceRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<SettlementPlan> =
        repository.loadSettlementPlan(groupId)
}
