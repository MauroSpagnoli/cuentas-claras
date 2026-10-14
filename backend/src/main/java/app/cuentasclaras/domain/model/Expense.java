package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Gasto compartido. El importe se guarda en unidades minimas (centimos).
 */
public record Expense(
        UUID id,
        UUID groupId,
        String description,
        long amountMinor,
        String currency,
        UUID paidByUserId,
        SplitType splitType,
        String category,
        LocalDate expenseDate,
        String notes,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Long version
) {

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
