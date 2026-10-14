package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de idempotencia de una mutacion (clave + hash del cuerpo + respuesta
 * almacenada para poder repetirla sin duplicar efectos).
 */
public record IdempotencyRecord(
        UUID id,
        UUID userId,
        String key,
        String endpoint,
        String requestHash,
        Integer responseStatus,
        String responseBody,
        Instant createdAt
) {

    public boolean isPending() {
        return responseBody == null;
    }
}
