package app.cuentasclaras.domain.model;

import java.util.List;

/**
 * Gasto con sus partes resueltas (modelo de lectura para la API).
 */
public record ExpenseDetail(Expense expense, List<ExpenseShare> shares) {
}
