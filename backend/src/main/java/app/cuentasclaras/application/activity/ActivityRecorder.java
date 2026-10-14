package app.cuentasclaras.application.activity;

import app.cuentasclaras.domain.model.ActivityEvent;
import app.cuentasclaras.domain.model.ActivityType;
import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.ActivityRepository;
import app.cuentasclaras.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Escribe eventos en el historial de un grupo. Se invoca desde los casos de uso
 * de gastos, pagos y miembros dentro de la misma transaccion, de modo que el
 * evento y el cambio de negocio se confirman juntos.
 * <p>
 * El resumen se resuelve aqui (con el nombre del actor) para que el feed se
 * lea sin necesidad de joins.
 */
@Component
public class ActivityRecorder {

    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final String UNKNOWN_ACTOR = "Alguien";

    private final ActivityRepository activities;
    private final UserRepository users;

    public ActivityRecorder(ActivityRepository activities, UserRepository users) {
        this.activities = activities;
        this.users = users;
    }

    public void expenseCreated(UUID groupId, UUID actorId, String description, long amountMinor, Instant at) {
        record(groupId, ActivityType.EXPENSE_CREATED, actorId,
                name(actorId) + " agregó el gasto «" + description + "»", amountMinor, at);
    }

    public void expenseUpdated(UUID groupId, UUID actorId, String description, long amountMinor, Instant at) {
        record(groupId, ActivityType.EXPENSE_UPDATED, actorId,
                name(actorId) + " actualizó el gasto «" + description + "»", amountMinor, at);
    }

    public void expenseDeleted(UUID groupId, UUID actorId, String description, long amountMinor, Instant at) {
        record(groupId, ActivityType.EXPENSE_DELETED, actorId,
                name(actorId) + " eliminó el gasto «" + description + "»", amountMinor, at);
    }

    public void settlementCreated(UUID groupId, UUID actorId, long amountMinor, Instant at) {
        record(groupId, ActivityType.SETTLEMENT_CREATED, actorId,
                name(actorId) + " registró un pago", amountMinor, at);
    }

    public void memberJoined(UUID groupId, UUID actorId, UUID subjectId, Instant at) {
        String summary = actorId.equals(subjectId)
                ? name(actorId) + " se unió al grupo"
                : name(actorId) + " agregó a " + name(subjectId) + " al grupo";
        record(groupId, ActivityType.MEMBER_JOINED, actorId, summary, null, at);
    }

    public void memberLeft(UUID groupId, UUID actorId, UUID subjectId, Instant at) {
        String summary = actorId.equals(subjectId)
                ? name(actorId) + " salió del grupo"
                : name(actorId) + " quitó a " + name(subjectId) + " del grupo";
        record(groupId, ActivityType.MEMBER_LEFT, actorId, summary, null, at);
    }

    private void record(UUID groupId, ActivityType type, UUID actorId, String summary, Long amountMinor,
                        Instant at) {
        ActivityEvent event = new ActivityEvent(UUID.randomUUID(), groupId, type, actorId,
                truncate(summary), amountMinor, at);
        activities.save(event);
    }

    private String name(UUID userId) {
        return users.findById(userId)
                .map(User::displayName)
                .filter(value -> value != null && !value.isBlank())
                .orElse(UNKNOWN_ACTOR);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_SUMMARY_LENGTH ? value : value.substring(0, MAX_SUMMARY_LENGTH);
    }
}
