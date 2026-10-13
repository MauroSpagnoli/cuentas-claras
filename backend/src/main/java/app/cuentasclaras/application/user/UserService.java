package app.cuentasclaras.application.user;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso del perfil de usuario.
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getProfile(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Usuario no encontrado"));
    }

    @Transactional
    public User updateProfile(UUID userId, String displayName, String defaultCurrency, String locale) {
        User current = getProfile(userId);
        String newDisplayName = displayName != null ? displayName.trim() : current.displayName();
        String newCurrency = defaultCurrency != null
                ? defaultCurrency.toUpperCase(Locale.ROOT)
                : current.defaultCurrency();
        String newLocale = locale != null ? locale : current.locale();
        User updated = new User(
                current.id(),
                current.email(),
                current.passwordHash(),
                newDisplayName,
                newCurrency,
                newLocale,
                current.createdAt()
        );
        return users.save(updated);
    }
}
