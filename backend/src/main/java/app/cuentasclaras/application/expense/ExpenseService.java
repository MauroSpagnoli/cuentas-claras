package app.cuentasclaras.application.expense;

import app.cuentasclaras.api.dto.CreateExpenseRequest;
import app.cuentasclaras.api.dto.ExpenseShareInput;
import app.cuentasclaras.api.dto.UpdateExpenseRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.common.CursorCodec;
import app.cuentasclaras.application.group.GroupAccessGuard;
import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.model.ExpenseDetail;
import app.cuentasclaras.domain.model.ExpenseShare;
import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.model.Page;
import app.cuentasclaras.domain.model.SplitType;
import app.cuentasclaras.domain.repository.ExpenseQuery;
import app.cuentasclaras.domain.repository.ExpenseRepository;
import app.cuentasclaras.domain.repository.ExpenseShareRepository;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.service.SplitCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Casos de uso de gastos: alta con reparto, listado paginado con filtros,
 * detalle, edicion y borrado logico. La invariante
 * {@code sum(shareMinor) == amountMinor} la garantiza {@link SplitCalculator}.
 */
@Service
public class ExpenseService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ExpenseRepository expenses;
    private final ExpenseShareRepository shares;
    private final GroupMemberRepository members;
    private final GroupAccessGuard guard;

    public ExpenseService(ExpenseRepository expenses, ExpenseShareRepository shares,
                          GroupMemberRepository members, GroupAccessGuard guard) {
        this.expenses = expenses;
        this.shares = shares;
        this.members = members;
        this.guard = guard;
    }

    @Transactional
    public ExpenseDetail create(UUID userId, UUID groupId, CreateExpenseRequest request) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);

        String currency = resolveCurrency(request.currency(), group.currency());
        List<SplitCalculator.ResolvedShare> resolved =
                SplitCalculator.resolve(request.amountMinor(), request.splitType(), toInputs(request.shares()));

        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(request.paidByUserId());
        resolved.forEach(share -> participants.add(share.userId()));
        requireActiveParticipants(groupId, participants);

        Instant now = Instant.now();
        Expense expense = new Expense(
                UUID.randomUUID(), groupId, request.description().trim(), request.amountMinor(), currency,
                request.paidByUserId(), request.splitType(), trimToNull(request.category()), request.expenseDate(),
                trimToNull(request.notes()), userId, now, now, null, 0L);
        Expense saved = expenses.save(expense);
        List<ExpenseShare> shareEntities = persistShares(saved.id(), resolved, now);
        return new ExpenseDetail(saved, shareEntities);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDetail> list(UUID userId, UUID groupId, Integer limit, String cursor,
                                    LocalDate from, LocalDate to, UUID paidByUserId, String category) {
        guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);

        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = (cursor == null || cursor.isBlank()) ? null : CursorCodec.decode(cursor);
        LocalDate cursorDate = decoded == null ? null : LocalDate.parse(decoded.key());
        UUID cursorId = decoded == null ? null : decoded.id();

        ExpenseQuery query = new ExpenseQuery(groupId, from, to, paidByUserId, trimToNull(category));
        List<Expense> found = expenses.findPage(query, cursorDate, cursorId, size + 1);

        boolean hasMore = found.size() > size;
        List<Expense> page = hasMore ? found.subList(0, size) : found;

        Map<UUID, List<ExpenseShare>> byExpense = shares
                .findByExpenseIds(page.stream().map(Expense::id).toList())
                .stream()
                .collect(Collectors.groupingBy(ExpenseShare::expenseId));
        List<ExpenseDetail> items = page.stream()
                .map(expense -> new ExpenseDetail(expense, byExpense.getOrDefault(expense.id(), List.of())))
                .toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Expense last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.expenseDate().toString(), last.id());
        }
        return new Page<>(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public ExpenseDetail get(UUID userId, UUID expenseId) {
        Expense expense = requireExpense(expenseId);
        guard.requireMembership(expense.groupId(), userId);
        return new ExpenseDetail(expense, shares.findByExpenseId(expenseId));
    }

    @Transactional
    public ExpenseDetail update(UUID userId, UUID expenseId, UpdateExpenseRequest request) {
        Expense current = requireExpense(expenseId);
        requireEditor(current, userId);

        if (request.version() != null && !request.version().equals(current.version())) {
            throw new ApiException(ErrorCode.CONFLICT, "El gasto fue modificado por otra petición");
        }

        SplitType splitType = request.splitType() != null ? request.splitType() : current.splitType();
        long amountMinor = request.amountMinor() != null ? request.amountMinor() : current.amountMinor();
        UUID paidByUserId = request.paidByUserId() != null ? request.paidByUserId() : current.paidByUserId();

        List<SplitCalculator.ShareInput> inputs = request.shares() != null
                ? toInputs(request.shares())
                : existingInputs(shares.findByExpenseId(expenseId), splitType);
        List<SplitCalculator.ResolvedShare> resolved = SplitCalculator.resolve(amountMinor, splitType, inputs);

        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(paidByUserId);
        resolved.forEach(share -> participants.add(share.userId()));
        requireActiveParticipants(current.groupId(), participants);

        Instant now = Instant.now();
        Expense updated = new Expense(
                current.id(), current.groupId(),
                request.description() != null ? request.description().trim() : current.description(),
                amountMinor, current.currency(), paidByUserId, splitType,
                request.category() != null ? trimToNull(request.category()) : current.category(),
                request.expenseDate() != null ? request.expenseDate() : current.expenseDate(),
                request.notes() != null ? trimToNull(request.notes()) : current.notes(),
                current.createdBy(), current.createdAt(), now, current.deletedAt(), current.version());
        Expense saved = expenses.save(updated);

        shares.deleteByExpenseId(saved.id());
        List<ExpenseShare> shareEntities = persistShares(saved.id(), resolved, now);
        return new ExpenseDetail(saved, shareEntities);
    }

    @Transactional
    public void delete(UUID userId, UUID expenseId) {
        Expense current = requireExpense(expenseId);
        requireEditor(current, userId);

        Instant now = Instant.now();
        Expense deleted = new Expense(
                current.id(), current.groupId(), current.description(), current.amountMinor(), current.currency(),
                current.paidByUserId(), current.splitType(), current.category(), current.expenseDate(),
                current.notes(), current.createdBy(), current.createdAt(), now, now, current.version());
        expenses.save(deleted);
    }

    private Expense requireExpense(UUID expenseId) {
        return expenses.findById(expenseId)
                .filter(expense -> !expense.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.EXPENSE_NOT_FOUND, "Gasto no encontrado"));
    }

    /** Exige ser miembro activo y, si no es el creador, tener rol ADMIN/OWNER. */
    private GroupMember requireEditor(Expense expense, UUID userId) {
        GroupMember member = guard.requireMembership(expense.groupId(), userId);
        if (!expense.createdBy().equals(userId) && !member.isManager()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE,
                    "Solo el creador o un ADMIN/OWNER pueden modificar el gasto");
        }
        return member;
    }

    private List<ExpenseShare> persistShares(UUID expenseId, List<SplitCalculator.ResolvedShare> resolved,
                                             Instant now) {
        List<ExpenseShare> entities = resolved.stream()
                .map(share -> new ExpenseShare(UUID.randomUUID(), expenseId, share.userId(), share.shareMinor(),
                        share.weight(), share.percentage(), now))
                .toList();
        return shares.saveAll(entities);
    }

    private static List<SplitCalculator.ShareInput> toInputs(List<ExpenseShareInput> inputs) {
        return inputs.stream()
                .map(share -> new SplitCalculator.ShareInput(share.userId(), share.amountMinor(),
                        share.percentage(), share.weight()))
                .toList();
    }

    /** Reconstruye los parametros del reparto a partir de las partes actuales. */
    private static List<SplitCalculator.ShareInput> existingInputs(List<ExpenseShare> current, SplitType splitType) {
        return current.stream()
                .map(share -> new SplitCalculator.ShareInput(
                        share.userId(),
                        splitType == SplitType.EXACT ? share.shareMinor() : null,
                        splitType == SplitType.PERCENTAGE ? share.percentage() : null,
                        splitType == SplitType.SHARES ? share.weight() : null))
                .toList();
    }

    private void requireActiveParticipants(UUID groupId, Collection<UUID> userIds) {
        for (UUID id : userIds) {
            boolean active = members.findByGroupAndUser(groupId, id)
                    .filter(member -> member.status() == MemberStatus.ACTIVE)
                    .isPresent();
            if (!active) {
                throw new ApiException(ErrorCode.INVALID_PARTICIPANT,
                        "El usuario " + id + " no es un miembro activo del grupo");
            }
        }
    }

    private static String resolveCurrency(String requested, String groupCurrency) {
        if (requested == null) {
            return groupCurrency;
        }
        String normalized = requested.toUpperCase(Locale.ROOT);
        if (!normalized.equals(groupCurrency)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "La moneda del gasto debe coincidir con la del grupo (" + groupCurrency + ")");
        }
        return normalized;
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
