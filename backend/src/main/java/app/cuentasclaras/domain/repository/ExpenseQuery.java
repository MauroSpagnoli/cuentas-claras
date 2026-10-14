package app.cuentasclaras.domain.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de listado de gastos de un grupo. Todos los campos salvo
 * {@code groupId} son opcionales (nulos = sin filtrar).
 */
public record ExpenseQuery(
        UUID groupId,
        LocalDate from,
        LocalDate to,
        UUID paidByUserId,
        String category
) {
}
