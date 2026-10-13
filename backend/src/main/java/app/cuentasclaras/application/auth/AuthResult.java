package app.cuentasclaras.application.auth;

import app.cuentasclaras.domain.model.User;

/**
 * Resultado de una operacion de autenticacion: tokens + usuario.
 */
public record AuthResult(String accessToken, String refreshToken, long expiresIn, User user) {
}
