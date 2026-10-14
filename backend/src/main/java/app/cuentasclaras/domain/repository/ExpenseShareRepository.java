package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.ExpenseShare;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de persistencia de las partes de un gasto.
 */
public interface ExpenseShareRepository {

    List<ExpenseShare> saveAll(List<ExpenseShare> shares);

    List<ExpenseShare> findByExpenseId(UUID expenseId);

    /** Partes de varios gastos en una sola consulta (evita el N+1 en el listado). */
    List<ExpenseShare> findByExpenseIds(Collection<UUID> expenseIds);

    /** Elimina todas las partes de un gasto (al reeditarlo). */
    void deleteByExpenseId(UUID expenseId);
}
