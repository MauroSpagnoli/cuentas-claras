package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.repository.GroupRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto GroupRepository sobre Spring Data JPA.
 */
@Component
class GroupRepositoryAdapter implements GroupRepository {

    private final GroupJpaRepository jpaRepository;

    GroupRepositoryAdapter(GroupJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Group save(Group group) {
        // saveAndFlush para que el @Version se incremente antes de mapear a dominio
        return toDomain(jpaRepository.saveAndFlush(toEntity(group)));
    }

    @Override
    public Optional<Group> findById(UUID id) {
        return jpaRepository.findById(id).map(GroupRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Group> findByInviteCode(String inviteCode) {
        return jpaRepository.findByInviteCode(inviteCode).map(GroupRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByInviteCode(String inviteCode) {
        return jpaRepository.existsByInviteCode(inviteCode);
    }

    @Override
    public List<Group> findForUser(UUID userId, Instant afterCreatedAt, UUID afterId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<GroupJpaEntity> rows = afterCreatedAt == null
                ? jpaRepository.findForUser(userId, MemberStatus.LEFT, pageable)
                : jpaRepository.findForUserAfter(userId, MemberStatus.LEFT, afterCreatedAt, afterId, pageable);
        return rows.stream().map(GroupRepositoryAdapter::toDomain).toList();
    }

    private static Group toDomain(GroupJpaEntity entity) {
        return new Group(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCurrency(),
                entity.getCreatedBy(),
                entity.getInviteCode(),
                entity.getArchivedAt(),
                entity.getDeletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private static GroupJpaEntity toEntity(Group group) {
        return new GroupJpaEntity(
                group.id(),
                group.name(),
                group.description(),
                group.currency(),
                group.createdBy(),
                group.inviteCode(),
                group.archivedAt(),
                group.deletedAt(),
                group.createdAt(),
                group.updatedAt(),
                group.version()
        );
    }
}
