package app.cuentasclaras.feature.balances.list

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.domain.model.MemberBalance
import app.cuentasclaras.domain.model.PairwiseDebt
import app.cuentasclaras.feature.balances.BalancesFormat
import app.cuentasclaras.feature.balances.R
import app.cuentasclaras.feature.balances.planAlgorithmLabel
import app.cuentasclaras.feature.balances.toMessage
import kotlin.math.abs

/**
 * Entrada de la pantalla de balances: enlaza el ViewModel con la UI y recarga
 * cada vez que la pantalla vuelve a primer plano, de modo que el regreso desde
 * el formulario de pago refleje el pago recién registrado.
 */
@Composable
fun BalancesEntry(
    onBack: () -> Unit,
    onRegisterPayment: (groupId: String, fromUserId: String, toUserId: String, amountMinor: Long) -> Unit,
    onNewPayment: (groupId: String) -> Unit,
    onOpenHistory: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RefreshOnStart(onStart = viewModel::refresh)

    BalancesScreen(
        state = state,
        currentUserId = currentUserId,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onRegisterPayment = { fromUserId, toUserId, amountMinor ->
            onRegisterPayment(viewModel.groupId, fromUserId, toUserId, amountMinor)
        },
        onNewPayment = { onNewPayment(viewModel.groupId) },
        onOpenHistory = { onOpenHistory(viewModel.groupId) },
        modifier = modifier,
    )
}

/** Lanza [onStart] cada vez que el destino pasa a `ON_START` (vuelta al frente). */
@Composable
private fun RefreshOnStart(onStart: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) onStart()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesScreen(
    state: BalancesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRegisterPayment: (fromUserId: String, toUserId: String, amountMinor: Long) -> Unit,
    onNewPayment: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.balances_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.balances_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.balances_history),
                        )
                    }
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.balances_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasData) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.balances_register)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onNewPayment,
                )
            }
        },
    ) { padding ->
        BalancesContent(
            state = state,
            currentUserId = currentUserId,
            onRetry = onRetry,
            onRegisterPayment = onRegisterPayment,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun BalancesContent(
    state: BalancesUiState,
    currentUserId: String?,
    onRetry: () -> Unit,
    onRegisterPayment: (fromUserId: String, toUserId: String, amountMinor: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && !state.hasData -> ProgressScreen(
            message = stringResource(R.string.balances_loading),
            modifier = modifier,
        )

        state.error != null && !state.hasData -> Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorBanner(message = state.error.toMessage())
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.balances_retry))
            }
        }

        state.members.isEmpty() -> EmptyState(
            title = stringResource(R.string.balances_empty_title),
            body = stringResource(R.string.balances_empty_body),
            modifier = modifier,
        )

        else -> BalancesList(
            state = state,
            currentUserId = currentUserId,
            onRegisterPayment = onRegisterPayment,
            modifier = modifier,
        )
    }
}

@Composable
private fun BalancesList(
    state: BalancesUiState,
    currentUserId: String?,
    onRegisterPayment: (fromUserId: String, toUserId: String, amountMinor: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxAbs = state.members.maxOfOrNull { abs(it.netMinor) } ?: 0L
    val settled = state.members.all { it.netMinor == 0L } && state.steps.isEmpty()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        state.error?.let { error ->
            item {
                ErrorBanner(
                    message = error.toMessage(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item { SectionTitle(stringResource(R.string.balances_net_header)) }

        if (settled) {
            item {
                Text(
                    text = stringResource(R.string.balances_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        items(items = state.members, key = { it.userId }) { member ->
            MemberBalanceRow(
                balance = member,
                maxAbs = maxAbs,
                isCurrentUser = member.userId == currentUserId,
                currency = state.currency,
            )
            HorizontalDivider()
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { PlanHeader(state = state) }

        if (state.steps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.balances_plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(
                items = state.steps,
                key = { "${it.fromUserId}-${it.toUserId}" },
            ) { step ->
                DebtRow(
                    step = step,
                    fromName = state.memberName(step.fromUserId),
                    toName = state.memberName(step.toUserId),
                    currency = state.currency,
                    onRegister = {
                        onRegisterPayment(step.fromUserId, step.toUserId, step.amountMinor)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Neto de un miembro con su barra proporcional al mayor saldo del grupo: es el
 * gráfico que deja ver de un vistazo quién está más lejos de cero.
 */
@Composable
private fun MemberBalanceRow(
    balance: MemberBalance,
    maxAbs: Long,
    isCurrentUser: Boolean,
    currency: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = balance.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isCurrentUser) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.balances_you),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = BalancesFormat.amount(balance.netMinor, currency),
                style = MaterialTheme.typography.titleMedium,
                color = netColor(balance.netMinor),
            )
        }

        if (balance.netMinor != 0L) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(abs(balance.netMinor).toFloat() / maxOf(maxAbs, 1L).toFloat())
                    .height(6.dp)
                    .background(netColor(balance.netMinor), RoundedCornerShape(3.dp)),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = netStatus(balance.netMinor, currency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanHeader(state: BalancesUiState) {
    val algorithm = state.plan?.algorithm
    val subtitle = if (algorithm.isNullOrBlank()) {
        stringResource(R.string.balances_plan_count, state.steps.size)
    } else {
        stringResource(
            R.string.balances_plan_subtitle,
            planAlgorithmLabel(algorithm),
            state.steps.size,
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.balances_plan_header),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DebtRow(
    step: PairwiseDebt,
    fromName: String?,
    toName: String?,
    currency: String,
    onRegister: () -> Unit,
) {
    val unknown = stringResource(R.string.balances_unknown_member)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.balances_debt_line,
                        fromName ?: unknown,
                        toName ?: unknown,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = BalancesFormat.amount(step.amountMinor, currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRegister) {
                Text(stringResource(R.string.balances_register_short))
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
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

@Composable
private fun netStatus(netMinor: Long, currency: String): String = when {
    netMinor > 0 -> stringResource(
        R.string.balances_net_positive,
        BalancesFormat.amount(netMinor, currency),
    )

    netMinor < 0 -> stringResource(
        R.string.balances_net_negative,
        BalancesFormat.amount(-netMinor, currency),
    )

    else -> stringResource(R.string.balances_net_zero)
}

@Composable
private fun netColor(netMinor: Long): Color = when {
    netMinor > 0 -> MaterialTheme.colorScheme.primary
    netMinor < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
