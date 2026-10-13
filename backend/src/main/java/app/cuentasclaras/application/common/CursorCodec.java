package app.cuentasclaras.application.common;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica y decodifica cursores de paginacion opacos.
 * <p>
 * El cursor contiene la clave de ordenacion ({@code createdAt}) y el
 * desempate ({@code id}) en base64url para que el cliente lo trate como opaco.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {
    }

    public record Cursor(Instant createdAt, UUID id) {
    }

    public static String encode(Cursor cursor) {
        String raw = cursor.createdAt() + SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw new IllegalArgumentException("cursor sin separador");
            }
            Instant createdAt = Instant.parse(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new Cursor(createdAt, id);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cursor inválido");
        }
    }
}
