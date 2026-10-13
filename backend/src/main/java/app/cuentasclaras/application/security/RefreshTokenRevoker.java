package app.cuentasclaras.application.security;

import app.cuentasclaras.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revoca tokens en una transaccion independiente (REQUIRES_NEW) para que el
 * cambio se confirme aunque la operacion que lo provoca acabe lanzando una
 * excepcion (por ejemplo, la deteccion de reuso de un refresh token).
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }
}
