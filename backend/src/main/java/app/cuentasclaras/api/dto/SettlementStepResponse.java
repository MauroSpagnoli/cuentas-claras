package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Transfer;

import java.util.UUID;

public record SettlementStepResponse(
        UUID fromUserId,
        UUID toUserId,
        long amountMinor
) {

    public static SettlementStepResponse from(Transfer transfer) {
        return new SettlementStepResponse(transfer.fromUserId(), transfer.toUserId(), transfer.amountMinor());
    }
}
