package app.cuentasclaras.domain.service;

/**
 * Error de validacion de un reparto. Se traduce a un codigo de la API en
 * {@code GlobalExceptionHandler} (sin acoplar el dominio a la capa HTTP).
 */
public class SplitValidationException extends RuntimeException {

    public enum SplitError {
        /** La suma de las partes no coincide con el importe. */
        SPLIT_MISMATCH,
        /** Los porcentajes no suman 100. */
        PERCENTAGE_SUM,
        /** Reparto estructuralmente invalido (vacío, negativos, sin peso...). */
        INVALID
    }

    private final SplitError error;

    public SplitValidationException(SplitError error, String message) {
        super(message);
        this.error = error;
    }

    public SplitError error() {
        return error;
    }
}
