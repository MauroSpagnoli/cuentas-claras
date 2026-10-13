package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de grupo. La moneda es un codigo ISO 4217 de 3 letras.
 */
public record CreateGroupRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217 de 3 letras")
        String currency
) {
}
