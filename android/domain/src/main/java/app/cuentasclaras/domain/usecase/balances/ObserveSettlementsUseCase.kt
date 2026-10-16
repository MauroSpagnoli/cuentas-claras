package app.cuentasclaras.domain.usecase.balances

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa la historia de pagos de un grupo, paginada por cursor contra la red. */
class ObserveSettlementsUseCase @Inject constructor(
    private val repository: BalanceRepository,
) {
    operator fun invoke(groupId: String): Flow<PagingData<Settlement>> =
        repository.observeSettlements(groupId)
}
