package app.cuentasclaras.application.security;

import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.infrastructure.config.JwtProperties;
import app.cuentasclaras.infrastructure.security.JwtKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite y valida access tokens JWT firmados con RS256.
 */
@Service
public class TokenService {

    private final JwtKeyProvider keyProvider;
    private final JwtProperties properties;

    public TokenService(JwtKeyProvider keyProvider, JwtProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("name", user.displayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(keyProvider.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Valida firma, emisor y expiracion y devuelve el id de usuario (subject).
     * Lanza JwtException si el token no es valido.
     */
    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(keyProvider.publicKey())
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
