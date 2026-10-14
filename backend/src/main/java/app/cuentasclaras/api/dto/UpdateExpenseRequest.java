package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Edicion parcial de gasto: los campos nulos se ignoran. Si se envia
 * {@code shares} o cambia {@code amountMinor}/{@code splitType}, el reparto se
 * vuelve a resolver.
 */
public record UpdateExpenseRequest(
        @Size(min = 1, max = 200)
        String description,

        @Min(value = 1, message = "debe ser mayor que cero")
        Long amountMinor,

        UUID paidByUserId,

        SplitType splitType,

        @Size(max = 32)
        String category,

        LocalDate expenseDate,

        @Size(max = 1000)
        String notes,

        @Valid
        List<ExpenseShareInput> shares,

        Long version
) {
}
