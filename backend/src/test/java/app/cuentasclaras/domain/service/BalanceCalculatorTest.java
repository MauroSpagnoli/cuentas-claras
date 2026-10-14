package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.Expense;
import app.cuentasclaras.domain.model.ExpenseShare;
import app.cuentasclaras.domain.model.Settlement;
import app.cuentasclaras.domain.model.SplitType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del calculo de netos: gastos, repartos y pagos. No depende de Spring.
 */
class BalanceCalculatorTest {

    private static final UUID ANA = uuid(1);
    private static final UUID BETO = uuid(2);
    private static final UUID CARLA = uuid(3);
    private static final UUID DORA = uuid(4);

    @Test
    void elPagadorSumaYLosParticipantesRestan() {
        Expense cena = expense(ANA, 3000);
        List<ExpenseShare> shares = List.of(share(cena, ANA, 1000), share(cena, BETO, 1000),
                share(cena, CARLA, 1000));

        Map<UUID, Long> nets = BalanceCalculator.netBalances(
                List.of(ANA, BETO, CARLA), List.of(cena), shares, List.of());

        assertThat(nets.get(ANA)).isEqualTo(2000);
        assertThat(nets.get(BETO)).isEqualTo(-1000);
        assertThat(nets.get(CARLA)).isEqualTo(-1000);
        assertThat(sum(nets)).isZero();
    }

    @Test
    void losPagosReducenLaDeuda() {
        Expense cena = expense(ANA, 3000);
        List<ExpenseShare> shares = List.of(share(cena, ANA, 1000), share(cena, BETO, 1000),
                share(cena, CARLA, 1000));
        // Beto le devuelve 1000 a Ana.
        Settlement pago = new Settlement(uuid(90), uuid(91), BETO, ANA, 1000, "EUR",
                LocalDate.of(2026, 4, 2), null, BETO, Instant.now(), null);

        Map<UUID, Long> nets = BalanceCalculator.netBalances(
                List.of(ANA, BETO, CARLA), List.of(cena), shares, List.of(pago));

        assertThat(nets.get(ANA)).isEqualTo(1000);
        assertThat(nets.get(BETO)).isZero();
        assertThat(nets.get(CARLA)).isEqualTo(-1000);
        assertThat(sum(nets)).isZero();
    }

    @Test
    void incluyeMiembrosSinActividadConNetoCero() {
        Map<UUID, Long> nets = BalanceCalculator.netBalances(
                List.of(ANA, BETO, CARLA, DORA), List.of(), List.of(), List.of());

        assertThat(nets).containsOnlyKeys(ANA, BETO, CARLA, DORA);
        assertThat(nets.values()).allMatch(value -> value == 0);
    }

    @Test
    void laSumaDeNetosEsSiempreCero() {
        Expense uno = expense(ANA, 1001);
        Expense dos = expense(BETO, 777);
        List<ExpenseShare> shares = List.of(
                share(uno, ANA, 334), share(uno, BETO, 334), share(uno, CARLA, 333),
                share(dos, ANA, 300), share(dos, CARLA, 477));
        Settlement pago = new Settlement(uuid(90), uuid(91), CARLA, ANA, 200, "EUR",
                LocalDate.of(2026, 4, 3), null, CARLA, Instant.now(), null);

        Map<UUID, Long> nets = BalanceCalculator.netBalances(
                List.of(ANA, BETO, CARLA), List.of(uno, dos), shares, List.of(pago));

        assertThat(sum(nets)).isZero();
    }

    private static long sum(Map<UUID, Long> nets) {
        return nets.values().stream().mapToLong(Long::longValue).sum();
    }

    private static Expense expense(UUID paidBy, long amountMinor) {
        return new Expense(UUID.randomUUID(), uuid(80), "Gasto", amountMinor, "EUR", paidBy,
                SplitType.EQUAL, null, LocalDate.of(2026, 4, 1), null, paidBy,
                Instant.now(), Instant.now(), null, 0L);
    }

    private static ExpenseShare share(Expense expense, UUID userId, long amountMinor) {
        return new ExpenseShare(UUID.randomUUID(), expense.id(), userId, amountMinor, null, null, Instant.now());
    }

    private static UUID uuid(int index) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", index));
    }
}
