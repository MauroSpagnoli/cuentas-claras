package app.cuentasclaras.feature.expenses

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MINOR_SCALE = 2

/**
 * Importes y fechas de la feature: formateo para la UI y conversión de fechas
 * ISO ↔ milisegundos (lo que admite el `DatePicker` de Material 3).
 *
 * No se usa `java.time` a propósito: el proyecto no activa el desugaring de
 * librerías y `minSdk` es 24.
 */
object ExpensesFormat {

    /** `1250` con `"EUR"` → `"12,50 EUR"`; sin moneda si no se conoce. */
    fun amount(amountMinor: Long, currency: String): String =
        if (currency.isBlank()) amount(amountMinor) else "${amount(amountMinor)} $currency"

    /** `1250` → `"12,50"` (sin moneda), con el separador del idioma del dispositivo. */
    fun amount(amountMinor: Long): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        return DecimalFormat("#,##0.00", symbols).format(BigDecimal.valueOf(amountMinor, MINOR_SCALE))
    }

    /** `BigDecimal` sin ceros de relleno: `33.330` → `"33.33"`. */
    fun decimal(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    /** Fecha ISO del día de hoy. */
    fun todayIso(): String = isoFormatter().format(Date())

    /** Fecha ISO válida, o `null`. */
    fun parseIso(iso: String): Date? {
        val value = iso.trim()
        val position = ParsePosition(0)
        val date = isoFormatter().parse(value, position) ?: return null
        return if (position.index == value.length) date else null
    }

    /** ISO → milisegundos UTC, el formato que espera el `DatePicker`. */
    fun millisFromIso(iso: String): Long? = parseIso(iso)?.time

    /** Milisegundos UTC del `DatePicker` → ISO. */
    fun isoFromMillis(millis: Long): String = isoFormatter().format(Date(millis))

    /** Fecha ISO → texto corto local (`31/01/2026`). */
    fun displayDate(iso: String): String {
        val date = parseIso(iso) ?: return iso
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }

    private fun isoFormatter(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Decimal crudo para editar en un campo de texto. */
    fun plainAmount(amountMinor: Long): String =
        BigDecimal.valueOf(amountMinor, MINOR_SCALE).stripTrailingZeros().toPlainString()
}
