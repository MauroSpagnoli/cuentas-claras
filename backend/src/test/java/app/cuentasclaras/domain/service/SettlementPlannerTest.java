package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.PlanAlgorithm;
import app.cuentasclaras.domain.model.SettlementPlan;
import app.cuentasclaras.domain.model.Transfer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del plan de liquidacion: golden cases, optimalidad del modo exacto y
 * tests de propiedad con vectores de netos aleatorios. No depende de Spring.
 */
class SettlementPlannerTest {

    @Test
    void sinDeudasElPlanEstaVacio() {
        Map<UUID, Long> nets = nets(0, 0, 0);

        assertThat(SettlementPlanner.greedy(nets)).isEmpty();
        SettlementPlan plan = SettlementPlanner.plan(nets);
        assertThat(plan.algorithm()).isEqualTo(PlanAlgorithm.EXACT);
        assertThat(plan.transfers()).isEmpty();
        assertThat(plan.transactionCount()).isZero();
    }

    @Test
    void unDeudorVariosAcreedores() {
        // [+5, +5, -10] -> 2 transferencias
        Map<UUID, Long> nets = nets(5, 5, -10);

        List<Transfer> transfers = SettlementPlanner.greedy(nets);

        assertThat(transfers).hasSize(2);
        assertThat(clears(nets, transfers)).isTrue();
    }

    @Test
    void variosDeudoresUnAcreedor() {
        // [-20, -10, +30] -> 2 transferencias
        Map<UUID, Long> nets = nets(-20, -10, 30);

        List<Transfer> transfers = SettlementPlanner.greedy(nets);

        assertThat(transfers).hasSize(2);
        assertThat(clears(nets, transfers)).isTrue();
    }

    @Test
    void elPlanEsDeterminista() {
        Map<UUID, Long> nets = nets(7, -3, 4, -8);

        assertThat(SettlementPlanner.plan(nets).transfers())
                .isEqualTo(SettlementPlanner.plan(nets).transfers());
    }

    @Test
    void exactoParteElVectorEnSubconjuntosDeSumaCero() {
        // Dos grupos disjuntos {+10,-10} y {+3,-3} -> 2 transferencias.
        Map<UUID, Long> nets = nets(10, -10, 3, -3);

        SettlementPlan plan = SettlementPlanner.plan(nets);

        assertThat(plan.algorithm()).isEqualTo(PlanAlgorithm.EXACT);
        assertThat(plan.transfers()).hasSize(2);
        assertThat(clears(nets, plan.transfers())).isTrue();
    }

    @Test
    void elExactoEsMejorQueElVorazEnGruposDisjuntos() {
        // 5 pares (+k,-k) y un trio {+5,+4,-9}: 6 subconjuntos de suma 0.
        // Optimo: 13 - 6 = 7 transferencias. El voraz cruza grupos y usa mas.
        Map<UUID, Long> nets = nets(10, -10, 9, -9, 8, -8, 7, -7, 6, -6, 5, 4, -9);

        List<Transfer> greedy = SettlementPlanner.greedy(nets);
        List<Transfer> exact = SettlementPlanner.exact(nets);

        assertThat(clears(nets, greedy)).isTrue();
        assertThat(clears(nets, exact)).isTrue();
        assertThat(exact).hasSize(7);
        assertThat(greedy).hasSizeGreaterThan(exact.size());
    }

    @Test
    void elPlanUsaExactoHastaElUmbralYVorazPorEncima() {
        Map<UUID, Long> doce = nets(1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6);
        Map<UUID, Long> trece = nets(10, -10, 9, -9, 8, -8, 7, -7, 6, -6, 5, 4, -9);

        assertThat(SettlementPlanner.plan(doce).algorithm()).isEqualTo(PlanAlgorithm.EXACT);
        assertThat(SettlementPlanner.plan(trece).algorithm()).isEqualTo(PlanAlgorithm.GREEDY);
    }

    @Test
    void ambosAlgoritmosSaldanVectoresAleatorios() {
        Random random = new Random(20260414);
        for (int iteration = 0; iteration < 300; iteration++) {
            Map<UUID, Long> nets = randomZeroSum(random, 2 + random.nextInt(7));

            List<Transfer> greedy = SettlementPlanner.greedy(nets);
            SettlementPlan plan = SettlementPlanner.plan(nets);

            assertThat(clears(nets, greedy))
                    .as("el voraz salda %s", nets)
                    .isTrue();
            assertThat(clears(nets, plan.transfers()))
                    .as("el plan salda %s", nets)
                    .isTrue();
            assertThat(plan.transfers()).hasSizeLessThanOrEqualTo(greedy.size());
            assertThat(greedy).hasSizeLessThanOrEqualTo(Math.max(1, nonzero(nets) - 1));
        }
    }

    @Test
    void rechazaVectoresQueNoSumanCero() {
        assertThatThrownBy(() -> SettlementPlanner.exact(nets(5, -3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static boolean clears(Map<UUID, Long> nets, List<Transfer> transfers) {
        Map<UUID, Long> remaining = new LinkedHashMap<>(nets);
        for (Transfer transfer : transfers) {
            if (transfer.amountMinor() <= 0 || transfer.fromUserId().equals(transfer.toUserId())) {
                return false;
            }
            remaining.merge(transfer.fromUserId(), transfer.amountMinor(), Long::sum);
            remaining.merge(transfer.toUserId(), -transfer.amountMinor(), Long::sum);
        }
        return remaining.values().stream().allMatch(value -> value == 0);
    }

    private static int nonzero(Map<UUID, Long> nets) {
        return (int) nets.values().stream().filter(value -> value != 0).count();
    }

    private static Map<UUID, Long> randomZeroSum(Random random, int size) {
        long[] values = new long[size];
        long sum = 0;
        for (int i = 0; i < size - 1; i++) {
            long value = random.nextInt(101) - 50;
            if (value == 0) {
                value = 1;
            }
            values[i] = value;
            sum += value;
        }
        values[size - 1] = -sum;
        if (values[size - 1] == 0) {
            values[size - 1] = 1;
            values[0] -= 1;
        }
        Map<UUID, Long> nets = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            nets.put(uuid(i + 1), values[i]);
        }
        return nets;
    }

    private static Map<UUID, Long> nets(long... values) {
        Map<UUID, Long> nets = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            nets.put(uuid(i + 1), values[i]);
        }
        return nets;
    }

    private static UUID uuid(int index) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", index));
    }
}
