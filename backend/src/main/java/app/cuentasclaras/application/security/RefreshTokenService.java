package app.cuentasclaras.application.security;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.domain.model.RefreshToken;
import app.cuentasclaras.domain.repository.RefreshTokenRepository;
import app.cuentasclaras.infrastructure.config.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida de los refresh tokens opacos: emision, validacion,
 * rotacion y revocacion (con deteccion de reuso).
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenRevoker revoker;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository,
                               RefreshTokenRevoker revoker,
                               JwtProperties properties) {
        this.repository = repository;
        this.revoker = revoker;
        this.properties = properties;
    }

    @Transactional
    public IssuedToken issue(UUID userId, String userAgent, String ip) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(),
                userId,
                sha256(rawToken),
                now.plus(properties.refreshTokenTtl()),
                null,
                null,
                userAgent,
                ip,
                now
        );
        repository.save(token);
        return new IssuedToken(rawToken, token);
    }

    @Transactional
    public RefreshToken requireActive(String rawToken) {
        RefreshToken token = repository.findByHash(sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN,
                        "Refresh token desconocido"));
        if (token.isRevoked()) {
            // Posible robo: se revoca toda la cadena de sesiones del usuario.
            // En una transaccion aparte para que persista pese al rollback del error.
            revoker.revokeAllForUser(token.userId());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token reutilizado; se han revocado todas las sesiones");
        }
        if (token.isExpired(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token expirado");
        }
        return token;
    }

    @Transactional
    public void revoke(UUID tokenId, UUID replacedById) {
        repository.revoke(tokenId, replacedById, Instant.now());
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record IssuedToken(String rawToken, RefreshToken token) {
    }
}
