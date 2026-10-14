package app.cuentasclaras.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ExpenseShareJpaRepository extends JpaRepository<ExpenseShareJpaEntity, UUID> {

    List<ExpenseShareJpaEntity> findByExpenseId(UUID expenseId);

    List<ExpenseShareJpaEntity> findByExpenseIdIn(Collection<UUID> expenseIds);

    /**
     * Borrado masivo e inmediato (bulk delete). Se usa {@code @Modifying} en vez
     * del borrado derivado para que el DELETE se ejecute antes de insertar las
     * nuevas partes y no choque con la restriccion unica (expense_id, user_id).
     */
    @Modifying
    @Query("delete from ExpenseShareJpaEntity s where s.expenseId = :expenseId")
    int deleteByExpenseId(@Param("expenseId") UUID expenseId);
}
