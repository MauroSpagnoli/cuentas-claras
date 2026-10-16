package app.cuentasclaras.domain.usecase.balances

import app.cuentasclaras.domain.model.GroupBalances
import app.cuentasclaras.domain.repository.BalanceRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Carga desde la red los balances netos y las deudas por pares de un grupo. */
class LoadBalancesUseCase @Inject constructor(
    private val repository: BalanceRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<GroupBalances> =
        repository.loadBalances(groupId)
}
