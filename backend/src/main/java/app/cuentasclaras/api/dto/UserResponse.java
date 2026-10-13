package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String defaultCurrency,
        String locale,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.defaultCurrency(),
                user.locale(),
                user.createdAt()
        );
    }
}
