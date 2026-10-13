package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Modelo de dominio del usuario (sin dependencias de framework).
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String defaultCurrency,
        String locale,
        Instant createdAt
) {
}
