package app.cuentasclaras.application.security;

import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.infrastructure.config.JwtProperties;
import app.cuentasclaras.infrastructure.security.JwtKeyProvider;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "cuentas-claras",
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            null,
            null
    );

    private User sampleUser() {
        return new User(UUID.randomUUID(), "ana@example.com", "hash", "Ana", "EUR", null, Instant.now());
    }

    @Test
    void creaYParseaAccessToken() {
        TokenService service = new TokenService(new JwtKeyProvider(PROPS), PROPS);
        User user = sampleUser();

        String token = service.createAccessToken(user);

        assertThat(service.parseUserId(token)).isEqualTo(user.id());
    }

    @Test
    void rechazaTokenExpirado() {
        JwtProperties expired = new JwtProperties(
                "cuentas-claras", Duration.ofMinutes(-1), Duration.ofDays(30), null, null);
        TokenService service = new TokenService(new JwtKeyProvider(expired), expired);
        String token = service.createAccessToken(sampleUser());

        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenDeOtroEmisor() {
        JwtProperties other = new JwtProperties(
                "otro-emisor", Duration.ofMinutes(15), Duration.ofDays(30), null, null);
        TokenService otherService = new TokenService(new JwtKeyProvider(other), other);
        String token = otherService.createAccessToken(sampleUser());

        TokenService service = new TokenService(new JwtKeyProvider(PROPS), PROPS);
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
