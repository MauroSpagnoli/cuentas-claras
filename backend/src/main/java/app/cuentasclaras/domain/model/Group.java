package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Grupo de gastos compartidos.
 */
public record Group(
        UUID id,
        String name,
        String description,
        String currency,
        UUID createdBy,
        String inviteCode,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
