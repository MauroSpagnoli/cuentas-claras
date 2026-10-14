package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parte de un gasto tal como la envia el cliente. El campo usado depende del
 * tipo de reparto: {@code amountMinor} para EXACT, {@code percentage} para
 * PERCENTAGE y {@code weight} para SHARES. En EQUAL solo importa {@code userId}.
 */
public record ExpenseShareInput(
        @NotNull
        UUID userId,

        Long amountMinor,

        BigDecimal percentage,

        BigDecimal weight
) {
}
