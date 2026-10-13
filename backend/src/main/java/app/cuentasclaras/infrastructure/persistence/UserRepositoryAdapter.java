package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto UserRepository sobre Spring Data JPA.
 */
@Component
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        UserJpaEntity saved = jpaRepository.save(toEntity(user));
        return toDomain(saved);
    }

    private static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getDefaultCurrency(),
                entity.getLocale(),
                entity.getCreatedAt()
        );
    }

    private static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.displayName(),
                user.defaultCurrency(),
                user.locale(),
                user.createdAt()
        );
    }
}
