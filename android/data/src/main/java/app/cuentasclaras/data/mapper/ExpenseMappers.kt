package app.cuentasclaras.data.mapper

import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.ExpenseShareDto
import app.cuentasclaras.core.network.dto.ExpenseShareInputDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShare
import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import java.math.BigDecimal

fun ExpenseDto.toDomain(): Expense = Expense(
    id = id,
    groupId = groupId,
    description = description,
    amountMinor = amountMinor,
    currency = currency,
    paidByUserId = paidByUserId,
    splitType = SplitType.fromApi(splitType),
    expenseDate = expenseDate,
    createdBy = createdBy,
    category = category,
    notes = notes,
    createdAt = createdAt,
    version = version,
    shares = shares.map { it.toDomain() },
)

fun ExpenseDto.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    groupId = groupId,
    description = description,
    amountMinor = amountMinor,
    currency = currency,
    paidByUserId = paidByUserId,
    splitType = SplitType.fromApi(splitType).name,
    category = category,
    expenseDate = expenseDate,
    notes = notes,
    createdBy = createdBy,
    createdAt = createdAt,
    version = version,
)

fun ExpenseShareDto.toDomain(): ExpenseShare = ExpenseShare(
    userId = userId,
    amountMinor = amountMinor,
    percentage = percentage,
    weight = weight,
)

fun ExpenseDto.toShareEntities(): List<ExpenseShareEntity> = shares.map { share ->
    ExpenseShareEntity(
        expenseId = id,
        userId = share.userId,
        amountMinor = share.amountMinor,
        percentage = share.percentage?.toPlainString(),
        weight = share.weight?.toPlainString(),
    )
}

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    groupId = groupId,
    description = description,
    amountMinor = amountMinor,
    currency = currency,
    paidByUserId = paidByUserId,
    splitType = SplitType.fromApi(splitType),
    expenseDate = expenseDate,
    createdBy = createdBy,
    category = category,
    notes = notes,
    createdAt = createdAt,
    version = version,
)

fun ExpenseEntity.toDomain(shares: List<ExpenseShare>): Expense = toDomain().copy(shares = shares)

fun ExpenseShareEntity.toDomain(): ExpenseShare = ExpenseShare(
    userId = userId,
    amountMinor = amountMinor,
    percentage = percentage?.let { BigDecimal(it) },
    weight = weight?.let { BigDecimal(it) },
)

fun ExpenseDraft.toCreateRequest(): CreateExpenseRequestDto = CreateExpenseRequestDto(
    description = description,
    amountMinor = amountMinor,
    paidByUserId = paidByUserId,
    splitType = splitType.name,
    expenseDate = expenseDate,
    shares = shares.map { it.toDto() },
    category = category,
    notes = notes,
)

/**
 * La edición va completa: el backend ignora lo que llegue nulo, así que la
 * categoría y las notas vacías se envían como cadena vacía para que se borren.
 */
fun ExpenseDraft.toUpdateRequest(): UpdateExpenseRequestDto = UpdateExpenseRequestDto(
    description = description,
    amountMinor = amountMinor,
    paidByUserId = paidByUserId,
    splitType = splitType.name,
    expenseDate = expenseDate,
    shares = shares.map { it.toDto() },
    category = category.orEmpty(),
    notes = notes.orEmpty(),
    version = version ?: 0,
)

private fun ExpenseShareInput.toDto(): ExpenseShareInputDto = ExpenseShareInputDto(
    userId = userId,
    amountMinor = amountMinor,
    percentage = percentage,
    weight = weight,
)
