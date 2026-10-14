package app.cuentasclaras;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke de migraciones: aplica TODO el historial Flyway sobre H2 en modo
 * PostgreSQL y deja que Hibernate (ddl-auto=validate) compruebe que las
 * entidades JPA encajan con el esquema creado por las migraciones.
 * <p>
 * H2 no acepta el alias TIMESTAMPTZ, por lo que se copia el directorio de
 * migraciones a target/flyway-smoke sustituyendo TIMESTAMPTZ por
 * TIMESTAMP WITH TIME ZONE. Las migraciones reales siguen apuntando a Postgres.
 */
@SpringBootTest
@ActiveProfiles("smoke")
class MigrationSmokeH2Test {

    private static final Path SMOKE_DIR = Paths.get("target/flyway-smoke");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        prepareMigrations();
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:migsmoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "filesystem:target/flyway-smoke");
    }

    private static void prepareMigrations() throws IOException {
        Files.createDirectories(SMOKE_DIR);
        Path source = Paths.get("src/main/resources/db/migration");
        List<Path> migrations;
        try (Stream<Path> files = Files.list(source)) {
            migrations = files.toList();
        }
        for (Path migration : migrations) {
            String sql = Files.readString(migration)
                    .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
            Files.writeString(SMOKE_DIR.resolve(migration.getFileName().toString()), sql);
        }
        assertThat(migrations).isNotEmpty();
    }

    @Test
    void migracionesYEsquemaCoinciden() {
        // Si el contexto arranca, Flyway aplico V1..V9 y Hibernate valido el mapeo.
    }
}
