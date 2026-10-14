package app.cuentasclaras.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface ActivityEventJpaRepository extends JpaRepository<ActivityEventJpaEntity, UUID>,
        JpaSpecificationExecutor<ActivityEventJpaEntity> {
}
