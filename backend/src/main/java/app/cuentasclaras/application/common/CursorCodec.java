package app.cuentasclaras.application.common;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica y decodifica cursores de paginacion opacos.
 * <p>
 * El cursor contiene la clave de ordenacion ({@code key}) y el desempate
 * ({@code id}) en base64url para que el cliente lo trate como opaco. La clave
 * es un texto que cada servicio interpreta segun su orden (por ejemplo
 * {@code Instant} en grupos o {@code LocalDate} en gastos).
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {
    }

    public record Cursor(String key, UUID id) {
    }

    public static String encode(String key, UUID id) {
        String raw = key + SEPARATOR + id;
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
            String key = raw.substring(0, separator);
            UUID id = UUID.fromString(raw.substring(separator + 1));
            if (key.isEmpty()) {
                throw new IllegalArgumentException("cursor sin clave");
            }
            return new Cursor(key, id);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cursor inválido");
        }
    }
}
