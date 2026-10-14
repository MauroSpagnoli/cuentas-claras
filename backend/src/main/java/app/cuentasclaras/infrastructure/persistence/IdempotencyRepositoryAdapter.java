package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.IdempotencyRecord;
import app.cuentasclaras.domain.repository.IdempotencyRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto IdempotencyRepository sobre Spring Data JPA.
 */
@Component
class IdempotencyRepositoryAdapter implements IdempotencyRepository {

    private final IdempotencyKeyJpaRepository jpaRepository;

    IdempotencyRepositoryAdapter(IdempotencyKeyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key) {
        return jpaRepository.findByUserIdAndKey(userId, key)
                .map(IdempotencyRepositoryAdapter::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        // saveAndFlush para que una violacion de unicidad (carrera) falle aqui
        return toDomain(jpaRepository.saveAndFlush(toEntity(record)));
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    private static IdempotencyRecord toDomain(IdempotencyKeyJpaEntity entity) {
        return new IdempotencyRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getKey(),
                entity.getEndpoint(),
                entity.getRequestHash(),
                entity.getResponseStatus(),
                entity.getResponseBody(),
                entity.getCreatedAt()
        );
    }

    private static IdempotencyKeyJpaEntity toEntity(IdempotencyRecord record) {
        return new IdempotencyKeyJpaEntity(
                record.id(),
                record.userId(),
                record.key(),
                record.endpoint(),
                record.requestHash(),
                record.responseStatus(),
                record.responseBody(),
                record.createdAt()
        );
    }
}
