package app.cuentasclaras.domain.validation

import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Campos validables del formulario de gasto. */
enum class ExpenseField { DESCRIPTION, AMOUNT, DATE, PAYER, CATEGORY, NOTES, PARTICIPANTS }

/** Qué ha fallado al validar un gasto o su reparto. */
sealed interface ExpenseIssue {
    data object Required : ExpenseIssue
    data class TooLong(val max: Int) : ExpenseIssue
    data object InvalidAmount : ExpenseIssue
    data object AmountNotPositive : ExpenseIssue
    data class AmountTooLarge(val maxMinor: Long) : ExpenseIssue

    /** Alguna parte del reparto no es un número válido. */
    data object InvalidNumber : ExpenseIssue

    /** Alguna parte del reparto es negativa. */
    data object NegativeNumber : ExpenseIssue

    data object NoParticipants : ExpenseIssue

    /** El pagador elegido no está entre los miembros activos del grupo. */
    data object PayerNotMember : ExpenseIssue

    data class ExactSumMismatch(val expectedMinor: Long, val actualMinor: Long) : ExpenseIssue
    data class PercentageSumNotHundred(val actual: BigDecimal) : ExpenseIssue
    data object SharesAllZero : ExpenseIssue
}

/**
 * Validación pura del formulario de gasto, alineada con las restricciones de la
 * API (`CreateExpenseRequest`, `UpdateExpenseRequest`) y con las reglas de
 * `SplitCalculator`.
 */
object ExpenseValidator {

    const val DESCRIPTION_MAX_LENGTH = 200
    const val CATEGORY_MAX_LENGTH = 32
    const val NOTES_MAX_LENGTH = 1000

    /** Importe máximo admitido: margen de sobra para las monedas reales. */
    const val AMOUNT_MAX_MINOR = 1_000_000_000_000L

    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * Valida la cabecera del gasto. [memberIds] son los miembros activos del
     * grupo: el pagador tiene que estar entre ellos.
     */
    fun validateFields(
        description: String,
        amount: String,
        expenseDate: String,
        paidByUserId: String?,
        category: String,
        notes: String,
        memberIds: Set<String>,
    ): Map<ExpenseField, ExpenseIssue> {
        val issues = linkedMapOf<ExpenseField, ExpenseIssue>()

        val trimmedDescription = description.trim()
        when {
            trimmedDescription.isEmpty() -> issues[ExpenseField.DESCRIPTION] = ExpenseIssue.Required
            trimmedDescription.length > DESCRIPTION_MAX_LENGTH ->
                issues[ExpenseField.DESCRIPTION] = ExpenseIssue.TooLong(DESCRIPTION_MAX_LENGTH)
        }

        amountIssue(amount)?.let { issues[ExpenseField.AMOUNT] = it }

        if (!isIsoDate(expenseDate)) issues[ExpenseField.DATE] = ExpenseIssue.Required

        when {
            paidByUserId == null -> issues[ExpenseField.PAYER] = ExpenseIssue.Required
            paidByUserId !in memberIds -> issues[ExpenseField.PAYER] = ExpenseIssue.PayerNotMember
        }

        if (category.trim().length > CATEGORY_MAX_LENGTH) {
            issues[ExpenseField.CATEGORY] = ExpenseIssue.TooLong(CATEGORY_MAX_LENGTH)
        }
        if (notes.trim().length > NOTES_MAX_LENGTH) {
            issues[ExpenseField.NOTES] = ExpenseIssue.TooLong(NOTES_MAX_LENGTH)
        }
        return issues
    }

    /** Valida el importe del gasto escrito por el usuario. */
    fun amountIssue(amount: String): ExpenseIssue? {
        if (amount.isBlank()) return ExpenseIssue.Required
        val minor = MoneyParser.parseMinor(amount) ?: return ExpenseIssue.InvalidAmount
        return when {
            minor <= 0 -> ExpenseIssue.AmountNotPositive
            minor > AMOUNT_MAX_MINOR -> ExpenseIssue.AmountTooLarge(AMOUNT_MAX_MINOR)
            else -> null
        }
    }

    /**
     * Valida el reparto completo. Devuelve `null` cuando es correcto, o el
     * primer problema encontrado.
     */
    fun validateSplit(
        amountMinor: Long,
        splitType: SplitType,
        shares: List<ExpenseShareInput>,
    ): ExpenseIssue? {
        if (amountMinor <= 0) return ExpenseIssue.AmountNotPositive
        if (shares.isEmpty()) return ExpenseIssue.NoParticipants

        return when (splitType) {
            SplitType.EQUAL -> null
            SplitType.EXACT -> validateExact(amountMinor, shares)
            SplitType.PERCENTAGE -> validatePercentage(shares)
            SplitType.SHARES -> validateShares(shares)
        }
    }

    private fun validateExact(amountMinor: Long, shares: List<ExpenseShareInput>): ExpenseIssue? {
        var total = 0L
        for (share in shares) {
            val amount = share.amountMinor ?: return ExpenseIssue.InvalidNumber
            if (amount < 0) return ExpenseIssue.NegativeNumber
            total += amount
        }
        return if (total == amountMinor) {
            null
        } else {
            ExpenseIssue.ExactSumMismatch(expectedMinor = amountMinor, actualMinor = total)
        }
    }

    private fun validatePercentage(shares: List<ExpenseShareInput>): ExpenseIssue? {
        var total = BigDecimal.ZERO
        for (share in shares) {
            val percentage = share.percentage ?: return ExpenseIssue.InvalidNumber
            if (percentage.signum() < 0) return ExpenseIssue.NegativeNumber
            total += percentage
        }
        return if (total.compareTo(BigDecimal.valueOf(100)) == 0) {
            null
        } else {
            ExpenseIssue.PercentageSumNotHundred(total)
        }
    }

    private fun validateShares(shares: List<ExpenseShareInput>): ExpenseIssue? {
        var total = BigDecimal.ZERO
        for (share in shares) {
            val weight = share.weight ?: return ExpenseIssue.InvalidNumber
            if (weight.signum() < 0) return ExpenseIssue.NegativeNumber
            total += weight
        }
        return if (total.signum() > 0) null else ExpenseIssue.SharesAllZero
    }

    /** Fecha ISO (`yyyy-MM-dd`) existente en el calendario. */
    fun isIsoDate(raw: String): Boolean {
        val value = raw.trim()
        if (!ISO_DATE_REGEX.matches(value)) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        return format.parse(value, position) != null && position.index == value.length
    }
}
