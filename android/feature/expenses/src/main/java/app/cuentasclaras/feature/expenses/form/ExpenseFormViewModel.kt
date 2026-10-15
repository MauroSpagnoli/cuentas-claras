package app.cuentasclaras.feature.expenses.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.result.errorOrNull
import app.cuentasclaras.domain.service.SplitCalculator
import app.cuentasclaras.domain.usecase.expenses.CreateExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.ObserveExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.RefreshExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.UpdateExpenseUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.validation.ExpenseIssue
import app.cuentasclaras.domain.validation.ExpenseValidator
import app.cuentasclaras.domain.validation.MoneyParser
import app.cuentasclaras.feature.expenses.ExpensesFormat as Format
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Formulario de un gasto (alta y edición).
 *
 * La caché es la fuente de datos: los participantes salen de los miembros del
 * grupo y, al editar, de las partes del gasto. El reparto se previsualiza con
 * el mismo algoritmo que usa el backend (`SplitCalculator`).
 */
@HiltViewModel
class ExpenseFormViewModel @Inject constructor(
    private val observeExpense: ObserveExpenseUseCase,
    observeGroup: ObserveGroupUseCase,
    observeMembers: ObserveMembersUseCase,
    private val refreshExpense: RefreshExpenseUseCase,
    private val refreshMembers: RefreshMembersUseCase,
    private val createExpense: CreateExpenseUseCase,
    private val updateExpense: UpdateExpenseUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = requireNotNull(savedStateHandle.get<String>(ExpensesRoutes.ARG_GROUP_ID)) {
        "El formulario de gastos necesita el id del grupo"
    }

    private val expenseId: String? = savedStateHandle.get<String>(ExpensesRoutes.ARG_EXPENSE_ID)

    private val currentUserId: String =
        savedStateHandle.get<String>(ExpensesRoutes.ARG_CURRENT_USER_ID).orEmpty()

    /** `true` cuando el formulario edita un gasto existente. */
    val isEditing: Boolean = expenseId != null

    private val _state = MutableStateFlow(
        ExpenseFormUiState(
            isEditing = isEditing,
            isLoading = isEditing,
            expenseDate = Format.todayIso(),
        ),
    )
    val state: StateFlow<ExpenseFormUiState> = _state.asStateFlow()

    /** El formulario se rellena una sola vez; después manda lo que escriba el usuario. */
    private var prefilled = false

    init {
        viewModelScope.launch {
            observeGroup(groupId).collect { group ->
                if (group != null) {
                    _state.update { it.copy(currency = group.currency) }
                }
            }
        }

        viewModelScope.launch {
            observeMembers(groupId).collect { members ->
                val active = members.filter { it.status == MemberStatus.ACTIVE }
                _state.update { current ->
                    current.copy(
                        members = active,
                        participants = active.associate { member ->
                            member.userId to (
                                current.participants[member.userId]
                                    ?: ParticipantInput(selected = !isEditing)
                                )
                        },
                        paidByUserId = current.paidByUserId ?: defaultPayer(active),
                    )
                }
            }
        }

        viewModelScope.launch { refreshMembers(groupId) }

        if (expenseId == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                observeExpense(expenseId).collect { expense ->
                    if (expense != null && !prefilled) {
                        prefilled = true
                        _state.update { current -> prefilled(current, expense) }
                    }
                }
            }
            viewModelScope.launch {
                val result = refreshExpense(expenseId)
                if (!prefilled) {
                    _state.update { it.copy(isLoading = false, error = result.errorOrNull()) }
                }
            }
        }
    }

    fun onEvent(event: ExpenseFormEvent) {
        when (event) {
            is ExpenseFormEvent.DescriptionChanged -> update { it.copy(description = event.value) }

            is ExpenseFormEvent.AmountChanged -> update {
                it.copy(amount = event.value).withPreview()
            }

            is ExpenseFormEvent.DateChanged -> update { it.copy(expenseDate = event.isoDate) }

            is ExpenseFormEvent.CategoryChanged -> update { it.copy(category = event.value) }

            is ExpenseFormEvent.NotesChanged -> update { it.copy(notes = event.value) }

            is ExpenseFormEvent.PayerChanged -> update { it.copy(paidByUserId = event.userId) }

            is ExpenseFormEvent.SplitTypeChanged -> update {
                it.copy(splitType = event.splitType, splitIssue = null).withPreview()
            }

            is ExpenseFormEvent.ParticipantToggled -> update { state ->
                val participant = state.participant(event.userId)
                state.withParticipant(event.userId) { participant.copy(selected = !participant.selected) }
                    .withPreview()
            }

            is ExpenseFormEvent.ParticipantAmountChanged -> update { state ->
                state.withParticipant(event.userId) { it.copy(amount = event.value) }.withPreview()
            }

            is ExpenseFormEvent.ParticipantPercentageChanged -> update { state ->
                state.withParticipant(event.userId) { it.copy(percentage = event.value) }.withPreview()
            }

            is ExpenseFormEvent.ParticipantWeightChanged -> update { state ->
                state.withParticipant(event.userId) { it.copy(weight = event.value) }.withPreview()
            }

            ExpenseFormEvent.Submitted -> submit()
        }
    }

    private fun update(transform: (ExpenseFormUiState) -> ExpenseFormUiState) {
        _state.update(transform)
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val issues = ExpenseValidator.validateFields(
            description = current.description,
            amount = current.amount,
            expenseDate = current.expenseDate,
            paidByUserId = current.paidByUserId,
            category = current.category,
            notes = current.notes,
            memberIds = current.members.map { it.userId }.toSet(),
        )
        val amountMinor = MoneyParser.parseMinor(current.amount)
        val shares = buildShares(current)
        val splitIssue = when {
            shares == null -> ExpenseIssue.NoParticipants
            amountMinor == null -> null
            else -> ExpenseValidator.validateSplit(amountMinor, current.splitType, shares)
        }

        if (issues.isNotEmpty() || splitIssue != null) {
            _state.update { it.copy(fieldIssues = issues, splitIssue = splitIssue) }
            return
        }

        val draft = ExpenseDraft(
            description = current.description,
            amountMinor = requireNotNull(amountMinor),
            paidByUserId = requireNotNull(current.paidByUserId),
            splitType = current.splitType,
            expenseDate = current.expenseDate,
            shares = requireNotNull(shares),
            category = current.category.takeIf { it.isNotBlank() },
            notes = current.notes.takeIf { it.isNotBlank() },
            version = current.version,
        )

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val existingExpenseId = expenseId
            val result = if (existingExpenseId == null) {
                createExpense(groupId, draft)
            } else {
                updateExpense(existingExpenseId, draft)
            }
            when (result) {
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
     * Construye las partes que se enviarán al backend: una por participante
     * seleccionado, con el parámetro que exige cada tipo de reparto.
     * Devuelve `null` si no hay participantes.
     */
    private fun buildShares(state: ExpenseFormUiState): List<ExpenseShareInput>? {
        val ids = state.participants.filterValues { it.selected }.keys.toList()
        if (ids.isEmpty()) return null

        return when (state.splitType) {
            SplitType.EQUAL -> ids.map { ExpenseShareInput(userId = it) }

            SplitType.EXACT -> ids.map { userId ->
                ExpenseShareInput(
                    userId = userId,
                    amountMinor = MoneyParser.parseMinor(state.participant(userId).amount),
                )
            }

            SplitType.PERCENTAGE -> ids.map { userId ->
                ExpenseShareInput(
                    userId = userId,
                    percentage = MoneyParser.parseNumber(state.participant(userId).percentage),
                )
            }

            SplitType.SHARES -> ids.map { userId ->
                ExpenseShareInput(
                    userId = userId,
                    weight = MoneyParser.parseNumber(state.participant(userId).weight),
                )
            }
        }
    }

    /** Reparto calculado en local, para que el formulario muestre el resultado. */
    private fun ExpenseFormUiState.withPreview(): ExpenseFormUiState {
        val minor = MoneyParser.parseMinor(amount) ?: return copy(preview = emptyMap())
        val shares = buildShares(this) ?: return copy(preview = emptyMap())
        return copy(
            preview = SplitCalculator.resolve(minor, splitType, shares)
                .associate { it.userId to it.amountMinor },
        )
    }

    private fun ExpenseFormUiState.withParticipant(
        userId: String,
        transform: (ParticipantInput) -> ParticipantInput,
    ): ExpenseFormUiState = copy(
        participants = participants + (userId to transform(participant(userId))),
    )

    private fun defaultPayer(activeMembers: List<app.cuentasclaras.domain.model.GroupMember>): String? =
        activeMembers.firstOrNull { it.userId == currentUserId }?.userId
            ?: activeMembers.firstOrNull()?.userId

    /** Vuelca en el formulario el gasto que se está editando. */
    private fun prefilled(current: ExpenseFormUiState, expense: Expense): ExpenseFormUiState {
        val sharesByUser = expense.shares.associateBy { it.userId }
        val participants = (current.members.map { it.userId } + sharesByUser.keys)
            .distinct()
            .associateWith { userId ->
                val share = sharesByUser[userId] ?: return@associateWith ParticipantInput()
                ParticipantInput(
                    selected = true,
                    amount = Format.plainAmount(share.amountMinor),
                    percentage = share.percentage?.let { Format.decimal(it) }.orEmpty(),
                    weight = share.weight?.let { Format.decimal(it) }.orEmpty(),
                )
            }

        return current.copy(
            isLoading = false,
            version = expense.version,
            description = expense.description,
            amount = Format.plainAmount(expense.amountMinor),
            expenseDate = expense.expenseDate,
            category = expense.category.orEmpty(),
            notes = expense.notes.orEmpty(),
            paidByUserId = expense.paidByUserId,
            splitType = expense.splitType,
            participants = participants,
            currency = expense.currency,
        ).withPreview()
    }
}
