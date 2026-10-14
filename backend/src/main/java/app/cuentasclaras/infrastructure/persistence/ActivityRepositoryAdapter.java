package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.ActivityEvent;
import app.cuentasclaras.domain.model.ActivityType;
import app.cuentasclaras.domain.repository.ActivityRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto ActivityRepository sobre Spring Data JPA.
 * El cursor de paginacion se construye con Specifications.
 */
@Component
class ActivityRepositoryAdapter implements ActivityRepository {

    private static final Sort ORDER = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final ActivityEventJpaRepository jpaRepository;

    ActivityRepositoryAdapter(ActivityEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ActivityEvent save(ActivityEvent event) {
        return toDomain(jpaRepository.saveAndFlush(toEntity(event)));
    }

    @Override
    public List<ActivityEvent> findPage(UUID groupId, Instant cursorOccurredAt, UUID cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, ORDER);
        return jpaRepository.findAll(specification(groupId, cursorOccurredAt, cursorId), pageable)
                .getContent().stream()
                .map(ActivityRepositoryAdapter::toDomain)
                .toList();
    }

    private static Specification<ActivityEventJpaEntity> specification(UUID groupId, Instant cursorOccurredAt,
                                                                        UUID cursorId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("groupId"), groupId));
            if (cursorOccurredAt != null && cursorId != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("occurredAt"), cursorOccurredAt),
                        cb.and(cb.equal(root.get("occurredAt"), cursorOccurredAt),
                                cb.lessThan(root.get("id"), cursorId))
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static ActivityEvent toDomain(ActivityEventJpaEntity entity) {
        return new ActivityEvent(
                entity.getId(),
                entity.getGroupId(),
                ActivityType.valueOf(entity.getType()),
                entity.getActorUserId(),
                entity.getSummary(),
                entity.getAmountMinor(),
                entity.getOccurredAt()
        );
    }

    private static ActivityEventJpaEntity toEntity(ActivityEvent event) {
        return new ActivityEventJpaEntity(
                event.id(),
                event.groupId(),
                event.type().name(),
                event.actorUserId(),
                event.summary(),
                event.amountMinor(),
                event.occurredAt()
        );
    }
}
