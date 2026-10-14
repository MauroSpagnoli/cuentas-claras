package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alta de gasto con su reparto. La moneda es opcional: si falta se usa la del
 * grupo.
 */
public record CreateExpenseRequest(
        @NotBlank
        @Size(max = 200)
        String description,

        @NotNull
        @Min(value = 1, message = "debe ser mayor que cero")
        Long amountMinor,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217 de 3 letras")
        String currency,

        @NotNull
        UUID paidByUserId,

        @NotNull
        SplitType splitType,

        @Size(max = 32)
        String category,

        @NotNull
        LocalDate expenseDate,

        @Size(max = 1000)
        String notes,

        @NotEmpty
        @Valid
        List<ExpenseShareInput> shares
) {
}
