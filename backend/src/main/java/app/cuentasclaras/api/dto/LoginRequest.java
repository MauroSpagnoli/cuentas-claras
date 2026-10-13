package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "es obligatorio")
        @Email(message = "debe ser un email válido")
        String email,

        @NotBlank(message = "es obligatoria")
        String password
) {
}
