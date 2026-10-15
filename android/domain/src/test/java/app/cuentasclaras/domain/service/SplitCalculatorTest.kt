package app.cuentasclaras.domain.service

import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    private fun resolve(
        amountMinor: Long,
        splitType: SplitType,
        shares: List<ExpenseShareInput>,
    ) = SplitCalculator.resolve(amountMinor, splitType, shares)

    private fun amounts(result: List<SplitCalculator.ResolvedShare>) =
        result.associate { it.userId to it.amountMinor }

    @Test
    fun `reparto a partes iguales divisible`() {
        val result = resolve(
            1000,
            SplitType.EQUAL,
            listOf(ExpenseShareInput("u-1"), ExpenseShareInput("u-2")),
        )

        assertEquals(mapOf("u-1" to 500L, "u-2" to 500L), amounts(result))
    }

    @Test
    fun `el sobrante de las partes iguales va a los userId mas bajos`() {
        val result = resolve(
            1000,
            SplitType.EQUAL,
            listOf(ExpenseShareInput("u-3"), ExpenseShareInput("u-1"), ExpenseShareInput("u-2")),
        )

        assertEquals(mapOf("u-1" to 334L, "u-2" to 333L, "u-3" to 333L), amounts(result))
        assertEquals(1000L, result.sumOf { it.amountMinor })
    }

    @Test
    fun `reparto exacto valido`() {
        val result = resolve(
            1000,
            SplitType.EXACT,
            listOf(
                ExpenseShareInput("u-1", amountMinor = 400),
                ExpenseShareInput("u-2", amountMinor = 600),
            ),
        )

        assertEquals(mapOf("u-1" to 400L, "u-2" to 600L), amounts(result))
    }

    @Test
    fun `reparto exacto que no cuadra no devuelve nada`() {
        val result = resolve(
            1000,
            SplitType.EXACT,
            listOf(ExpenseShareInput("u-1", amountMinor = 400)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `porcentajes con sobrante`() {
        val result = resolve(
            1000,
            SplitType.PERCENTAGE,
            listOf(
                ExpenseShareInput("u-1", percentage = BigDecimal("33.33")),
                ExpenseShareInput("u-2", percentage = BigDecimal("33.33")),
                ExpenseShareInput("u-3", percentage = BigDecimal("33.34")),
            ),
        )

        assertEquals(1000L, result.sumOf { it.amountMinor })
        assertEquals(334L, result.single { it.userId == "u-1" }.amountMinor)
        assertEquals(BigDecimal("33.33"), result.single { it.userId == "u-1" }.percentage)
    }

    @Test
    fun `porcentajes que no suman cien no devuelven nada`() {
        val result = resolve(
            1000,
            SplitType.PERCENTAGE,
            listOf(ExpenseShareInput("u-1", percentage = BigDecimal("99.99"))),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `partes proporcionales con sobrante`() {
        val result = resolve(
            1000,
            SplitType.SHARES,
            listOf(
                ExpenseShareInput("u-1", weight = BigDecimal.ONE),
                ExpenseShareInput("u-2", weight = BigDecimal("2")),
            ),
        )

        assertEquals(mapOf("u-1" to 334L, "u-2" to 666L), amounts(result))
        assertEquals(1000L, result.sumOf { it.amountMinor })
    }

    @Test
    fun `las partes solo con ceros no devuelven nada`() {
        val result = resolve(
            1000,
            SplitType.SHARES,
            listOf(
                ExpenseShareInput("u-1", weight = BigDecimal.ZERO),
                ExpenseShareInput("u-2", weight = BigDecimal.ZERO),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `importe no positivo o sin participantes no devuelve nada`() {
        assertTrue(resolve(0, SplitType.EQUAL, listOf(ExpenseShareInput("u-1"))).isEmpty())
        assertTrue(resolve(-10, SplitType.EQUAL, listOf(ExpenseShareInput("u-1"))).isEmpty())
        assertTrue(resolve(1000, SplitType.EQUAL, emptyList()).isEmpty())
    }

    @Test
    fun `faltar el parametro del tipo de reparto no devuelve nada`() {
        assertTrue(resolve(1000, SplitType.EXACT, listOf(ExpenseShareInput("u-1"))).isEmpty())
        assertTrue(resolve(1000, SplitType.PERCENTAGE, listOf(ExpenseShareInput("u-1"))).isEmpty())
        assertTrue(resolve(1000, SplitType.SHARES, listOf(ExpenseShareInput("u-1"))).isEmpty())
    }

    @Test
    fun `la suma de las partes siempre cuadra con el importe`() {
        val shares = listOf(
            ExpenseShareInput("u-1", percentage = BigDecimal("16.66")),
            ExpenseShareInput("u-2", percentage = BigDecimal("16.66")),
            ExpenseShareInput("u-3", percentage = BigDecimal("16.66")),
            ExpenseShareInput("u-4", percentage = BigDecimal("16.66")),
            ExpenseShareInput("u-5", percentage = BigDecimal("16.66")),
            ExpenseShareInput("u-6", percentage = BigDecimal("16.70")),
        )

        val result = resolve(999, SplitType.PERCENTAGE, shares)

        assertEquals(999L, result.sumOf { it.amountMinor })
    }
}
