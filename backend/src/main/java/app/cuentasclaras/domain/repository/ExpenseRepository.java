package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.Expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de gastos.
 */
public interface ExpenseRepository {

    Expense save(Expense expense);

    Optional<Expense> findById(UUID id);

    /**
     * Gastos activos (no borrados) que cumplen los filtros, ordenados por
     * {@code expense_date DESC, id DESC}. Si {@code cursorDate} no es nulo
     * devuelve solo los posteriores al cursor.
     */
    List<Expense> findPage(ExpenseQuery query, LocalDate cursorDate, UUID cursorId, int limit);
}
