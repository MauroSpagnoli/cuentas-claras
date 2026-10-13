package app.cuentasclaras.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de invitaciones. La URL publica es la base del enlace que se
 * comparte para unirse a un grupo: {@code base-url}/{code}.
 */
@ConfigurationProperties(prefix = "app.invite")
public record InviteProperties(String baseUrl) {
}
