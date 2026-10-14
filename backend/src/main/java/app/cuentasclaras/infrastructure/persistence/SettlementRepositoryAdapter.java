package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.Settlement;
import app.cuentasclaras.domain.repository.SettlementRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto SettlementRepository sobre Spring Data JPA.
 * Los filtros opcionales se construyen con Specifications para no enviar
 * parametros nulos al motor.
 */
@Component
class SettlementRepositoryAdapter implements SettlementRepository {

    private static final Sort ORDER = Sort.by(Sort.Order.desc("settledOn"), Sort.Order.desc("id"));

    private final SettlementJpaRepository jpaRepository;

    SettlementRepositoryAdapter(SettlementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Settlement save(Settlement settlement) {
        return toDomain(jpaRepository.saveAndFlush(toEntity(settlement)));
    }

    @Override
    public Optional<Settlement> findById(UUID id) {
        return jpaRepository.findById(id).map(SettlementRepositoryAdapter::toDomain);
    }

    @Override
    public List<Settlement> findPage(UUID groupId, LocalDate cursorDate, UUID cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, ORDER);
        return jpaRepository.findAll(specification(groupId, cursorDate, cursorId), pageable)
                .getContent().stream()
                .map(SettlementRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Settlement> findActiveByGroup(UUID groupId) {
        return jpaRepository.findByGroupIdAndDeletedAtIsNull(groupId).stream()
                .map(SettlementRepositoryAdapter::toDomain)
                .toList();
    }

    private static Specification<SettlementJpaEntity> specification(UUID groupId, LocalDate cursorDate,
                                                                    UUID cursorId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("groupId"), groupId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (cursorDate != null && cursorId != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("settledOn"), cursorDate),
                        cb.and(cb.equal(root.get("settledOn"), cursorDate),
                                cb.lessThan(root.get("id"), cursorId))
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Settlement toDomain(SettlementJpaEntity entity) {
        return new Settlement(
                entity.getId(),
                entity.getGroupId(),
                entity.getFromUserId(),
                entity.getToUserId(),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getSettledOn(),
                entity.getNote(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getDeletedAt()
        );
    }

    private static SettlementJpaEntity toEntity(Settlement settlement) {
        return new SettlementJpaEntity(
                settlement.id(),
                settlement.groupId(),
                settlement.fromUserId(),
                settlement.toUserId(),
                settlement.amountMinor(),
                settlement.currency(),
                settlement.settledOn(),
                settlement.note(),
                settlement.createdBy(),
                settlement.createdAt(),
                settlement.deletedAt()
        );
    }
}
