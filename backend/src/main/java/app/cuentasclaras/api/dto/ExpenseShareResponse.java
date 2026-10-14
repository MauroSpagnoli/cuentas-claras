package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.ExpenseShare;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parte resuelta de un gasto. {@code percentage} y {@code weight} reflejan el
 * parametro original del reparto (nulos si no aplica).
 */
public record ExpenseShareResponse(
        UUID userId,
        long amountMinor,
        BigDecimal percentage,
        BigDecimal weight
) {

    public static ExpenseShareResponse from(ExpenseShare share) {
        return new ExpenseShareResponse(
                share.userId(),
                share.shareMinor(),
                share.percentage(),
                share.weight()
        );
    }
}
