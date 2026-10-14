package app.cuentasclaras.infrastructure.persistence;

import app.cuentasclaras.domain.model.ExpenseShare;
import app.cuentasclaras.domain.repository.ExpenseShareRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador que implementa el puerto ExpenseShareRepository sobre Spring Data JPA.
 */
@Component
class ExpenseShareRepositoryAdapter implements ExpenseShareRepository {

    private final ExpenseShareJpaRepository jpaRepository;

    ExpenseShareRepositoryAdapter(ExpenseShareJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ExpenseShare> saveAll(List<ExpenseShare> shares) {
        return jpaRepository.saveAll(shares.stream().map(ExpenseShareRepositoryAdapter::toEntity).toList())
                .stream()
                .map(ExpenseShareRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ExpenseShare> findByExpenseId(UUID expenseId) {
        return jpaRepository.findByExpenseId(expenseId).stream()
                .map(ExpenseShareRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ExpenseShare> findByExpenseIds(Collection<UUID> expenseIds) {
        if (expenseIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByExpenseIdIn(expenseIds).stream()
                .map(ExpenseShareRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public void deleteByExpenseId(UUID expenseId) {
        jpaRepository.deleteByExpenseId(expenseId);
    }

    private static ExpenseShare toDomain(ExpenseShareJpaEntity entity) {
        return new ExpenseShare(
                entity.getId(),
                entity.getExpenseId(),
                entity.getUserId(),
                entity.getShareMinor(),
                entity.getWeight(),
                entity.getPercentage(),
                entity.getCreatedAt()
        );
    }

    private static ExpenseShareJpaEntity toEntity(ExpenseShare share) {
        return new ExpenseShareJpaEntity(
                share.id(),
                share.expenseId(),
                share.userId(),
                share.shareMinor(),
                share.weight(),
                share.percentage(),
                share.createdAt()
        );
    }
}
