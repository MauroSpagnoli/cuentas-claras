package app.cuentasclaras.feature.expenses.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.SyncFailure
import app.cuentasclaras.feature.expenses.ExpensesFormat
import app.cuentasclaras.feature.expenses.R
import app.cuentasclaras.feature.expenses.label
import app.cuentasclaras.feature.expenses.toMessage

@Composable
fun ExpensesListEntry(
    onBack: () -> Unit,
    onOpenExpense: (groupId: String, expenseId: String) -> Unit,
    onCreateExpense: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpensesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expenses = viewModel.expenses.collectAsLazyPagingItems()

    ExpensesListScreen(
        state = state,
        expenses = expenses,
        onBack = onBack,
        onOpenExpense = { expenseId -> onOpenExpense(viewModel.groupId, expenseId) },
        onCreateExpense = { onCreateExpense(viewModel.groupId) },
        onRetrySync = viewModel::retrySync,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesListScreen(
    state: ExpensesListUiState,
    expenses: LazyPagingItems<Expense>,
    onBack: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onCreateExpense: () -> Unit,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.groupName ?: stringResource(R.string.expenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.expense_detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { expenses.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.expenses_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.expenses_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onCreateExpense,
            )
        },
    ) { padding ->
        ExpensesListContent(
            expenses = expenses,
            memberName = { state.memberName(it) },
            syncFailures = state.syncFailures,
            onOpenExpense = onOpenExpense,
            onRetrySync = onRetrySync,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ExpensesListContent(
    expenses: LazyPagingItems<Expense>,
    memberName: (String) -> String?,
    syncFailures: List<SyncFailure>,
    onOpenExpense: (String) -> Unit,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (syncFailures.isNotEmpty()) {
            SyncFailuresBanner(
                message = syncFailures.first().error.toMessage(),
                onRetry = onRetrySync,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        val contentModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        val refresh = expenses.loadState.refresh

        when {
            refresh is LoadState.Loading && expenses.itemCount == 0 -> ProgressScreen(
                message = stringResource(R.string.expenses_loading),
                modifier = contentModifier,
            )

            refresh is LoadState.Error && expenses.itemCount == 0 -> Column(
                modifier = contentModifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorBanner(message = refresh.error.toMessage())
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = expenses::retry) {
                    Text(stringResource(R.string.expenses_retry))
                }
            }

            expenses.itemCount == 0 -> EmptyExpenses(modifier = contentModifier)

            else -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(
                    count = expenses.itemCount,
                    key = expenses.itemKey { it.id },
                ) { index ->
                    expenses[index]?.let { expense ->
                        ExpenseRow(
                            expense = expense,
                            payerName = memberName(expense.paidByUserId),
                            onClick = { onOpenExpense(expense.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }

                when (val append = expenses.loadState.append) {
                    is LoadState.Loading -> item {
                        ProgressScreen(
                            message = stringResource(R.string.expenses_loading_more),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is LoadState.Error -> item {
                        Column(Modifier.padding(16.dp)) {
                            ErrorBanner(message = append.error.toMessage())
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = expenses::retry) {
                                Text(stringResource(R.string.expenses_retry))
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    payerName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = ExpensesFormat.amount(expense.amountMinor, expense.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (expense.pendingOperation != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.expenses_pending_sync),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = ExpensesFormat.displayDate(expense.expenseDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.expenses_payer,
                        payerName ?: stringResource(R.string.expenses_unknown_member),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = expense.splitType.label(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            expense.category?.takeIf { it.isNotBlank() }?.let { category ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SyncFailuresBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.expenses_sync_failed_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.expenses_sync_retry))
            }
        }
    }
}

@Composable
private fun EmptyExpenses(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.expenses_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.expenses_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressScreen(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
