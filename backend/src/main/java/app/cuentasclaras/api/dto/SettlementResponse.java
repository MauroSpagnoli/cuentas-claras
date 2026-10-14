package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Settlement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID groupId,
        UUID fromUserId,
        UUID toUserId,
        long amountMinor,
        String currency,
        LocalDate settledOn,
        String note,
        Instant createdAt
) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.id(),
                settlement.groupId(),
                settlement.fromUserId(),
                settlement.toUserId(),
                settlement.amountMinor(),
                settlement.currency(),
                settlement.settledOn(),
                settlement.note(),
                settlement.createdAt()
        );
    }
}
