package app.cuentasclaras.api.error;

import org.springframework.http.HttpStatus;

/**
 * Catalogo de errores de aplicacion con su estado HTTP y titulo legible.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Petición inválida"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "No autenticado"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token reutilizado"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acceso denegado"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "El email ya está registrado"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso no encontrado"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
