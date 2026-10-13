package app.cuentasclaras.api.dto;

import app.cuentasclaras.application.auth.AuthResult;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthTokensResponse from(AuthResult result) {
        return new AuthTokensResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.expiresIn(),
                UserResponse.from(result.user())
        );
    }
}
