package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GroupMemberJpaRepository extends JpaRepository<GroupMemberJpaEntity, UUID> {

    Optional<GroupMemberJpaEntity> findByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupIdAndStatusNot(UUID groupId, MemberStatus status);

    long countByGroupIdAndRoleAndStatusNot(UUID groupId, GroupRole role, MemberStatus status);

    @Query("""
            select m.groupId as groupId, count(m) as total
            from GroupMemberJpaEntity m
            where m.groupId in :groupIds and m.status <> :excluded
            group by m.groupId
            """)
    List<GroupCount> countActiveByGroupIds(@Param("groupIds") Collection<UUID> groupIds,
                                           @Param("excluded") MemberStatus excluded);

    @Query("""
            select m.userId as userId, u.displayName as displayName, u.email as email,
                   m.role as role, m.status as status, m.joinedAt as joinedAt
            from GroupMemberJpaEntity m, UserJpaEntity u
            where u.id = m.userId and m.groupId = :groupId
            order by m.createdAt asc
            """)
    List<MemberRow> findMemberRows(@Param("groupId") UUID groupId);

    interface GroupCount {
        UUID getGroupId();

        long getTotal();
    }

    interface MemberRow {
        UUID getUserId();

        String getDisplayName();

        String getEmail();

        GroupRole getRole();

        MemberStatus getStatus();

        Instant getJoinedAt();
    }
}
