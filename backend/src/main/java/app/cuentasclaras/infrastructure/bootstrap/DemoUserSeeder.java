package app.cuentasclaras.infrastructure.bootstrap;

import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.UserRepository;
import app.cuentasclaras.infrastructure.config.DemoUserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Crea el usuario de demostración al arrancar en el perfil {@code dev} para poder
 * probar la app sin pasar por el registro.
 * <p>
 * Es <b>idempotente</b>: si el email ya existe no hace nada. Se puede desactivar
 * con {@code app.demo-user.enabled=false}.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.demo-user", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DemoUserProperties properties;

    DemoUserSeeder(UserRepository users,
                   PasswordEncoder passwordEncoder,
                   DemoUserProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            log.debug("Usuario demo '{}' ya existe; no se crea de nuevo", email);
            return;
        }
        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(properties.password()),
                properties.displayName().trim(),
                properties.defaultCurrency().toUpperCase(Locale.ROOT),
                null,
                Instant.now()
        );
        users.save(user);
        // Credencial de desarrollo (no hay registros reales en el perfil dev).
        log.info("Usuario demo creado -> email={} password={} (desactivable con app.demo-user.enabled=false)",
                email, properties.password());
    }
}
