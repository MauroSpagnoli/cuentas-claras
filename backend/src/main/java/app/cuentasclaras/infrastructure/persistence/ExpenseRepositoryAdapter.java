package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.repository.ExpenseQuery;
import app.cuentasclaras.domain.repository.ExpenseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto ExpenseRepository sobre Spring Data JPA.
 * Los filtros opcionales se construyen con Specifications para no enviar
 * parametros nulos al motor (problema de tipado en PostgreSQL).
 */
@Component
class ExpenseRepositoryAdapter implements ExpenseRepository {

    private static final Sort ORDER = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));

    private final ExpenseJpaRepository jpaRepository;

    ExpenseRepositoryAdapter(ExpenseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Expense save(Expense expense) {
        // saveAndFlush para que el @Version se incremente antes de mapear a dominio
        return toDomain(jpaRepository.saveAndFlush(toEntity(expense)));
    }

    @Override
    public Optional<Expense> findById(UUID id) {
        return jpaRepository.findById(id).map(ExpenseRepositoryAdapter::toDomain);
    }

    @Override
    public List<Expense> findPage(ExpenseQuery query, LocalDate cursorDate, UUID cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, ORDER);
        return jpaRepository.findAll(specification(query, cursorDate, cursorId), pageable)
                .getContent().stream()
                .map(ExpenseRepositoryAdapter::toDomain)
                .toList();
    }

    private static Specification<ExpenseJpaEntity> specification(ExpenseQuery query, LocalDate cursorDate,
                                                                 UUID cursorId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("groupId"), query.groupId()));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), query.to()));
            }
            if (query.paidByUserId() != null) {
                predicates.add(cb.equal(root.get("paidByUserId"), query.paidByUserId()));
            }
            if (query.category() != null) {
                predicates.add(cb.equal(root.get("category"), query.category()));
            }
            if (cursorDate != null && cursorId != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("expenseDate"), cursorDate),
                        cb.and(cb.equal(root.get("expenseDate"), cursorDate),
                                cb.lessThan(root.get("id"), cursorId))
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Expense toDomain(ExpenseJpaEntity entity) {
        return new Expense(
                entity.getId(),
                entity.getGroupId(),
                entity.getDescription(),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getPaidByUserId(),
                entity.getSplitType(),
                entity.getCategory(),
                entity.getExpenseDate(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getVersion()
        );
    }

    private static ExpenseJpaEntity toEntity(Expense expense) {
        return new ExpenseJpaEntity(
                expense.id(),
                expense.groupId(),
                expense.description(),
                expense.amountMinor(),
                expense.currency(),
                expense.paidByUserId(),
                expense.splitType(),
                expense.category(),
                expense.expenseDate(),
                expense.notes(),
                expense.createdBy(),
                expense.createdAt(),
                expense.updatedAt(),
                expense.deletedAt(),
                expense.version()
        );
    }
}
