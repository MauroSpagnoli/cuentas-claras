package app.cuentasclaras.feature.balances.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.balances.RegisterSettlementUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.validation.MoneyParser
import app.cuentasclaras.domain.validation.SettlementField
import app.cuentasclaras.domain.validation.SettlementValidator
import app.cuentasclaras.feature.balances.BalancesFormat
import app.cuentasclaras.feature.balances.BalancesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Formulario para registrar un pago entre dos miembros del grupo.
 *
 * Los miembros y la moneda salen de la caché del grupo (`ObserveMembers`, que se
 * refresca al entrar) y la sugerencia del plan de liquidación llega por los
 * argumentos de la ruta. La validación es la misma del backend, hecha en local.
 */
@HiltViewModel
class SettlementFormViewModel @Inject constructor(
    observeGroup: ObserveGroupUseCase,
    observeMembers: ObserveMembersUseCase,
    private val refreshMembers: RefreshMembersUseCase,
    private val registerSettlement: RegisterSettlementUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = requireNotNull(savedStateHandle.get<String>(BalancesRoutes.ARG_GROUP_ID)) {
        "El formulario de pago necesita el id del grupo"
    }

    private val currentUserId: String =
        savedStateHandle.get<String>(BalancesRoutes.ARG_CURRENT_USER_ID).orEmpty()

    private val _state = MutableStateFlow(
        SettlementFormUiState(
            settledOn = BalancesFormat.todayIso(),
            fromUserId = savedStateHandle.get<String>(BalancesRoutes.ARG_FROM_USER_ID)
                ?.takeIf { it.isNotBlank() },
            toUserId = savedStateHandle.get<String>(BalancesRoutes.ARG_TO_USER_ID)
                ?.takeIf { it.isNotBlank() },
            amount = savedStateHandle.get<Long>(BalancesRoutes.ARG_AMOUNT_MINOR)
                ?.takeIf { it > 0L }
                ?.let { BalancesFormat.plainAmount(it) }
                .orEmpty(),
        ),
    )
    val state: StateFlow<SettlementFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeGroup(groupId).collect { group ->
                if (group != null) _state.update { it.copy(currency = group.currency) }
            }
        }

        viewModelScope.launch {
            observeMembers(groupId).collect { members ->
                val active = members.filter { it.status == MemberStatus.ACTIVE }
                _state.update { current -> current.withMembers(active) }
            }
        }

        viewModelScope.launch { refreshMembers(groupId) }
    }

    fun onEvent(event: SettlementFormEvent) {
        when (event) {
            is SettlementFormEvent.PayerChanged -> update {
                it.copy(
                    fromUserId = event.userId,
                    fieldIssues = it.fieldIssues - SettlementField.PARTIES,
                )
            }

            is SettlementFormEvent.PayeeChanged -> update {
                it.copy(
                    toUserId = event.userId,
                    fieldIssues = it.fieldIssues - SettlementField.PARTIES,
                )
            }

            is SettlementFormEvent.AmountChanged -> update {
                it.copy(
                    amount = event.value,
                    fieldIssues = it.fieldIssues - SettlementField.AMOUNT,
                )
            }

            is SettlementFormEvent.DateChanged -> update {
                it.copy(
                    settledOn = event.isoDate,
                    fieldIssues = it.fieldIssues - SettlementField.DATE,
                )
            }

            is SettlementFormEvent.NoteChanged -> update {
                it.copy(
                    note = event.value,
                    fieldIssues = it.fieldIssues - SettlementField.NOTE,
                )
            }

            SettlementFormEvent.Submitted -> submit()
        }
    }

    private fun update(transform: (SettlementFormUiState) -> SettlementFormUiState) {
        _state.update(transform)
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val amountMinor = MoneyParser.parseMinor(current.amount)
        val issues = SettlementValidator.validate(
            fromUserId = current.fromUserId.orEmpty(),
            toUserId = current.toUserId.orEmpty(),
            amountMinor = amountMinor,
            settledOn = current.settledOn,
            note = current.note,
        )
        if (issues.isNotEmpty()) {
            _state.update { it.copy(fieldIssues = issues) }
            return
        }

        val draft = SettlementDraft(
            fromUserId = requireNotNull(current.fromUserId),
            toUserId = requireNotNull(current.toUserId),
            amountMinor = requireNotNull(amountMinor),
            settledOn = current.settledOn,
            note = current.note.takeIf { it.isNotBlank() },
        )

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = registerSettlement(groupId, draft)) {
                is AppResult.Success -> _state.update {
                    it.copy(isSubmitting = false, saved = true)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    /**
     * Vuelca la lista de miembros activos conservando lo que ya eligió el
     * usuario (o la sugerencia de la ruta) y completando lo que falte: por
     * defecto paga el usuario de la sesión y cobra el primer miembro distinto.
     */
    private fun SettlementFormUiState.withMembers(active: List<GroupMember>): SettlementFormUiState {
        val payer = fromUserId?.takeIf { id -> active.any { it.userId == id } }
            ?: active.firstOrNull { it.userId == currentUserId }?.userId
            ?: active.firstOrNull()?.userId

        val payee = toUserId?.takeIf { id -> id != payer && active.any { it.userId == id } }
            ?: active.firstOrNull { it.userId != payer }?.userId

        return copy(
            members = active,
            fromUserId = payer,
            toUserId = payee,
            isLoading = false,
        )
    }
}
