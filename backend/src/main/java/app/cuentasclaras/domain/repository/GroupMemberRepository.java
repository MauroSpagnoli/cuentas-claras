package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.GroupRole;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de pertenencias a grupos.
 */
public interface GroupMemberRepository {

    GroupMember save(GroupMember member);

    Optional<GroupMember> findByGroupAndUser(UUID groupId, UUID userId);

    List<GroupMemberView> findViewsByGroup(UUID groupId);

    /** Numero de miembros con estado distinto de {@code LEFT}. */
    long countMembers(UUID groupId);

    /** Numero de OWNER activos (para la regla del ultimo OWNER). */
    long countActiveOwners(UUID groupId);

    /** Numero de miembros (no LEFT) por grupo, en una sola consulta. */
    Map<UUID, Long> countMembersByGroups(Collection<UUID> groupIds);
}
