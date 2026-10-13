package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.MemberStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GroupJpaRepository extends JpaRepository<GroupJpaEntity, UUID> {

    Optional<GroupJpaEntity> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    @Query("""
            select g from GroupJpaEntity g
            where g.deletedAt is null
              and g.id in (
                  select m.groupId from GroupMemberJpaEntity m
                  where m.userId = :userId and m.status <> :excluded
              )
            order by g.createdAt desc, g.id desc
            """)
    List<GroupJpaEntity> findForUser(@Param("userId") UUID userId,
                                     @Param("excluded") MemberStatus excluded,
                                     Pageable pageable);

    @Query("""
            select g from GroupJpaEntity g
            where g.deletedAt is null
              and g.id in (
                  select m.groupId from GroupMemberJpaEntity m
                  where m.userId = :userId and m.status <> :excluded
              )
              and (g.createdAt < :afterCreatedAt
                   or (g.createdAt = :afterCreatedAt and g.id < :afterId))
            order by g.createdAt desc, g.id desc
            """)
    List<GroupJpaEntity> findForUserAfter(@Param("userId") UUID userId,
                                          @Param("excluded") MemberStatus excluded,
                                          @Param("afterCreatedAt") Instant afterCreatedAt,
                                          @Param("afterId") UUID afterId,
                                          Pageable pageable);
}
