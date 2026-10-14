package app.cuentasclaras.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

interface SettlementJpaRepository extends JpaRepository<SettlementJpaEntity, UUID>,
        JpaSpecificationExecutor<SettlementJpaEntity> {

    List<SettlementJpaEntity> findByGroupIdAndDeletedAtIsNull(UUID groupId);
}
