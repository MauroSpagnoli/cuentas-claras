package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento inmutable del historial de un grupo. {@code summary} es un texto
 * legible ya resuelto (incluye el nombre del actor) para no depender de joins
 * al leer el feed. {@code amountMinor} es opcional (nulo en eventos de
 * miembros).
 */
public record ActivityEvent(
        UUID id,
        UUID groupId,
        ActivityType type,
        UUID actorUserId,
        String summary,
        Long amountMinor,
        Instant occurredAt
) {
}
