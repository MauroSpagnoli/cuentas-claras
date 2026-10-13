package app.cuentasclaras.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracion JWT. Las claves PEM son opcionales: si no se definen se genera
 * un par RSA efimero al arrancar (solo para desarrollo).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String privateKey,
        String publicKey
) {
}
