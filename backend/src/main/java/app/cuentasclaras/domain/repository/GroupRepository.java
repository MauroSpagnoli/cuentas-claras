package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.Group;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de grupos.
 */
public interface GroupRepository {

    Group save(Group group);

    Optional<Group> findById(UUID id);

    Optional<Group> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /**
     * Grupos en los que el usuario participa (no dado de baja), ordenados por
     * {@code createdAt DESC}. Si {@code afterCreatedAt} no es nulo, devuelve solo
     * los posteriores al cursor (paginacion).
     */
    List<Group> findForUser(UUID userId, Instant afterCreatedAt, UUID afterId, int limit);
}
