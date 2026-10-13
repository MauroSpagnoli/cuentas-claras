package app.cuentasclaras.infrastructure.security;

import app.cuentasclaras.api.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Respuesta 403 en formato RFC 7807 cuando el usuario autenticado no tiene permiso.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String TYPE_BASE = "https://cuentas-claras.app/errors/";

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "No tiene permiso para acceder a este recurso");
        problem.setTitle(ErrorCode.FORBIDDEN.title());
        problem.setType(URI.create(TYPE_BASE + ErrorCode.FORBIDDEN.name().toLowerCase()));
        problem.setProperty("code", ErrorCode.FORBIDDEN.name());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
