package app.cuentasclaras.application.balance;

import app.cuentasclaras.application.group.GroupAccessGuard;
import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.model.ExpenseShare;
import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.MemberBalance;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.model.Settlement;
import app.cuentasclaras.domain.model.SettlementPlan;
import app.cuentasclaras.domain.repository.ExpenseRepository;
import app.cuentasclaras.domain.repository.ExpenseShareRepository;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.repository.SettlementRepository;
import app.cuentasclaras.domain.service.BalanceCalculator;
import app.cuentasclaras.domain.service.SettlementPlanner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso de balances: saldo neto por miembro, deudas por pares y plan de
 * liquidacion. Todo se calcula en el dominio a partir de gastos y pagos; nada
 * se persiste aqui (el plan es solo una sugerencia).
 */
@Service
public class BalanceService {

    private final ExpenseRepository expenses;
    private final ExpenseShareRepository shares;
    private final SettlementRepository settlements;
    private final GroupMemberRepository members;
    private final GroupAccessGuard guard;

    public BalanceService(ExpenseRepository expenses, ExpenseShareRepository shares,
                          SettlementRepository settlements, GroupMemberRepository members,
                          GroupAccessGuard guard) {
        this.expenses = expenses;
        this.shares = shares;
        this.settlements = settlements;
        this.members = members;
        this.guard = guard;
    }

    /** Balance completo: netos por miembro y deudas por pares. */
    @Transactional(readOnly = true)
    public GroupBalances balances(UUID userId, UUID groupId) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);
        Snapshot snapshot = snapshot(groupId);
        SettlementPlan plan = SettlementPlanner.plan(snapshot.nets());
        return new GroupBalances(group.currency(), memberBalances(snapshot, group.currency()), plan);
    }

    /** Solo el plan de liquidacion (minimo de transferencias que salda el grupo). */
    @Transactional(readOnly = true)
    public GroupPlan settlementPlan(UUID userId, UUID groupId) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);
        return new GroupPlan(group.currency(), SettlementPlanner.plan(snapshot(groupId).nets()));
    }

    private Snapshot snapshot(UUID groupId) {
        List<GroupMemberView> views = members.findViewsByGroup(groupId);
        List<Expense> groupExpenses = expenses.findActiveByGroup(groupId);
        List<ExpenseShare> groupShares = shares.findByExpenseIds(groupExpenses.stream().map(Expense::id).toList());
        List<Settlement> groupSettlements = settlements.findActiveByGroup(groupId);
        Map<UUID, Long> nets = BalanceCalculator.netBalances(
                views.stream().map(GroupMemberView::userId).toList(), groupExpenses, groupShares, groupSettlements);
        return new Snapshot(views, nets);
    }

    /**
     * Incluye a todos los miembros no dados de baja y, ademas, a cualquier
     * miembro que se haya ido pero conserve un saldo pendiente (para no ocultar
     * deudas).
     */
    private static List<MemberBalance> memberBalances(Snapshot snapshot, String currency) {
        List<MemberBalance> result = new ArrayList<>();
        for (GroupMemberView view : snapshot.views()) {
            long net = snapshot.nets().getOrDefault(view.userId(), 0L);
            if (view.status() != MemberStatus.LEFT || net != 0) {
                result.add(new MemberBalance(view.userId(), view.displayName(), net, currency));
            }
        }
        return result;
    }

    private record Snapshot(List<GroupMemberView> views, Map<UUID, Long> nets) {
    }
}
