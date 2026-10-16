package app.cuentasclaras.domain.usecase.balances

import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.repository.BalanceRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Registra un pago entre dos miembros del grupo. */
class RegisterSettlementUseCase @Inject constructor(
    private val repository: BalanceRepository,
) {
    suspend operator fun invoke(groupId: String, draft: SettlementDraft): AppResult<Settlement> =
        repository.registerSettlement(groupId, draft.normalized())
}
