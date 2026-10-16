package app.cuentasclaras.feature.balances.history

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
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.feature.balances.BalancesFormat
import app.cuentasclaras.feature.balances.R
import app.cuentasclaras.feature.balances.toMessage

/**
 * Entrada de la historia de pagos: enlaza el ViewModel con la UI.
 */
@Composable
fun SettlementHistoryEntry(
    onBack: () -> Unit,
    onRegisterPayment: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettlementHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settlements = viewModel.settlements.collectAsLazyPagingItems()

    SettlementHistoryScreen(
        state = state,
        settlements = settlements,
        onBack = onBack,
        onRegisterPayment = { onRegisterPayment(viewModel.groupId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementHistoryScreen(
    state: SettlementHistoryUiState,
    settlements: LazyPagingItems<Settlement>,
    onBack: () -> Unit,
    onRegisterPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settlement_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.balances_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { settlements.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.balances_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.balances_register)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onRegisterPayment,
            )
        },
    ) { padding ->
        SettlementHistoryContent(
            settlements = settlements,
            memberName = { state.memberName(it) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun SettlementHistoryContent(
    settlements: LazyPagingItems<Settlement>,
    memberName: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val refresh = settlements.loadState.refresh

    when {
        refresh is LoadState.Loading && settlements.itemCount == 0 -> ProgressScreen(
            message = stringResource(R.string.settlement_history_loading),
            modifier = modifier,
        )

        refresh is LoadState.Error && settlements.itemCount == 0 -> Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorBanner(message = refresh.error.toMessage())
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = settlements::retry) {
                Text(stringResource(R.string.balances_retry))
            }
        }

        settlements.itemCount == 0 -> EmptyHistory(modifier = modifier)

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(
                count = settlements.itemCount,
                key = settlements.itemKey { it.id },
            ) { index ->
                settlements[index]?.let { settlement ->
                    SettlementRow(
                        settlement = settlement,
                        fromName = memberName(settlement.fromUserId),
                        toName = memberName(settlement.toUserId),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            when (val append = settlements.loadState.append) {
                is LoadState.Loading -> item {
                    ProgressScreen(
                        message = stringResource(R.string.settlement_history_loading_more),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is LoadState.Error -> item {
                    Column(Modifier.padding(16.dp)) {
                        ErrorBanner(message = append.error.toMessage())
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = settlements::retry) {
                            Text(stringResource(R.string.balances_retry))
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun SettlementRow(
    settlement: Settlement,
    fromName: String?,
    toName: String?,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.balances_unknown_member)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.settlement_history_line,
                        fromName ?: unknown,
                        toName ?: unknown,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = BalancesFormat.amount(settlement.amountMinor, settlement.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = BalancesFormat.displayDate(settlement.settledOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            settlement.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settlement_history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settlement_history_empty_body),
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
