package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Pertenencia de un usuario a un grupo, con su rol y estado.
 */
public record GroupMember(
        UUID id,
        UUID groupId,
        UUID userId,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt,
        Instant createdAt
) {

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isManager() {
        return role == GroupRole.OWNER || role == GroupRole.ADMIN;
    }
}
