package app.cuentasclaras.feature.balances.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.usecase.balances.ObserveSettlementsUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.feature.balances.BalancesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la historia de pagos de un grupo.
 *
 * El `PagingData` se pide directamente a la red (no hay caché: los pagos son
 * datos derivados) y se cachea en el scope del ViewModel para sobrevivir a
 * recomposiciones. Los nombres de los miembros se leen de la caché de grupos.
 */
@HiltViewModel
class SettlementHistoryViewModel @Inject constructor(
    observeSettlements: ObserveSettlementsUseCase,
    observeMembers: ObserveMembersUseCase,
    refreshMembers: RefreshMembersUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Grupo cuyos pagos se listan; viaja en la ruta. */
    val groupId: String = requireNotNull(savedStateHandle.get<String>(BalancesRoutes.ARG_GROUP_ID)) {
        "La ruta de la historia de pagos necesita el id del grupo"
    }

    val settlements: Flow<PagingData<Settlement>> =
        observeSettlements(groupId).cachedIn(viewModelScope)

    private val _state = MutableStateFlow(SettlementHistoryUiState())
    val state: StateFlow<SettlementHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeMembers(groupId).collect { members ->
                _state.update { it.copy(members = members) }
            }
        }
        viewModelScope.launch { refreshMembers(groupId) }
    }
}
