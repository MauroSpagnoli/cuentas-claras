package app.cuentasclaras.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciales del usuario de demostración que se crea al arrancar en el perfil
 * {@code dev}, para poder entrar en la app sin registrarse.
 * <p>
 * Los valores viven en {@code application-dev.yml}; si falta alguno, se usan los
 * valores por defecto de abajo.
 */
@ConfigurationProperties(prefix = "app.demo-user")
public record DemoUserProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("demo@cuentasclaras.app") String email,
        @DefaultValue("demo1234") String password,
        @DefaultValue("Demo") String displayName,
        @DefaultValue("EUR") String defaultCurrency
) {
}
