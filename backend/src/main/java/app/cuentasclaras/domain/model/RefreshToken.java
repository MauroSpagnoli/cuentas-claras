package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token opaco. Se persiste solo su hash (SHA-256).
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedById,
        String userAgent,
        String ip,
        Instant createdAt
) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
