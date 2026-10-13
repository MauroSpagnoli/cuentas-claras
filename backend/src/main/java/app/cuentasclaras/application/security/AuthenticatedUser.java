package app.cuentasclaras.application.security;

import java.util.UUID;

/**
 * Principal autenticado que se expone en el SecurityContext.
 */
public record AuthenticatedUser(UUID id) {
}
