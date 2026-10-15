package app.cuentasclaras.domain.validation

import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseValidatorTest {

    private val members = setOf("u-1", "u-2")

    private fun validateFields(
        description: String = "Cena",
        amount: String = "20,00",
        expenseDate: String = "2026-01-31",
        paidByUserId: String? = "u-1",
        category: String = "",
        notes: String = "",
    ) = ExpenseValidator.validateFields(
        description = description,
        amount = amount,
        expenseDate = expenseDate,
        paidByUserId = paidByUserId,
        category = category,
        notes = notes,
        memberIds = members,
    )

    @Test
    fun `un gasto correcto no tiene problemas`() {
        assertTrue(validateFields().isEmpty())
    }

    @Test
    fun `la descripcion es obligatoria`() {
        assertEquals(ExpenseIssue.Required, validateFields(description = "   ")[ExpenseField.DESCRIPTION])
    }

    @Test
    fun `la descripcion no puede pasar del limite`() {
        val tooLong = "a".repeat(ExpenseValidator.DESCRIPTION_MAX_LENGTH + 1)
        assertEquals(
            ExpenseIssue.TooLong(ExpenseValidator.DESCRIPTION_MAX_LENGTH),
            validateFields(description = tooLong)[ExpenseField.DESCRIPTION],
        )
    }

    @Test
    fun `el importe debe ser valido y positivo`() {
        assertEquals(ExpenseIssue.Required, validateFields(amount = " ")[ExpenseField.AMOUNT])
        assertEquals(ExpenseIssue.InvalidAmount, validateFields(amount = "12,345")[ExpenseField.AMOUNT])
        assertEquals(ExpenseIssue.AmountNotPositive, validateFields(amount = "0")[ExpenseField.AMOUNT])
    }

    @Test
    fun `la fecha tiene que ser una fecha ISO real`() {
        assertEquals(ExpenseField.DATE, validateFields(expenseDate = "31/01/2026").keys.first())
        assertEquals(ExpenseField.DATE, validateFields(expenseDate = "2026-02-30").keys.first())
        assertTrue(validateFields(expenseDate = "2026-02-28").isEmpty())
    }

    @Test
    fun `el pagador tiene que ser un miembro activo`() {
        assertEquals(ExpenseField.PAYER, validateFields(paidByUserId = null).keys.first())
        assertEquals(
            ExpenseIssue.PayerNotMember,
            validateFields(paidByUserId = "u-9")[ExpenseField.PAYER],
        )
    }

    @Test
    fun `categoria y notas no pueden pasarse de longitud`() {
        assertEquals(
            ExpenseIssue.TooLong(ExpenseValidator.CATEGORY_MAX_LENGTH),
            validateFields(category = "c".repeat(ExpenseValidator.CATEGORY_MAX_LENGTH + 1))
                [ExpenseField.CATEGORY],
        )
        assertEquals(
            ExpenseIssue.TooLong(ExpenseValidator.NOTES_MAX_LENGTH),
            validateFields(notes = "n".repeat(ExpenseValidator.NOTES_MAX_LENGTH + 1))[ExpenseField.NOTES],
        )
    }

    @Test
    fun `isIsoDate acepta solo el formato esperado`() {
        assertTrue(ExpenseValidator.isIsoDate("2026-01-01"))
        assertFalse(ExpenseValidator.isIsoDate("2026-1-1"))
        assertFalse(ExpenseValidator.isIsoDate("2026-13-01"))
        assertFalse(ExpenseValidator.isIsoDate(""))
    }

    @Test
    fun `un reparto a partes iguales siempre cuadra`() {
        val shares = listOf(ExpenseShareInput("u-1"), ExpenseShareInput("u-2"))
        assertNull(ExpenseValidator.validateSplit(1000, SplitType.EQUAL, shares))
    }

    @Test
    fun `el reparto exacto tiene que sumar el importe`() {
        assertNull(
            ExpenseValidator.validateSplit(
                1000,
                SplitType.EXACT,
                listOf(
                    ExpenseShareInput("u-1", amountMinor = 400),
                    ExpenseShareInput("u-2", amountMinor = 600),
                ),
            ),
        )
        assertEquals(
            ExpenseIssue.ExactSumMismatch(expectedMinor = 1000, actualMinor = 900),
            ExpenseValidator.validateSplit(
                1000,
                SplitType.EXACT,
                listOf(
                    ExpenseShareInput("u-1", amountMinor = 400),
                    ExpenseShareInput("u-2", amountMinor = 500),
                ),
            ),
        )
    }

    @Test
    fun `el reparto exacto rechaza una parte sin importe`() {
        assertEquals(
            ExpenseIssue.InvalidNumber,
            ExpenseValidator.validateSplit(
                1000,
                SplitType.EXACT,
                listOf(ExpenseShareInput("u-1", amountMinor = 1000), ExpenseShareInput("u-2")),
            ),
        )
    }

    @Test
    fun `los porcentajes tienen que sumar cien`() {
        assertNull(
            ExpenseValidator.validateSplit(
                1000,
                SplitType.PERCENTAGE,
                listOf(
                    ExpenseShareInput("u-1", percentage = BigDecimal("33.33")),
                    ExpenseShareInput("u-2", percentage = BigDecimal("66.67")),
                ),
            ),
        )
        assertEquals(
            ExpenseIssue.PercentageSumNotHundred(BigDecimal("99.99")),
            ExpenseValidator.validateSplit(
                1000,
                SplitType.PERCENTAGE,
                listOf(
                    ExpenseShareInput("u-1", percentage = BigDecimal("33.33")),
                    ExpenseShareInput("u-2", percentage = BigDecimal("66.66")),
                ),
            ),
        )
    }

    @Test
    fun `las partes tienen que sumar mas que cero`() {
        assertEquals(
            ExpenseIssue.SharesAllZero,
            ExpenseValidator.validateSplit(
                1000,
                SplitType.SHARES,
                listOf(
                    ExpenseShareInput("u-1", weight = BigDecimal.ZERO),
                    ExpenseShareInput("u-2", weight = BigDecimal.ZERO),
                ),
            ),
        )
        assertNull(
            ExpenseValidator.validateSplit(
                1000,
                SplitType.SHARES,
                listOf(
                    ExpenseShareInput("u-1", weight = BigDecimal.ONE),
                    ExpenseShareInput("u-2", weight = BigDecimal("2")),
                ),
            ),
        )
    }

    @Test
    fun `sin participantes no hay reparto`() {
        assertEquals(ExpenseIssue.NoParticipants, ExpenseValidator.validateSplit(1000, SplitType.EQUAL, emptyList()))
    }

    @Test
    fun `un importe no positivo invalida el reparto`() {
        val shares = listOf(ExpenseShareInput("u-1"))
        assertEquals(
            ExpenseIssue.AmountNotPositive,
            ExpenseValidator.validateSplit(0, SplitType.EQUAL, shares),
        )
        assertEquals(
            ExpenseIssue.AmountNotPositive,
            ExpenseValidator.validateSplit(-500, SplitType.EQUAL, shares),
        )
    }

    @Test
    fun `las partes nulas o negativas se rechazan`() {
        assertEquals(
            ExpenseIssue.InvalidNumber,
            ExpenseValidator.validateSplit(
                1000,
                SplitType.PERCENTAGE,
                listOf(ExpenseShareInput("u-1"), ExpenseShareInput("u-2", percentage = BigDecimal("100"))),
            ),
        )
        assertEquals(
            ExpenseIssue.NegativeNumber,
            ExpenseValidator.validateSplit(
                1000,
                SplitType.EXACT,
                listOf(
                    ExpenseShareInput("u-1", amountMinor = -1),
                    ExpenseShareInput("u-2", amountMinor = 1001),
                ),
            ),
        )
    }
}
