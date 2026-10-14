package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.model.ExpenseDetail;
import app.cuentasclaras.domain.model.SplitType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
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
        Long version,
        List<ExpenseShareResponse> shares
) {

    public static ExpenseResponse from(ExpenseDetail detail) {
        Expense expense = detail.expense();
        return new ExpenseResponse(
                expense.id(),
                expense.groupId(),
                expense.description(),
                expense.amountMinor(),
                expense.currency(),
                expense.paidByUserId(),
                expense.splitType(),
                expense.category(),
                expense.expenseDate(),
                expense.notes(),
                expense.createdBy(),
                expense.createdAt(),
                expense.version(),
                detail.shares().stream().map(ExpenseShareResponse::from).toList()
        );
    }
}
