package app.cuentasclaras.infrastructure.bootstrap;

import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.UserRepository;
import app.cuentasclaras.infrastructure.config.DemoUserProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El usuario demo del perfil dev se crea al arrancar y es idempotente: volver a
 * ejecutar el seeder no duplica ni altera el usuario existente.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DemoUserSeederTest {

    @Autowired
    private DemoUserSeeder seeder;

    @Autowired
    private UserRepository users;

    @Autowired
    private DemoUserProperties properties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void creaElUsuarioDemoYEsIdempotente() throws Exception {
        String email = properties.email().trim().toLowerCase(Locale.ROOT);

        // Al arrancar el contexto el seeder ya corrio una vez.
        User first = users.findByEmail(email).orElseThrow();
        assertThat(first.displayName()).isEqualTo(properties.displayName().trim());
        assertThat(first.defaultCurrency()).isEqualTo(properties.defaultCurrency().toUpperCase(Locale.ROOT));
        assertThat(passwordEncoder.matches(properties.password(), first.passwordHash())).isTrue();

        // Segunda ejecucion: mismo usuario, mismo id.
        UUID idBefore = first.id();
        seeder.run(new DefaultApplicationArguments());
        Optional<User> after = users.findByEmail(email);
        assertThat(after).isPresent();
        assertThat(after.orElseThrow().id()).isEqualTo(idBefore);
    }
}
