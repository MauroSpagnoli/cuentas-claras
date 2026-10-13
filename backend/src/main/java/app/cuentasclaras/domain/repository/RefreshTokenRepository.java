package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de refresh tokens.
 */
public interface RefreshTokenRepository {

    void save(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    void revoke(UUID id, UUID replacedById, Instant revokedAt);

    void revokeAllForUser(UUID userId, Instant revokedAt);
}
