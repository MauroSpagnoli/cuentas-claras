package app.cuentasclaras.feature.expenses.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseShare
import app.cuentasclaras.feature.expenses.ExpensesFormat
import app.cuentasclaras.feature.expenses.R
import app.cuentasclaras.feature.expenses.label
import app.cuentasclaras.feature.expenses.toMessage
import androidx.compose.ui.res.stringResource

@Composable
fun ExpenseDetailEntry(
    onBack: () -> Unit,
    onEdit: (groupId: String, expenseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    ExpenseDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onEdit = { onEdit(viewModel.groupId, viewModel.expenseId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    state: ExpenseDetailUiState,
    onEvent: (ExpenseDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expense = state.expense

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = expense?.description ?: stringResource(R.string.expense_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.expense_detail_back),
                        )
                    }
                },
                actions = {
                    if (state.canEdit()) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.expense_action_edit),
                            )
                        }
                        IconButton(onClick = { onEvent(ExpenseDetailEvent.DeleteDialogOpened) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.expense_action_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let { error ->
                ErrorBanner(
                    message = error.toMessage(),
                    modifier = Modifier.padding(16.dp),
                )
            }

            when {
                expense != null -> ExpenseDetailContent(
                    expense = expense,
                    memberName = { state.memberName(it) },
                    modifier = Modifier.fillMaxSize(),
                )

                state.isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }

                else -> Text(
                    text = stringResource(R.string.expense_detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    if (state.deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(ExpenseDetailEvent.DeleteDialogDismissed) },
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.expense_delete_body))
                    state.deleteError?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        ErrorBanner(message = error.toMessage())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(ExpenseDetailEvent.DeleteConfirmed) },
                    enabled = !state.isDeleting,
                ) {
                    Text(stringResource(R.string.expense_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(ExpenseDetailEvent.DeleteDialogDismissed) },
                    enabled = !state.isDeleting,
                ) {
                    Text(stringResource(R.string.expense_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ExpenseDetailContent(
    expense: Expense,
    memberName: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = ExpensesFormat.amount(expense.amountMinor, expense.currency),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = expense.splitType.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { HorizontalDivider() }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(
                    label = stringResource(R.string.expense_detail_date),
                    value = ExpensesFormat.displayDate(expense.expenseDate),
                )
                DetailRow(
                    label = stringResource(R.string.expense_form_paid_by),
                    value = memberName(expense.paidByUserId) ?: expense.paidByUserId,
                )
                DetailRow(
                    label = stringResource(R.string.expense_detail_category),
                    value = expense.category?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.expense_detail_no_category),
                )
                DetailRow(
                    label = stringResource(R.string.expense_detail_notes),
                    value = expense.notes?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.expense_detail_no_notes),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.expense_detail_split),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        items(items = expense.shares, key = { it.userId }) { share ->
            ShareRow(
                share = share,
                currency = expense.currency,
                memberName = memberName(share.userId),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShareRow(
    share: ExpenseShare,
    currency: String,
    memberName: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memberName ?: share.userId,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            splitParameter(share)?.let { parameter ->
                Text(
                    text = parameter,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = ExpensesFormat.amount(share.amountMinor, currency),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Etiqueta con el parámetro original del reparto, si lo hay. */
private fun splitParameter(share: ExpenseShare): String? {
    val percentage = share.percentage
    if (percentage != null) return "${ExpensesFormat.decimal(percentage)} %"
    val weight = share.weight
    if (weight != null) return "${ExpensesFormat.decimal(weight)} ×"
    return null
}
