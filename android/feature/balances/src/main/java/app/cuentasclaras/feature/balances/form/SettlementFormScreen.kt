package app.cuentasclaras.feature.balances.form

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.validation.SettlementField
import app.cuentasclaras.feature.balances.BalancesFormat
import app.cuentasclaras.feature.balances.R
import app.cuentasclaras.feature.balances.toMessage

@Composable
fun SettlementFormEntry(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettlementFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    SettlementFormScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementFormScreen(
    state: SettlementFormUiState,
    onEvent: (SettlementFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settlement_form_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.balances_back),
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
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
            if (state.members.size < 2) {
                Text(
                    text = stringResource(R.string.settlement_form_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.settlement_form_parties),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(12.dp))

                PartyPicker(
                    label = stringResource(R.string.settlement_form_from),
                    members = state.members,
                    selectedId = state.fromUserId,
                    enabled = !state.isSubmitting,
                    onSelect = { onEvent(SettlementFormEvent.PayerChanged(it)) },
                )

                Spacer(Modifier.height(12.dp))

                PartyPicker(
                    label = stringResource(R.string.settlement_form_to),
                    members = state.members,
                    selectedId = state.toUserId,
                    enabled = !state.isSubmitting,
                    onSelect = { onEvent(SettlementFormEvent.PayeeChanged(it)) },
                )

                state.fieldIssues[SettlementField.PARTIES]?.let { issue ->
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(message = issue.toMessage())
                }

                Spacer(Modifier.height(20.dp))

                AppTextField(
                    value = state.amount,
                    onValueChange = { onEvent(SettlementFormEvent.AmountChanged(it)) },
                    label = stringResource(R.string.settlement_form_amount),
                    enabled = !state.isSubmitting,
                    isError = state.fieldIssues.containsKey(SettlementField.AMOUNT),
                    supportingText = state.fieldIssues[SettlementField.AMOUNT]?.toMessage(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.settlement_form_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { datePickerVisible = true },
                    enabled = !state.isSubmitting,
                ) {
                    Text(BalancesFormat.displayDate(state.settledOn))
                }

                Spacer(Modifier.height(16.dp))

                AppTextField(
                    value = state.note,
                    onValueChange = { onEvent(SettlementFormEvent.NoteChanged(it)) },
                    label = stringResource(R.string.settlement_form_note),
                    enabled = !state.isSubmitting,
                    isError = state.fieldIssues.containsKey(SettlementField.NOTE),
                    supportingText = state.fieldIssues[SettlementField.NOTE]?.toMessage(),
                )

                state.error?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(message = error.toMessage())
                }

                Spacer(Modifier.height(24.dp))

                PrimaryButton(
                    text = stringResource(R.string.settlement_form_submit),
                    onClick = { onEvent(SettlementFormEvent.Submitted) },
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (datePickerVisible) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = BalancesFormat.millisFromIso(state.settledOn),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onEvent(SettlementFormEvent.DateChanged(BalancesFormat.isoFromMillis(millis)))
                        }
                        datePickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.settlement_form_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.settlement_form_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Selector de un miembro del grupo, con el nombre del elegido o un aviso. */
@Composable
private fun PartyPicker(
    label: String,
    members: List<GroupMember>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuVisible by remember { mutableStateOf(false) }
    val selectedName = members.firstOrNull { it.userId == selectedId }?.displayName

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { menuVisible = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selectedName ?: stringResource(R.string.settlement_form_choose),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
            }
            DropdownMenu(
                expanded = menuVisible,
                onDismissRequest = { menuVisible = false },
            ) {
                members.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.displayName) },
                        onClick = {
                            onSelect(member.userId)
                            menuVisible = false
                        },
                    )
                }
            }
        }
    }
}
