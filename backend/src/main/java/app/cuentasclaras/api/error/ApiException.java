package app.cuentasclaras.api.error;

/**
 * Excepcion de negocio con un ErrorCode asociado. Se traduce a RFC 7807
 * en {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        this(code, code.title());
    }

    public ErrorCode code() {
        return code;
    }
}
