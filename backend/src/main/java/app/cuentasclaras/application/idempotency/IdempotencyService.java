package app.cuentasclaras.application.idempotency;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.domain.model.IdempotencyRecord;
import app.cuentasclaras.domain.repository.IdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Soporta el patron de idempotencia de las mutaciones: reservar la clave,
 * ejecutar la operacion y completarla con la respuesta, para que un reintento
 * con la misma {@code Idempotency-Key} devuelva el resultado original sin
 * duplicar el efecto.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Devuelve la respuesta ya almacenada para la clave (reintento). Lanza 409 si
     * la clave se uso con otro cuerpo o si hay una operacion en curso.
     */
    @Transactional(readOnly = true)
    public Optional<StoredResponse> findCompleted(UUID userId, String key, String requestHash) {
        Optional<IdempotencyRecord> found = repository.findByUserAndKey(userId, key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = found.get();
        if (!record.requestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "La Idempotency-Key ya fue usada con un cuerpo distinto");
        }
        if (record.isPending()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Hay una operación en curso con esa Idempotency-Key");
        }
        return Optional.of(new StoredResponse(record.responseStatus(), record.responseBody()));
    }

    /** Reserva la clave antes de ejecutar. Lanza 409 si ya existe. */
    @Transactional
    public void reserve(UUID userId, String key, String endpoint, String requestHash) {
        if (repository.findByUserAndKey(userId, key).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Hay una operación en curso con esa Idempotency-Key");
        }
        repository.save(new IdempotencyRecord(UUID.randomUUID(), userId, key, endpoint, requestHash,
                null, null, Instant.now()));
    }

    /** Guarda la respuesta para poder repetirla. */
    @Transactional
    public void complete(UUID userId, String key, int status, String body) {
        repository.findByUserAndKey(userId, key).ifPresent(record ->
                repository.save(new IdempotencyRecord(record.id(), record.userId(), record.key(),
                        record.endpoint(), record.requestHash(), status, body, record.createdAt())));
    }

    /** Libera la reserva si la operacion fallo, permitiendo reintentar. */
    @Transactional
    public void release(UUID userId, String key) {
        repository.findByUserAndKey(userId, key)
                .filter(IdempotencyRecord::isPending)
                .ifPresent(record -> repository.delete(record.id()));
    }

    public String hash(Object payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(payload));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo calcular el hash de idempotencia", ex);
        }
    }

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la respuesta idempotente", ex);
        }
    }

    public <T> T deserialize(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo deserializar la respuesta idempotente", ex);
        }
    }

    public record StoredResponse(int status, String body) {
    }
}
