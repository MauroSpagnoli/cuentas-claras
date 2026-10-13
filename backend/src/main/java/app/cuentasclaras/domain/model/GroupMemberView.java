package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un miembro con los datos de usuario necesarios para la API.
 */
public record GroupMemberView(
        UUID userId,
        String displayName,
        String email,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt
) {
}
