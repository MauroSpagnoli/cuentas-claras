package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.model.ExpenseShare;
import app.cuentasclaras.domain.model.Settlement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calcula el balance neto por miembro a partir de gastos, repartos y pagos.
 *
 * <p>{@code neto(u) = pagado(u) - debe(u) + pagos(u) - cobros(u)}. La suma de
 * todos los netos es siempre 0: los gastos y los pagos mueven dinero entre los
 * miembros, no lo crean ni lo destruyen.
 *
 * <p>Es logica de dominio pura: no depende de Spring ni de JPA.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * @param memberIds   miembros del grupo (para incluir tambien los que quedan a 0)
     * @param expenses    gastos activos del grupo
     * @param shares      partes de esos gastos
     * @param settlements pagos registrados (activos)
     * @return neto por miembro, con todos los {@code memberIds} presentes
     */
    public static Map<UUID, Long> netBalances(Collection<UUID> memberIds,
                                              Collection<Expense> expenses,
                                              Collection<ExpenseShare> shares,
                                              Collection<Settlement> settlements) {
        Map<UUID, Long> net = new LinkedHashMap<>();
        for (UUID id : memberIds) {
            net.put(id, 0L);
        }
        for (Expense expense : expenses) {
            net.merge(expense.paidByUserId(), expense.amountMinor(), Long::sum);
        }
        for (ExpenseShare share : shares) {
            net.merge(share.userId(), -share.shareMinor(), Long::sum);
        }
        for (Settlement settlement : settlements) {
            net.merge(settlement.fromUserId(), settlement.amountMinor(), Long::sum);
            net.merge(settlement.toUserId(), -settlement.amountMinor(), Long::sum);
        }
        return net;
    }
}
