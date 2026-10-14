package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.ActivityEvent;

import java.time.Instant;
import java.util.UUID;

public record ActivityItemResponse(
        UUID id,
        String type,
        UUID actorUserId,
        String summary,
        Long amountMinor,
        Instant occurredAt
) {

    public static ActivityItemResponse from(ActivityEvent event) {
        return new ActivityItemResponse(
                event.id(),
                event.type().name(),
                event.actorUserId(),
                event.summary(),
                event.amountMinor(),
                event.occurredAt()
        );
    }
}
