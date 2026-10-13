package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador que implementa el puerto GroupMemberRepository sobre Spring Data JPA.
 */
@Component
class GroupMemberRepositoryAdapter implements GroupMemberRepository {

    private final GroupMemberJpaRepository jpaRepository;

    GroupMemberRepositoryAdapter(GroupMemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GroupMember save(GroupMember member) {
        return toDomain(jpaRepository.save(toEntity(member)));
    }

    @Override
    public Optional<GroupMember> findByGroupAndUser(UUID groupId, UUID userId) {
        return jpaRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMemberRepositoryAdapter::toDomain);
    }

    @Override
    public List<GroupMemberView> findViewsByGroup(UUID groupId) {
        return jpaRepository.findMemberRows(groupId).stream()
                .map(row -> new GroupMemberView(
                        row.getUserId(),
                        row.getDisplayName(),
                        row.getEmail(),
                        row.getRole(),
                        row.getStatus(),
                        row.getJoinedAt()))
                .toList();
    }

    @Override
    public long countMembers(UUID groupId) {
        return jpaRepository.countByGroupIdAndStatusNot(groupId, MemberStatus.LEFT);
    }

    @Override
    public long countActiveOwners(UUID groupId) {
        return jpaRepository.countByGroupIdAndRoleAndStatusNot(groupId, GroupRole.OWNER, MemberStatus.LEFT);
    }

    @Override
    public Map<UUID, Long> countMembersByGroups(Collection<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countActiveByGroupIds(groupIds, MemberStatus.LEFT).stream()
                .collect(Collectors.toMap(
                        GroupMemberJpaRepository.GroupCount::getGroupId,
                        GroupMemberJpaRepository.GroupCount::getTotal));
    }

    private static GroupMember toDomain(GroupMemberJpaEntity entity) {
        return new GroupMember(
                entity.getId(),
                entity.getGroupId(),
                entity.getUserId(),
                entity.getRole(),
                entity.getStatus(),
                entity.getJoinedAt(),
                entity.getCreatedAt()
        );
    }

    private static GroupMemberJpaEntity toEntity(GroupMember member) {
        return new GroupMemberJpaEntity(
                member.id(),
                member.groupId(),
                member.userId(),
                member.role(),
                member.status(),
                member.joinedAt(),
                member.createdAt()
        );
    }
}
