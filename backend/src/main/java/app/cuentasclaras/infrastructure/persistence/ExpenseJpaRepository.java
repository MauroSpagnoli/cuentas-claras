package app.cuentasclaras.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface ExpenseJpaRepository extends JpaRepository<ExpenseJpaEntity, UUID>,
        JpaSpecificationExecutor<ExpenseJpaEntity> {
}
