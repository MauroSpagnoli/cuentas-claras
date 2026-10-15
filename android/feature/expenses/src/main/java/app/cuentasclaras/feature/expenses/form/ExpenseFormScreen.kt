package app.cuentasclaras.feature.expenses.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.core.designsystem.component.AppTextField
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.core.designsystem.component.PrimaryButton
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.validation.ExpenseField
import app.cuentasclaras.feature.expenses.ExpensesFormat
import app.cuentasclaras.feature.expenses.R
import app.cuentasclaras.feature.expenses.label
import app.cuentasclaras.feature.expenses.toMessage

@Composable
fun ExpenseFormEntry(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    ExpenseFormScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    state: ExpenseFormUiState,
    onEvent: (ExpenseFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerVisible by remember { mutableStateOf(false) }
    var payerMenuVisible by remember { mutableStateOf(false) }
    val currency = state.currency

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditing) {
                            stringResource(R.string.expense_form_edit_title)
                        } else {
                            stringResource(R.string.expense_form_create_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.expense_form_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.expense_form_loading))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            AppTextField(
                value = state.description,
                onValueChange = { onEvent(ExpenseFormEvent.DescriptionChanged(it)) },
                label = stringResource(R.string.expense_form_description),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(ExpenseField.DESCRIPTION),
                supportingText = state.fieldIssues[ExpenseField.DESCRIPTION]?.toMessage(currency),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.amount,
                onValueChange = { onEvent(ExpenseFormEvent.AmountChanged(it)) },
                label = stringResource(R.string.expense_form_amount),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(ExpenseField.AMOUNT),
                supportingText = state.fieldIssues[ExpenseField.AMOUNT]?.toMessage(currency),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.expense_form_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { datePickerVisible = true },
                enabled = !state.isSubmitting,
            ) {
                Text(ExpensesFormat.displayDate(state.expenseDate))
            }

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.category,
                onValueChange = { onEvent(ExpenseFormEvent.CategoryChanged(it)) },
                label = stringResource(R.string.expense_form_category),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(ExpenseField.CATEGORY),
                supportingText = state.fieldIssues[ExpenseField.CATEGORY]?.toMessage(currency),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.expense_form_paid_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Box {
                OutlinedButton(
                    onClick = { payerMenuVisible = true },
                    enabled = !state.isSubmitting && state.members.isNotEmpty(),
                ) {
                    val payerName = state.paidByUserId?.let { state.memberName(it) }
                    Text(payerName ?: stringResource(R.string.expense_form_choose))
                }
                DropdownMenu(
                    expanded = payerMenuVisible,
                    onDismissRequest = { payerMenuVisible = false },
                ) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayName) },
                            onClick = {
                                payerMenuVisible = false
                                onEvent(ExpenseFormEvent.PayerChanged(member.userId))
                            },
                        )
                    }
                }
            }
            if (state.fieldIssues.containsKey(ExpenseField.PAYER)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.fieldIssues.getValue(ExpenseField.PAYER).toMessage(currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.expense_form_split_type),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            SplitTypeSelector(
                selected = state.splitType,
                enabled = !state.isSubmitting,
                onSelect = { onEvent(ExpenseFormEvent.SplitTypeChanged(it)) },
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.expense_form_participants),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))

            if (state.members.isEmpty()) {
                Text(
                    text = stringResource(R.string.expense_form_participants_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.members.forEach { member ->
                    ParticipantRow(
                        name = member.displayName,
                        participant = state.participant(member.userId),
                        splitType = state.splitType,
                        preview = state.preview[member.userId]?.let {
                            ExpensesFormat.amount(it, currency)
                        },
                        enabled = !state.isSubmitting,
                        onToggle = { onEvent(ExpenseFormEvent.ParticipantToggled(member.userId)) },
                        onAmountChange = {
                            onEvent(ExpenseFormEvent.ParticipantAmountChanged(member.userId, it))
                        },
                        onPercentageChange = {
                            onEvent(ExpenseFormEvent.ParticipantPercentageChanged(member.userId, it))
                        },
                        onWeightChange = {
                            onEvent(ExpenseFormEvent.ParticipantWeightChanged(member.userId, it))
                        },
                    )
                }
            }

            state.splitIssue?.let { issue ->
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = issue.toMessage(currency))
            }

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.notes,
                onValueChange = { onEvent(ExpenseFormEvent.NotesChanged(it)) },
                label = stringResource(R.string.expense_form_notes),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(ExpenseField.NOTES),
                supportingText = state.fieldIssues[ExpenseField.NOTES]?.toMessage(currency),
            )

            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                ErrorBanner(message = error.toMessage())
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (state.isEditing) {
                    stringResource(R.string.expense_form_submit_editing)
                } else {
                    stringResource(R.string.expense_form_submit)
                },
                onClick = { onEvent(ExpenseFormEvent.Submitted) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (datePickerVisible) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = ExpensesFormat.millisFromIso(state.expenseDate),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onEvent(ExpenseFormEvent.DateChanged(ExpensesFormat.isoFromMillis(millis)))
                        }
                        datePickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.expense_form_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.expense_form_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SplitTypeSelector(
    selected: SplitType,
    enabled: Boolean,
    onSelect: (SplitType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        listOf(
            listOf(SplitType.EQUAL, SplitType.EXACT),
            listOf(SplitType.PERCENTAGE, SplitType.SHARES),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { splitType ->
                    FilterChip(
                        selected = selected == splitType,
                        onClick = { onSelect(splitType) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = splitType.label(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ParticipantRow(
    name: String,
    participant: ParticipantInput,
    splitType: SplitType,
    preview: String?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onAmountChange: (String) -> Unit,
    onPercentageChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = participant.selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            preview?.let {
                Text(
                    text = stringResource(R.string.expense_form_preview, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (splitType) {
            SplitType.EQUAL -> Unit

            SplitType.EXACT -> ParticipantField(
                value = participant.amount,
                label = stringResource(R.string.expense_form_amount_label),
                enabled = enabled && participant.selected,
                onValueChange = onAmountChange,
            )

            SplitType.PERCENTAGE -> ParticipantField(
                value = participant.percentage,
                label = stringResource(R.string.expense_form_percentage_label),
                enabled = enabled && participant.selected,
                onValueChange = onPercentageChange,
            )

            SplitType.SHARES -> ParticipantField(
                value = participant.weight,
                label = stringResource(R.string.expense_form_weight_label),
                enabled = enabled && participant.selected,
                onValueChange = onWeightChange,
            )
        }
    }
}

@Composable
private fun ParticipantField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(116.dp),
    )
}
