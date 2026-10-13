package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.AuthTokensResponse;
import app.cuentasclaras.api.dto.LoginRequest;
import app.cuentasclaras.api.dto.RefreshRequest;
import app.cuentasclaras.api.dto.RegisterRequest;
import app.cuentasclaras.application.auth.AuthResult;
import app.cuentasclaras.application.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokensResponse register(@Valid @RequestBody RegisterRequest request,
                                       HttpServletRequest httpRequest) {
        AuthResult result = authService.register(request, userAgent(httpRequest), clientIp(httpRequest));
        return AuthTokensResponse.from(result);
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest httpRequest) {
        AuthResult result = authService.login(request, userAgent(httpRequest), clientIp(httpRequest));
        return AuthTokensResponse.from(result);
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request,
                                      HttpServletRequest httpRequest) {
        AuthResult result = authService.refresh(request.refreshToken(), userAgent(httpRequest), clientIp(httpRequest));
        return AuthTokensResponse.from(result);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.USER_AGENT);
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
