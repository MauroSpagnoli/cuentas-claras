package app.cuentasclaras.feature.balances.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.result.errorOrNull
import app.cuentasclaras.domain.result.getOrNull
import app.cuentasclaras.domain.usecase.balances.LoadBalancesUseCase
import app.cuentasclaras.domain.usecase.balances.LoadSettlementPlanUseCase
import app.cuentasclaras.feature.balances.BalancesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Balances de un grupo: netos por miembro, deudas por pares y plan de
 * liquidación.
 *
 * El backend es la fuente de verdad, así que la pantalla vuelve a cargar cada
 * vez que aparece (incluido el regreso del formulario de pago) para reflejar al
 * instante un pago recién registrado.
 */
@HiltViewModel
class BalancesViewModel @Inject constructor(
    private val loadBalances: LoadBalancesUseCase,
    private val loadSettlementPlan: LoadSettlementPlanUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Grupo cuyos balances se muestran; viaja en la ruta. */
    val groupId: String = requireNotNull(savedStateHandle.get<String>(BalancesRoutes.ARG_GROUP_ID)) {
        "La ruta de balances necesita el id del grupo"
    }

    private val _state = MutableStateFlow(BalancesUiState())
    val state: StateFlow<BalancesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Recarga balances y plan desde la red; ante un fallo conserva lo ya cargado. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val balances = loadBalances(groupId)
            val plan = loadSettlementPlan(groupId)
            val balancesValue = balances.getOrNull()
            val planValue = plan.getOrNull()

            _state.update { current ->
                current.copy(
                    currency = balancesValue?.currency ?: current.currency,
                    members = balancesValue?.members ?: current.members,
                    pairs = balancesValue?.pairs ?: current.pairs,
                    plan = planValue ?: current.plan,
                    isLoading = false,
                    error = balances.errorOrNull() ?: plan.errorOrNull(),
                )
            }
        }
    }
}
