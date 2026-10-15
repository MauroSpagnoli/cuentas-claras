package app.cuentasclaras.domain.service

import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Resuelve un reparto en importes exactos (unidades mínimas) con la misma
 * invariante que el backend: la suma de las partes es el importe del gasto y el
 * sobrante no divisible se asigna de forma determinista a los participantes con
 * menor `userId`.
 *
 * Se usa para previsualizar el reparto en el formulario. El cálculo definitivo
 * lo hace el servidor, que devuelve las partes ya resueltas.
 */
object SplitCalculator {

    /** Cien por cien, escala de comparación de los porcentajes. */
    private val HUNDRED: BigDecimal = BigDecimal.valueOf(100)

    /** Escala con la que se calculan las proporciones, igual que en el backend. */
    private const val RATIO_SCALE = 10

    data class ResolvedShare(
        val userId: String,
        val amountMinor: Long,
        val percentage: BigDecimal? = null,
        val weight: BigDecimal? = null,
    )

    /**
     * Devuelve el reparto resuelto, o una lista vacía si los datos no son
     * válidos (importe no positivo, falta algún parámetro del tipo de reparto…).
     */
    fun resolve(
        amountMinor: Long,
        splitType: SplitType,
        shares: List<ExpenseShareInput>,
    ): List<ResolvedShare> {
        if (amountMinor <= 0 || shares.isEmpty()) return emptyList()
        return when (splitType) {
            SplitType.EQUAL -> resolveEqual(amountMinor, shares)
            SplitType.EXACT -> resolveExact(amountMinor, shares)
            SplitType.PERCENTAGE -> resolvePercentage(amountMinor, shares)
            SplitType.SHARES -> resolveShares(amountMinor, shares)
        } ?: emptyList()
    }

    private fun resolveEqual(amountMinor: Long, shares: List<ExpenseShareInput>): List<ResolvedShare> {
        val base = amountMinor / shares.size
        val remainder = (amountMinor % shares.size).toInt()
        val extra = lowestUserIds(shares, remainder)
        return shares.map { share ->
            ResolvedShare(userId = share.userId, amountMinor = base + if (share.userId in extra) 1 else 0)
        }
    }

    private fun resolveExact(amountMinor: Long, shares: List<ExpenseShareInput>): List<ResolvedShare>? {
        var total = 0L
        val amounts = mutableListOf<Long>()
        for (share in shares) {
            val amount = share.amountMinor ?: return null
            if (amount < 0) return null
            total += amount
            amounts += amount
        }
        if (total != amountMinor) return null
        return shares.mapIndexed { index, share ->
            ResolvedShare(userId = share.userId, amountMinor = amounts[index])
        }
    }

    private fun resolvePercentage(amountMinor: Long, shares: List<ExpenseShareInput>): List<ResolvedShare>? {
        val percentages = mutableListOf<BigDecimal>()
        var total = BigDecimal.ZERO
        for (share in shares) {
            val percentage = share.percentage ?: return null
            if (percentage.signum() < 0) return null
            percentages += percentage
            total += percentage
        }
        if (total.compareTo(HUNDRED) != 0) return null

        val floors = percentages.map { floor(amountMinor, it.divide(HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP)) }
        val extra = lowestUserIds(shares, (amountMinor - floors.sum()).toInt())
        return shares.mapIndexed { index, share ->
            ResolvedShare(
                userId = share.userId,
                amountMinor = floors[index] + if (share.userId in extra) 1 else 0,
                percentage = percentages[index],
            )
        }
    }

    private fun resolveShares(amountMinor: Long, shares: List<ExpenseShareInput>): List<ResolvedShare>? {
        val weights = mutableListOf<BigDecimal>()
        var total = BigDecimal.ZERO
        for (share in shares) {
            val weight = share.weight ?: return null
            if (weight.signum() < 0) return null
            weights += weight
            total += weight
        }
        if (total.signum() <= 0) return null

        val floors = weights.map { floor(amountMinor, it.divide(total, RATIO_SCALE, RoundingMode.HALF_UP)) }
        val extra = lowestUserIds(shares, (amountMinor - floors.sum()).toInt())
        return shares.mapIndexed { index, share ->
            ResolvedShare(
                userId = share.userId,
                amountMinor = floors[index] + if (share.userId in extra) 1 else 0,
                weight = weights[index],
            )
        }
    }

    private fun floor(amountMinor: Long, ratio: BigDecimal): Long =
        BigDecimal.valueOf(amountMinor).multiply(ratio).setScale(0, RoundingMode.DOWN).longValueExact()

    /** Los `count` participantes con menor `userId`, como hace el backend. */
    private fun lowestUserIds(shares: List<ExpenseShareInput>, count: Int): Set<String> {
        if (count <= 0) return emptySet()
        return shares.map { it.userId }.sorted().take(count).toSet()
    }
}
