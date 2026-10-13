package app.cuentasclaras.api.dto;

import java.time.Instant;

public record InviteResponse(
        String code,
        String url,
        Instant expiresAt
) {
}
