package app.cuentasclaras.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de la parte de un gasto asignada a un usuario.
 */
@Entity
@Table(name = "expense_shares")
public class ExpenseShareJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "expense_id", nullable = false, updatable = false)
    private UUID expenseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "share_minor", nullable = false)
    private long shareMinor;

    @Column(name = "weight", precision = 12, scale = 4)
    private BigDecimal weight;

    @Column(name = "percentage", precision = 7, scale = 4)
    private BigDecimal percentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpenseShareJpaEntity() {
        // Requerido por JPA
    }

    public ExpenseShareJpaEntity(UUID id, UUID expenseId, UUID userId, long shareMinor, BigDecimal weight,
                                 BigDecimal percentage, Instant createdAt) {
        this.id = id;
        this.expenseId = expenseId;
        this.userId = userId;
        this.shareMinor = shareMinor;
        this.weight = weight;
        this.percentage = percentage;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getShareMinor() {
        return shareMinor;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
