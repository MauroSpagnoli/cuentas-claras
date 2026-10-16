package app.cuentasclaras.domain.validation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Campos validables del formulario de registrar un pago. */
enum class SettlementField { PARTIES, AMOUNT, DATE, NOTE }

/** Incidencia de validación de un pago, independiente del idioma (la UI la traduce). */
sealed interface SettlementIssue {
    data object Required : SettlementIssue

    /** Quien paga y quien cobra son el mismo miembro. */
    data object SameParty : SettlementIssue

    data object InvalidAmount : SettlementIssue
    data object AmountNotPositive : SettlementIssue
    data object InvalidDate : SettlementIssue
    data class TooLong(val max: Int) : SettlementIssue
}

/**
 * Validación pura del formulario de registrar un pago, alineada con las
 * restricciones del backend (`CreateSettlementRequest`).
 *
 * No se usa `java.time` a propósito: `minSdk` es 24 y el proyecto no activa el
 * desugaring de librerías.
 */
object SettlementValidator {

    const val NOTE_MAX_LENGTH = 500

    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(
        fromUserId: String,
        toUserId: String,
        amountMinor: Long?,
        settledOn: String,
        note: String,
    ): Map<SettlementField, SettlementIssue> {
        val issues = linkedMapOf<SettlementField, SettlementIssue>()

        val from = fromUserId.trim()
        val to = toUserId.trim()
        when {
            from.isEmpty() || to.isEmpty() -> issues[SettlementField.PARTIES] = SettlementIssue.Required
            from == to -> issues[SettlementField.PARTIES] = SettlementIssue.SameParty
        }

        when {
            amountMinor == null -> issues[SettlementField.AMOUNT] = SettlementIssue.InvalidAmount
            amountMinor <= 0 -> issues[SettlementField.AMOUNT] = SettlementIssue.AmountNotPositive
        }

        if (!isValidIso(settledOn)) issues[SettlementField.DATE] = SettlementIssue.InvalidDate

        if (note.trim().length > NOTE_MAX_LENGTH) {
            issues[SettlementField.NOTE] = SettlementIssue.TooLong(NOTE_MAX_LENGTH)
        }

        return issues
    }

    /** `true` cuando [iso] es una fecha `yyyy-MM-dd` real (`2026-13-01` no lo es). */
    fun isValidIso(iso: String): Boolean {
        val value = iso.trim()
        if (!ISO_DATE.matches(value)) return false
        val position = ParsePosition(0)
        formatter().parse(value, position) ?: return false
        return position.index == value.length
    }

    /** Formato ISO en UTC, no tolerante con valores fuera de rango. */
    private fun formatter(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
