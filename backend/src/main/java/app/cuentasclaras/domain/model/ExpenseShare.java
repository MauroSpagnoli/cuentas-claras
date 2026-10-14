package app.cuentasclaras.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Parte de un gasto asignada a un usuario.
 * <p>
 * Siempre se persiste el importe resuelto ({@code shareMinor}); {@code weight} y
 * {@code percentage} guardan el parametro original segun el tipo de reparto.
 */
public record ExpenseShare(
        UUID id,
        UUID expenseId,
        UUID userId,
        long shareMinor,
        BigDecimal weight,
        BigDecimal percentage,
        Instant createdAt
) {
}
