package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.RefreshToken;
import app.cuentasclaras.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto RefreshTokenRepository sobre Spring Data JPA.
 */
@Component
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(RefreshToken token) {
        jpaRepository.save(toEntity(token));
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenRepositoryAdapter::toDomain);
    }

    @Override
    public void revoke(UUID id, UUID replacedById, Instant revokedAt) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setRevokedAt(revokedAt);
            entity.setReplacedById(replacedById);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        jpaRepository.revokeAllForUser(userId, revokedAt);
    }

    private static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedById(),
                entity.getUserAgent(),
                entity.getIp(),
                entity.getCreatedAt()
        );
    }

    private static RefreshTokenJpaEntity toEntity(RefreshToken token) {
        return new RefreshTokenJpaEntity(
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.expiresAt(),
                token.revokedAt(),
                token.replacedById(),
                token.userAgent(),
                token.ip(),
                token.createdAt()
        );
    }
}
