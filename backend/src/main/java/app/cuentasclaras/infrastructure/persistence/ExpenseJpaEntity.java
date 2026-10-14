package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.SplitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entidad JPA de gasto. El importe se guarda en unidades minimas (centimos).
 */
@Entity
@Table(name = "expenses")
public class ExpenseJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "paid_by_user_id", nullable = false)
    private UUID paidByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 16)
    private SplitType splitType;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ExpenseJpaEntity() {
        // Requerido por JPA
    }

    public ExpenseJpaEntity(UUID id, UUID groupId, String description, long amountMinor, String currency,
                            UUID paidByUserId, SplitType splitType, String category, LocalDate expenseDate,
                            String notes, UUID createdBy, Instant createdAt, Instant updatedAt, Instant deletedAt,
                            Long version) {
        this.id = id;
        this.groupId = groupId;
        this.description = description;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.paidByUserId = paidByUserId;
        this.splitType = splitType;
        this.category = category;
        this.expenseDate = expenseDate;
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getPaidByUserId() {
        return paidByUserId;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }
}
