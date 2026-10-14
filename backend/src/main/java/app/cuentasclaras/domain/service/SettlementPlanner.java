package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.PlanAlgorithm;
import app.cuentasclaras.domain.model.SettlementPlan;
import app.cuentasclaras.domain.model.Transfer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Construye un plan de liquidacion: un conjunto de transferencias que deja a
 * todos los miembros a cero a partir de un vector de balances netos que suma 0.
 *
 * <p>Hasta {@link #EXACT_MAX_MEMBERS} participantes se resuelve el minimo
 * exacto partiendo el conjunto en el mayor numero de subconjuntos de suma 0
 * (minimo de transferencias = {@code n - subconjuntos}). Por encima de ese
 * umbral (donde {@code 2^n} deja de ser barato) se usa una heuristica voraz: en
 * cada paso el mayor deudor paga al mayor acreedor.
 *
 * <p>Es logica de dominio pura: no depende de Spring ni de JPA. El resultado es
 * determinista (mismo vector de netos -> mismo plan).
 */
public final class SettlementPlanner {

    /** Umbral a partir del cual se usa la heuristica en lugar del optimo exacto. */
    public static final int EXACT_MAX_MEMBERS = 12;

    private SettlementPlanner() {
    }

    /** Elige el algoritmo segun el numero de participantes con neto distinto de 0. */
    public static SettlementPlan plan(Map<UUID, Long> netBalances) {
        List<Map.Entry<UUID, Long>> nonZero = nonZero(netBalances);
        if (nonZero.size() <= EXACT_MAX_MEMBERS) {
            return new SettlementPlan(PlanAlgorithm.EXACT, exact(nonZero));
        }
        return new SettlementPlan(PlanAlgorithm.GREEDY, greedy(nonZero));
    }

    /** Heuristica voraz: en cada paso el mayor deudor paga al mayor acreedor. */
    public static List<Transfer> greedy(Map<UUID, Long> netBalances) {
        return greedy(nonZero(netBalances));
    }

    /** Minimo exacto de transferencias (subset-sum). Pensado para grupos pequenos. */
    public static List<Transfer> exact(Map<UUID, Long> netBalances) {
        return exact(nonZero(netBalances));
    }

    private static List<Map.Entry<UUID, Long>> nonZero(Map<UUID, Long> netBalances) {
        return netBalances.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .toList();
    }

    private static List<Transfer> greedy(List<Map.Entry<UUID, Long>> nonZero) {
        PriorityQueue<Balance> creditors = new PriorityQueue<>(largestFirst());
        PriorityQueue<Balance> debtors = new PriorityQueue<>(largestFirst());
        for (Map.Entry<UUID, Long> entry : nonZero) {
            if (entry.getValue() > 0) {
                creditors.add(new Balance(entry.getKey(), entry.getValue()));
            } else {
                debtors.add(new Balance(entry.getKey(), -entry.getValue()));
            }
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance creditor = creditors.poll();
            Balance debtor = debtors.poll();
            long amount = Math.min(creditor.amount(), debtor.amount());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), amount));
            if (creditor.amount() > amount) {
                creditors.add(new Balance(creditor.userId(), creditor.amount() - amount));
            }
            if (debtor.amount() > amount) {
                debtors.add(new Balance(debtor.userId(), debtor.amount() - amount));
            }
        }
        return transfers;
    }

    /**
     * Programacion dinamica sobre subconjuntos: {@code best[mask]} es el maximo
     * numero de subconjuntos de suma 0 en que se puede partir {@code mask}.
     */
    private static List<Transfer> exact(List<Map.Entry<UUID, Long>> nonZero) {
        int size = nonZero.size();
        if (size == 0) {
            return List.of();
        }

        UUID[] users = new UUID[size];
        long[] nets = new long[size];
        for (int i = 0; i < size; i++) {
            users[i] = nonZero.get(i).getKey();
            nets[i] = nonZero.get(i).getValue();
        }

        int full = (1 << size) - 1;
        long[] sum = new long[1 << size];
        for (int mask = 1; mask <= full; mask++) {
            int low = mask & -mask;
            sum[mask] = sum[mask ^ low] + nets[Integer.numberOfTrailingZeros(low)];
        }
        if (sum[full] != 0) {
            throw new IllegalArgumentException("Los balances netos deben sumar cero");
        }

        int[] best = new int[1 << size];
        int[] group = new int[1 << size];
        Arrays.fill(best, -1);
        best[0] = 0;
        for (int mask = 1; mask <= full; mask++) {
            for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
                if (sum[sub] == 0 && best[mask ^ sub] >= 0 && best[mask ^ sub] + 1 > best[mask]) {
                    best[mask] = best[mask ^ sub] + 1;
                    group[mask] = sub;
                }
            }
        }

        List<Transfer> transfers = new ArrayList<>();
        int mask = full;
        while (mask != 0) {
            int sub = group[mask];
            transfers.addAll(greedy(groupEntries(sub, users, nets)));
            mask ^= sub;
        }
        return transfers;
    }

    private static List<Map.Entry<UUID, Long>> groupEntries(int mask, UUID[] users, long[] nets) {
        List<Map.Entry<UUID, Long>> group = new ArrayList<>();
        for (int i = 0; i < users.length; i++) {
            if ((mask & (1 << i)) != 0) {
                group.add(Map.entry(users[i], nets[i]));
            }
        }
        return group;
    }

    private static Comparator<Balance> largestFirst() {
        return Comparator.comparingLong(Balance::amount).reversed()
                .thenComparing(balance -> balance.userId().toString());
    }

    private record Balance(UUID userId, long amount) {
    }
}
