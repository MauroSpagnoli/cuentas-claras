package app.cuentasclaras.api.dto;

import app.cuentasclaras.application.balance.GroupBalances;

import java.util.List;

public record BalancesResponse(
        String currency,
        List<MemberBalanceResponse> members,
        List<PairwiseDebtResponse> pairs
) {

    public static BalancesResponse from(GroupBalances balances) {
        return new BalancesResponse(
                balances.currency(),
                balances.members().stream().map(MemberBalanceResponse::from).toList(),
                balances.plan().transfers().stream().map(PairwiseDebtResponse::from).toList()
        );
    }
}
