package app.cuentasclaras.api.error;

import app.cuentasclaras.domain.service.SplitValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Traduce excepciones a RFC 7807 (application/problem+json).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://cuentas-claras.app/errors/";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return build(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(SplitValidationException.class)
    public ResponseEntity<ProblemDetail> handleSplit(SplitValidationException ex) {
        ErrorCode code = switch (ex.error()) {
            case SPLIT_MISMATCH -> ErrorCode.SPLIT_MISMATCH;
            case PERCENTAGE_SUM -> ErrorCode.PERCENTAGE_SUM;
            case INVALID -> ErrorCode.VALIDATION_ERROR;
        };
        return build(code, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ResponseEntity<ProblemDetail> response =
                build(ErrorCode.VALIDATION_ERROR, "Hay campos inválidos en la petición");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> Map.of(
                        "field", field.getField(),
                        "message", field.getDefaultMessage() == null ? "valor inválido" : field.getDefaultMessage()))
                .toList();
        ProblemDetail body = response.getBody();
        if (body != null) {
            body.setProperty("errors", errors);
        }
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "El cuerpo de la petición no es válido");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Hay parámetros inválidos en la petición");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Parámetro inválido: " + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Falta el parámetro: " + ex.getParameterName());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(ErrorCode.CONFLICT, "El recurso fue modificado por otra petición");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(ErrorCode.CONFLICT, "La operación entra en conflicto con datos existentes");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.title());
    }

    private ResponseEntity<ProblemDetail> build(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setType(URI.create(TYPE_BASE + code.name().toLowerCase()));
        problem.setProperty("code", code.name());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
