package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Actualizacion parcial del perfil: los campos nulos se ignoran.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 120)
        String displayName,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217 de 3 letras")
        String defaultCurrency,

        @Size(max = 10)
        String locale
) {
}
