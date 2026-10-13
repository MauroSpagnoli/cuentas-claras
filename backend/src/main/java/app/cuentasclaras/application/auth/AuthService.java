package app.cuentasclaras.application.auth;

import app.cuentasclaras.api.dto.LoginRequest;
import app.cuentasclaras.api.dto.RegisterRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.security.RefreshTokenService;
import app.cuentasclaras.application.security.TokenService;
import app.cuentasclaras.domain.model.RefreshToken;
import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de autenticacion: registro, login, refresco y logout.
 */
@Service
public class AuthService {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String userAgent, String ip) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL, "El email ya está registrado");
        }
        String currency = request.defaultCurrency() == null
                ? DEFAULT_CURRENCY
                : request.defaultCurrency().toUpperCase(Locale.ROOT);
        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                currency,
                null,
                Instant.now()
        );
        User saved = users.save(user);
        return issueFor(saved, userAgent, ip);
    }

    @Transactional
    public AuthResult login(LoginRequest request, String userAgent, String ip) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Credenciales inválidas"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Credenciales inválidas");
        }
        return issueFor(user, userAgent, ip);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ip) {
        RefreshToken current = refreshTokens.requireActive(rawRefreshToken);
        User user = users.findById(current.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Usuario no encontrado"));

        RefreshTokenService.IssuedToken issued = refreshTokens.issue(user.id(), userAgent, ip);
        refreshTokens.revoke(current.id(), issued.token().id());

        return new AuthResult(
                tokenService.createAccessToken(user),
                issued.rawToken(),
                tokenService.accessTokenTtlSeconds(),
                user
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken current = refreshTokens.requireActive(rawRefreshToken);
        refreshTokens.revoke(current.id(), null);
    }

    private AuthResult issueFor(User user, String userAgent, String ip) {
        RefreshTokenService.IssuedToken issued = refreshTokens.issue(user.id(), userAgent, ip);
        return new AuthResult(
                tokenService.createAccessToken(user),
                issued.rawToken(),
                tokenService.accessTokenTtlSeconds(),
                user
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
