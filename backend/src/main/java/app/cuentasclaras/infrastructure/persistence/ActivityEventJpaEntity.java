package app.cuentasclaras.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de un evento de actividad del grupo. Inmutable: no se actualiza
 * ni se borra.
 */
@Entity
@Table(name = "activity_events")
public class ActivityEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private String type;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "summary", nullable = false, updatable = false, length = 300)
    private String summary;

    @Column(name = "amount_minor", updatable = false)
    private Long amountMinor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ActivityEventJpaEntity() {
        // Requerido por JPA
    }

    public ActivityEventJpaEntity(UUID id, UUID groupId, String type, UUID actorUserId, String summary,
                                  Long amountMinor, Instant occurredAt) {
        this.id = id;
        this.groupId = groupId;
        this.type = type;
        this.actorUserId = actorUserId;
        this.summary = summary;
        this.amountMinor = amountMinor;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getType() {
        return type;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getSummary() {
        return summary;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
