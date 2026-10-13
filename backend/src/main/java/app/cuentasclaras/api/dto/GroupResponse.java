package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Group;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        String currency,
        String inviteCode,
        long memberCount,
        Instant createdAt,
        Long version
) {

    public static GroupResponse from(Group group, long memberCount) {
        return new GroupResponse(
                group.id(),
                group.name(),
                group.description(),
                group.currency(),
                group.inviteCode(),
                memberCount,
                group.createdAt(),
                group.version()
        );
    }
}
