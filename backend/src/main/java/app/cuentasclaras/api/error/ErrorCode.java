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
    NOT_MEMBER(HttpStatus.FORBIDDEN, "No perteneces a este grupo"),
    INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN, "Permisos insuficientes en el grupo"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "El email ya está registrado"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso no encontrado"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Grupo no encontrado"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Usuario no encontrado"),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "Código de invitación no válido"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "El usuario ya pertenece al grupo"),
    LAST_OWNER(HttpStatus.CONFLICT, "El grupo debe conservar al menos un OWNER"),
    CONFLICT(HttpStatus.CONFLICT, "Conflicto de versión o estado"),
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
