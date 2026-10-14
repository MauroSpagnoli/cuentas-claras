package app.cuentasclaras.domain.service;

import app.cuentasclaras.domain.model.SplitType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resuelve un reparto en importes exactos (unidades minimas) y garantiza la
 * invariante {@code sum(shareMinor) == amountMinor}.
 *
 * <p>El resto no divisible (centimos sobrantes) se asigna siempre a las
 * primeras partes ordenadas por {@code userId}, de forma determinista.
 *
 * <p>Es logica de dominio pura: no depende de Spring ni de JPA.
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    /**
     * @param userId      participante
     * @param amountMinor importe para {@code EXACT} (en el resto de tipos se ignora)
     * @param percentage  porcentaje (0-100) para {@code PERCENTAGE}
     * @param weight      peso relativo para {@code SHARES}
     */
    public record ShareInput(UUID userId, Long amountMinor, BigDecimal percentage, BigDecimal weight) {
    }

    public record ResolvedShare(UUID userId, long shareMinor, BigDecimal percentage, BigDecimal weight) {
    }

    public static List<ResolvedShare> resolve(long amountMinor, SplitType splitType, List<ShareInput> shares) {
        if (amountMinor <= 0) {
            throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                    "El importe debe ser mayor que cero");
        }
        if (shares == null || shares.isEmpty()) {
            throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                    "El reparto necesita al menos una parte");
        }
        Set<UUID> seen = new HashSet<>();
        for (ShareInput share : shares) {
            if (share.userId() == null || !seen.add(share.userId())) {
                throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                        "Cada participante debe aparecer exactamente una vez");
            }
        }
        return switch (splitType) {
            case EQUAL -> equal(amountMinor, shares);
            case EXACT -> exact(amountMinor, shares);
            case PERCENTAGE -> percentage(amountMinor, shares);
            case SHARES -> shares(amountMinor, shares);
        };
    }

    private static List<ResolvedShare> equal(long amountMinor, List<ShareInput> shares) {
        int count = shares.size();
        long base = amountMinor / count;
        long remainder = amountMinor % count;
        Set<UUID> withExtra = firstByUserId(shares, (int) remainder);
        List<ResolvedShare> result = new ArrayList<>(count);
        for (ShareInput share : shares) {
            long amount = base + (withExtra.contains(share.userId()) ? 1 : 0);
            result.add(new ResolvedShare(share.userId(), amount, null, null));
        }
        return result;
    }

    private static List<ResolvedShare> exact(long amountMinor, List<ShareInput> shares) {
        long total = 0;
        for (ShareInput share : shares) {
            if (share.amountMinor() == null || share.amountMinor() < 0) {
                throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                        "Cada parte EXACT necesita un amountMinor >= 0");
            }
            total += share.amountMinor();
        }
        if (total != amountMinor) {
            throw new SplitValidationException(SplitValidationException.SplitError.SPLIT_MISMATCH,
                    "La suma de las partes (" + total + ") no es igual al importe (" + amountMinor + ")");
        }
        List<ResolvedShare> result = new ArrayList<>(shares.size());
        for (ShareInput share : shares) {
            result.add(new ResolvedShare(share.userId(), share.amountMinor(), null, null));
        }
        return result;
    }

    private static List<ResolvedShare> percentage(long amountMinor, List<ShareInput> shares) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShareInput share : shares) {
            if (share.percentage() == null || share.percentage().signum() < 0) {
                throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                        "Cada parte PERCENTAGE necesita un percentage >= 0");
            }
            total = total.add(share.percentage());
        }
        if (total.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new SplitValidationException(SplitValidationException.SplitError.PERCENTAGE_SUM,
                    "Los porcentajes suman " + total.stripTrailingZeros().toPlainString() + ", no 100");
        }
        long[] floors = new long[shares.size()];
        long sum = 0;
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal ratio = shares.get(i).percentage()
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            floors[i] = floor(amountMinor, ratio);
            sum += floors[i];
        }
        Set<UUID> withExtra = firstByUserId(shares, (int) (amountMinor - sum));
        List<ResolvedShare> result = new ArrayList<>(shares.size());
        for (int i = 0; i < shares.size(); i++) {
            ShareInput share = shares.get(i);
            long amount = floors[i] + (withExtra.contains(share.userId()) ? 1 : 0);
            result.add(new ResolvedShare(share.userId(), amount, share.percentage(), null));
        }
        return result;
    }

    private static List<ResolvedShare> shares(long amountMinor, List<ShareInput> shares) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ShareInput share : shares) {
            if (share.weight() == null || share.weight().signum() < 0) {
                throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                        "Cada parte SHARES necesita un weight >= 0");
            }
            totalWeight = totalWeight.add(share.weight());
        }
        if (totalWeight.signum() <= 0) {
            throw new SplitValidationException(SplitValidationException.SplitError.INVALID,
                    "Al menos una parte debe tener weight > 0");
        }
        long[] floors = new long[shares.size()];
        long sum = 0;
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal ratio = shares.get(i).weight().divide(totalWeight, 10, RoundingMode.HALF_UP);
            floors[i] = floor(amountMinor, ratio);
            sum += floors[i];
        }
        Set<UUID> withExtra = firstByUserId(shares, (int) (amountMinor - sum));
        List<ResolvedShare> result = new ArrayList<>(shares.size());
        for (int i = 0; i < shares.size(); i++) {
            ShareInput share = shares.get(i);
            long amount = floors[i] + (withExtra.contains(share.userId()) ? 1 : 0);
            result.add(new ResolvedShare(share.userId(), amount, null, share.weight()));
        }
        return result;
    }

    private static long floor(long amountMinor, BigDecimal ratio) {
        return BigDecimal.valueOf(amountMinor).multiply(ratio).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** Los {@code count} participantes con menor {@code userId} (orden estable). */
    private static Set<UUID> firstByUserId(List<ShareInput> shares, int count) {
        if (count <= 0) {
            return Set.of();
        }
        List<UUID> ordered = shares.stream()
                .map(ShareInput::userId)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        return new HashSet<>(ordered.subList(0, Math.min(count, ordered.size())));
    }
}
