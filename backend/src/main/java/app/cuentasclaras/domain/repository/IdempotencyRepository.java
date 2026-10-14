package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de claves de idempotencia.
 */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key);

    IdempotencyRecord save(IdempotencyRecord record);

    void delete(UUID id);
}
