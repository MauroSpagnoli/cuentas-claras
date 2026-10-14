package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.SplitType;
import app.cuentasclaras.domain.service.SplitCalculator.ResolvedShare;
import app.cuentasclaras.domain.service.SplitCalculator.ShareInput;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests golden del reparto: los cuatro tipos y el reparto de resto. No depende
 * de Spring.
 */
class SplitCalculatorTest {

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void equalDivideEnPartesIguales() {
        List<ResolvedShare> shares = SplitCalculator.resolve(3000, SplitType.EQUAL,
                List.of(share(U1), share(U2), share(U3)));

        assertThat(shares).allSatisfy(s -> assertThat(s.shareMinor()).isEqualTo(1000));
        assertThat(sum(shares)).isEqualTo(3000);
    }

    @Test
    void equalReparteElRestoAlMenorUserIdDeFormaDeterminista() {
        // Orden de entrada invertido: el centimo sobrante va al menor userId (U1).
        List<ResolvedShare> shares = SplitCalculator.resolve(1000, SplitType.EQUAL,
                List.of(share(U3), share(U1), share(U2)));

        assertThat(amountOf(shares, U1)).isEqualTo(334);
        assertThat(amountOf(shares, U2)).isEqualTo(333);
        assertThat(amountOf(shares, U3)).isEqualTo(333);
        assertThat(sum(shares)).isEqualTo(1000);
    }

    @Test
    void exactAceptaSiLaSumaCuadra() {
        List<ResolvedShare> shares = SplitCalculator.resolve(3000, SplitType.EXACT, List.of(
                exact(U1, 1500), exact(U2, 1000), exact(U3, 500)));

        assertThat(sum(shares)).isEqualTo(3000);
        assertThat(amountOf(shares, U2)).isEqualTo(1000);
    }

    @Test
    void exactRechazaSiLaSumaNoCuadra() {
        assertSplitError(SplitValidationException.SplitError.SPLIT_MISMATCH,
                () -> SplitCalculator.resolve(3000, SplitType.EXACT, List.of(
                        exact(U1, 1000), exact(U2, 1000))));
    }

    @Test
    void percentageReparteElRestoDeCentimos() {
        List<ResolvedShare> shares = SplitCalculator.resolve(3001, SplitType.PERCENTAGE, List.of(
                percentage(U1, "50"), percentage(U2, "50")));

        assertThat(amountOf(shares, U1)).isEqualTo(1501);
        assertThat(amountOf(shares, U2)).isEqualTo(1500);
        assertThat(sum(shares)).isEqualTo(3001);
    }

    @Test
    void percentageRechazaSiNoSuman100() {
        assertSplitError(SplitValidationException.SplitError.PERCENTAGE_SUM,
                () -> SplitCalculator.resolve(3000, SplitType.PERCENTAGE, List.of(
                        percentage(U1, "60"), percentage(U2, "30"))));
    }

    @Test
    void sharesReparteProporcionalAlPeso() {
        List<ResolvedShare> shares = SplitCalculator.resolve(1000, SplitType.SHARES, List.of(
                weight(U1, "2"), weight(U2, "1")));

        assertThat(amountOf(shares, U1)).isEqualTo(667);
        assertThat(amountOf(shares, U2)).isEqualTo(333);
        assertThat(sum(shares)).isEqualTo(1000);
    }

    @Test
    void sharesRechazaSiElPesoTotalEsCero() {
        assertSplitError(SplitValidationException.SplitError.INVALID,
                () -> SplitCalculator.resolve(1000, SplitType.SHARES, List.of(weight(U1, "0"))));
    }

    @Test
    void rechazaParticipantesDuplicados() {
        assertSplitError(SplitValidationException.SplitError.INVALID,
                () -> SplitCalculator.resolve(1000, SplitType.EQUAL, List.of(share(U1), share(U1))));
    }

    private static ShareInput share(UUID userId) {
        return new ShareInput(userId, null, null, null);
    }

    private static ShareInput exact(UUID userId, long amountMinor) {
        return new ShareInput(userId, amountMinor, null, null);
    }

    private static ShareInput percentage(UUID userId, String value) {
        return new ShareInput(userId, null, new BigDecimal(value), null);
    }

    private static ShareInput weight(UUID userId, String value) {
        return new ShareInput(userId, null, null, new BigDecimal(value));
    }

    private static long sum(List<ResolvedShare> shares) {
        return shares.stream().mapToLong(ResolvedShare::shareMinor).sum();
    }

    private static long amountOf(List<ResolvedShare> shares, UUID userId) {
        return shares.stream().filter(s -> s.userId().equals(userId)).findFirst().orElseThrow().shareMinor();
    }

    private static void assertSplitError(SplitValidationException.SplitError expected, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(SplitValidationException.class)
                .satisfies(ex -> assertThat(((SplitValidationException) ex).error()).isEqualTo(expected));
    }
}
