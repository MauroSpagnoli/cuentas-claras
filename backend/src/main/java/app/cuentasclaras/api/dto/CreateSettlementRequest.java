package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de un pago entre dos miembros. La moneda es opcional: si falta se usa la
 * del grupo.
 */
public record CreateSettlementRequest(
        @NotNull
        UUID fromUserId,

        @NotNull
        UUID toUserId,

        @NotNull
        @Min(value = 1, message = "debe ser mayor que cero")
        Long amountMinor,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217 de 3 letras")
        String currency,

        @NotNull
        LocalDate settledOn,

        @Size(max = 500)
        String note
) {
}
