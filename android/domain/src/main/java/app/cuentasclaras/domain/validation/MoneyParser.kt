package app.cuentasclaras.domain.validation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conversión entre lo que escribe el usuario y los valores que viajan por la
 * API: importes en unidades mínimas y porcentajes/pesos decimales.
 *
 * Acepta punto o coma como separador decimal. Los importes admiten hasta dos
 * decimales, que es lo que asume hoy la app para cualquier moneda del grupo.
 */
object MoneyParser {

    /** Decimales que se aceptan en el importe del formulario. */
    const val DECIMALS = 2

    private val AMOUNT_REGEX = Regex("^\\d+(\\.\\d{1,2})?$")
    private val NUMBER_REGEX = Regex("^\\d+(\\.\\d+)?$")

    /** Convierte `"12,50"` o `"12.5"` en unidades mínimas; `null` si no es válido. */
    fun parseMinor(raw: String): Long? {
        val normalized = normalize(raw)
        if (!AMOUNT_REGEX.matches(normalized)) return null
        return runCatching {
            BigDecimal(normalized)
                .movePointRight(DECIMALS)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }

    /** Convierte unidades mínimas en texto con dos decimales (`1250` → `"12.50"`). */
    fun toPlainString(amountMinor: Long): String = BigDecimal(amountMinor)
        .movePointLeft(DECIMALS)
        .setScale(DECIMALS, RoundingMode.UNNECESSARY)
        .toPlainString()

    /** Convierte `"33,33"` en `33.33` (porcentajes y pesos); `null` si no es un número. */
    fun parseNumber(raw: String): BigDecimal? {
        val normalized = normalize(raw)
        if (!NUMBER_REGEX.matches(normalized)) return null
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    private fun normalize(raw: String): String =
        raw.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.')
}
