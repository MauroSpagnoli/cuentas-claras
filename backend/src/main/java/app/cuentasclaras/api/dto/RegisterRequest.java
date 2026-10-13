package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "es obligatorio")
        @Email(message = "debe ser un email válido")
        @Size(max = 255)
        String email,

        @NotBlank(message = "es obligatoria")
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        String password,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120)
        String displayName,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217 de 3 letras")
        String defaultCurrency
) {
}
