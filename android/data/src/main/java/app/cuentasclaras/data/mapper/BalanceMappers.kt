package app.cuentasclaras.data.mapper

import app.cuentasclaras.core.network.dto.BalancesDto
import app.cuentasclaras.core.network.dto.CreateSettlementRequestDto
import app.cuentasclaras.core.network.dto.MemberBalanceDto
import app.cuentasclaras.core.network.dto.PairwiseDebtDto
import app.cuentasclaras.core.network.dto.SettlementDto
import app.cuentasclaras.core.network.dto.SettlementPlanDto
import app.cuentasclaras.domain.model.GroupBalances
import app.cuentasclaras.domain.model.MemberBalance
import app.cuentasclaras.domain.model.PairwiseDebt
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.model.SettlementPlan

fun BalancesDto.toDomain(): GroupBalances = GroupBalances(
    currency = currency,
    members = members.map { it.toDomain() },
    pairs = pairs.map { it.toDomain() },
)

fun MemberBalanceDto.toDomain(): MemberBalance = MemberBalance(
    userId = userId,
    displayName = displayName,
    netMinor = netMinor,
    currency = currency,
)

fun PairwiseDebtDto.toDomain(): PairwiseDebt = PairwiseDebt(
    fromUserId = fromUserId,
    toUserId = toUserId,
    amountMinor = amountMinor,
)

fun SettlementPlanDto.toDomain(): SettlementPlan = SettlementPlan(
    currency = currency,
    algorithm = algorithm,
    transactionCount = transactionCount,
    steps = steps.map { it.toDomain() },
)

fun SettlementDto.toDomain(): Settlement = Settlement(
    id = id,
    groupId = groupId,
    fromUserId = fromUserId,
    toUserId = toUserId,
    amountMinor = amountMinor,
    currency = currency,
    settledOn = settledOn,
    note = note,
    createdAt = createdAt,
)

fun SettlementDraft.toRequest(): CreateSettlementRequestDto = CreateSettlementRequestDto(
    fromUserId = fromUserId,
    toUserId = toUserId,
    amountMinor = amountMinor,
    currency = null,
    settledOn = settledOn,
    note = note,
)
