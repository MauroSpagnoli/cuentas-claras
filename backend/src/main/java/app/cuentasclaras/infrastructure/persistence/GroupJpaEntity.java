package app.cuentasclaras.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de grupo. La tabla se cita con backticks porque {@code GROUPS} es
 * palabra reservada en H2 (y aun asi PostgreSQL la guarda en minusculas).
 */
@Entity
@Table(name = "`groups`")
public class GroupJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "invite_code", nullable = false, unique = true, length = 32)
    private String inviteCode;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected GroupJpaEntity() {
        // Requerido por JPA
    }

    public GroupJpaEntity(UUID id, String name, String description, String currency, UUID createdBy,
                          String inviteCode, Instant archivedAt, Instant deletedAt, Instant createdAt,
                          Instant updatedAt, Long version) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.currency = currency;
        this.createdBy = createdBy;
        this.inviteCode = inviteCode;
        this.archivedAt = archivedAt;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
