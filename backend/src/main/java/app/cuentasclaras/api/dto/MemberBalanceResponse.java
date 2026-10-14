package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.MemberBalance;

import java.util.UUID;

public record MemberBalanceResponse(
        UUID userId,
        String displayName,
        long netMinor,
        String currency
) {

    public static MemberBalanceResponse from(MemberBalance balance) {
        return new MemberBalanceResponse(
                balance.userId(),
                balance.displayName(),
                balance.netMinor(),
                balance.currency()
        );
    }
}
