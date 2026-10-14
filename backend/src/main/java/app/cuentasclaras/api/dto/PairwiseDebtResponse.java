package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Transfer;

import java.util.UUID;

/**
 * Deuda minima entre dos miembros: {@code fromUserId} debe {@code amountMinor}
 * a {@code toUserId}.
 */
public record PairwiseDebtResponse(
        UUID fromUserId,
        UUID toUserId,
        long amountMinor
) {

    public static PairwiseDebtResponse from(Transfer transfer) {
        return new PairwiseDebtResponse(transfer.fromUserId(), transfer.toUserId(), transfer.amountMinor());
    }
}
