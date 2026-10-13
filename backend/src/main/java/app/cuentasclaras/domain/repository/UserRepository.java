package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de usuarios.
 */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
