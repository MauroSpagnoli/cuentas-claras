package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.ActivityEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de persistencia del historial de actividad de un grupo.
 */
public interface ActivityRepository {

    ActivityEvent save(ActivityEvent event);

    /**
     * Eventos de un grupo ordenados por {@code occurred_at DESC, id DESC}. Si
     * {@code cursorOccurredAt} no es nulo devuelve solo los posteriores al
     * cursor.
     */
    List<ActivityEvent> findPage(UUID groupId, Instant cursorOccurredAt, UUID cursorId, int limit);
}
