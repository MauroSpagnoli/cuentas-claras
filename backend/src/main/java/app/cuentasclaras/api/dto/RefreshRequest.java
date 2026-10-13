package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "es obligatorio")
        String refreshToken
) {
}
